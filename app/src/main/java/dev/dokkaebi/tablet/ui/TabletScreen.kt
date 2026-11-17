package dev.dokkaebi.tablet.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import dev.dokkaebi.tablet.ContactMode
import dev.dokkaebi.tablet.PickPhoneContact
import dev.dokkaebi.tablet.RealCaller
import dev.dokkaebi.tablet.readPickedContact
import dev.dokkaebi.tablet.rememberRealCaller
import dev.dokkaebi.tablet.recon.ReconViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dokkaebi.tablet.Status
import dev.dokkaebi.tablet.TabletViewModel
import dev.dokkaebi.tablet.Target
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

private enum class Tab(val label: String) { TARGETS("TARGETS"), FEEDS("FEEDS"), LOG("LOG"), RECON("RECON"), CONFIG("CONFIG") }

@Composable
fun TabletScreen(vm: TabletViewModel, rvm: ReconViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TARGETS) }
    val realCaller = rememberRealCaller(vm)
    val anyRinging = vm.targets.any { it.status == Status.RINGING }

    Box(Modifier.fillMaxSize().background(Hud.Bg)) {
        Canvas(Modifier.fillMaxSize()) { hexGrid(Hud.Line.copy(alpha = 0.25f), 22.dp.toPx()) }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            TopBar(vm, tab, anyRinging) { tab = it }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }, label = "tab") { t ->
                    when (t) {
                        Tab.TARGETS -> TargetsPanel(vm, realCaller)
                        Tab.FEEDS -> FeedsPanel(vm)
                        Tab.LOG -> LogPanel(vm)
                        Tab.RECON -> ReconPanel(rvm)
                        Tab.CONFIG -> ConfigPanel(vm)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (tab != Tab.RECON) ActionBar(vm)
        }
        if (anyRinging) AlertEdge()
        RollingBand()
    }
}

@Composable
private fun TopBar(vm: TabletViewModel, tab: Tab, alert: Boolean, onTab: (Tab) -> Unit) {
    val blink = rememberBlink(450)
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("HH:mm:ss", Locale.US)
        while (true) { clock = f.format(Date()); delay(500) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 600.dp
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                GoblinMask(Modifier.size(34.dp), eyeGlow = if (alert && blink) 1f else 0.4f)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    GlitchText("DOKKAEBI", Hud.mono(18, FontWeight.Black, Hud.Accent, 6f))
                    Text(
                        when {
                            alert -> "● LINES ACTIVE"
                            vm.mode == ContactMode.REAL -> "LIVE MODE // REAL CALLS"
                            else -> "LOGIC BOMB // ARMED"
                        },
                        style = Hud.mono(9, color = if (alert && blink) Hud.Hot else Hud.TextDim, spacing = 2f),
                    )
                }
                if (wide) TabRow(tab, onTab, Modifier.padding(horizontal = 12.dp))
                SignalBars()
                Spacer(Modifier.width(10.dp))
                Text(clock, style = Hud.mono(13, FontWeight.Bold))
            }
            if (!wide) {
                Spacer(Modifier.height(6.dp))
                TabRow(tab, onTab, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TabRow(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tab.entries.forEach { t ->
            val sel = t == tab
            val bg by animateColorAsState(if (sel) Hud.Accent else Color.Transparent, label = "tabbg")
            Box(
                Modifier
                    .border(1.dp, if (sel) Hud.Accent else Hud.Line)
                    .background(bg)
                    .clickable { onTab(t) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, style = Hud.mono(11, FontWeight.Bold, if (sel) Hud.Bg else Hud.Text, 2f))
            }
        }
    }
}

@Composable
private fun SignalBars() {
    val t = rememberInfiniteTransition(label = "sig")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "p")
    Canvas(Modifier.size(28.dp, 16.dp)) {
        val bars = 5
        val w = size.width / bars
        val lit = 3 + ((sin(p * 6.28f) + 1) * 1.01f).toInt()
        for (i in 0 until bars) {
            val h = size.height * (i + 1) / bars
            drawRect(if (i < lit) Hud.Accent else Hud.Line, Offset(i * w + 1, size.height - h), Size(w - 3, h))
        }
    }
}

