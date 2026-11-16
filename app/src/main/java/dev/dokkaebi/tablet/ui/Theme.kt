package dev.dokkaebi.tablet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Hud {
    val Bg = Color(0xFF07090B)
    val Panel = Color(0xFF0E1317)
    val PanelHi = Color(0xFF151C22)
    val Line = Color(0xFF26323A)
    val Accent = Color(0xFFC6FF00)      // acid yellow-green
    val AccentDim = Color(0xFF5E7A00)
    val Hot = Color(0xFFFF2D7A)         // glitch magenta
    val Cyan = Color(0xFF00E5FF)
    val Warn = Color(0xFFFFB300)
    val Text = Color(0xFFD7E3E8)
    val TextDim = Color(0xFF6B7C86)

    val Mono = FontFamily.Monospace
    fun mono(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Text, spacing: Float = 1f) =
        TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = spacing.sp)
}

@Composable
fun DokkaebiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Hud.Accent,
            onPrimary = Hud.Bg,
            secondary = Hud.Hot,
            background = Hud.Bg,
            surface = Hud.Panel,
            onSurface = Hud.Text,
            onBackground = Hud.Text,
        ),
        content = content,
    )
}
