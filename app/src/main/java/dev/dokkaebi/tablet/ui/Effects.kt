package dev.dokkaebi.tablet.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** CRT scanlines + a slow rolling bright band + subtle flicker, drawn over the content. */
fun Modifier.crt(): Modifier = this.then(Modifier.drawWithContent {
    drawContent()
    val step = 3.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            center = center,
            radius = size.maxDimension * 0.75f,
        )
    )
})

@Composable
fun RollingBand(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "band")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "p")
    val flicker by t.animateFloat(0.0f, 0.04f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "f")
    Canvas(modifier.fillMaxSize()) {
        val h = size.height * 0.18f
        val y = -h + (size.height + h) * p
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Hud.Accent.copy(alpha = 0.05f), Color.Transparent),
                startY = y, endY = y + h,
            ),
            topLeft = Offset(0f, y), size = Size(size.width, h),
        )
        drawRect(Color.White.copy(alpha = flicker))
    }
}

/** Text that occasionally tears into RGB-split, offset copies. */
@Composable
fun GlitchText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    var glitch by remember { mutableStateOf(false) }
    var dx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(intensity) {
        while (true) {
            delay((Random.nextLong(900, 3800) / intensity.coerceAtLeast(0.1f)).toLong())
            repeat(Random.nextInt(2, 6)) {
                glitch = true
                dx = Random.nextFloat() * 6f - 3f
                delay(Random.nextLong(30, 90))
            }
            glitch = false
            dx = 0f
        }
    }
    Box(modifier) {
        if (glitch) {
            Text(text, style = style.copy(color = Hud.Hot.copy(alpha = 0.8f)), modifier = Modifier.offset(x = (dx - 2).dp))
            Text(text, style = style.copy(color = Hud.Cyan.copy(alpha = 0.8f)), modifier = Modifier.offset(x = (-dx + 2).dp))
        }
        Text(text, style = style, modifier = Modifier.offset(x = if (glitch) dx.dp else 0.dp))
    }
}

private const val SCRAMBLE = "ABCDEF0123456789#\$%&@!?/\\<>=+*도깨비"

/** Characters churn randomly before resolving left to right. */
@Composable
fun ScrambleText(text: String, style: TextStyle, modifier: Modifier = Modifier, durationMs: Long = 700) {
    var shown by remember(text) { mutableStateOf(text.map { if (it == ' ') ' ' else SCRAMBLE.random() }.joinToString("")) }
    LaunchedEffect(text) {
        val frames = (durationMs / 30).toInt().coerceAtLeast(1)
        for (f in 0..frames) {
            val resolved = text.length * f / frames
            shown = text.mapIndexed { i, c -> if (i < resolved || c == ' ') c else SCRAMBLE.random() }.joinToString("")
            delay(30)
        }
        shown = text
    }
    Text(shown, style = style, modifier = modifier)
}

/** Ticks a frame counter every vsync — used to drive per-frame random effects. */
@Composable
fun rememberFrameTick(): Long {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { tick = it } }
    return tick
}

/** Animated TV static with tracking lines. [clarity] 0 = pure noise, 1 = mostly clear. */
@Composable
fun StaticNoise(modifier: Modifier = Modifier, clarity: Float = 0f, tint: Color = Color.White) {
    val tick = rememberFrameTick()
    Canvas(modifier) {
        val rnd = Random(tick)
        val cell = 4.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val count = ((cols * rows) * (1f - clarity) * 0.35f).toInt()
        repeat(count) {
            val x = rnd.nextInt(cols) * cell
            val y = rnd.nextInt(rows) * cell
            drawRect(tint.copy(alpha = rnd.nextFloat() * 0.6f), Offset(x, y), Size(cell, cell))
        }
        repeat(2) {
            val y = rnd.nextFloat() * size.height
            drawRect(tint.copy(alpha = 0.08f + 0.1f * (1 - clarity)), Offset(0f, y), Size(size.width, rnd.nextFloat() * 12.dp.toPx()))
        }
    }
}

/** Faint hex grid for panel backgrounds. */
fun DrawScope.hexGrid(color: Color, r: Float) {
    val w = sqrt(3f) * r
    val h = 1.5f * r
    var row = 0
    var y = 0f
    while (y < size.height + r) {
        var x = if (row % 2 == 0) 0f else w / 2
        while (x < size.width + w) {
            val p = Path()
            for (i in 0..5) {
                val a = Math.toRadians((60 * i - 30).toDouble())
                val px = x + r * cos(a).toFloat()
                val py = y + r * sin(a).toFloat()
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            drawPath(p, color, style = Stroke(1f))
            x += w
        }
        y += h
        row++
    }
}

/** Corner brackets around a panel, HUD style. */
fun Modifier.brackets(color: Color = Hud.Accent, len: Float = 14f, stroke: Float = 2f): Modifier =
    this.then(Modifier.drawWithContent {
        drawContent()
        val l = len.dp.toPx()
        val s = stroke.dp.toPx()
        val w = size.width
        val h = size.height
        fun seg(a: Offset, b: Offset) = drawLine(color, a, b, s, cap = StrokeCap.Square)
        seg(Offset(0f, 0f), Offset(l, 0f)); seg(Offset(0f, 0f), Offset(0f, l))
        seg(Offset(w, 0f), Offset(w - l, 0f)); seg(Offset(w, 0f), Offset(w, l))
        seg(Offset(0f, h), Offset(l, h)); seg(Offset(0f, h), Offset(0f, h - l))
        seg(Offset(w, h), Offset(w - l, h)); seg(Offset(w, h), Offset(w, h - l))
    })

/** Blinking boolean, for cursors and alerts. */
@Composable
fun rememberBlink(periodMs: Int = 500): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs.toLong()); on = !on } }
    return on
}

/** Integer counter of seconds since [start] (epoch ms), updating each second. */
@Composable
fun rememberElapsedSeconds(start: Long): Int {
    var s by remember(start) { mutableIntStateOf(0) }
    LaunchedEffect(start) {
        while (true) {
            s = ((System.currentTimeMillis() - start) / 1000).toInt()
            delay(250)
        }
    }
    return s
}