@Composable
private fun AlertEdge() {
    val t = rememberInfiniteTransition(label = "edge")
    val a by t.animateFloat(0.15f, 0.6f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Hud.Hot.copy(alpha = a), style = Stroke(4.dp.toPx()))
    }
}

/* ------------------------------ TARGETS ------------------------------ */

@Composable
private fun TargetsPanel(vm: TabletViewModel, realCaller: RealCaller) {
    if (vm.targets.isEmpty()) {
        EmptyNote(
            if (vm.mode == ContactMode.REAL) "NO CONTACTS TAGGED\nImport phone contacts in CONFIG"
            else "NO DEVICES TAGGED\nAdd targets in CONFIG"
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(vm.targets, key = { it.id }) { t ->
            if (vm.mode == ContactMode.REAL) RealTargetCard(t, realCaller) else TargetCard(t, vm)
        }
    }
}

@Composable
private fun TargetCard(t: Target, vm: TabletViewModel) {
    val color = when (t.status) {
        Status.IDLE -> Hud.Line
        Status.DIALING -> Hud.Warn
        Status.RINGING -> Hud.Hot
        Status.BREACHED -> Hud.Accent
    }
    val border by animateColorAsState(color, label = "border")
    val ringing = t.status == Status.RINGING
    val inf = rememberInfiniteTransition(label = "card")
    val wave by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "wave")
    val shake by inf.animateFloat(-14f, 14f, infiniteRepeatable(tween(55), RepeatMode.Reverse), label = "shake")
    val secs = rememberElapsedSeconds(t.since)

    Column(
        Modifier
            .background(Hud.Panel.copy(alpha = 0.92f))
            .border(1.dp, border)
            .brackets(if (t.status == Status.IDLE) Hud.AccentDim else border, 10f)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    if (ringing || t.status == Status.DIALING) {
                        for (k in 0..2) {
                            val ph = (wave + k / 3f) % 1f
                            drawCircle(color.copy(alpha = (1 - ph) * 0.8f), radius = size.minDimension / 2 * (0.35f + 0.65f * ph), style = Stroke(2.dp.toPx()))
                        }
                    } else {
                        drawCircle(color.copy(alpha = 0.6f), radius = size.minDimension / 2 * 0.62f, style = Stroke(1.5.dp.toPx()))
                    }
                }
                Icon(
                    Icons.Filled.Call, null,
                    tint = if (t.status == Status.IDLE) Hud.TextDim else color,
                    modifier = Modifier.size(24.dp).rotate(if (ringing) shake else 0f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = Hud.mono(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.device, style = Hud.mono(9, color = Hud.TextDim, spacing = 0f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        val statusText = when (t.status) {
            Status.IDLE -> "IDLE"
            Status.DIALING -> "DIALING…"
            Status.RINGING -> "RINGING  %02d:%02d".format(secs / 60, secs % 60)
            Status.BREACHED -> "COMPROMISED  %02d:%02d".format(secs / 60, secs % 60)
        }
        Text(statusText, style = Hud.mono(11, FontWeight.Bold, if (t.status == Status.IDLE) Hud.TextDim else color, 2f))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (t.status) {
                Status.IDLE -> HudButton("CALL", Hud.Accent, Modifier.weight(1f)) { vm.call(t.id) }
                Status.DIALING, Status.RINGING ->
                    HudButton("HANG UP", Hud.Hot, Modifier.weight(1f), filled = true) { vm.hangUp(t.id) }
                Status.BREACHED -> {
                    HudButton("RE-CALL", Hud.Accent, Modifier.weight(1f)) { vm.call(t.id) }
                    HudIconButton(Icons.Filled.Refresh, Hud.TextDim) { vm.reset(t.id) }
                }
            }
        }
    }
}

@Composable
private fun RealTargetCard(t: Target, realCaller: RealCaller) {
    val recent = t.lastCalled > 0 && System.currentTimeMillis() - t.lastCalled < 60_000
    val color = if (recent) Hud.Hot else Hud.Line
    val inf = rememberInfiniteTransition(label = "real")
    val wave by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "wave")
    Column(
        Modifier
            .background(Hud.Panel.copy(alpha = 0.92f))
            .border(1.dp, color)
            .brackets(if (recent) Hud.Hot else Hud.Warn.copy(alpha = 0.6f), 10f)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    if (recent) for (k in 0..2) {
                        val ph = (wave + k / 3f) % 1f
                        drawCircle(Hud.Hot.copy(alpha = (1 - ph) * 0.8f), radius = size.minDimension / 2 * (0.35f + 0.65f * ph), style = Stroke(2.dp.toPx()))
                    } else drawCircle(Hud.Warn.copy(alpha = 0.6f), radius = size.minDimension / 2 * 0.62f, style = Stroke(1.5.dp.toPx()))
                }
                Icon(Icons.Filled.Call, null, tint = if (recent) Hud.Hot else Hud.Warn, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = Hud.mono(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.number ?: "", style = Hud.mono(10, color = Hud.TextDim, spacing = 0f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        val status = if (t.lastCalled == 0L) "LIVE · READY"
        else "LAST CALL " + SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date(t.lastCalled)).uppercase()
        Text(status, style = Hud.mono(11, FontWeight.Bold, if (recent) Hud.Hot else Hud.Warn, 2f), maxLines = 1)
        Spacer(Modifier.height(10.dp))
        HudButton("CALL", Hud.Hot, Modifier.fillMaxWidth()) { realCaller.call(t) }
    }
}

@Composable
fun HudButton(label: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .border(1.dp, color)
            .background(if (filled) color else color.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Hud.mono(12, FontWeight.Bold, if (filled) Hud.Bg else color, 2f), maxLines = 1)
    }
}

@Composable
internal fun HudIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).border(1.dp, color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
}

