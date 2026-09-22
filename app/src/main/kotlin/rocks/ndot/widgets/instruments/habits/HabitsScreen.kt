package rocks.ndot.widgets.instruments.habits

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.app.LightChrome
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.SheetDialog
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/**
 * A ledger of small daily things. One tap marks today; a habit's page shows its streak large
 * and lets you fill in days you forgot. The board up top is the last few months at a glance,
 * each dot as bright as the share of habits done that day.
 */
@Composable
fun HabitsScreen() {
    val context = LocalContext.current
    var habits by remember { mutableStateOf(HabitStore.load(context)) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }
    val today = remember { HabitStore.today() }

    LightChrome(false)

    fun toggle(habit: Habit, day: Long) {
        val wasDone = habit.isDone(day)
        habits = HabitStore.toggle(context, habit.id, day)
        Feedback.play(context, Feedback.Sound.KEY_UP)
        Feedback.haptic(context, if (wasDone) Feedback.Haptic.TICK else Feedback.Haptic.CLICK)
    }

    Column(Modifier.fillMaxSize().background(Ink.Black)) {
        AnimatedContent(
            targetState = openId,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (targetState != null) it / 6 else -it / 6 })
                    .togetherWith(fadeOut(tween(120)))
            },
            modifier = Modifier.weight(1f),
            label = "habits",
        ) { id ->
            val open = habits.firstOrNull { it.id == id }
            if (open == null) {
                Ledger(
                    habits = habits,
                    today = today,
                    onToggle = { toggle(it, today) },
                    onOpen = { openId = it.id },
                    onNew = { creating = true },
                )
            } else {
                Detail(
                    habit = open,
                    today = today,
                    onBack = { openId = null },
                    onToggle = { day -> toggle(open, day) },
                    onEdit = { editing = open },
                )
            }
        }
        NavSpacer()
    }

    if (creating) {
        HabitDialog(
            title = "NEW HABIT",
            initialName = "",
            initialColor = 0,
            onDismiss = { creating = false },
            onSave = { name, color ->
                habits = HabitStore.add(context, name, color)
                creating = false
            },
        )
    }
    editing?.let { habit ->
        HabitDialog(
            title = "EDIT HABIT",
            initialName = habit.name,
            initialColor = habit.color,
            onDismiss = { editing = null },
            onSave = { name, color ->
                habits = HabitStore.update(context, habit.id, name, color)
                editing = null
            },
            onDelete = {
                habits = HabitStore.delete(context, habit.id)
                editing = null
                openId = null
            },
        )
    }
}

