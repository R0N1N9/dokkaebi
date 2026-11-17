package dev.dokkaebi.tablet.recon

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReconTool(val label: String) { NETWORK("NETWORK"), DISCOVER("DISCOVER"), PORTS("PORTS"), DNS("DNS"), HTTP("HTTP") }

class ReconViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("recon", 0)

    var acknowledged by mutableStateOf(prefs.getBoolean("ack", false)); private set
    var tool by mutableStateOf(ReconTool.NETWORK)

    // NETWORK
    var net by mutableStateOf<NetInfo?>(null); private set

    // DISCOVER
    val hosts = mutableStateListOf<NetTools.Host>()
    var discoverProgress by mutableFloatStateOf(0f); private set
    var discoverRange by mutableStateOf<String?>(null); private set
    private var discoverJob: Job? = null
    var discovering by mutableStateOf(false); private set

    // PORTS
    var portHost by mutableStateOf("")
    var portSpec by mutableStateOf("top")
    var portTimeout by mutableStateOf(400)
    val openPorts = mutableStateListOf<NetTools.PortResult>()
    var portProgress by mutableFloatStateOf(0f); private set
    var portStatus by mutableStateOf<String?>(null); private set
    private var portJob: Job? = null
    var portScanning by mutableStateOf(false); private set

    // DNS
    var dnsQuery by mutableStateOf("")
    var dnsResult by mutableStateOf<NetTools.DnsResult?>(null); private set
    var pingResult by mutableStateOf<String?>(null); private set
    var pingPort by mutableStateOf("443")

    // HTTP
    var httpUrl by mutableStateOf("")
    var httpReport by mutableStateOf<NetTools.HttpReport?>(null); private set

    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /** Receives one-line events for the main activity LOG. */
    var logSink: (String, Int) -> Unit = { _, _ -> }

    fun acknowledge() {
        acknowledged = true
        prefs.edit().putBoolean("ack", true).apply()
        refreshNet()
    }

    fun refreshNet() {
        net = runCatching { readNetInfo(getApplication()) }.getOrNull()
    }

    fun startDiscovery() {
        refreshNet()
        val n = net
        val ip = n?.ipv4
        val prefix = n?.prefix
        if (ip == null || prefix == null) { error = "No IPv4 network — connect to Wi-Fi first"; return }
        if (n.transport == "CELLULAR") { error = "On mobile data: subnet discovery only makes sense on Wi-Fi/Ethernet"; return }
        error = null
        discoverJob?.cancel()
        hosts.clear()
        discoverProgress = 0f
        val list = NetTools.subnetHosts(ip, prefix)
        discoverRange = NetTools.cidr(ip, prefix) + " · ${list.size} addresses"
        logSink("RECON: SWEEP ${NetTools.cidr(ip, prefix)}", 1)
        discoverJob = viewModelScope.launch {
            NetTools.discover(list, onProgress = { d, t -> discoverProgress = d.toFloat() / t }).collect { h ->
                val i = hosts.indexOfFirst { NetTools.ipv4ToInt(it.ip).toUInt() > NetTools.ipv4ToInt(h.ip).toUInt() }
                if (i < 0) hosts.add(h) else hosts.add(i, h)
            }
            discoverProgress = 1f
            logSink("RECON: ${hosts.size} LIVE HOSTS", 1)
        }
        discovering = true
        discoverJob?.invokeOnCompletion { discovering = false }
    }

    fun stopDiscovery() { discoverJob?.cancel(); logSink("RECON: SWEEP ABORTED", 2) }

    fun scanHost(ip: String) {
        portHost = ip
        tool = ReconTool.PORTS
        startPortScan()
    }

    fun startPortScan() {
        val host = portHost.trim()
        if (host.isEmpty()) { error = "Enter a host or IP"; return }
        val ports = try { NetTools.parsePorts(portSpec) } catch (e: IllegalArgumentException) { error = e.message; return }
        error = null
        portJob?.cancel()
        openPorts.clear()
        portProgress = 0f
        portStatus = "Scanning ${ports.size} ports on $host…"
        logSink("RECON: PORTSCAN $host (${ports.size} ports)", 1)
        val started = System.currentTimeMillis()
        portJob = viewModelScope.launch {
            try {
                NetTools.scanPorts(host, ports, portTimeout, onProgress = { d, t -> portProgress = d.toFloat() / t }).collect { r ->
                    val i = openPorts.indexOfFirst { it.port > r.port }
                    if (i < 0) openPorts.add(r) else openPorts.add(i, r)
                }
                val secs = (System.currentTimeMillis() - started) / 1000.0
                portStatus = "${openPorts.size} open of ${ports.size} scanned on $host in ${"%.1f".format(secs)}s"
                logSink("RECON: $host → ${openPorts.joinToString(",") { it.port.toString() }.ifEmpty { "no open ports" }}", 1)
            } catch (e: java.net.UnknownHostException) {
                portStatus = null; error = "Can't resolve $host"
            }
        }
        portScanning = true
        portJob?.invokeOnCompletion { portScanning = false }
    }

    fun stopPortScan() { portJob?.cancel(); portStatus = "Stopped — ${openPorts.size} open so far" }

    fun runDns() {
        val q = dnsQuery.trim()
        if (q.isEmpty()) { error = "Enter a hostname or IP"; return }
        launchBusy {
            dnsResult = NetTools.dnsLookup(q)
            pingResult = null
            logSink("RECON: DNS $q → ${dnsResult?.addresses?.joinToString()}", 0)
        }
    }

    fun runPing() {
        val q = dnsQuery.trim()
        if (q.isEmpty()) { error = "Enter a hostname or IP"; return }
        launchBusy {
            val port = pingPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: throw IllegalArgumentException("Bad port")
            val r = NetTools.tcpPing(q, port)
            val ok = r.filterNotNull()
            pingResult = if (ok.isEmpty()) "tcp/$port: no response (${r.size} attempts)"
            else "tcp/$port: ${ok.size}/${r.size} replies · min ${ok.min()} / avg ${ok.average().toInt()} / max ${ok.max()} ms"
        }
    }

    fun runHttp() {
        val u = httpUrl.trim()
        if (u.isEmpty()) { error = "Enter a URL"; return }
        launchBusy {
            httpReport = NetTools.inspectHttp(u)
            val missing = httpReport!!.checks.count { !it.present }
            logSink("RECON: HTTP ${httpReport!!.url} → ${httpReport!!.code}, $missing security headers missing", 0)
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        error = null
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: java.net.UnknownHostException) {
                error = "Can't resolve host"
            } catch (e: Exception) {
                error = (e.message ?: e.javaClass.simpleName).take(200)
            } finally {
                busy = false
            }
        }
    }

    init {
        if (acknowledged) viewModelScope.launch { net = withContext(Dispatchers.Default) { runCatching { readNetInfo(getApplication()) }.getOrNull() } }
    }
}
