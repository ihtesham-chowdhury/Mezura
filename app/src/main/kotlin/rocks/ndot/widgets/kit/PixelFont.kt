package rocks.ndot.widgets.kit

import android.graphics.Canvas
import android.graphics.Paint

/**
 * A 5x7 dot-matrix alphabet, drawn rather than bundled as a font. The same glyphs become square
 * pixels on the Pomodoro LCDs and round dots for the Nothing-style faces, and neither needs a
 * font licence.
 */
object PixelFont {

    const val COLS = 5
    const val ROWS = 7

    private val source = mapOf(
        '0' to "01110 10001 10001 10001 10001 10001 01110",
        '1' to "00100 01100 00100 00100 00100 00100 01110",
        '2' to "01110 10001 00001 00010 00100 01000 11111",
        '3' to "11111 00010 00100 00010 00001 10001 01110",
        '4' to "00010 00110 01010 10010 11111 00010 00010",
        '5' to "11111 10000 11110 00001 00001 10001 01110",
        '6' to "00110 01000 10000 11110 10001 10001 01110",
        '7' to "11111 00001 00010 00100 01000 01000 01000",
        '8' to "01110 10001 10001 01110 10001 10001 01110",
        '9' to "01110 10001 10001 01111 00001 00010 01100",
        ':' to "00000 00100 00100 00000 00100 00100 00000",
        '.' to "00000 00000 00000 00000 00000 01100 01100",
        '-' to "00000 00000 00000 11111 00000 00000 00000",
        '/' to "00001 00010 00010 00100 01000 01000 10000",
        ' ' to "00000 00000 00000 00000 00000 00000 00000",
        'A' to "01110 10001 10001 11111 10001 10001 10001",
        'B' to "11110 10001 10001 11110 10001 10001 11110",
        'C' to "01110 10001 10000 10000 10000 10001 01110",
        'D' to "11100 10010 10001 10001 10001 10010 11100",
        'E' to "11111 10000 10000 11110 10000 10000 11111",
        'F' to "11111 10000 10000 11110 10000 10000 10000",
        'G' to "01110 10001 10000 10111 10001 10001 01111",
        'H' to "10001 10001 10001 11111 10001 10001 10001",
        'I' to "01110 00100 00100 00100 00100 00100 01110",
        'J' to "00111 00010 00010 00010 00010 10010 01100",
        'K' to "10001 10010 10100 11000 10100 10010 10001",
        'L' to "10000 10000 10000 10000 10000 10000 11111",
        'M' to "10001 11011 10101 10101 10001 10001 10001",
        'N' to "10001 10001 11001 10101 10011 10001 10001",
        'O' to "01110 10001 10001 10001 10001 10001 01110",
        'P' to "11110 10001 10001 11110 10000 10000 10000",
        'Q' to "01110 10001 10001 10001 10101 10010 01101",
        'R' to "11110 10001 10001 11110 10100 10010 10001",
        'S' to "01111 10000 10000 01110 00001 00001 11110",
        'T' to "11111 00100 00100 00100 00100 00100 00100",
        'U' to "10001 10001 10001 10001 10001 10001 01110",
        'V' to "10001 10001 10001 10001 10001 01010 00100",
        'W' to "10001 10001 10001 10101 10101 10101 01010",
        'X' to "10001 10001 01010 00100 01010 10001 10001",
        'Y' to "10001 10001 10001 01010 00100 00100 00100",
        'Z' to "11111 00001 00010 00100 01000 10000 11111",
    )

    /** Each glyph as 7 row bitmasks, bit 4 the leftmost column. */
    private val glyphs: Map<Char, IntArray> = source.mapValues { (_, rows) ->
        rows.split(" ").map { it.toInt(2) }.toIntArray()
    }

    fun isOn(ch: Char, col: Int, row: Int): Boolean {
        val g = glyphs[ch.uppercaseChar()] ?: return false
        return (g[row] shr (COLS - 1 - col)) and 1 == 1
    }

    /** How wide a character is in cells, including the gap after it. Colons and dots are narrow. */
    fun advance(ch: Char): Float = when (ch) {
        ':', '.' -> 3f
        ' ' -> 4f
        else -> 6f
    }

    /** Total width of [text] in cells, without the trailing gap. */
    fun width(text: String): Float = if (text.isEmpty()) 0f else text.sumOf { advance(it).toDouble() }.toFloat() - 1f

    /**
     * Draws [text] with its top-left at ([x], [y]). [dim] paints the unlit cells too, like a
     * real LCD's ghosting; pass null to leave them out. [round] draws dots instead of squares.
     */
    fun draw(
        canvas: Canvas, text: String, x: Float, y: Float, cell: Float,
        on: Paint, dim: Paint? = null, round: Boolean = false,
    ) {
        var cx = x
        for (ch in text) {
            // Narrow glyphs are centred in their slot: the 5-wide bitmap is shifted left.
            val shift = if (advance(ch) == 3f) -cell else 0f
            for (row in 0 until ROWS) for (col in 0 until COLS) {
                val lit = isOn(ch, col, row)
                val paint = if (lit) on else dim ?: continue
                if (!lit && advance(ch) != 6f) continue
                val px = cx + shift + col * cell
                val py = y + row * cell
                if (round) {
                    canvas.drawCircle(px + cell / 2f, py + cell / 2f, cell * 0.36f, paint)
                } else {
                    canvas.drawRect(px + cell * 0.07f, py + cell * 0.07f, px + cell * 0.93f, py + cell * 0.93f, paint)
                }
            }
            cx += advance(ch) * cell
        }
    }
}
