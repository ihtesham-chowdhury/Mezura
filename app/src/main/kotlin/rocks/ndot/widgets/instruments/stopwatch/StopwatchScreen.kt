package rocks.ndot.widgets.instruments.stopwatch

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.app.KeepAwake
import rocks.ndot.widgets.app.LightChrome
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.Clicks
import rocks.ndot.widgets.common.StopwatchRenderer
import rocks.ndot.widgets.kit.ChipColors
import rocks.ndot.widgets.kit.ChipRow
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle
import rocks.ndot.widgets.kit.rememberFrameTicker
import rocks.ndot.widgets.widgets.StopwatchEngine
import rocks.ndot.widgets.widgets.StopwatchWidgetProvider
import rocks.ndot.widgets.widgets.TimerService
import kotlin.math.min

/**
 * The Swiss chronograph. There are no on-screen buttons: the watch is operated the way a real
 * one is - press the crown to start and stop, press the side pusher to take a lap while it
 * runs or to zero it once stopped. The face is sized to whatever space the screen has, keeping
 * the case round and the crown in view on any aspect ratio.
 */
@Composable
fun StopwatchScreen() {
    val context = LocalContext.current
    var white by remember { mutableStateOf(StopwatchWidgetProvider.isWhiteDial(context)) }
    var snap by remember { mutableStateOf(StopwatchEngine.snapshot(context)) }
    val accent = remember { Appearance.accent(context) }
    val crownPress = remember { Animatable(0f) }
    val pusherPress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val ticker = rememberFrameTicker(snap.running)

    LightChrome(false)
    KeepAwake(snap.running)

    fun commit() {
        snap = StopwatchEngine.snapshot(context)
        StopwatchWidgetProvider.updateAll(context)
        TimerService.syncToState(context)
    }

    fun press(anim: Animatable<Float, *>) {
        scope.launch {
            anim.animateTo(1f, tween(55))
            anim.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 900f))
        }
    }

    fun onCrown() {
        val wasRunning = snap.running
        StopwatchEngine.toggle(context)
        click(context, if (wasRunning) Clicks.Kind.STOP else Clicks.Kind.START)
        press(crownPress)
        commit()
    }

    fun onPusher() {
        val now = System.currentTimeMillis()
        when {
            snap.running -> {
                StopwatchEngine.recordLap(context)
                click(context, Clicks.Kind.LAP)
            }
            snap.elapsedAt(now) > 0L -> {
                StopwatchEngine.reset(context)
                click(context, Clicks.Kind.STOP)
            }
            else -> return
        }
        press(pusherPress)
        commit()
    }

    Column(Modifier.fillMaxSize().background(Ink.Black)) {
        ToolHeader("STOPWATCH", Ink.Muted)
        ChipRow(
            items = listOf("Black dial", "White dial"),
            selected = if (white) 1 else 0,
            colors = ChipColors.Dark,
            onSelect = {
                white = it == 1
                StopwatchWidgetProvider.setWhiteDial(context, white)
                StopwatchWidgetProvider.updateAll(context)
            },
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            // Fit the case to whichever dimension runs out first, so it is never cropped or
            // squashed - the fix for the face that didn't scale with the screen.
            val faceWidth = min(maxWidth.value, maxHeight.value * StopwatchRenderer.ASPECT).dp
            WatchFace(
                modifier = Modifier.size(faceWidth, faceWidth / StopwatchRenderer.ASPECT).align(Alignment.Center),
                snap = snap,
                dial = if (white) StopwatchRenderer.Dial.WHITE else StopwatchRenderer.Dial.BLACK,
                accent = accent,
                ticker = { ticker.longValue },
                crownPress = { crownPress.value },
                pusherPress = { pusherPress.value },
                onCrown = ::onCrown,
                onPusher = ::onPusher,
            )
        }

        Readout(snap)
        Spacer(Modifier.height(4.dp))
        BasicText(
            if (snap.running) "PUSHER · LAP" else if (snap.accumulated > 0) "CROWN · RESUME    PUSHER · RESET" else "PRESS THE CROWN TO START",
            style = labelStyle(Ink.Faint, 10.sp).copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        LapList(snap.laps, Modifier.fillMaxWidth().height(118.dp).padding(horizontal = 28.dp))
        NavSpacer()
    }
}

