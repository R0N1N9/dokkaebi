package dev.dokkaebi.tablet.recon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Basic network reconnaissance primitives. Everything is a plain TCP connect / DNS / HTTP
 * operation from an unprivileged app — no raw sockets, no root, nothing that modifies targets.
 */
object NetTools {

    /** Well-known TCP ports used by the "top" preset and for service labels. */
    val COMMON_PORTS: Map<Int, String> = linkedMapOf(
        21 to "ftp", 22 to "ssh", 23 to "telnet", 25 to "smtp", 53 to "dns", 80 to "http",
        81 to "http-alt", 88 to "kerberos", 110 to "pop3", 111 to "rpcbind", 135 to "msrpc",
        139 to "netbios-ssn", 143 to "imap", 389 to "ldap", 443 to "https", 445 to "smb",
        465 to "smtps", 514 to "rsh", 515 to "printer", 548 to "afp", 554 to "rtsp",
        587 to "submission", 631 to "ipp", 636 to "ldaps", 873 to "rsync", 993 to "imaps",
        995 to "pop3s", 1080 to "socks", 1433 to "mssql", 1521 to "oracle", 1723 to "pptp",
        1883 to "mqtt", 1900 to "upnp", 2049 to "nfs", 2375 to "docker", 2376 to "docker-tls",
        3000 to "http-dev", 3306 to "mysql", 3389 to "rdp", 4443 to "https-alt", 5000 to "upnp/http",
        5060 to "sip", 5353 to "mdns", 5432 to "postgres", 5555 to "adb", 5900 to "vnc",
        5985 to "winrm", 6379 to "redis", 6443 to "k8s-api", 7000 to "http-alt", 8000 to "http-alt",
        8008 to "http-alt", 8080 to "http-proxy", 8081 to "http-alt", 8443 to "https-alt",
        8883 to "mqtt-tls", 8888 to "http-alt", 9000 to "http-alt", 9090 to "http-alt",
        9100 to "jetdirect", 9200 to "elasticsearch", 11211 to "memcached", 27017 to "mongodb",
        32400 to "plex", 49152 to "upnp",
    )

    /** Ports probed during host discovery: a refused or accepted connect proves the host is up. */
    val DISCOVERY_PORTS = listOf(80, 443, 22, 445, 139, 53, 8080, 62078, 5353, 7)

    private val HTTP_PORTS = setOf(80, 81, 3000, 5000, 7000, 8000, 8008, 8080, 8081, 8888, 9000, 9090)
    private val TLS_PORTS = setOf(443, 465, 636, 993, 995, 2376, 4443, 6443, 8443, 8883)

    fun serviceName(port: Int) = COMMON_PORTS[port] ?: "unknown"

