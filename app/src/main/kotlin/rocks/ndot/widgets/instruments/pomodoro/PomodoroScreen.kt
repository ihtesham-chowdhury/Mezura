package rocks.ndot.widgets.instruments.pomodoro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.app.KeepAwake
import rocks.ndot.widgets.app.LightChrome
import rocks.ndot.widgets.kit.ChipColors
import rocks.ndot.widgets.kit.ChipRow
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.StepKey
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle
import rocks.ndot.widgets.kit.rememberFrameTicker
import rocks.ndot.widgets.widgets.HourglassWidgetProvider
import rocks.ndot.widgets.widgets.TimerEngine
import rocks.ndot.widgets.widgets.TimerService
import kotlin.math.abs
import kotlin.math.min

private val FOCUS_PRESETS = listOf(10, 25, 45, 60, 90)
private val BREAK_PRESETS = listOf(5, 10, 15, 30)

/**
 * The Pomodoro timer, as a small piece of hardware you choose from three. Every key presses in,
 * clicks and buzzes; the Module's wheel and the Slab's crown dial the focus length in by the
 * minute. The session is the same one the sand-timer widget shows, so starting it here starts
 * it there too.
 */
@Composable
fun PomodoroScreen() {
    val context = LocalContext.current
    var device by remember { mutableStateOf(Device.byId(AppPrefs.design(context, "pomodoro", Device.MODULE.id))) }
    var snap by remember { mutableStateOf(TimerEngine.snapshot(context)) }
    var soundOn by remember { mutableStateOf(AppPrefs.sound(context)) }
    val askNotifications = rocks.ndot.widgets.app.LocalAskNotifications.current

    fun refresh() {
        snap = TimerEngine.snapshot(context)
        HourglassWidgetProvider.updateAll(context)
        TimerService.syncToState(context)
    }

    // Poll the engine: the service may roll the phase over, and the clock needs its seconds.
    LaunchedEffect(Unit) {
        while (true) {
            val advanced = TimerEngine.advanceIfDue(context)
            if (advanced != null) {
                // The app is on screen, so it chimes itself and the service keeps quiet.
                Feedback.play(context, Feedback.Sound.CHIME)
                Feedback.haptic(context, Feedback.Haptic.DONE)
                refresh()
            } else {
                val fresh = TimerEngine.snapshot(context)
                if (fresh != snap) snap = fresh
            }
            delay(200)
        }
    }

    LightChrome(device.light)
    KeepAwake(snap.running)

    val background = if (device.light) {
        Brush.verticalGradient(listOf(Color(0xFFE6E6E4), Color(0xFFD3D3D1)))
    } else {
        Brush.radialGradient(listOf(Color(0xFF262626), Color(0xFF0A0A0A)), radius = 1400f)
    }
    val chips = if (device.light) ChipColors.Light else ChipColors(
        Color(0x14FFFFFF), Color(0xFFCFCFCF),
        if (device == Device.MODULE) Color(0xFFEE8A2A) else Color(0xFFE0322A), Color(0xFF111111),
    )
    val labelColor = if (device.light) Color(0x80000000) else Color(0x80FFFFFF)

    Column(Modifier.fillMaxSize().background(background)) {
        ToolHeader("POMODORO", labelColor) {
            TextAction("RESET", labelColor, {
                TimerEngine.reset(context)
                Feedback.haptic(context, Feedback.Haptic.HEAVY)
                refresh()
            })
        }
        ChipRow(
            items = Device.entries.map { it.label },
            selected = device.ordinal,
            colors = chips,
            onSelect = {
                device = Device.entries[it]
                AppPrefs.setDesign(context, "pomodoro", device.id)
                Feedback.press(context, device.sound, Feedback.Haptic.TICK)
            },
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = device,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.94f) + slideInHorizontally(tween(260)) { it / 10 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "device",
            ) { d ->
                val dTotal = d.aspect + Device.PAD_TOP + Device.PAD_BOTTOM
                val du = min(maxWidth.value, maxHeight.value / dTotal).coerceAtMost(520f)
                DeviceFace(
                    device = d,
                    snap = snap,
                    soundOn = soundOn,
                    modifier = Modifier.size(du.dp, (du * dTotal).dp),
                    onAction = { action ->
                        when (action) {
                            DeviceAction.TOGGLE -> {
                                if (!snap.running) askNotifications()
                                TimerEngine.toggle(context)
                            }
                            DeviceAction.RESET -> TimerEngine.reset(context)
                            DeviceAction.SKIP -> if (snap.phase != TimerEngine.Phase.IDLE) TimerEngine.advancePhase(context, countIt = false)
                            DeviceAction.NEXT_FOCUS_PRESET -> TimerEngine.setFocusMinutes(context, nextPreset(FOCUS_PRESETS, TimerEngine.focusMinutes(context)))
                            DeviceAction.WORK -> when (snap.phase) {
                                TimerEngine.Phase.BREAK -> TimerEngine.switchTo(context, TimerEngine.Phase.FOCUS)
                                TimerEngine.Phase.IDLE -> TimerEngine.setFocusMinutes(context, nextPreset(FOCUS_PRESETS, TimerEngine.focusMinutes(context)))
                                TimerEngine.Phase.FOCUS -> Unit
                            }
                            DeviceAction.BREAK -> when (snap.phase) {
                                TimerEngine.Phase.FOCUS -> TimerEngine.switchTo(context, TimerEngine.Phase.BREAK)
                                TimerEngine.Phase.IDLE -> TimerEngine.setBreakMinutes(context, nextPreset(BREAK_PRESETS, TimerEngine.breakMinutes(context)))
                                TimerEngine.Phase.BREAK -> Unit
                            }
                            DeviceAction.CYCLES -> TimerEngine.setGoal(context, if (snap.goal >= 8) 0 else snap.goal + 1)
                            DeviceAction.VOLUME -> {
                                soundOn = !soundOn
                                AppPrefs.setSound(context, soundOn)
                            }
                        }
                        refresh()
                    },
                    onDial = { steps ->
                        // The wheel only sets up the next session; a running one isn't stretched.
                        if (snap.phase == TimerEngine.Phase.IDLE) {
                            TimerEngine.setFocusMinutes(context, TimerEngine.focusMinutes(context) + steps)
                            refresh()
                            true
                        } else false
                    },
                )
            }
        }

        Settings(snap, chips, labelColor, device.light) { refresh() }
        NavSpacer()
    }
}

