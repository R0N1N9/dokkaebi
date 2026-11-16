package dev.dokkaebi.tablet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Original goblin (dokkaebi) mask mark drawn on a 100x100 grid: two horns,
 * slanted eyes, and a fanged grin. [eyeGlow] 0..1 brightens the eyes.
 */
@Composable
fun GoblinMask(modifier: Modifier, color: Color = Hud.Accent, eye: Color = Hud.Hot, eyeGlow: Float = 1f) {
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val ox = (size.width - 100 * s) / 2
        val oy = (size.height - 100 * s) / 2
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        fun path(vararg pts: Pair<Float, Float>) = Path().apply {
            pts.forEachIndexed { i, (x, y) -> val o = p(x, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            close()
        }
        // horns
        drawPath(path(26f to 30f, 14f to 2f, 40f to 22f), color)
        drawPath(path(74f to 30f, 86f to 2f, 60f to 22f), color)
        // face outline
        val face = Path().apply {
            val a = p(50f, 18f); moveTo(a.x, a.y)
            val c1 = p(76f, 18f); val c2 = p(88f, 36f); val e = p(88f, 54f); cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
            val c3 = p(88f, 80f); val c4 = p(70f, 96f); val e2 = p(50f, 96f); cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
            val c5 = p(30f, 96f); val c6 = p(12f, 80f); val e3 = p(12f, 54f); cubicTo(c5.x, c5.y, c6.x, c6.y, e3.x, e3.y)
            val c7 = p(12f, 36f); val c8 = p(24f, 18f); cubicTo(c7.x, c7.y, c8.x, c8.y, a.x, a.y)
            close()
        }
        drawPath(face, color, style = Stroke(4f * s))
        // brow
        drawLine(color, p(24f, 40f), p(44f, 46f), 3f * s)
        drawLine(color, p(76f, 40f), p(56f, 46f), 3f * s)
        // eyes
        val eyeC = eye.copy(alpha = 0.35f + 0.65f * eyeGlow)
        drawPath(path(24f to 50f, 44f to 55f, 25f to 60f), eyeC)
        drawPath(path(76f to 50f, 56f to 55f, 75f to 60f), eyeC)
        // grin with fangs
        drawPath(
            path(
                22f to 68f, 32f to 70f, 36f to 80f, 41f to 72f, 50f to 74f,
                59f to 72f, 64f to 80f, 68f to 70f, 78f to 68f, 64f to 86f, 50f to 89f, 36f to 86f,
            ),
            color,
        )
    }
}