@Composable
private fun Ledger(habits: List<Habit>, today: Long, onToggle: (Habit) -> Unit, onOpen: (Habit) -> Unit, onNew: () -> Unit) {
    val locale = Locale.getDefault()
    val date = LocalDate.ofEpochDay(today)
    Column(Modifier.fillMaxSize()) {
        ToolHeader("HABITS", Ink.Muted) { TextAction("+ NEW", Ink.Text, onNew) }
        if (habits.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("0", style = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.Bold, fontSize = 120.sp, color = Ink.Faint))
                BasicText("NO HABITS YET", style = labelStyle(Ink.Muted, 12.sp, FontWeight.SemiBold))
                Spacer(Modifier.height(8.dp))
                BasicText(
                    "Add one small thing you want to do every day. One tap a day marks it done.",
                    style = TextStyle(fontFamily = Fonts.sans, fontSize = 14.sp, color = Ink.Muted, textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(20.dp))
                TextAction("+ NEW HABIT", Ink.Text, onNew)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                BasicText(
                    date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).uppercase(locale),
                    style = labelStyle(Ink.Muted, 11.sp),
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
            item {
                val doneToday = habits.count { it.isDone(today) }
                val best = habits.maxOf { it.currentStreak(today) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("STREAK", "$best", if (best == 1) "DAY" else "DAYS", highlight = best > 0, modifier = Modifier.weight(1f))
                    Tile("TODAY", "$doneToday/${habits.size}", if (doneToday == habits.size) "ALL DONE" else "DONE", highlight = false, modifier = Modifier.weight(1f))
                }
            }
            item { Board(habits, today) }
            item { BasicText("// HABITS", style = labelStyle(Ink.Faint, 10.sp), modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
            items(habits, key = { it.id }) { habit -> HabitRow(habit, today, onToggle, onOpen) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun Tile(title: String, value: String, unit: String, highlight: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Ink.Surface)
            .border(1.dp, if (highlight) Color(0x66D71921) else Ink.Hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicText(title, style = labelStyle(Ink.Muted, 10.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(value, style = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = if (highlight) Ink.Red else Ink.Text))
            Spacer(Modifier.width(6.dp))
            BasicText(unit, style = labelStyle(Ink.Muted, 10.sp), modifier = Modifier.padding(bottom = 7.dp))
        }
    }
}

/** Twenty weeks as a dot matrix: each dot lit by the share of habits done that day. */
@Composable
private fun Board(habits: List<Habit>, today: Long) {
    val weeks = 20
    val todayDate = LocalDate.ofEpochDay(today)
    // Columns are weeks ending with the current one; rows run Monday to Sunday.
    val lastMonday = today - (todayDate.dayOfWeek.value - 1)
    val firstDay = lastMonday - (weeks - 1) * 7
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.Surface)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(18.dp)).padding(14.dp),
    ) {
        Row {
            BasicText("// BOARD", style = labelStyle(Ink.Faint, 10.sp), modifier = Modifier.weight(1f))
            BasicText("$weeks WEEKS", style = labelStyle(Ink.Faint, 10.sp))
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().aspectRatio(weeks / 7f)) {
            val step = size.width / weeks
            val r = step * 0.32f
            for (w in 0 until weeks) for (d in 0 until 7) {
                val day = firstDay + w * 7 + d
                val center = Offset(step * (w + 0.5f), step * (d + 0.5f))
                if (day > today) {
                    drawCircle(Color(0xFF111111), radius = r * 0.6f, center = center)
                    continue
                }
                val share = habits.count { it.isDone(day) }.toFloat() / habits.size
                val color = if (share <= 0f) Color(0xFF222222) else Color.White.copy(alpha = 0.25f + 0.75f * share)
                drawCircle(color, radius = r, center = center)
                if (day == today) drawCircle(Ink.Red, radius = r + step * 0.1f, center = center, style = Stroke(step * 0.08f))
            }
        }
    }
}

@Composable
private fun HabitRow(habit: Habit, today: Long, onToggle: (Habit) -> Unit, onOpen: (Habit) -> Unit) {
    val color = Color(HabitStore.COLORS[habit.color])
    val streak = habit.currentStreak(today)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (habit.isDone(today)) color.copy(alpha = 0.08f) else Ink.Surface)
            .border(1.dp, if (habit.isDone(today)) color.copy(alpha = 0.45f) else Ink.Hairline, RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(habit) }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(habit.isDone(today), color) { onToggle(habit) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(habit.name.uppercase(), style = labelStyle(Ink.Text, 13.sp, FontWeight.SemiBold).copy(letterSpacing = 0.06.sp))
            Spacer(Modifier.height(3.dp))
            BasicText(
                if (streak > 0) "$streak DAY STREAK" else "NO STREAK YET",
                style = labelStyle(if (streak > 0) color.copy(alpha = 0.85f) else Ink.Faint, 10.sp),
            )
        }
        // The last seven days, oldest first.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 6 downTo 0) {
                val done = habit.isDone(today - i)
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (done) color else Color(0xFF262626)))
            }
        }
    }
}

