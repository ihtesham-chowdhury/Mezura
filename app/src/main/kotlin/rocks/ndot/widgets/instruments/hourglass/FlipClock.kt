package rocks.ndot.widgets.instruments.hourglass

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.PixelFont
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/** How a flip clock is finished, so it matches the hourglass beneath it. */
class FlipTheme(
    val cardTop: Int,
    val cardBottom: Int,
    val digit: Int,
    val hinge: Int,
    val dots: Boolean = false,
    val shadow: Boolean = false,
    val typeface: Typeface = Fonts.condensedTypeface,
)

/**
 * A split-flap clock. When a digit changes, the top leaf carrying the old digit falls forward
 * over the hinge, and the bottom leaf carrying the new one lands in its place - drawn as a
 * vertical squash about the hinge line, which is what that rotation looks like face-on.
 */
@Composable
fun FlipClock(text: String, theme: FlipTheme, modifier: Modifier = Modifier, maxCardHeight: Float = 118f) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val digits = text.count { it != ':' }
        val colons = text.length - digits
        val gap = maxWidth.value * 0.02f
        val colonW = maxWidth.value * 0.06f
        var cardW = (maxWidth.value - colonW * colons - gap * (text.length - 1)) / digits.coerceAtLeast(1)
        var cardH = cardW * 1.42f
        if (cardH > maxCardHeight) {
            cardH = maxCardHeight
            cardW = cardH / 1.42f
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap.dp), verticalAlignment = Alignment.CenterVertically) {
            // Keyed by position from the right, so going from 10:00 to 9:59 doesn't reshuffle cards.
            text.forEachIndexed { index, ch ->
                if (ch == ':') {
                    Colon(theme, Modifier.size(colonW.dp, cardH.dp))
                } else {
                    androidx.compose.runtime.key(text.length - index) {
                        FlipCard(ch, theme, Modifier.size(cardW.dp, cardH.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Colon(theme: FlipTheme, modifier: Modifier) {
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.digit }
            val r = size.width * 0.16f
            canvas.nativeCanvas.drawCircle(size.width / 2f, size.height * 0.36f, r, paint)
            canvas.nativeCanvas.drawCircle(size.width / 2f, size.height * 0.64f, r, paint)
        }
    }
}

@Composable
private fun FlipCard(ch: Char, theme: FlipTheme, modifier: Modifier) {
    var current by remember { mutableStateOf(ch) }
    var previous by remember { mutableStateOf(ch) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(ch) {
        if (ch != current) {
            previous = current
            current = ch
            progress.snapTo(0f)
            progress.animateTo(1f, tween(420, easing = FastOutLinearInEasing))
        }
    }
    Canvas(modifier) {
        val p = progress.value
        drawIntoCanvas { FlipArt.draw(it.nativeCanvas, size.width, size.height, previous, current, p, theme) }
    }
}

private object FlipArt {
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun draw(c: Canvas, w: Float, h: Float, old: Char, new: Char, p: Float, t: FlipTheme) {
        if (t.shadow) {
            card.color = t.cardBottom
            card.setShadowLayer(w * 0.14f, 0f, w * 0.05f, 0x2E000000)
            rect.set(0f, 0f, w, h)
            c.drawRoundRect(rect, w * 0.12f, w * 0.12f, card)
            card.clearShadowLayer()
        }
        // The halves that don't move: new digit on top, old one below until the leaf lands.
        half(c, w, h, new, top = true, t)
        half(c, w, h, if (p >= 1f) new else old, top = false, t)
        if (p < 1f) {
            val hinge = h / 2f
            c.save()
            if (p < 0.5f) {
                // The old top leaf falling towards you: squashing onto the hinge.
                val s = cos(p * PI).toFloat()
                c.scale(1f, s, w / 2f, hinge)
                half(c, w, h, old, top = true, t)
                shadeLeaf(c, w, 0f, hinge, 1f - s)
            } else {
                // The new bottom leaf coming down to rest.
                val s = abs(cos(p * PI).toFloat())
                c.scale(1f, s, w / 2f, hinge)
                half(c, w, h, new, top = false, t)
                shadeLeaf(c, w, hinge, h, (1f - s) * 0.8f)
            }
            c.restore()
        }
        // Hinge line across the middle.
        card.shader = null
        card.color = t.hinge
        val hw = maxOf(1.5f, h * 0.016f)
        c.drawRect(0f, h / 2f - hw / 2f, w, h / 2f + hw / 2f, card)
    }

    private fun shadeLeaf(c: Canvas, w: Float, top: Float, bottom: Float, amount: Float) {
        shade.color = android.graphics.Color.argb((140 * amount).toInt().coerceIn(0, 255), 0, 0, 0)
        c.drawRect(0f, top, w, bottom, shade)
    }

    private fun half(c: Canvas, w: Float, h: Float, ch: Char, top: Boolean, t: FlipTheme) {
        val r = w * 0.12f
        c.save()
        if (top) c.clipRect(0f, 0f, w, h / 2f) else c.clipRect(0f, h / 2f, w, h)
        card.shader = null
        card.color = if (top) t.cardTop else t.cardBottom
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, r, r, card)
        if (t.dots) {
            val cell = min(w / 6.4f, h / 9.4f)
            glyph.color = t.digit
            dim.color = 0x0FFFFFFF
            PixelFont.draw(c, ch.toString(), (w - cell * 5f) / 2f, (h - cell * 7f) / 2f, cell, glyph, dim, round = true)
        } else {
            glyph.typeface = t.typeface
            glyph.textSize = h * 0.78f
            glyph.color = t.digit
            c.drawText(ch.toString(), w / 2f, h * 0.53f - (glyph.ascent() + glyph.descent()) / 2f, glyph)
        }
        if (top) {
            card.shader = LinearGradient(0f, 0f, 0f, h / 2f, 0x10FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h / 2f, card)
            card.shader = null
        }
        c.restore()
    }
}
