package rocks.ndot.widgets.instruments.hourglass

import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import rocks.ndot.widgets.kit.ChipColors
import rocks.ndot.widgets.kit.ChipRow
import rocks.ndot.widgets.kit.DurationDialog
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle
import rocks.ndot.widgets.kit.rememberFrameTicker
import rocks.ndot.widgets.widgets.HourglassEngine
import rocks.ndot.widgets.widgets.HourglassEngine.State
import rocks.ndot.widgets.widgets.TimerService

/** The chrome that goes with each glass: labels, chips and a flip clock in its materials. */
private class GlassLook(val label: Color, val chips: ChipColors, val flip: FlipTheme)

private fun lookFor(design: GlassDesign): GlassLook = when (design) {
    GlassDesign.CLASSIC -> GlassLook(
        Color(0x99F1E3C4),
        ChipColors(Color(0x14FFFFFF), Color(0xFFE6DCC8), Color(0xFFE6C864), Color(0xFF2A1A0C)),
        FlipTheme(0xFF3D2717.toInt(), 0xFF33200F.toInt(), 0xFFF1E3C4.toInt(), 0xFF140A03.toInt()),
    )
    GlassDesign.DUSK -> GlassLook(
        Color(0xB3F39C80),
        ChipColors(Color(0x14FFFFFF), Color(0xFFE9D7E4), Color(0xFFE07C62), Color(0xFF2A1320)),
        FlipTheme(0xFF2B1936.toInt(), 0xFF25142E.toInt(), 0xFFF39C80.toInt(), 0xFF170B1E.toInt()),
    )
    GlassDesign.PRISM -> GlassLook(
        Color(0x8C2B2B30),
        ChipColors(Color(0x0F000000), Color(0xFF3A3A40), Color(0xFF2B2B30), Color(0xFFF5F5F5)),
        FlipTheme(0xFFF7F6F4.toInt(), 0xFFEEEDEA.toInt(), 0xFF2B2B30.toInt(), 0xFFC9C7C3.toInt(), shadow = true),
    )
    GlassDesign.DOT -> GlassLook(
        Color(0x80FFFFFF),
        ChipColors(Color(0xFF141414), Color(0xFFB8B8B8), Color(0xFFD71921), Color(0xFFFFFFFF)),
        FlipTheme(0xFF111111.toInt(), 0xFF0C0C0C.toInt(), 0xFFF2F2F2.toInt(), 0xFF000000.toInt(), dots = true),
    )
}

/**
 * The hourglass. At rest its sand has run through, the way one sits on a desk; pick a time and
 * it turns itself over and starts. Tap the glass to pause or carry on, hold it to reset. The flip
 * clock above says exactly how long is left, in the glass's own materials.
 */
