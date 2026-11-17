package dev.dokkaebi.tablet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dokkaebi.tablet.recon.NetTools
import dev.dokkaebi.tablet.recon.ReconTool
import dev.dokkaebi.tablet.recon.ReconViewModel

@Composable
fun ReconPanel(rvm: ReconViewModel) {
    if (!rvm.acknowledged) { AuthorizationGate(rvm); return }
    LaunchedEffect(Unit) { if (rvm.net == null) rvm.refreshNet() }

    Column(Modifier.fillMaxSize().background(Hud.Panel.copy(alpha = 0.92f)).border(1.dp, Hud.Line)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReconTool.entries.forEach { t ->
                val sel = t == rvm.tool
                Box(
                    Modifier.border(1.dp, if (sel) Hud.Cyan else Hud.Line)
                        .background(if (sel) Hud.Cyan.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { rvm.tool = t }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) { Text(t.label, style = Hud.mono(10, FontWeight.Bold, if (sel) Hud.Cyan else Hud.TextDim, 2f)) }
            }
        }
        rvm.error?.let {
            Text("! $it", style = Hud.mono(11, FontWeight.Bold, Hud.Hot, 0f), modifier = Modifier.padding(horizontal = 12.dp))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (rvm.tool) {
                ReconTool.NETWORK -> networkTool(rvm)
                ReconTool.DISCOVER -> discoverTool(rvm)
                ReconTool.PORTS -> portsTool(rvm)
                ReconTool.DNS -> dnsTool(rvm)
                ReconTool.HTTP -> httpTool(rvm)
            }
        }
    }
}

@Composable
private fun AuthorizationGate(rvm: ReconViewModel) {
    Box(Modifier.fillMaxSize().border(1.dp, Hud.Hot).brackets(Hud.Hot), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlitchText("AUTHORIZED USE ONLY", Hud.mono(18, FontWeight.Black, Hud.Hot, 4f))
            Spacer(Modifier.height(12.dp))
            Text(
                "These are real network tools: host discovery, TCP port scanning, banner grabbing, DNS " +
                    "and HTTP/TLS inspection.\n\nOnly scan networks and hosts you own or have explicit written " +
                    "permission to test. Unauthorized scanning can violate law (e.g. CFAA, Computer Misuse Act) " +
                    "and network terms of service.",
                style = Hud.mono(12, color = Hud.Text, spacing = 0f),
            )
            Spacer(Modifier.height(18.dp))
            HudButton("I UNDERSTAND — PROCEED", Hud.Hot, filled = true) { rvm.acknowledge() }
        }
    }
}

/* ---------------- shared bits ---------------- */

@Composable
private fun KV(k: String, v: String?, vColor: Color = Hud.Text) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, style = Hud.mono(11, color = Hud.TextDim, spacing = 0f), modifier = Modifier.width(120.dp))
        Text(v ?: "—", style = Hud.mono(11, FontWeight.Bold, vColor, 0f))
    }
}

@Composable
private fun Progress(p: Float, color: Color = Hud.Cyan) {
    Box(
        Modifier.fillMaxWidth().height(6.dp).background(Hud.Line).drawBehind {
            drawRect(color, size = Size(size.width * p.coerceIn(0f, 1f), size.height))
        }
    )
}

/* ---------------- NETWORK ---------------- */

private fun LazyListScope.networkTool(rvm: ReconViewModel) {
    item { SectionTitle("ACTIVE NETWORK") }
    val n = rvm.net
    if (n == null) {
        item { Text("No active network.", style = Hud.mono(12, color = Hud.TextDim)) }
    } else {
        item { KV("TRANSPORT", n.transport + if (n.vpn) " (VPN)" else "", Hud.Cyan) }
        item { KV("INTERFACE", n.iface) }
        item { KV("IPV4", n.ipv4?.let { "$it/${n.prefix}" }) }
        item { KV("SUBNET", n.cidr) }
        item { KV("GATEWAY", n.gateway) }
        item { KV("DNS", n.dns.joinToString("\n").ifEmpty { null }) }
        item { KV("IPV6", n.ipv6.joinToString("\n").ifEmpty { null }) }
        item { KV("MAC", n.mac ?: "hidden by Android") }
        item { KV("INTERNET", if (n.validated) "VALIDATED" else "NOT VALIDATED", if (n.validated) Hud.Accent else Hud.Warn) }
        item { KV("METERED", if (n.metered) "YES" else "NO") }
        item { KV("LINK EST.", if (n.downKbps != null) "↓ ${n.downKbps / 1000} Mbps  ↑ ${(n.upKbps ?: 0) / 1000} Mbps" else null) }
    }
    item {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("REFRESH", Hud.Cyan) { rvm.refreshNet() }
            if (n?.gateway != null) HudButton("SCAN GATEWAY", Hud.Accent) { rvm.scanHost(n.gateway) }
        }
    }
}