@Composable
private fun CheckSquare(checked: Boolean, color: Color, onClick: () -> Unit) {
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "check")
    val tick by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tick")
    val interaction = remember { MutableInteractionSource() }
    val ink = if (color.red + color.green + color.blue > 2.2f) Color(0xFF0B0B0B) else Color.White
    Box(
        Modifier.size(30.dp).scale(0.9f + 0.1f * tick).clip(RoundedCornerShape(8.dp)).background(fill)
            .border(1.5.dp, if (checked) color else Color(0xFF4A4A4A), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            if (tick > 0.01f) {
                val a = Offset(size.width * 0.05f, size.height * 0.55f)
                val b = Offset(size.width * 0.40f, size.height * 0.88f)
                val c = Offset(size.width * 0.98f, size.height * 0.12f)
                val first = (tick * 2f).coerceAtMost(1f)
                val second = ((tick - 0.5f) * 2f).coerceIn(0f, 1f)
                drawLine(ink, a, a + (b - a) * first, strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                if (second > 0f) drawLine(ink, b, b + (c - b) * second, strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun Detail(habit: Habit, today: Long, onBack: () -> Unit, onToggle: (Long) -> Unit, onEdit: () -> Unit) {
    val color = Color(HabitStore.COLORS[habit.color])
    val locale = Locale.getDefault()
    val streak = habit.currentStreak(today)
    val thisMonth = YearMonth.from(LocalDate.ofEpochDay(today))
    Column(Modifier.fillMaxSize()) {
        ToolHeader("HABITS", Ink.Muted) {
            TextAction("‹ BACK", Ink.Muted, onBack)
            Spacer(Modifier.width(6.dp))
            TextAction("EDIT", Ink.Text, onEdit)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.Surface)
                    .border(1.dp, Ink.Hairline, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText("YOUR HABIT", style = labelStyle(Ink.Faint, 10.sp))
                    BasicText(habit.name.uppercase(), style = labelStyle(Ink.Text, 14.sp, FontWeight.SemiBold))
                    Spacer(Modifier.height(18.dp))
                    BasicText("LONGEST STREAK", style = labelStyle(Ink.Faint, 10.sp))
                    BasicText("${habit.longestStreak()}", style = labelStyle(Ink.Text, 14.sp, FontWeight.SemiBold))
                    Spacer(Modifier.height(8.dp))
                    BasicText("DAYS DONE", style = labelStyle(Ink.Faint, 10.sp))
                    BasicText("${habit.done.size}", style = labelStyle(Ink.Text, 14.sp, FontWeight.SemiBold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(
                        "$streak",
                        style = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.Bold, fontSize = 112.sp, lineHeight = 112.sp, color = if (streak > 0) color else Ink.Faint),
                    )
                    BasicText("CURRENT STREAK", style = labelStyle(Ink.Muted, 10.sp))
                }
            }
            Spacer(Modifier.height(18.dp))
            // This month and the two before it; tap any day up to today to mark or unmark it.
            for (back in 0..2) {
                val month = thisMonth.minusMonths(back.toLong())
                val days = (1..month.lengthOfMonth()).map { month.atDay(it).toEpochDay() }
                val counted = days.filter { it <= today && it >= habit.createdDay.coerceAtMost(today) }
                val share = if (counted.isEmpty()) 0 else counted.count { habit.isDone(it) } * 100 / counted.size
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                    BasicText(month.month.getDisplayName(JTextStyle.FULL_STANDALONE, locale).uppercase(locale), style = labelStyle(Ink.Muted, 10.5.sp), modifier = Modifier.weight(1f))
                    BasicText("$share%", style = labelStyle(Ink.Muted, 10.5.sp))
                }
                days.chunked(10).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { day ->
                            val done = habit.isDone(day)
                            val future = day > today
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                    .background(if (done) color else Color.Transparent)
                                    .border(1.dp, if (done) color else if (future) Color(0xFF1A1A1A) else Color(0xFF3A3A3A), CircleShape)
                                    .clickable(enabled = !future, interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                val ink = if (done) { if (color.red + color.green + color.blue > 2.2f) Color(0xFF0B0B0B) else Color.White } else if (future) Color(0xFF333333) else Color(0xFF8A8A8A)
                                BasicText(
                                    LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                                    style = TextStyle(fontFamily = Fonts.mono, fontSize = 11.sp, color = ink, textAlign = TextAlign.Center,
                                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal),
                                )
                            }
                        }
                        repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HabitDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var confirmDelete by remember { mutableStateOf(false) }
    SheetDialog(onDismiss) {
        BasicText(title, style = labelStyle(Ink.Muted, 11.sp, FontWeight.SemiBold))
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.Raised)
                .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            if (name.isEmpty()) BasicText("e.g. Read 20 minutes", style = TextStyle(fontFamily = Fonts.sans, fontSize = 16.sp, color = Ink.Faint))
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Fonts.sans, fontSize = 16.sp, color = Ink.Text),
                cursorBrush = SolidColor(Ink.Red),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitStore.COLORS.forEachIndexed { i, c ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                        .border(2.dp, if (i == color) Ink.Text else Color.Transparent, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { color = i },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) {
                TextAction(if (confirmDelete) "SURE? DELETE" else "DELETE", Ink.Red, {
                    if (confirmDelete) onDelete() else confirmDelete = true
                })
            }
            Spacer(Modifier.weight(1f))
            TextAction("CANCEL", Ink.Muted, onDismiss)
            TextAction("SAVE", Ink.Text, { if (name.isNotBlank()) onSave(name, color) })
        }
    }
}