    /**
     * Parses a port spec such as `top`, `all`, `22`, `22,80,443`, `8000-8100`, or a mix.
     * Returns sorted distinct ports in 1..65535; throws IllegalArgumentException on bad input.
     */
    fun parsePorts(spec: String): List<Int> {
        val s = spec.trim().lowercase()
        if (s.isEmpty() || s == "top") return COMMON_PORTS.keys.sorted()
        if (s == "all") return (1..65535).toList()
        val out = sortedSetOf<Int>()
        for (part in s.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }) {
            if (part == "top") { out += COMMON_PORTS.keys; continue }
            val range = part.split('-')
            require(range.size in 1..2) { "Bad port token: $part" }
            val a = range[0].toIntOrNull() ?: throw IllegalArgumentException("Bad port: $part")
            val b = if (range.size == 2) range[1].toIntOrNull() ?: throw IllegalArgumentException("Bad port: $part") else a
            require(a in 1..65535 && b in 1..65535 && a <= b) { "Port out of range: $part" }
            for (p in a..b) out += p
        }
        require(out.isNotEmpty()) { "No ports given" }
        return out.toList()
    }

    fun ipv4ToInt(ip: String): Int {
        val parts = ip.split('.')
        require(parts.size == 4) { "Not an IPv4 address: $ip" }
        return parts.fold(0) { acc, p ->
            val v = p.toIntOrNull() ?: throw IllegalArgumentException("Not an IPv4 address: $ip")
            require(v in 0..255) { "Not an IPv4 address: $ip" }
            (acc shl 8) or v
        }
    }

    fun intToIpv4(v: Int) = "${(v ushr 24) and 255}.${(v ushr 16) and 255}.${(v ushr 8) and 255}.${v and 255}"

    /**
     * Host addresses in the subnet of [ip]/[prefix], excluding network and broadcast.
     * Subnets larger than /[minPrefix] are narrowed to the /[minPrefix] around [ip] to keep scans quick.
     */
    fun subnetHosts(ip: String, prefix: Int, minPrefix: Int = 24): List<String> {
        val p = prefix.coerceIn(minPrefix, 30)
        val mask = if (p == 0) 0 else (-1 shl (32 - p))
        val net = ipv4ToInt(ip) and mask
        val size = 1 shl (32 - p)
        return (1 until size - 1).map { intToIpv4(net + it) }
    }

    fun cidr(ip: String, prefix: Int, minPrefix: Int = 24): String {
        val p = prefix.coerceIn(minPrefix, 30)
        val mask = -1 shl (32 - p)
        return "${intToIpv4(ipv4ToInt(ip) and mask)}/$p"
    }

    sealed interface Probe {
        data class Open(val ms: Long) : Probe
        data class Closed(val ms: Long) : Probe   // RST: port closed but host is alive
        data object Filtered : Probe               // timeout / unreachable
    }

    /** Single TCP connect probe. */
    fun tcpProbe(host: String, port: Int, timeoutMs: Int): Probe {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            Probe.Open((System.nanoTime() - start) / 1_000_000)
        } catch (e: ConnectException) {
            val ms = (System.nanoTime() - start) / 1_000_000
            if (e.message?.contains("refused", ignoreCase = true) == true) Probe.Closed(ms) else Probe.Filtered
        } catch (e: IOException) {
            Probe.Filtered
        }
    }

    data class Host(val ip: String, val hostname: String?, val latencyMs: Long, val openPorts: List<Int>, val via: String)

    /** Checks whether [ip] is up: ICMP/echo via isReachable, then TCP probes of common ports. */
    fun probeHost(ip: String, timeoutMs: Int, resolveNames: Boolean = true): Host? {
        val open = mutableListOf<Int>()
        var latency: Long? = null
        var via = ""
        for ((i, port) in DISCOVERY_PORTS.withIndex()) {
            when (val r = tcpProbe(ip, port, timeoutMs)) {
                is Probe.Open -> { open += port; if (latency == null) { latency = r.ms; via = "tcp/$port open" } }
                is Probe.Closed -> if (latency == null) { latency = r.ms; via = "tcp/$port rst" }
                Probe.Filtered -> {}
            }
            // Once the host is known to be up, a few ports are enough for a fingerprint hint.
            if (latency != null && i >= 3) break
        }
        if (latency == null) {
            val start = System.nanoTime()
            val reachable = runCatching { InetAddress.getByName(ip).isReachable(timeoutMs) }.getOrDefault(false)
            if (reachable) { latency = (System.nanoTime() - start) / 1_000_000; via = "echo" }
        }
        if (latency == null) return null
        val name = if (resolveNames) reverseLookup(ip) else null
        return Host(ip, name, latency, open, via)
    }

    /** Sweeps [hosts] concurrently, emitting each live host. [onProgress] gets (done, total). */
    fun discover(
        hosts: List<String>,
        timeoutMs: Int = 350,
        concurrency: Int = 48,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Flow<Host> = channelFlow {
        val sem = Semaphore(concurrency)
        var done = 0
        val lock = Any()
        for (ip in hosts) launch {
            sem.withPermit {
                probeHost(ip, timeoutMs)?.let { send(it) }
                val d = synchronized(lock) { ++done }
                onProgress(d, hosts.size)
            }
        }
    }.flowOn(Dispatchers.IO)

    data class PortResult(val port: Int, val service: String, val latencyMs: Long, val banner: String?)

    /** TCP connect scan of [ports] on [host], emitting open ports (with a banner if one is offered). */
    fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 400,
        concurrency: Int = 128,
        grabBanners: Boolean = true,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Flow<PortResult> = channelFlow {
        val addr = withContext(Dispatchers.IO) { InetAddress.getByName(host).hostAddress!! }
        val sem = Semaphore(concurrency)
        var done = 0
        val lock = Any()
        for (port in ports) launch {
            sem.withPermit {
                val r = tcpProbe(addr, port, timeoutMs)
                if (r is Probe.Open) {
                    val banner = if (grabBanners) grabBanner(addr, port, host, timeoutMs.coerceAtLeast(800)) else null
                    send(PortResult(port, serviceName(port), r.ms, banner))
                }
                val d = synchronized(lock) { ++done }
                onProgress(d, ports.size)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Reads what a service announces. HTTP ports get a HEAD request and report the status
     * line and Server header; TLS ports are reported as such; everything else is read passively
     * (SSH, FTP, SMTP, POP3, IMAP, Redis-style banners).
     */
    fun grabBanner(ip: String, port: Int, hostHeader: String = ip, timeoutMs: Int = 1000): String? {
        if (port in TLS_PORTS) return "TLS service"
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                s.soTimeout = timeoutMs
                if (port in HTTP_PORTS) {
                    s.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: $hostHeader\r\nUser-Agent: recon\r\n\r\n".toByteArray())
                }
                val buf = ByteArray(1024)
                val n = try { s.getInputStream().read(buf) } catch (_: SocketTimeoutException) { -1 }
                if (n <= 0) return null
                val text = String(buf, 0, n, Charsets.ISO_8859_1)
                summarizeBanner(text)
            }
        } catch (_: IOException) {
            null
        }
    }

    /** First meaningful line of a banner; for HTTP, "status line · Server: x". */
    fun summarizeBanner(raw: String): String? {
        val lines = raw.split("\r\n", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val first = lines[0].filter { it.code in 32..126 }.take(120)
        if (first.startsWith("HTTP/")) {
            val server = lines.firstOrNull { it.startsWith("server:", ignoreCase = true) }
            return if (server != null) "$first · ${server.take(80)}" else first
        }
        return first.ifEmpty { null }
    }

    fun reverseLookup(ip: String): String? = runCatching {
        val name = InetAddress.getByName(ip).canonicalHostName
        name.takeIf { it != ip }
    }.getOrNull()

    data class DnsResult(val query: String, val addresses: List<String>, val reverse: String?, val ms: Long)

    /** Forward lookup (all A/AAAA records the resolver returns), plus reverse lookup if given an IP. */
    suspend fun dnsLookup(query: String): DnsResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        val start = System.nanoTime()
        val addrs = InetAddress.getAllByName(q).map { it.hostAddress ?: it.toString() }
        val ms = (System.nanoTime() - start) / 1_000_000
        val isIp = runCatching { ipv4ToInt(q) }.isSuccess || q.contains(':')
        DnsResult(q, addrs.distinct(), if (isIp) reverseLookup(q) else null, ms)
    }

    /** Security response headers worth checking, with a short note on what each mitigates. */
    val SECURITY_HEADERS = linkedMapOf(
        "Strict-Transport-Security" to "forces HTTPS (HSTS)",
        "Content-Security-Policy" to "limits script/resource origins (XSS)",
        "X-Frame-Options" to "blocks framing (clickjacking)",
        "X-Content-Type-Options" to "stops MIME sniffing",
        "Referrer-Policy" to "limits referrer leakage",
        "Permissions-Policy" to "restricts browser features",
    )

    data class HeaderCheck(val name: String, val present: Boolean, val value: String?, val note: String)

    fun checkSecurityHeaders(headers: Map<String, String>, https: Boolean): List<HeaderCheck> {
        val lower = headers.mapKeys { it.key.lowercase() }
        return SECURITY_HEADERS.map { (name, note) ->
            val v = lower[name.lowercase()]
            val present = v != null || (name == "X-Frame-Options" && lower["content-security-policy"]?.contains("frame-ancestors") == true)
            // HSTS is meaningless over plain HTTP; report it as missing there regardless.
            HeaderCheck(name, present && (https || name != "Strict-Transport-Security"), v, note)
        }
    }

    data class TlsInfo(val cipher: String, val subject: String, val issuer: String, val notAfter: String, val daysLeft: Long, val sans: List<String>)

    data class HttpReport(
        val url: String,
        val code: Int,
        val message: String?,
        val ms: Long,
        val headers: List<Pair<String, String>>,
        val checks: List<HeaderCheck>,
        val tls: TlsInfo?,
        val cookies: List<String>,
    )

    /** Fetches [rawUrl] (HEAD, falling back to GET) without following redirects and audits the response. */
    suspend fun inspectHttp(rawUrl: String, timeoutMs: Int = 6000): HttpReport = withContext(Dispatchers.IO) {
        val url = normalizeUrl(rawUrl)
        fun open(method: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "Mozilla/5.0 (recon header check)")
        }
        val start = System.nanoTime()
        var conn = open("HEAD")
        var code = runCatching { conn.responseCode }.getOrElse { -1 }
        if (code == -1 || code == 405 || code == 501) {
            conn.disconnect(); conn = open("GET"); code = conn.responseCode
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        val headers = conn.headerFields.entries
            .filter { it.key != null }
            .flatMap { (k, vs) -> vs.map { k to it } }
            .sortedBy { it.first.lowercase() }
        val flat = headers.groupBy({ it.first }, { it.second }).mapValues { it.value.joinToString("; ") }
        val tls = (conn as? HttpsURLConnection)?.let { https ->
            runCatching {
                val cert = https.serverCertificates.firstOrNull() as? X509Certificate
                TlsInfo(
                    cipher = https.cipherSuite,
                    subject = cert?.subjectX500Principal?.name ?: "?",
                    issuer = cert?.issuerX500Principal?.name ?: "?",
                    notAfter = cert?.notAfter?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) } ?: "?",
                    daysLeft = cert?.notAfter?.let { (it.time - System.currentTimeMillis()) / 86_400_000L } ?: 0,
                    sans = cert?.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() }?.take(12) ?: emptyList(),
                )
            }.getOrNull()
        }
        val cookies = headers.filter { it.first.equals("Set-Cookie", true) }.map { auditCookie(it.second) }
        val message = runCatching { conn.responseMessage }.getOrNull()
        conn.disconnect()
        HttpReport(url, code, message, ms, headers, checkSecurityHeaders(flat, url.startsWith("https")), tls, cookies)
    }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        require(t.isNotEmpty()) { "Enter a URL" }
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }

    /** "name: Secure HttpOnly SameSite=Lax" — or flags what's missing. */
    fun auditCookie(setCookie: String): String {
        val parts = setCookie.split(';').map { it.trim() }
        val name = parts.first().substringBefore('=')
        val attrs = parts.drop(1).map { it.lowercase() }
        val missing = buildList {
            if (attrs.none { it == "secure" }) add("Secure")
            if (attrs.none { it == "httponly" }) add("HttpOnly")
            if (attrs.none { it.startsWith("samesite") }) add("SameSite")
        }
        return if (missing.isEmpty()) "$name ✓" else "$name — missing ${missing.joinToString(", ")}"
    }

    /** Average TCP connect latency to host:port over [count] attempts (works where ICMP is blocked). */
    suspend fun tcpPing(host: String, port: Int, count: Int = 4, timeoutMs: Int = 1500): List<Long?> = withContext(Dispatchers.IO) {
        val ip = InetAddress.getByName(host).hostAddress!!
        (1..count).map {
            when (val r = tcpProbe(ip, port, timeoutMs)) {
                is Probe.Open -> r.ms
                is Probe.Closed -> r.ms
                Probe.Filtered -> null
            }
        }
    }

    fun isIpv4(s: String) = s.isNotBlank() && runCatching { ipv4ToInt(s) }.isSuccess

}