@Composable
fun HourglassScreen() {
    val context = LocalContext.current
    var design by remember {
        mutableStateOf(
            GlassDesign.byId(AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_HOURGLASS_STYLE, GlassDesign.CLASSIC.id))
        )
    }
    var snap by remember { mutableStateOf(HourglassEngine.snapshot(context)) }
    var presets by remember { mutableStateOf(AppPrefs.hourglassPresets(context)) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    val renderer = remember { GlassRenderer() }
    val flip = remember { Animatable(0f) }
    var flipping by remember { mutableStateOf(false) }
    var glassBox by remember { mutableStateOf(RectF()) }
    val accent = remember { Appearance.accent(context) }
    val scope = rememberCoroutineScope()
    val look = lookFor(design)
    val askNotifications = rocks.ndot.widgets.app.LocalAskNotifications.current
    val running = snap.state == State.RUNNING
    val ticker = rememberFrameTicker(running || flipping)

    LightChrome(design.light)
    KeepAwake(running)

    fun commit() {
        snap = HourglassEngine.snapshot(context)
        TimerService.syncToState(context)
    }

    // Finish on time while on screen: chime, buzz, and let the service know it can stop.
    LaunchedEffect(snap) {
        while (snap.state == State.RUNNING) {
            val left = snap.remainingAt(System.currentTimeMillis())
            if (left <= 0L) {
                if (HourglassEngine.finish(context)) {
                    Feedback.play(context, Feedback.Sound.CHIME)
                    Feedback.haptic(context, Feedback.Haptic.DONE)
                }
                commit()
                break
            }
            delay(minOf(left, 200L))
            val fresh = HourglassEngine.snapshot(context)
            if (fresh != snap) { snap = fresh; break }
        }
    }

    // The flip clock's text, refreshed a few times a second rather than every frame.
    var clockText by remember { mutableStateOf(clockFor(snap, System.currentTimeMillis())) }
    LaunchedEffect(snap) {
        clockText = clockFor(snap, System.currentTimeMillis())
        while (snap.state == State.RUNNING) {
            delay(100)
            clockText = clockFor(snap, System.currentTimeMillis())
        }
    }

    fun turnOver(seconds: Long) {
        if (flipping) return
        askNotifications()
        scope.launch {
            HourglassEngine.setDuration(context, seconds * 1000L)
            snap = HourglassEngine.snapshot(context)
            clockText = clockFor(snap, System.currentTimeMillis())
            flipping = true
            Feedback.haptic(context, Feedback.Haptic.TICK)
            flip.snapTo(0f)
            flip.animateTo(1f, tween(950, easing = CubicBezierEasing(0.6f, 0f, 0.2f, 1f)))
            flipping = false
            flip.snapTo(0f)
            HourglassEngine.start(context, seconds * 1000L)
            Feedback.press(context, Feedback.Sound.CLACK, Feedback.Haptic.HEAVY)
            commit()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            ticker.longValue
            // Nothing to fit the glass into until the layout has measured its box.
            if (glassBox.width() <= 0f || glassBox.height() <= 0f) return@Canvas
            val now = System.currentTimeMillis()
            val s = snap
            val remaining = when (s.state) {
                State.IDLE, State.DONE -> 0f
                else -> s.fractionAt(now)
            }
            val seconds = ((System.nanoTime() / 1_000_000L) % 1_000_000L) / 1000f
            drawIntoCanvas {
                renderer.draw(
                    it.nativeCanvas, design, size.width, size.height, glassBox,
                    remaining = if (flipping) 0f else remaining,
                    seconds = seconds,
                    running = s.state == State.RUNNING && !flipping,
                    settled = flipping || s.state != State.RUNNING,
                    flipDegrees = if (flipping) 180f * flip.value else 0f,
                    accent = accent,
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
            ToolHeader("HOURGLASS", look.label) {
                if (snap.state == State.RUNNING || snap.state == State.PAUSED || snap.state == State.DONE) {
                    TextAction("RESET", look.label, {
                        HourglassEngine.reset(context)
                        Feedback.haptic(context, Feedback.Haptic.HEAVY)
                        commit()
                    })
                }
            }
            ChipRow(
                items = GlassDesign.entries.map { it.label },
                selected = design.ordinal,
                colors = look.chips,
                onSelect = {
                    design = GlassDesign.entries[it]
                    AppPrefs.setDesign(context, "hourglass", design.id)
                    Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                },
                onLongPress = {
                    val picked = GlassDesign.entries[it]
                    design = picked
                    AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_HOURGLASS_STYLE, picked.id, "${picked.label} style for Hourglass")
                },
            )
            Spacer(Modifier.height(14.dp))
            FlipClock(clockText, look.flip, Modifier.fillMaxWidth().padding(horizontal = 40.dp))
            Spacer(Modifier.height(8.dp))
            BasicText(
                when {
                    flipping -> "TURNING OVER"
                    snap.state == State.RUNNING -> "TAP THE GLASS TO PAUSE"
                    snap.state == State.PAUSED -> "PAUSED · TAP TO CARRY ON"
                    snap.state == State.DONE -> "TIME'S UP · PICK A TIME TO GO AGAIN"
                    else -> "PICK A TIME TO TURN IT OVER"
                },
                style = labelStyle(look.label, 10.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInParent()
                        val r = RectF(b.left, b.top, b.right, b.bottom)
                        if (r != glassBox) glassBox = r
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val s = HourglassEngine.snapshot(context)
                                when (s.state) {
                                    State.RUNNING -> { HourglassEngine.pause(context); Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.CLICK) }
                                    State.PAUSED -> { HourglassEngine.resume(context); Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.CLICK) }
                                    State.IDLE, State.DONE -> turnOver(s.durationMs / 1000L)
                                }
                                commit()
                            },
                            onLongPress = {
                                HourglassEngine.reset(context)
                                Feedback.haptic(context, Feedback.Haptic.HEAVY)
                                commit()
                            },
                        )
                    }
            )

            ChipRow(
                items = presets.map { presetLabel(it) } + "+",
                selected = presets.indexOf(snap.durationMs / 1000L),
                colors = look.chips,
                onSelect = { i ->
                    if (i == presets.size) {
                        adding = true
                    } else {
                        Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.CLICK)
                        turnOver(presets[i])
                    }
                },
                onLongPress = { i -> if (i < presets.size) editing = i },
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                "HOLD A TIME TO CHANGE IT",
                style = labelStyle(look.label.copy(alpha = look.label.alpha * 0.6f), 9.sp).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            NavSpacer()
        }
    }

    editing?.let { index ->
        DurationDialog(
            title = "EDIT PRESET",
            initialSeconds = presets[index],
            onDismiss = { editing = null },
            onConfirm = { secs ->
                presets = (presets.toMutableList().also { it[index] = secs }).distinct().sorted()
                AppPrefs.setHourglassPresets(context, presets)
                editing = null
            },
            onDelete = if (presets.size > 1) {
                {
                    presets = presets.toMutableList().also { it.removeAt(index) }
                    AppPrefs.setHourglassPresets(context, presets)
                    editing = null
                }
            } else null,
        )
    }
    if (adding) {
        DurationDialog(
            title = "NEW PRESET",
            initialSeconds = 300,
            onDismiss = { adding = false },
            onConfirm = { secs ->
                presets = (presets + secs).distinct().sorted()
                AppPrefs.setHourglassPresets(context, presets)
                adding = false
            },
        )
    }
}

private fun clockFor(s: HourglassEngine.Snapshot, now: Long): String {
    val ms = when (s.state) {
        State.IDLE -> s.durationMs
        State.DONE -> 0L
        else -> s.remainingAt(now)
    }
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** 60 -> "1m", 90 -> "1:30", 5400 -> "1h30", 45 -> "45s". */
fun presetLabel(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds / 60) % 60
    val s = seconds % 60
    return when {
        h > 0 && m == 0L && s == 0L -> "${h}h"
        h > 0 -> "${h}h%02d".format(m)
        seconds < 60 -> "${s}s"
        s == 0L -> "${m}m"
        else -> "$m:%02d".format(s)
    }
}
