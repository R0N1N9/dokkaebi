package dev.dokkaebi.tablet.recon

import dev.dokkaebi.tablet.TabletViewModel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class NetToolsTest {

    @Test fun parsePorts_handlesListsRangesAndPresets() {
        assertEquals(listOf(22, 80, 443, 8000, 8001, 8002), NetTools.parsePorts("443, 22,80 8000-8002"))
        assertEquals(NetTools.COMMON_PORTS.keys.sorted(), NetTools.parsePorts("top"))
        assertEquals(65535, NetTools.parsePorts("all").size)
        assertTrue(NetTools.parsePorts("top,65000").contains(65000))
    }

    @Test(expected = IllegalArgumentException::class) fun parsePorts_rejectsOutOfRange() { NetTools.parsePorts("70000") }
    @Test(expected = IllegalArgumentException::class) fun parsePorts_rejectsReversedRange() { NetTools.parsePorts("90-80") }
    @Test(expected = IllegalArgumentException::class) fun parsePorts_rejectsJunk() { NetTools.parsePorts("ssh") }

    @Test fun subnetMath() {
        val hosts = NetTools.subnetHosts("192.168.1.57", 24)
        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertEquals("10.0.0.0/24", NetTools.cidr("10.0.0.9", 8))        // huge subnets narrowed to /24
        assertEquals(2, NetTools.subnetHosts("10.1.2.5", 30).size)       // /30 → .5 and .6
        assertEquals("10.1.2.4/30", NetTools.cidr("10.1.2.5", 30))
        assertEquals("255.255.255.255", NetTools.intToIpv4(NetTools.ipv4ToInt("255.255.255.255")))
        assertFalse(NetTools.isIpv4("300.1.1.1"))
        assertTrue(NetTools.isIpv4("8.8.8.8"))
    }

    @Test fun tcpProbe_distinguishesOpenAndClosed() {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
            val port = server.localPort
            assertTrue(NetTools.tcpProbe("127.0.0.1", port, 500) is NetTools.Probe.Open)
            server.close()
            assertTrue(NetTools.tcpProbe("127.0.0.1", port, 500) is NetTools.Probe.Closed)
        }
    }

    @Test fun scanPorts_findsOpenPortAndGrabsBanner() = runBlocking {
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val t = thread(isDaemon = true) {
            while (!server.isClosed) runCatching {
                server.accept().use { it.getOutputStream().write("SSH-2.0-TestServer_1.0\r\n".toByteArray()); Thread.sleep(50) }
            }
        }
        val port = server.localPort
        val closed = ServerSocket(0).let { val p = it.localPort; it.close(); p }
        val results = NetTools.scanPorts("127.0.0.1", listOf(port, closed), timeoutMs = 500).toList()
        server.close(); t.join(500)
        assertEquals(1, results.size)
        assertEquals(port, results[0].port)
        assertEquals("SSH-2.0-TestServer_1.0", results[0].banner)
    }

    @Test fun summarizeBanner_http() {
        val raw = "HTTP/1.1 200 OK\r\nDate: x\r\nServer: nginx/1.25.3\r\n\r\n"
        assertEquals("HTTP/1.1 200 OK · Server: nginx/1.25.3", NetTools.summarizeBanner(raw))
        assertNull(NetTools.summarizeBanner("\r\n\r\n"))
    }

    @Test fun securityHeaders() {
        val checks = NetTools.checkSecurityHeaders(
            mapOf("strict-transport-security" to "max-age=63072000", "Content-Security-Policy" to "default-src 'self'; frame-ancestors 'none'"),
            https = true,
        ).associateBy { it.name }
        assertTrue(checks["Strict-Transport-Security"]!!.present)
        assertTrue(checks["X-Frame-Options"]!!.present)            // covered by CSP frame-ancestors
        assertFalse(checks["X-Content-Type-Options"]!!.present)
        val http = NetTools.checkSecurityHeaders(mapOf("Strict-Transport-Security" to "max-age=1"), https = false)
        assertFalse(http.first { it.name == "Strict-Transport-Security" }.present) // HSTS ignored over HTTP
    }

    @Test fun cookieAudit() {
        assertEquals("sid ✓", NetTools.auditCookie("sid=abc; Path=/; Secure; HttpOnly; SameSite=Lax"))
        assertEquals("t — missing Secure, HttpOnly, SameSite", NetTools.auditCookie("t=1; Path=/"))
    }

    @Test fun normalizeUrl() {
        assertEquals("https://example.com", NetTools.normalizeUrl(" example.com "))
        assertEquals("http://x.lan", NetTools.normalizeUrl("http://x.lan"))
    }

    @Test fun sanitizePhoneNumbers() {
        assertEquals("+15551234567", TabletViewModel.sanitizeNumber("+1 (555) 123-4567"))
        assertEquals("*67911", TabletViewModel.sanitizeNumber("*67 911"))
        assertEquals("5551234", TabletViewModel.sanitizeNumber("555+1234"))   // + only allowed first
        assertNull(TabletViewModel.sanitizeNumber("12"))
        assertNull(TabletViewModel.sanitizeNumber("call me"))
    }
}
