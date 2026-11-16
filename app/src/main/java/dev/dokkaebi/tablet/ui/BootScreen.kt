package dev.dokkaebi.tablet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val BOOT_LINES = listOf(
    "> mount /dev/payload ............ OK",
    "> spoof carrier handshake ....... OK",
    "> enumerate nearby handsets ..... 5 FOUND",
    "> inject ring daemon ............ OK",
    "> bypass lockscreen hooks ....... OK",
    "> open camera relay ............. STANDBY",
    "> 도깨비 장난 시작 ................ READY",
)

@Composable
fun BootScreen(onDone: () -> Unit) {
    val lines = remember { mutableStateListOf<String>() }
    val progress = remember { Animatable(0f) }
    val maskIn = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { maskIn.animateTo(1f, tween(900)) }
        launch { progress.animateTo(1f, tween(3200)) }
        delay(500)
        for (l in BOOT_LINES) {
            lines.add(l)
            delay(Random.nextLong(220, 420))
        }
        delay(500)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Hud.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        StaticNoise(Modifier.fillMaxSize().alpha(0.12f), clarity = maskIn.value * 0.7f, tint = Hud.Accent)
        Column(
            Modifier.widthIn(max = 520.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GoblinMask(Modifier.size(140.dp).alpha(maskIn.value), eyeGlow = maskIn.value)
            Spacer(Modifier.height(16.dp))
            GlitchText("DOKKAEBI", Hud.mono(34, FontWeight.Black, Hud.Accent, 10f), intensity = 3f)
            ScrambleText("LOGIC BOMB // REMOTE HANDSET EXPLOIT", Hud.mono(11, color = Hud.TextDim, spacing = 2f), durationMs = 1100)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().height(150.dp)) {
                lines.forEach { Text(it, style = Hud.mono(12, color = if (it.endsWith("READY")) Hud.Hot else Hud.Accent)) }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .drawBehind {
                        drawRect(Hud.Line, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                        val segs = 32
                        val w = size.width / segs
                        val n = (segs * progress.value).toInt()
                        for (i in 0 until n) drawRect(Hud.Accent, Offset(i * w + 2, 2f), Size(w - 4, size.height - 4))
                    }
            )
            Spacer(Modifier.height(8.dp))
            Text("${(progress.value * 100).toInt()}%   ·   TAP TO SKIP", style = Hud.mono(10, color = Hud.TextDim))
        }
        RollingBand()
    }
}