/* ------------------------------ ACTION BAR ------------------------------ */

@Composable
private fun ActionBar(vm: TabletViewModel) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(Hud.Panel.copy(alpha = 0.92f)).border(1.dp, Hud.Line).padding(10.dp)
    ) {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Charges(vm.charges, vm.recharge)
                    Spacer(Modifier.width(12.dp))
                    LogicBombButton(Modifier.weight(1f), enabled = vm.charges > 0, realMode = vm.mode == ContactMode.REAL) { vm.logicBomb() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudButton("END ALL", Hud.Hot, Modifier.weight(1f)) { vm.hangUpAll() }
                    HudButton("RESET", Hud.TextDim, Modifier.weight(1f)) { vm.resetAll() }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Charges(vm.charges, vm.recharge)
                Spacer(Modifier.width(12.dp))
                LogicBombButton(Modifier.weight(1f), enabled = vm.charges > 0, realMode = vm.mode == ContactMode.REAL) { vm.logicBomb() }
                Spacer(Modifier.width(8.dp))
                HudButton("END ALL", Hud.Hot) { vm.hangUpAll() }
                Spacer(Modifier.width(6.dp))
                HudButton("RESET", Hud.TextDim) { vm.resetAll() }
            }
        }
    }
}

@Composable
private fun Charges(charges: Int, recharge: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(TabletViewModel.MAX_CHARGES) { i ->
                val full = i < charges
                val partial = i == charges
                Box(
                    Modifier.size(14.dp, 24.dp).border(1.dp, if (full) Hud.Accent else Hud.Line).drawBehind {
                        val f = if (full) 1f else if (partial) recharge else 0f
                        drawRect(Hud.Accent.copy(alpha = if (full) 1f else 0.45f), Offset(0f, size.height * (1 - f)), Size(size.width, size.height * f))
                    }
                )
            }
        }
        Text("CHG", style = Hud.mono(8, color = Hud.TextDim))
    }
}