/* ---------------- DISCOVER ---------------- */

private fun LazyListScope.discoverTool(rvm: ReconViewModel) {
    item { SectionTitle("HOST DISCOVERY") }
    item {
        Text(
            "Sweeps your local subnet with TCP connect probes (a reply or a reset both prove a host is up), " +
                "then echo. Tap a host to port-scan it.",
            style = Hud.mono(10, color = Hud.TextDim, spacing = 0f),
        )
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rvm.discovering) HudButton("STOP", Hud.Hot, filled = true) { rvm.stopDiscovery() }
            else HudButton("SWEEP SUBNET", Hud.Cyan) { rvm.startDiscovery() }
            Text(rvm.discoverRange ?: rvm.net?.cidr ?: "", style = Hud.mono(11, color = Hud.TextDim, spacing = 0f))
        }
    }
    if (rvm.discovering || rvm.discoverProgress > 0f) item {
        Column {
            Progress(rvm.discoverProgress)
            Text("${(rvm.discoverProgress * 100).toInt()}% · ${rvm.hosts.size} live", style = Hud.mono(10, color = Hud.TextDim))
        }
    }
    items(rvm.hosts, key = { it.ip }) { h ->
        val tags = buildList {
            if (h.ip == rvm.net?.gateway) add("GATEWAY")
            if (h.ip == rvm.net?.ipv4) add("THIS DEVICE")
        }
        Row(
            Modifier.fillMaxWidth().border(1.dp, Hud.Line).clickable { rvm.scanHost(h.ip) }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.ip, style = Hud.mono(13, FontWeight.Bold, Hud.Accent, 0f))
                    tags.forEach { Text("  [$it]", style = Hud.mono(10, FontWeight.Bold, Hud.Cyan, 0f)) }
                }
                Text(
                    listOfNotNull(h.hostname, h.via, h.openPorts.takeIf { it.isNotEmpty() }?.joinToString(prefix = "open ") { "$it/${NetTools.serviceName(it)}" })
                        .joinToString(" · "),
                    style = Hud.mono(10, color = Hud.TextDim, spacing = 0f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${h.latencyMs}ms", style = Hud.mono(11, color = Hud.Text))
            Spacer(Modifier.width(8.dp))
            Text("SCAN ▸", style = Hud.mono(11, FontWeight.Bold, Hud.Cyan))
        }
    }
}

/* ---------------- PORTS ---------------- */

private fun LazyListScope.portsTool(rvm: ReconViewModel) {
    item { SectionTitle("TCP PORT SCAN") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudField(rvm.portHost, { rvm.portHost = it.trim() }, "HOST OR IP", Modifier.fillMaxWidth(), maxLen = 253)
            HudField(rvm.portSpec, { rvm.portSpec = it }, "PORTS: top | all | 22,80,8000-8100", Modifier.fillMaxWidth(), maxLen = 200)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TIMEOUT", style = Hud.mono(10, color = Hud.TextDim))
                listOf(200, 400, 1000, 2500).forEach { ms ->
                    val sel = rvm.portTimeout == ms
                    Box(
                        Modifier.border(1.dp, if (sel) Hud.Cyan else Hud.Line).clickable { rvm.portTimeout = ms }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("${ms}ms", style = Hud.mono(10, FontWeight.Bold, if (sel) Hud.Cyan else Hud.TextDim, 0f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rvm.portScanning) HudButton("STOP", Hud.Hot, filled = true) { rvm.stopPortScan() }
                else HudButton("SCAN", Hud.Cyan) { rvm.startPortScan() }
                HudButton("TOP ${NetTools.COMMON_PORTS.size}", Hud.TextDim) { rvm.portSpec = "top" }
                HudButton("1-1024", Hud.TextDim) { rvm.portSpec = "1-1024" }
            }
        }
    }
    if (rvm.portScanning || rvm.portProgress > 0f) item { Progress(rvm.portProgress) }
    rvm.portStatus?.let { s -> item { Text(s, style = Hud.mono(11, color = Hud.TextDim, spacing = 0f)) } }
    items(rvm.openPorts, key = { it.port }) { r ->
        Row(Modifier.fillMaxWidth().border(1.dp, Hud.Line).padding(8.dp), verticalAlignment = Alignment.Top) {
            Text("${r.port}/tcp", style = Hud.mono(12, FontWeight.Bold, Hud.Accent, 0f), modifier = Modifier.width(88.dp))
            Column(Modifier.weight(1f)) {
                Text("OPEN  ${r.service}", style = Hud.mono(12, FontWeight.Bold, Hud.Text, 0f))
                r.banner?.let { Text(it, style = Hud.mono(10, color = Hud.Cyan, spacing = 0f)) }
            }
            Text("${r.latencyMs}ms", style = Hud.mono(10, color = Hud.TextDim))
        }
    }
}

/* ---------------- DNS ---------------- */

private fun LazyListScope.dnsTool(rvm: ReconViewModel) {
    item { SectionTitle("DNS / TCP PING") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudField(rvm.dnsQuery, { rvm.dnsQuery = it.trim() }, "HOSTNAME OR IP", Modifier.fillMaxWidth(), maxLen = 253)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton(if (rvm.busy) "…" else "LOOKUP", Hud.Cyan) { if (!rvm.busy) rvm.runDns() }
                HudButton("TCP PING", Hud.Accent) { if (!rvm.busy) rvm.runPing() }
                Text("PORT", style = Hud.mono(10, color = Hud.TextDim))
                HudField(rvm.pingPort, { rvm.pingPort = it.filter(Char::isDigit) }, "443", Modifier.width(70.dp), maxLen = 5)
            }
        }
    }
    rvm.dnsResult?.let { r ->
        item { KV("QUERY", r.query, Hud.Cyan) }
        item { KV("ADDRESSES", r.addresses.joinToString("\n"), Hud.Accent) }
        if (r.reverse != null) item { KV("PTR", r.reverse) }
        item { KV("RESOLVED IN", "${r.ms} ms") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.addresses.firstOrNull()?.let { a -> HudButton("PORTSCAN $a", Hud.Cyan) { rvm.scanHost(a) } }
            }
        }
    }
    rvm.pingResult?.let { p -> item { KV("PING", p, Hud.Accent) } }
}