/** The pusher click, honouring the app's sound and haptics switches independently. */
private fun click(context: android.content.Context, kind: Clicks.Kind) {
    if (AppPrefs.sound(context)) {
        Clicks.play(context, kind, withHaptic = AppPrefs.haptics(context))
    } else {
        rocks.ndot.widgets.kit.Feedback.haptic(context, rocks.ndot.widgets.kit.Feedback.Haptic.CLICK)
    }
}

@Composable
private fun WatchFace(
    modifier: Modifier,
    snap: StopwatchEngine.Snapshot,
    dial: StopwatchRenderer.Dial,
    accent: Int,
    ticker: () -> Long,
    crownPress: () -> Float,
    pusherPress: () -> Float,
    onCrown: () -> Unit,
    onPusher: () -> Unit,
) {
    val body = remember { BodyCache() }
    val currentSnap by androidx.compose.runtime.rememberUpdatedState(snap)
    Canvas(
        modifier.pointerInput(Unit) {
            val minTouch = 28.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                val g = StopwatchRenderer.geometry(size.width, size.height)
                // A pusher acts on the way down, like the real thing - not on release.
                when {
                    g.crownHit(down.position.x, down.position.y, minTouch) -> { down.consume(); onCrown() }
                    g.pusherHit(down.position.x, down.position.y, minTouch) -> { down.consume(); onPusher() }
                }
                waitForUpOrCancellation()
            }
        }
    ) {
        ticker() // redraw every frame while running
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) return@Canvas
        val g = StopwatchRenderer.geometry(w, h)
        val s = currentSnap
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            StopwatchRenderer.drawControls(c, g, dial, crownPress(), pusherPress())
            c.drawBitmap(body.get(w, h, dial, g), 0f, 0f, null)
            StopwatchRenderer.drawHands(
                c, g,
                StopwatchRenderer.Face(s.elapsedAt(System.currentTimeMillis()), s.laps.lastOrNull(), s.running),
                dial, accent,
            )
        }
    }
}

/** The static watch body, rendered once per size and dial rather than every frame. */
private class BodyCache {
    private var bitmap: Bitmap? = null
    private var key = ""

    fun get(w: Int, h: Int, dial: StopwatchRenderer.Dial, g: StopwatchRenderer.Geometry): Bitmap {
        val wanted = "$w:$h:$dial"
        val existing = bitmap
        if (existing != null && key == wanted) return existing
        // The old one isn't recycled by hand: the render thread may still be drawing it this
        // frame, and recycling under it crashes. The collector takes it once nothing refers to it.
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        StopwatchRenderer.drawBody(android.graphics.Canvas(bmp), g, dial)
        bitmap = bmp
        key = wanted
        return bmp
    }
}

@Composable
private fun Readout(snap: StopwatchEngine.Snapshot) {
    // Tenths only need ten updates a second; the dial is what runs at the full frame rate.
    var text by remember { mutableStateOf(StopwatchEngine.format(snap.elapsedAt(System.currentTimeMillis()))) }
    LaunchedEffect(snap) {
        text = StopwatchEngine.format(snap.elapsedAt(System.currentTimeMillis()))
        while (snap.running) {
            delay(50)
            text = StopwatchEngine.format(snap.elapsedAt(System.currentTimeMillis()))
        }
    }
    BasicText(
        text,
        style = TextStyle(
            fontFamily = Fonts.condensed, fontWeight = FontWeight.Bold, fontSize = 46.sp,
            color = Ink.Text, textAlign = TextAlign.Center, fontFeatureSettings = "tnum",
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LapList(laps: List<Long>, modifier: Modifier) {
    if (laps.isEmpty()) {
        Box(modifier)
        return
    }
    val splits = laps.mapIndexed { i, total -> total - (if (i == 0) 0L else laps[i - 1]) }
    val fastest = if (splits.size > 1) splits.min() else -1L
    val slowest = if (splits.size > 1) splits.max() else -1L
    LazyColumn(modifier) {
        itemsIndexed(laps.reversed()) { i, total ->
            val index = laps.size - i
            val split = splits[index - 1]
            val color = when (split) {
                fastest -> Ink.Text
                slowest -> Ink.Red
                else -> Ink.Muted
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("LAP %02d".format(index), style = labelStyle(Ink.Faint, 11.sp), modifier = Modifier.weight(1f))
                BasicText("+" + StopwatchEngine.format(split), style = labelStyle(color, 12.sp).copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1.2f))
                BasicText(StopwatchEngine.format(total), style = labelStyle(Ink.Muted, 12.sp).copy(textAlign = TextAlign.End, fontFeatureSettings = "tnum"), modifier = Modifier.weight(1.2f))
            }
        }
    }
}