/** Press-and-hold to deploy: a fill sweeps across while held; releasing early cancels. */
@Composable
private fun LogicBombButton(modifier: Modifier, enabled: Boolean, realMode: Boolean = false, onFire: () -> Unit) {
    if (realMode) {
        Box(modifier.height(48.dp).border(2.dp, Hud.Line).background(Hud.Line.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text("LOGIC BOMB · FAKE MODE ONLY", style = Hud.mono(12, FontWeight.Bold, Hud.TextDim, 2f), maxLines = 1)
        }
        return
    }
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    var flash by remember { mutableIntStateOf(0) }
    val flashA = remember { Animatable(0f) }
    LaunchedEffect(flash) { if (flash > 0) { flashA.snapTo(1f); flashA.animateTo(0f, tween(500)) } }
    val color = if (enabled) Hud.Hot else Hud.Line
    var job by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier
            .height(48.dp)
            .clipToBounds()
            .border(2.dp, color)
            .pointerInput(enabled) {
                awaitEachGesture {
                    awaitFirstDown()
                    job = scope.launch {
                        fill.animateTo(1f, tween(650, easing = LinearEasing))
                        onFire()
                        flash++
                        fill.snapTo(0f)
                    }
                    waitForUpOrCancellation()
                    if (fill.value < 1f) {
                        job?.cancel()
                        scope.launch { fill.animateTo(0f, tween(150)) }
                    }
                }
            }
            .drawBehind {
                drawRect(color.copy(alpha = 0.12f))
                drawRect(color, size = Size(size.width * fill.value, size.height))
                // hazard stripes
                val step = 14.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(color.copy(alpha = 0.18f), Offset(x, size.height), Offset(x + size.height, 0f), 4.dp.toPx(), cap = StrokeCap.Butt)
                    x += step
                }
                drawRect(Color.White.copy(alpha = flashA.value * 0.7f))
            },
        contentAlignment = Alignment.Center,
    ) {
        GlitchText(
            if (enabled) "HOLD ▸ LOGIC BOMB" else "RECHARGING…",
            Hud.mono(14, FontWeight.Black, if (fill.value > 0.5f) Hud.Bg else Hud.Text, 3f),
            intensity = if (enabled) 1.5f else 0.3f,
        )
    }
}

/* ------------------------------ FEEDS ------------------------------ */