/* ---------------- HTTP ---------------- */

private fun LazyListScope.httpTool(rvm: ReconViewModel) {
    item { SectionTitle("HTTP / TLS AUDIT") }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudField(rvm.httpUrl, { rvm.httpUrl = it.trim() }, "https://example.com", Modifier.weight(1f), maxLen = 500)
            Spacer(Modifier.width(8.dp))
            HudButton(if (rvm.busy) "…" else "INSPECT", Hud.Cyan) { if (!rvm.busy) rvm.runHttp() }
        }
    }
    val r = rvm.httpReport ?: return
    item { KV("URL", r.url, Hud.Cyan) }
    item { KV("STATUS", "${r.code} ${r.message ?: ""}  (${r.ms} ms)", if (r.code in 200..399) Hud.Accent else Hud.Warn) }
    r.headers.firstOrNull { it.first.equals("Location", true) }?.let { item { KV("REDIRECT →", it.second, Hud.Warn) } }
    r.headers.firstOrNull { it.first.equals("Server", true) }?.let { item { KV("SERVER", it.second) } }
    item { Spacer(Modifier.height(4.dp)); SectionTitle("SECURITY HEADERS ${r.checks.count { it.present }}/${r.checks.size}") }
    items(r.checks, key = { "h-" + it.name }) { c ->
        Row(Modifier.fillMaxWidth()) {
            Text(if (c.present) "✓" else "✗", style = Hud.mono(12, FontWeight.Black, if (c.present) Hud.Accent else Hud.Hot), modifier = Modifier.width(20.dp))
            Column {
                Text(c.name, style = Hud.mono(11, FontWeight.Bold, Hud.Text, 0f))
                Text(c.value ?: c.note, style = Hud.mono(10, color = Hud.TextDim, spacing = 0f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (r.cookies.isNotEmpty()) {
        item { SectionTitle("COOKIES") }
        items(r.cookies) { c -> Text(c, style = Hud.mono(10, color = if ("missing" in c) Hud.Warn else Hud.Accent, spacing = 0f)) }
    }
    r.tls?.let { t ->
        item { SectionTitle("TLS CERTIFICATE") }
        item { KV("SUBJECT", t.subject) }
        item { KV("ISSUER", t.issuer) }
        item { KV("EXPIRES", "${t.notAfter} (${t.daysLeft} days)", if (t.daysLeft < 21) Hud.Hot else Hud.Accent) }
        item { KV("CIPHER", t.cipher) }
        if (t.sans.isNotEmpty()) item { KV("SANS", t.sans.joinToString("\n")) }
    }
    item { SectionTitle("RAW HEADERS") }
    items(r.headers) { (k, v) ->
        Text("$k: $v", style = Hud.mono(10, color = Hud.TextDim, spacing = 0f))
    }
}