private fun nextPreset(presets: List<Int>, current: Int): Int =
    presets.firstOrNull { it > current } ?: presets.first()

enum class DeviceAction { TOGGLE, RESET, SKIP, NEXT_FOCUS_PRESET, WORK, BREAK, CYCLES, VOLUME }

private fun actionFor(device: Device, key: Int, long: Boolean): DeviceAction? = when (device) {
    Device.MODULE -> when (key) {
        0 -> DeviceAction.RESET
        1 -> DeviceAction.TOGGLE
        2 -> DeviceAction.SKIP
        3 -> DeviceAction.NEXT_FOCUS_PRESET
        else -> null
    }
    Device.SLAB -> when (key) {
        0 -> if (long) DeviceAction.RESET else DeviceAction.TOGGLE
        1 -> DeviceAction.CYCLES
        2 -> DeviceAction.WORK
        3 -> DeviceAction.BREAK
        else -> null
    }
    Device.POCKET -> when (key) {
        0 -> DeviceAction.TOGGLE
        1 -> DeviceAction.RESET
        DeviceArt.VOLUME -> DeviceAction.VOLUME
        else -> null
    }
}

@Composable
private fun DeviceFace(
    device: Device,
    snap: TimerEngine.Snapshot,
    soundOn: Boolean,
    modifier: Modifier,
    onAction: (DeviceAction) -> Unit,
    onDial: (Int) -> Boolean,
) {
    val context = LocalContext.current
    var pressed by remember { mutableIntStateOf(-1) }
    var wheel by remember { mutableFloatStateOf(0f) }
    val currentSnap by rememberUpdatedState(snap)
    val currentSound by rememberUpdatedState(soundOn)
    val act by rememberUpdatedState(onAction)
    val dial by rememberUpdatedState(onDial)
    // The Pocket's grille and sleeping pet move continuously, so it gets the frame clock. The
    // other two only change when a second ticks over, so four redraws a second is plenty.
    val ticker = rememberFrameTicker(device == Device.POCKET && snap.running)
    val slowTick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(snap.running, device) {
        while (snap.running && device != Device.POCKET) {
            delay(250)
            slowTick.longValue = System.currentTimeMillis()
        }
    }

    Canvas(
        modifier.pointerInput(device) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val u = size.width.toFloat()
                val top = u * Device.PAD_TOP
                val hit = DeviceArt.hit(device, down.position.x / u, (down.position.y - top) / u)
                if (hit == DeviceArt.WHEEL) {
                    down.consume()
                    Feedback.haptic(context, Feedback.Haptic.TICK)
                    var accumulated = 0f
                    val notch = u * 0.035f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.position - change.previousPosition
                        // The Module's wheel turns vertically, the Slab's crown sideways.
                        val move = if (device == Device.SLAB) delta.x else -delta.y
                        accumulated += move
                        wheel += move / (u * 0.19f)
                        while (abs(accumulated) >= notch) {
                            val step = if (accumulated > 0) 1 else -1
                            accumulated -= step * notch
                            if (dial(step)) {
                                Feedback.play(context, Feedback.Sound.DETENT)
                                Feedback.haptic(context, Feedback.Haptic.TICK)
                            } else {
                                // Locked while a session runs: a dull double knock says so.
                                Feedback.haptic(context, Feedback.Haptic.DOUBLE)
                                accumulated = 0f
                                break
                            }
                        }
                        change.consume()
                    }
                } else if (hit >= 0) {
                    down.consume()
                    pressed = hit
                    Feedback.press(context, device.sound, Feedback.Haptic.CLICK)
                    var released: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    val finished = withTimeoutOrNull(550L) {
                        released = waitForUpOrCancellation()
                        true
                    }
                    if (finished == null) {
                        // Held: some keys have a second job (the Slab's Play resets).
                        actionFor(device, hit, long = true)?.let {
                            Feedback.haptic(context, Feedback.Haptic.HEAVY)
                            act(it)
                        }
                        waitForUpOrCancellation()
                    } else if (released != null) {
                        actionFor(device, hit, long = false)?.let { act(it) }
                    }
                    pressed = -1
                    Feedback.play(context, Feedback.Sound.KEY_UP)
                }
            }
        }
    ) {
        ticker.longValue
        slowTick.longValue
        val now = System.currentTimeMillis()
        val s = currentSnap
        val remaining = s.remainingAt(now)
        val state = DeviceState(
            time = formatClock(remaining),
            onBreak = s.phase == TimerEngine.Phase.BREAK,
            running = s.running,
            idle = s.phase == TimerEngine.Phase.IDLE,
            progress = if (s.phase == TimerEngine.Phase.IDLE) 0f else 1f - s.fractionAt(now),
            session = s.sessionNumber,
            goal = s.goal,
            totalLeft = totalLeft(s, remaining),
            wheel = wheel,
            seconds = (now % 600_000L) / 1000f,
            pressed = pressed,
            soundOn = currentSound,
            done = s.setComplete,
        )
        val u = size.width
        drawIntoCanvas { DeviceArt.draw(it.nativeCanvas, device, 0f, u * Device.PAD_TOP, u, state) }
    }
}