@Composable
private fun FeedsPanel(vm: TabletViewModel) {
    val feeds = vm.targets.filter { it.status == Status.BREACHED }
    if (feeds.isEmpty()) {
        EmptyNote(
            if (vm.mode == ContactMode.REAL) "FEEDS ARE SIMULATED\nSwitch to FAKE mode in CONFIG to use them"
            else "NO COMPROMISED DEVICES\nRing a target — when they hang up, their camera is yours"
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(feeds, key = { it.id }) { t -> CameraFeed(t) }
    }
}

@Composable
private fun CameraFeed(t: Target) {
    val clarity = remember(t.id, t.since) { Animatable(0f) }
    LaunchedEffect(t.id, t.since) { clarity.animateTo(0.85f, tween(1800)) }
    val blink = rememberBlink(600)
    val inf = rememberInfiniteTransition(label = "pan")
    val pan by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(7000), RepeatMode.Reverse), label = "pan")
    var stamp by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        while (true) { stamp = f.format(Date()); delay(1000) }
    }
    val seed = t.id * 7919

    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(androidx.compose.ui.graphics.RectangleShape)
            .background(Color(0xFF0A1410)).border(1.dp, Hud.AccentDim)
    ) {
        // Procedural "room": floor grid in perspective + a few boxes, slowly panning.
        Canvas(Modifier.fillMaxSize()) {
            val g = Hud.Accent.copy(alpha = 0.35f)
            val vx = size.width * (0.5f + 0.12f * pan)
            val vy = size.height * 0.42f
            for (i in -8..8) drawLine(g, Offset(vx, vy), Offset(vx + i * size.width * 0.18f, size.height), 1f)
            var y = vy
            var d = 4f
            while (y < size.height) { drawLine(g, Offset(0f, y), Offset(size.width, y), 1f); y += d; d *= 1.35f }
            drawLine(g, Offset(0f, vy), Offset(size.width, vy), 1.5f)
            val r = kotlin.random.Random(seed)
            repeat(3) {
                val bw = size.width * (0.08f + r.nextFloat() * 0.14f)
                val bh = size.height * (0.12f + r.nextFloat() * 0.25f)
                val bx = size.width * r.nextFloat() - pan * size.width * 0.06f
                drawRect(Hud.Accent.copy(alpha = 0.22f), Offset(bx, vy + size.height * 0.25f - bh), Size(bw, bh), style = Stroke(1.5f))
            }
            // crosshair
            val c = center
            drawLine(Hud.Accent.copy(alpha = 0.5f), c.copy(x = c.x - 14), c.copy(x = c.x + 14), 1f)
            drawLine(Hud.Accent.copy(alpha = 0.5f), c.copy(y = c.y - 14), c.copy(y = c.y + 14), 1f)
        }
        StaticNoise(Modifier.fillMaxSize(), clarity = clarity.value, tint = Hud.Accent)
        Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (blink) Hud.Hot else Color.Transparent))
            Spacer(Modifier.width(6.dp))
            Text("REC  ${t.name}", style = Hud.mono(11, FontWeight.Bold))
        }
        Text("CAM-${"%02d".format(t.id)}", style = Hud.mono(10, color = Hud.Accent), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        Text(stamp, style = Hud.mono(9, color = Hud.TextDim), modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        Text(t.device, style = Hud.mono(9, color = Hud.TextDim, spacing = 0f), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
}

/* ------------------------------ LOG ------------------------------ */

@Composable
private fun LogPanel(vm: TabletViewModel) {
    val state = rememberLazyListState()
    LaunchedEffect(vm.log.size) { if (vm.log.isNotEmpty()) state.animateScrollToItem(vm.log.size - 1) }
    val blink = rememberBlink(500)
    Box(Modifier.fillMaxSize().background(Hud.Panel.copy(alpha = 0.92f)).border(1.dp, Hud.Line).padding(12.dp)) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(vm.log) { l ->
                val c = when (l.level) { 1 -> Hud.Accent; 2 -> Hud.Hot; else -> Hud.Text }
                Row {
                    Text("[${l.time}] ", style = Hud.mono(12, color = Hud.TextDim, spacing = 0f))
                    Text(l.text, style = Hud.mono(12, color = c, spacing = 0f))
                }
            }
            item { Text(if (blink) "█" else " ", style = Hud.mono(12, color = Hud.Accent)) }
        }
    }
}

/* ------------------------------ CONFIG ------------------------------ */

