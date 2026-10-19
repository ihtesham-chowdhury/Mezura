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
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.TextAction
import java.util.UUID
import rocks.ndot.widgets.instruments.folio.BookStore
import rocks.ndot.widgets.instruments.folio.BookItem
import rocks.ndot.widgets.instruments.folio.BookStatus
import rocks.ndot.widgets.instruments.folio.ReadingSessionLog
import rocks.ndot.widgets.instruments.folio.BookTrackMode
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
    var device by remember {
        mutableStateOf(
            Device.byId(AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_POMODORO_STYLE, Device.MODULE.id))
        )
    }
    var snap by remember { mutableStateOf(TimerEngine.snapshot(context)) }
    var soundOn by remember { mutableStateOf(AppPrefs.sound(context)) }
    val askNotifications = rocks.ndot.widgets.app.LocalAskNotifications.current

    var isCountUp by remember { mutableStateOf(false) }
    var countUpRunning by remember { mutableStateOf(false) }
    var countUpStartMillis by remember { mutableLongStateOf(0L) }
    var countUpAccumulatedMs by remember { mutableLongStateOf(0L) }
    var countUpCurrentElapsedMs by remember { mutableLongStateOf(0L) }

    var showFinishReadingDialog by remember { mutableStateOf(false) }
    var activeReading by remember(snap.running, snap.phase, countUpRunning) {
        mutableStateOf(BookStore.getActiveReadingBook(context))
    }

    fun refresh() {
        snap = TimerEngine.snapshot(context)
        HourglassWidgetProvider.updateAll(context)
        TimerService.syncToState(context)
    }

    LaunchedEffect(countUpRunning) {
        if (countUpRunning) {
            countUpStartMillis = System.currentTimeMillis()
            while (countUpRunning) {
                countUpCurrentElapsedMs = countUpAccumulatedMs + (System.currentTimeMillis() - countUpStartMillis)
                delay(200L)
            }
        }
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
    KeepAwake(if (isCountUp) countUpRunning else snap.running)

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
                if (isCountUp) {
                    countUpRunning = false
                    countUpAccumulatedMs = 0L
                    countUpCurrentElapsedMs = 0L
                } else {
                    TimerEngine.reset(context)
                }
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
            onLongPress = {
                val picked = Device.entries[it]
                device = picked
                AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_POMODORO_STYLE, picked.id, "${picked.label} for Pomodoro")
            },
        )

        // Mode Selector: COUNTDOWN vs COUNT UP / UNLIMITED (♾)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (device.light) Color(0x18000000) else Color(0x24FFFFFF))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(false to "⏱ COUNTDOWN", true to "♾ COUNT UP").forEach { (up, title) ->
                    val isSel = isCountUp == up
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSel) (if (device == Device.MODULE) Color(0xFFEE8A2A) else Color(0xFFE0322A)) else Color.Transparent)
                            .clickable {
                                if (isCountUp != up) {
                                    isCountUp = up
                                    Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = title,
                            style = TextStyle(
                                color = if (isSel) Color.White else (if (device.light) Color(0x99000000) else Color(0x99FFFFFF)),
                                fontSize = 11.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }

        activeReading?.let { (book, startPage) ->
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (device.light) Color(0x18000000) else Color(0x24FFFFFF))
                    .border(1.dp, if (device.light) Color(0x28000000) else Color(0x35FFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                    val startStr = if (isPct) "$startPage%" else "P. $startPage"
                    BasicText("📖 ", style = TextStyle(fontSize = 11.sp))
                    BasicText(
                        "${book.title.uppercase()} · FROM $startStr",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (device.light) Color(0xFF111111) else Color(0xFFF0F0F0),
                            fontSize = 10.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                rocks.ndot.widgets.kit.TextAction(
                    text = if (isCountUp && countUpRunning) "STOP & LOG" else "LOG PAGES",
                    color = if (device == Device.MODULE) Color(0xFFEE8A2A) else Color(0xFFE0322A),
                    onClick = {
                        if (isCountUp && countUpRunning) {
                            countUpAccumulatedMs += System.currentTimeMillis() - countUpStartMillis
                            countUpCurrentElapsedMs = countUpAccumulatedMs
                            countUpRunning = false
                        }
                        showFinishReadingDialog = true
                    }
                )
            }
        }

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
                    isCountUp = isCountUp,
                    countUpElapsedMs = countUpCurrentElapsedMs,
                    countUpRunning = countUpRunning,
                    onAction = { action ->
                        if (isCountUp) {
                            when (action) {
                                DeviceAction.TOGGLE -> {
                                    if (!countUpRunning) {
                                        countUpStartMillis = System.currentTimeMillis()
                                        countUpRunning = true
                                        Feedback.press(context, device.sound, Feedback.Haptic.CLICK)
                                    } else {
                                        countUpAccumulatedMs += System.currentTimeMillis() - countUpStartMillis
                                        countUpCurrentElapsedMs = countUpAccumulatedMs
                                        countUpRunning = false
                                        Feedback.press(context, device.sound, Feedback.Haptic.CLICK)
                                        if (activeReading != null) {
                                            showFinishReadingDialog = true
                                        }
                                    }
                                }
                                DeviceAction.RESET -> {
                                    countUpRunning = false
                                    countUpAccumulatedMs = 0L
                                    countUpCurrentElapsedMs = 0L
                                    Feedback.haptic(context, Feedback.Haptic.HEAVY)
                                }
                                DeviceAction.SKIP -> {
                                    if (activeReading != null) {
                                        if (countUpRunning) {
                                            countUpAccumulatedMs += System.currentTimeMillis() - countUpStartMillis
                                            countUpCurrentElapsedMs = countUpAccumulatedMs
                                            countUpRunning = false
                                        }
                                        showFinishReadingDialog = true
                                    } else if (countUpCurrentElapsedMs >= 30_000L) {
                                        val durSec = countUpCurrentElapsedMs / 1000L
                                        val now = System.currentTimeMillis()
                                        rocks.ndot.widgets.instruments.trace.TraceLedger.recordEvent(
                                            context,
                                            rocks.ndot.widgets.instruments.trace.TraceEvent(
                                                activityType = rocks.ndot.widgets.instruments.trace.ActivityType.FOCUS,
                                                sourceInstrument = "pomodoro",
                                                title = "Count-Up Focus",
                                                startedAtMillis = now - (durSec * 1000L),
                                                endedAtMillis = now,
                                                durationSeconds = durSec,
                                                outcome = rocks.ndot.widgets.instruments.trace.SessionOutcome.COMPLETED
                                            )
                                        )
                                        Toast.makeText(context, "Recorded ${durSec / 60}m focus to Trace", Toast.LENGTH_SHORT).show()
                                        countUpRunning = false
                                        countUpAccumulatedMs = 0L
                                        countUpCurrentElapsedMs = 0L
                                    }
                                }
                                DeviceAction.VOLUME -> {
                                    soundOn = !soundOn
                                    AppPrefs.setSound(context, soundOn)
                                }
                                else -> Unit
                            }
                        } else {
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
                        }
                    },
                    onDial = { steps ->
                        if (!isCountUp) {
                            if (snap.phase == TimerEngine.Phase.IDLE) {
                                TimerEngine.setFocusMinutes(context, TimerEngine.focusMinutes(context) + steps)
                                refresh()
                                true
                            } else false
                        } else false
                    },
                )
            }
        }

        Settings(
            snap = snap,
            chips = chips,
            label = labelColor,
            light = device.light,
            isCountUp = isCountUp,
            onToggleCountUp = { isCountUp = it },
            onChange = { refresh() }
        )
        NavSpacer()
    }

    if (showFinishReadingDialog) {
        val reading = activeReading
        if (reading != null) {
            val (book, startPage) = reading
            FinishReadingDialog(
                book = book,
                startPage = startPage,
                onDismiss = { showFinishReadingDialog = false },
                onSave = { endPage ->
                    val now = System.currentTimeMillis()
                    val durationSeconds = if (isCountUp) {
                        (countUpCurrentElapsedMs / 1000L).coerceAtLeast(1L)
                    } else {
                        val startTime = BookStore.getActiveReadingStartTime(context)
                        ((now - startTime) / 1000L).coerceAtLeast(60L)
                    }
                    val durationMinutes = (durationSeconds / 60L).toInt().coerceAtLeast(1)
                    val pagesRead = (endPage - startPage).coerceAtLeast(0)
                    val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                    val session = ReadingSessionLog(
                        id = UUID.randomUUID().toString(),
                        bookId = book.id,
                        timestampMillis = if (isCountUp) (now - durationSeconds * 1000L) else BookStore.getActiveReadingStartTime(context),
                        startPage = startPage,
                        endPage = endPage,
                        pagesRead = pagesRead,
                        durationMinutes = durationMinutes,
                        durationSeconds = durationSeconds,
                        manuallyLogged = false
                    )
                    BookStore.addSession(context, session)
                    val newStatus = if (endPage >= book.totalPages && book.totalPages > 0) BookStatus.COMPLETED else BookStatus.READING
                    BookStore.upsertBook(context, book.copy(currentPage = endPage, status = newStatus))
                    BookStore.clearActiveReadingBook(context)
                    activeReading = null
                    showFinishReadingDialog = false
                    countUpRunning = false
                    countUpAccumulatedMs = 0L
                    countUpCurrentElapsedMs = 0L
                    Feedback.success(context)
                    val unitStr = if (isPct) "$pagesRead%" else "$pagesRead pages"
                    Toast.makeText(context, "Logged $unitStr read in ${durationMinutes}m", Toast.LENGTH_SHORT).show()
                },
                onDiscard = {
                    BookStore.clearActiveReadingBook(context)
                    activeReading = null
                    showFinishReadingDialog = false
                    countUpRunning = false
                    countUpAccumulatedMs = 0L
                    countUpCurrentElapsedMs = 0L
                    Toast.makeText(context, "Reading session discarded", Toast.LENGTH_SHORT).show()
                }
            )
        }
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
    isCountUp: Boolean = false,
    countUpElapsedMs: Long = 0L,
    countUpRunning: Boolean = false,
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
    val isRunningFace = if (isCountUp) countUpRunning else snap.running
    val ticker = rememberFrameTicker(device == Device.POCKET && isRunningFace)
    val slowTick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRunningFace, device) {
        while (isRunningFace && device != Device.POCKET) {
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
        val state = if (isCountUp) {
            val totalSec = countUpElapsedMs / 1000L
            val mm = totalSec / 60L
            val ss = totalSec % 60L
            val timeText = if (mm >= 60L) {
                val hh = mm / 60L
                val remM = mm % 60L
                String.format(java.util.Locale.US, "%02d:%02d:%02d", hh, remM, ss)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", mm, ss)
            }
            DeviceState(
                time = timeText,
                onBreak = false,
                running = countUpRunning,
                idle = !countUpRunning && countUpElapsedMs == 0L,
                progress = if (countUpRunning) ((countUpElapsedMs % 60_000L) / 60_000f) else 0f,
                session = 1,
                goal = 0,
                totalLeft = "COUNT UP",
                wheel = wheel,
                seconds = (now % 600_000L) / 1000f,
                pressed = pressed,
                soundOn = currentSound,
                done = false,
            )
        } else {
            val s = currentSnap
            val remaining = s.remainingAt(now)
            DeviceState(
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
        }
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
private fun Settings(
    snap: TimerEngine.Snapshot,
    chips: ChipColors,
    label: Color,
    light: Boolean,
    isCountUp: Boolean,
    onToggleCountUp: (Boolean) -> Unit,
    onChange: () -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetRow("FOCUS", FOCUS_PRESETS, if (isCountUp) -1 else (snap.focusMs / 60_000L).toInt(), chips, label, extraItem = "♾ UP", extraSelected = isCountUp, onPickExtra = {
            onToggleCountUp(true)
        }) {
            onToggleCountUp(false)
            TimerEngine.setFocusMinutes(context, it); onChange()
        }
        if (!isCountUp) {
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
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("MODE", style = labelStyle(label, 10.5.sp, FontWeight.SemiBold), modifier = Modifier.width(76.dp))
                BasicText("UNLIMITED COUNT-UP · TAP TOGGLE TO RUN / PAUSE", style = labelStyle(label, 10.5.sp, FontWeight.Normal))
            }
        }
    }
}

@Composable
private fun PresetRow(
    title: String,
    presets: List<Int>,
    current: Int,
    chips: ChipColors,
    label: Color,
    extraItem: String? = null,
    extraSelected: Boolean = false,
    onPickExtra: (() -> Unit)? = null,
    onPick: (Int) -> Unit
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = labelStyle(label, 10.5.sp, FontWeight.SemiBold), modifier = Modifier.width(60.dp))
        Box(Modifier.weight(1f)) {
            val baseItems = presets.map { it.toString() } + if (current !in presets && current > 0) listOf("$current") else emptyList()
            val allItems = if (extraItem != null) baseItems + listOf(extraItem) else baseItems
            val selectedIdx = if (extraSelected && extraItem != null) {
                allItems.lastIndex
            } else {
                presets.indexOf(current).let { if (it < 0) -1 else it }
            }
            ChipRow(
                items = allItems,
                selected = selectedIdx,
                colors = chips,
                onSelect = { i ->
                    Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                    if (extraItem != null && i == allItems.lastIndex) {
                        onPickExtra?.invoke()
                    } else if (i < presets.size) {
                        onPick(presets[i])
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun FinishReadingDialog(
    book: BookItem,
    startPage: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onDiscard: () -> Unit
) {
    var endPageText by remember { mutableStateOf(book.currentPage.coerceAtLeast(startPage).toString()) }
    val endPage = endPageText.toIntOrNull() ?: startPage
    val pagesRead = (endPage - startPage).coerceAtLeast(0)

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "LOG READING SESSION",
                    style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_3)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("×", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp))
                }
            }

            Spacer(Modifier.height(12.dp))

            BasicText(
                text = book.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            BasicText(
                text = "By ${book.author}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp)
            )

            Spacer(Modifier.height(16.dp))

            // Start & End inputs
            val isPct = book.trackMode == BookTrackMode.PERCENTAGE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(if (isPct) "STARTED AT (%)" else "STARTED AT", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .padding(12.dp)
                    ) {
                        BasicText(if (isPct) "$startPage%" else "P. $startPage", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    BasicText(if (isPct) "CURRENT / END %" else "CURRENT / END PAGE", style = TextStyle(color = Ink.BRASS, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.BRASS, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = endPageText,
                            onValueChange = {
                                val clean = it.filter { ch -> ch.isDigit() }
                                endPageText = if (isPct) (clean.toIntOrNull()?.coerceIn(0, 100)?.toString() ?: clean) else clean
                            },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(Ink.BRASS),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Calculated progress badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.SURFACE_2)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(if (isPct) "PROGRESS THIS SESSION:" else "PAGES READ THIS SESSION:", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono))
                val progBadge = if (isPct) "+$pagesRead%" else "+$pagesRead PAGES"
                BasicText(progBadge, style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_3)
                        .clickable { onDiscard() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("DISCARD", style = TextStyle(color = Ink.VERMILION, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }

                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.BRASS)
                        .clickable { onSave(endPage) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("SAVE & FINISH", style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

