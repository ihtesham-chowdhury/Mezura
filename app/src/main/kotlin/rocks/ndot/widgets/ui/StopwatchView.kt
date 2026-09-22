package rocks.ndot.widgets.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import rocks.ndot.widgets.common.StopwatchRenderer

/**
 * The stopwatch at full frame rate. This is the one place the sweep can actually be smooth: a
 * normal View in a normal window, repainted by the Choreographer, with no RemoteViews rate
 * limit sitting between the drawing and the screen.
 *
 * It draws straight onto the view's own canvas rather than into a bitmap, so a running sweep
 * allocates nothing per frame.
 */
class StopwatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Read once per frame; owned by the Activity so the view holds no timer state itself. */
    var elapsedProvider: () -> Long = { 0L }
    var lapProvider: () -> Long? = { null }
    var runningProvider: () -> Boolean = { false }

    var dial: StopwatchRenderer.Dial = StopwatchRenderer.Dial.BLACK
        set(value) { field = value; invalidate() }

    var accent: Int = 0xFFD71921.toInt()
        set(value) { field = value; invalidate() }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // Only ask for a frame while something is actually moving; a stopped face is
            // static, so there is nothing to redraw.
            if (runningProvider()) invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        StopwatchRenderer.draw(
            c = canvas,
            widthPx = width,
            heightPx = height,
            face = StopwatchRenderer.Face(
                elapsedMs = elapsedProvider(),
                splitMs = lapProvider(),
                running = runningProvider(),
            ),
            dial = dial,
            accent = accent,
        )
    }
}