@Composable
private fun ConfigPanel(vm: TabletViewModel) {
    var newName by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().background(Hud.Panel.copy(alpha = 0.92f)).border(1.dp, Hud.Line),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("FEEDBACK") }
        item { ToggleRow("RING TONE", vm.sound, vm::updateSound) }
        item { ToggleRow("VIBRATION", vm.haptics, vm::updateHaptics) }
        item {
            Column {
                Text(
                    "AUTO HANG-UP: " + if (vm.autoHangUpSec == 0) "MANUAL ONLY" else "${vm.autoHangUpSec}s",
                    style = Hud.mono(12, FontWeight.Bold),
                )
                Slider(
                    value = vm.autoHangUpSec.toFloat(),
                    onValueChange = { vm.updateAutoHangUp(it.toInt()) },
                    valueRange = 0f..30f, steps = 29,
                    colors = SliderDefaults.colors(thumbColor = Hud.Accent, activeTrackColor = Hud.Accent, inactiveTrackColor = Hud.Line),
                )
            }
        }
        item { SectionTitle("CONTACT MODE") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactMode.entries.forEach { m ->
                    val sel = vm.mode == m
                    val c = if (m == ContactMode.REAL) Hud.Hot else Hud.Accent
                    HudButton(if (m == ContactMode.REAL) "REAL CONTACTS" else "FAKE TARGETS", c, Modifier.weight(1f), filled = sel) { vm.switchMode(m) }
                }
            }
        }
        item {
            Text(
                if (vm.mode == ContactMode.REAL)
                    "REAL: CALL places an actual phone call to the contact (asks for call permission; if denied, opens your dialer instead). Logic bomb and feeds are disabled."
                else "FAKE: everything is simulated. No calls leave the device.",
                style = Hud.mono(10, color = if (vm.mode == ContactMode.REAL) Hud.Warn else Hud.TextDim, spacing = 0f),
            )
        }
        if (vm.mode == ContactMode.REAL) {
            item { ToggleRow("CONFIRM BEFORE CALLING", vm.confirmRealCalls, vm::updateConfirmRealCalls) }
        }
        item { SectionTitle("TARGETS (${vm.targets.size})") }
        if (vm.mode == ContactMode.REAL) {
            item { RealContactAdder(vm) }
        } else item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudField(newName, { newName = it }, "TAG NEW DEVICE…", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                HudButton("ADD", Hud.Accent) { vm.addTarget(newName); newName = "" }
            }
        }
        items(vm.targets, key = { "${vm.mode}-${it.id}" }) { t ->
            var name by remember(t.id, vm.mode) { mutableStateOf(t.name) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudField(name, { name = it; vm.rename(t.id, it) }, "NAME", Modifier.weight(1f))
                t.number?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = Hud.mono(11, color = Hud.TextDim, spacing = 0f), maxLines = 1, modifier = Modifier.widthIn(max = 150.dp))
                }
                Spacer(Modifier.width(8.dp))
                HudIconButton(Icons.Filled.Delete, Hud.Hot) { vm.remove(t.id) }
            }
        }
        item {
            Text(
                "Fan-made prop app. All visuals are generated in code; not affiliated with any game publisher. " +
                    "Real calls happen only in REAL mode, one at a time, when you press CALL. " +
                    "RECON tools are for networks you own or are authorized to test.",
                style = Hud.mono(10, color = Hud.TextDim, spacing = 0f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun RealContactAdder(vm: TabletViewModel) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        val picked = uri?.let { readPickedContact(context, it) }
        if (picked != null) vm.addContact(picked.first, picked.second)
        else if (uri != null) vm.log("COULD NOT READ PICKED CONTACT", 2)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudButton("IMPORT FROM CONTACTS", Hud.Hot, Modifier.fillMaxWidth()) {
            try { picker.launch(Unit) } catch (e: android.content.ActivityNotFoundException) { vm.log("NO CONTACTS APP", 2) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudField(name, { name = it }, "NAME", Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            HudField(number, { number = it.filter { c -> c.isDigit() || c in "+*# -()" } }, "NUMBER", Modifier.weight(1f), maxLen = 24)
            Spacer(Modifier.width(6.dp))
            HudButton("ADD", Hud.Hot) { if (vm.addContact(name, number)) { name = ""; number = "" } }
        }
    }
}

@Composable
internal fun SectionTitle(s: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp, 14.dp).background(Hud.Accent))
        Spacer(Modifier.width(8.dp))
        Text(s, style = Hud.mono(12, FontWeight.Black, Hud.Accent, 3f))
    }
}

@Composable
internal fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = Hud.mono(12, FontWeight.Bold), modifier = Modifier.weight(1f))
        Switch(
            value, onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Hud.Bg, checkedTrackColor = Hud.Accent, uncheckedTrackColor = Hud.Line, uncheckedThumbColor = Hud.TextDim),
        )
    }
}

@Composable
internal fun HudField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, maxLen: Int = 18) {
    Box(modifier.height(36.dp).border(1.dp, Hud.Line).background(Hud.Bg).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, style = Hud.mono(12, color = Hud.TextDim))
        BasicTextField(
            value, { onChange(it.take(maxLen)) },
            singleLine = true,
            textStyle = Hud.mono(12, FontWeight.Bold, Hud.Text),
            cursorBrush = SolidColor(Hud.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize().border(1.dp, Hud.Line).brackets(Hud.AccentDim), contentAlignment = Alignment.Center) {
        Text(text, style = Hud.mono(12, color = Hud.TextDim), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}
