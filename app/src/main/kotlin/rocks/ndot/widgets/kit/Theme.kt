package rocks.ndot.widgets.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The shared chrome. Each instrument has its own material and palette - wood, aluminium, glass,
 * paper - but the frame around them is always this: black, one red, and small spaced capitals.
 * That constant frame is what makes six very different objects feel like one drawer.
 */
object Ink {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0D0D0D)
    val Raised = Color(0xFF161616)
    val Hairline = Color(0x1FFFFFFF)
    val Text = Color(0xFFF2F2F2)
    val Muted = Color(0xFF8E8E8E)
    val Faint = Color(0xFF4A4A4A)
    val Red = Color(0xFFD71921)

    // v4 Design Tokens (§4.1)
    val GROUND = Color(0xFF0A0B0D)
    val SURFACE_1 = Color(0xFF121317)
    val SURFACE_2 = Color(0xFF1A1C21)
    val SURFACE_3 = Color(0xFF23262C)
    val HAIRLINE_V4 = Color(0xFF2B2E35)
    val TEXT_WARM = Color(0xFFF2EEE6)
    val TEXT_MUTED_V4 = Color(0xFF9A968E)
    val TEXT_FAINT_V4 = Color(0xFF5E5C58)

    val AMBER = Color(0xFFFFB14A)
    val AMBER_DEEP = Color(0xFFE0872B)
    val BRASS = Color(0xFFE8B25C)
    val LED = Color(0xFFFFD08A)
    val VERMILION = Color(0xFFE5533D)
    val SAGE = Color(0xFF9CC5A1)
    val BRICK = Color(0xFFD9663F)

    val BREATH_IN = Color(0xFF7FE0F5)
    val BREATH_HOLD = Color(0xFFB9A7FF)
    val BREATH_OUT = Color(0xFFFFB48A)
}

enum class ColorTheme(val id: String, val label: String, val accent: Color) {
    CLASSIC("classic", "Classic", Color(0xFFD71921)),
    AMBER("amber", "Amber Ink", Color(0xFFFFAF38)),
    CRIMSON("crimson", "Crimson Ink", Color(0xFFE63946)),
    EMERALD("emerald", "Emerald Ink", Color(0xFF2EC4B6)),
    COBALT("cobalt", "Cobalt Ink", Color(0xFF3A86FF));

    companion object {
        fun byId(id: String?): ColorTheme = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

object Fonts {
    val mono: FontFamily = FontFamily.Monospace
    val serif: FontFamily = FontFamily.Serif
    val sans: FontFamily = FontFamily.SansSerif
    val condensed: FontFamily = FontFamily(
        Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    )
    val condensedTypeface: android.graphics.Typeface =
        android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    val lightTypeface: android.graphics.Typeface =
        android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    val mediumTypeface: android.graphics.Typeface =
        android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
}

/** The small spaced capitals used for every label in the app chrome. */
fun labelStyle(color: Color = Ink.Muted, size: TextUnit = 11.sp, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = Fonts.mono, fontWeight = weight, fontSize = size, letterSpacing = 0.14.em, color = color)

/**
 * A value that changes every display frame while [active], and never otherwise. Read it inside
 * a draw lambda and only the drawing re-runs - no recomposition, no layout - which is what keeps
 * the moving instruments at the panel's full 90 or 120Hz.
 */
@Composable
fun rememberFrameTicker(active: Boolean): LongState {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            while (true) withFrameNanos { tick.longValue = it }
        }
    }
    return tick
}