/** mm:ss, rounding up so a timer never shows 00:00 while there's still time on it. */
fun formatClock(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}

private fun totalLeft(s: TimerEngine.Snapshot, remaining: Long): String {
    if (s.goal <= 0) return "--:--"
    val total = when (s.phase) {
        TimerEngine.Phase.IDLE -> s.goal * s.focusMs + (s.goal - 1).coerceAtLeast(0) * s.breakMs
        TimerEngine.Phase.FOCUS -> remaining + (s.goal - s.sessionNumber) * (s.breakMs + s.focusMs)
        TimerEngine.Phase.BREAK -> remaining + (s.goal - s.completed) * s.focusMs + (s.goal - s.completed - 1).coerceAtLeast(0) * s.breakMs
    }
    return formatClock(total)
}

@Composable
private fun Settings(snap: TimerEngine.Snapshot, chips: ChipColors, label: Color, light: Boolean, onChange: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetRow("FOCUS", FOCUS_PRESETS, (snap.focusMs / 60_000L).toInt(), chips, label) {
            TimerEngine.setFocusMinutes(context, it); onChange()
        }
        PresetRow("BREAK", BREAK_PRESETS, (snap.breakMs / 60_000L).toInt(), chips, label) {
            TimerEngine.setBreakMinutes(context, it); onChange()
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("SESSIONS", style = labelStyle(label, 10.5.sp, FontWeight.SemiBold), modifier = Modifier.width(76.dp))
            StepKey("−", { TimerEngine.setGoal(context, (snap.goal - 1).coerceAtLeast(0)); onChange() }, size = 34.dp, dark = !light)
            BasicText(
                if (snap.goal == 0) "∞" else snap.goal.toString(),
                style = labelStyle(if (light) Color(0xFF1C1C1C) else Color(0xFFF2F2F2), 15.sp, FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            StepKey("+", { TimerEngine.setGoal(context, (snap.goal + 1).coerceAtMost(12)); onChange() }, size = 34.dp, dark = !light)
            Spacer(Modifier.weight(1f))
            BasicText(
                if (snap.setComplete) "SET COMPLETE" else "${snap.completed} DONE",
                style = labelStyle(label, 10.5.sp, FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun PresetRow(title: String, presets: List<Int>, current: Int, chips: ChipColors, label: Color, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = labelStyle(label, 10.5.sp, FontWeight.SemiBold), modifier = Modifier.width(60.dp))
        Box(Modifier.weight(1f)) {
            ChipRow(
                items = presets.map { it.toString() } + if (current !in presets) listOf("$current") else emptyList(),
                selected = presets.indexOf(current).let { if (it < 0) presets.size else it },
                colors = chips,
                onSelect = { i ->
                    if (i < presets.size) {
                        Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                        onPick(presets[i])
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(0.dp))
}
