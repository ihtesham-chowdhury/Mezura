package rocks.ndot.widgets.app

import android.graphics.Paint
import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rocks.ndot.widgets.instruments.habits.HabitStore
import rocks.ndot.widgets.instruments.hourglass.presetLabel
import rocks.ndot.widgets.instruments.pomodoro.formatClock
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.PixelFont
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle
import rocks.ndot.widgets.widgets.HourglassEngine
import rocks.ndot.widgets.widgets.StopwatchEngine
import rocks.ndot.widgets.widgets.TimerEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The home screen is deliberately quiet: the time in dots, the date, how far through the year
 * you are, and - only if something is running - what it is. No grid of cards. Everything else
 * is one tap away in the drawer.
 */
@Composable
fun HomeScreen(onOpen: (Tool) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    LightChrome(false)

    val dateTime = LocalDateTime.now()
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(context)
    val clock = if (is24) "%02d:%02d".format(dateTime.hour, dateTime.minute)
    else "%d:%02d".format(((dateTime.hour + 11) % 12) + 1, dateTime.minute)

    val timer = TimerEngine.snapshot(context)
    val glass = HourglassEngine.snapshot(context)
    val watch = StopwatchEngine.snapshot(context)
    val habits = HabitStore.load(context)
    val today = HabitStore.today()

    Column(Modifier.fillMaxSize().background(Ink.Black)) {
        ToolHeader("NDOT · INSTRUMENTS", Ink.Muted) { TextAction("SETTINGS", Ink.Muted, onSettings) }
        Spacer(Modifier.weight(0.7f))

        DotClock(clock, blink = (now / 1000) % 2 == 0L, modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp))
        Spacer(Modifier.height(18.dp))
        BasicText(
            dateTime.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", locale)).uppercase(locale),
            style = labelStyle(Ink.Text, 12.sp, FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 38.dp),
        )
        Spacer(Modifier.height(22.dp))
        YearBar(LocalDate.now(), locale, Modifier.padding(horizontal = 38.dp))

        Spacer(Modifier.weight(0.5f))

        Column(Modifier.padding(horizontal = 26.dp)) {
            val lines = ArrayList<Triple<Tool, String, String>>()
            if (timer.running || (timer.phase != TimerEngine.Phase.IDLE)) {
                val phase = if (timer.phase == TimerEngine.Phase.BREAK) "BREAK" else "FOCUS"
                lines += Triple(Tool.POMODORO, "POMODORO", "$phase · ${formatClock(timer.remainingAt(now))}" + if (!timer.running) " · PAUSED" else "")
            }
            if (glass.state == HourglassEngine.State.RUNNING || glass.state == HourglassEngine.State.PAUSED) {
                lines += Triple(Tool.HOURGLASS, "HOURGLASS", formatClock(glass.remainingAt(now)) + if (glass.state == HourglassEngine.State.PAUSED) " · PAUSED" else "")
            }
            if (watch.running || watch.accumulated > 0) {
                lines += Triple(Tool.STOPWATCH, "STOPWATCH", StopwatchEngine.format(watch.elapsedAt(now)).substringBeforeLast('.') + if (!watch.running) " · STOPPED" else "")
            }
            if (lines.isNotEmpty()) {
                BasicText("// NOW", style = labelStyle(Ink.Faint, 10.sp), modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
                lines.forEach { (tool, name, value) -> LiveRow(name, value, live = true) { onOpen(tool) } }
            } else {
                val last = Tool.byId(AppPrefs.lastTool(context))
                BasicText("// NOTHING RUNNING", style = labelStyle(Ink.Faint, 10.sp), modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
                if (last != null) LiveRow("CONTINUE", last.label, live = false) { onOpen(last) }
                else LiveRow("TRY", "HOURGLASS · ${presetLabel(300)}", live = false) { onOpen(Tool.HOURGLASS) }
            }
            if (habits.isNotEmpty()) {
                val done = habits.count { it.isDone(today) }
                LiveRow("HABITS", "$done/${habits.size} TODAY", live = false) { onOpen(Tool.HABITS) }
            }
        }

        Spacer(Modifier.weight(0.35f))
        BasicText(
            "THE DRAWER IS BELOW",
            style = labelStyle(Color(0xFF333333), 10.sp).copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        NavSpacer()
    }
}

/** The time in large dot-matrix figures, the colon blinking red with the seconds. */
@Composable
private fun DotClock(text: String, blink: Boolean, modifier: Modifier) {
    val cells = PixelFont.width(text)
    Canvas(modifier.aspectRatio(cells / 7f)) {
        val cell = size.width / cells
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F2F2.toInt() }
        val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F0F0F.toInt() }
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (blink) 0xFFD71921.toInt() else 0xFF3A0A0C.toInt() }
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            var x = 0f
            for (ch in text) {
                if (ch == ':') {
                    PixelFont.draw(c, ":", x, 0f, cell, red, null, round = true)
                } else {
                    PixelFont.draw(c, ch.toString(), x, 0f, cell, on, dim, round = true)
                }
                x += PixelFont.advance(ch) * cell
            }
        }
    }
}

/** The year as a row of weeks, filled up to this one. */
@Composable
private fun YearBar(today: LocalDate, locale: Locale, modifier: Modifier) {
    val week = today.get(WeekFields.of(locale).weekOfWeekBasedYear())
    val percent = today.dayOfYear * 100 / today.lengthOfYear()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val weeks = 52
            val step = size.width / weeks
            for (i in 0 until weeks) {
                val color = when {
                    i + 1 < week -> Color(0xFFF2F2F2)
                    i + 1 == week -> Ink.Red
                    else -> Color(0xFF262626)
                }
                val h = if ((i + 1) % 13 == 0) size.height else size.height * 0.62f
                drawLine(color, Offset(i * step + step / 2, size.height - h), Offset(i * step + step / 2, size.height), strokeWidth = step * 0.34f)
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText("WEEK $week · $percent% OF ${today.year}", style = labelStyle(Ink.Muted, 10.5.sp))
    }
}

@Composable
private fun LiveRow(name: String, value: String, live: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveDot")
    Row(
        Modifier.fillMaxWidth().clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).alpha(if (live) alpha else 1f).clip(CircleShape).background(if (live) Ink.Red else Ink.Faint))
        Spacer(Modifier.width(12.dp))
        BasicText(name, style = labelStyle(Ink.Muted, 11.sp, FontWeight.SemiBold), modifier = Modifier.width(104.dp))
        BasicText(value, style = labelStyle(Ink.Text, 12.sp, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f))
        BasicText("›", style = labelStyle(Ink.Faint, 16.sp))
    }
}
