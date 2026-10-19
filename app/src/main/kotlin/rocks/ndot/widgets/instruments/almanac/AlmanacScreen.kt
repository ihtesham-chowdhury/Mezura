package rocks.ndot.widgets.instruments.almanac

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.instruments.habits.Achievement
import rocks.ndot.widgets.instruments.habits.DayAggregate
import rocks.ndot.widgets.instruments.habits.Dream
import rocks.ndot.widgets.instruments.habits.Entry
import rocks.ndot.widgets.instruments.habits.EntryKind
import rocks.ndot.widgets.instruments.habits.Goal
import rocks.ndot.widgets.instruments.habits.GoalPaceStatus
import rocks.ndot.widgets.instruments.habits.HabitItem
import rocks.ndot.widgets.instruments.habits.HabitMasterStore
import rocks.ndot.widgets.instruments.habits.Milestone
import rocks.ndot.widgets.instruments.habits.Period
import rocks.ndot.widgets.instruments.habits.ScoreEngine
import rocks.ndot.widgets.kit.DotMatrixText
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.NavSpacer
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Lens definition
sealed class AlmanacLens {
    object All : AlmanacLens()
    data class HabitLens(val habitId: String, val name: String) : AlmanacLens()
    data class DreamLens(val dreamId: String, val name: String) : AlmanacLens()

    fun id(): String = when (this) {
        is All -> "all"
        is HabitLens -> "habit_$habitId"
        is DreamLens -> "dream_$dreamId"
    }

    fun label(): String = when (this) {
        is All -> "ALL"
        is HabitLens -> name
        is DreamLens -> name
    }
}

@Composable
fun AlmanacScreen(
    onNavigateToGoal: ((String) -> Unit)? = null,
    onNavigateToDream: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentYear = remember { LocalDate.now().year }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var selectedLens by remember { mutableStateOf<AlmanacLens>(AlmanacLens.All) }

    val habits = remember { HabitMasterStore.getHabits(context) }
    val allHabits = remember { HabitMasterStore.getAllHabits(context) }
    val entries = remember { HabitMasterStore.getEntries(context) }
    val goals = remember { HabitMasterStore.getGoals(context) }
    val dreams = remember { HabitMasterStore.getDreams(context) }
    val achievements = remember { HabitMasterStore.getAchievements(context) }

    val todayEpoch = remember { HabitMasterStore.today() }
    val showUpStreak = remember {
        ScoreEngine.calculateShowUpStreak(habits, entries, todayEpoch) { hId, d ->
            val h = habits.firstOrNull { it.id == hId }
            if (h != null) HabitMasterStore.getAutoSourceQuantity(context, h.autoSource, d) else 0.0
        }
    }

    // Day Sheet state
    var inspectingDay by remember { mutableStateOf<Long?>(null) }

    // 900ms Entrance sweep animation from Jan 1 to today
    val sweepAnim = remember { Animatable(0f) }
    LaunchedEffect(selectedYear) {
        sweepAnim.snapTo(0f)
        sweepAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900)
        )
    }

    val dayOfYear = LocalDate.now().dayOfYear
    val isLeapYear = Year.isLeap(selectedYear.toLong())
    val totalDaysInYear = if (isLeapYear) 366 else 365
    val daysLeftInYear = (totalDaysInYear - dayOfYear).coerceAtLeast(0)

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(14.dp))

        // 1. Header: YEARLINE, show-up streak flame, year in dot matrix, days left
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                BasicText(
                    text = "YEARLINE",
                    style = TextStyle(
                        color = Ink.TEXT_WARM,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = if (selectedYear == currentYear) "DAY $dayOfYear OF $totalDaysInYear · $daysLeftInYear LEFT" else "$totalDaysInYear DAYS",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                )
            }

            // Streak & Year Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Flame Streak Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.SURFACE_2)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("▲", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp))
                        Spacer(Modifier.width(4.dp))
                        BasicText(
                            text = "$showUpStreak",
                            style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Year selector with arrows
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_2)
                            .clickable {
                                selectedYear--
                                Feedback.tick(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("‹", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontFamily = Fonts.mono))
                    }

                    DotMatrixText(
                        text = "$selectedYear",
                        dotPitch = 1.8.dp,
                        litColor = Ink.TEXT_WARM,
                        glow = false
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_2)
                            .clickable {
                                selectedYear++
                                Feedback.tick(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("›", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontFamily = Fonts.mono))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2. Lens Chips (Horizontal scrolling)
        val lensChips = remember(habits, dreams) {
            val list = mutableListOf<AlmanacLens>(AlmanacLens.All)
            dreams.forEach { list.add(AlmanacLens.DreamLens(it.id, it.name)) }
            habits.forEach { list.add(AlmanacLens.HabitLens(it.id, it.name)) }
            list
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lensChips.forEach { lens ->
                val isSelected = selectedLens.id() == lens.id()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) Ink.AMBER else Ink.SURFACE_2)
                        .border(1.dp, if (isSelected) Ink.AMBER else Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                        .clickable {
                            selectedLens = lens
                            Feedback.tick(context)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    BasicText(
                        text = lens.label(),
                        maxLines = 1,
                        style = TextStyle(
                            color = if (isSelected) Ink.GROUND else Ink.TEXT_WARM,
                            fontSize = 11.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 3. Dot Grid (12 Rows x 31 Columns)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Ink.SURFACE_1)
                .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            AlmanacDotGrid(
                year = selectedYear,
                lens = selectedLens,
                habits = habits,
                allHabits = allHabits,
                entries = entries,
                goals = goals,
                dreams = dreams,
                achievements = achievements,
                todayEpoch = todayEpoch,
                sweepFraction = sweepAnim.value,
                onDayTapped = { epochDay ->
                    inspectingDay = epochDay
                    Feedback.tick(context)
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        // 4. Stats Trio: ACTIVE DAYS · GOLD DAYS · ACHIEVED
        val yearStartEpoch = remember(selectedYear) { LocalDate.of(selectedYear, 1, 1).toEpochDay() }
        val yearEndEpoch = remember(selectedYear) { LocalDate.of(selectedYear, 12, 31).toEpochDay() }

        val yearEntries = remember(entries, selectedYear) {
            entries.filter { it.epochDay in yearStartEpoch..yearEndEpoch }
        }
        val yearAchievements = remember(achievements, selectedYear) {
            achievements.filter { it.epochDay in yearStartEpoch..yearEndEpoch }
        }

        val activeDaysCount = remember(yearEntries) {
            yearEntries.filter { it.kind == EntryKind.LOG && it.quantity > 0.0 }.map { it.epochDay }.distinct().size
        }
        val goldDaysCount = remember(yearEntries, habits) {
            // Days where overall >= 1.25
            val days = yearEntries.map { it.epochDay }.distinct()
            days.count { d ->
                val dow = LocalDate.ofEpochDay(d).dayOfWeek.value
                val scheduled = habits.filter { it.period == Period.DAY && dow in it.daysOfWeek }
                if (scheduled.isEmpty()) false
                else {
                    var totalW = 0.0
                    var creditW = 0.0
                    scheduled.forEach { h ->
                        val w = h.weight.factor
                        totalW += w
                        val q = yearEntries.filter { it.habitId == h.id && it.epochDay == d && it.kind == EntryKind.LOG }.sumOf { it.quantity }
                        val r = if (h.target > 0.0) q / h.target else 0.0
                        creditW += w * ScoreEngine.credit(r)
                    }
                    val overall = if (totalW > 0.0) creditW / totalW else 0.0
                    overall >= 1.25
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatTrioModule("ACTIVE DAYS", "$activeDaysCount", Ink.AMBER)
            StatTrioModule("GOLD DAYS", "$goldDaysCount", Color(0xFFFFD08A))
            StatTrioModule("ACHIEVED", "${yearAchievements.size}", Ink.SAGE)
        }

        Spacer(Modifier.height(18.dp))

        // 5. The Lines (Section 5.4 - Bullet bars for goals and dream timelines)
        BasicText(
            text = "THE LINES",
            style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))

        TheLinesView(
            selectedYear = selectedYear,
            todayEpoch = todayEpoch,
            goals = goals,
            dreams = dreams,
            achievements = achievements,
            entries = entries,
            habits = habits,
            onGoalClick = { onNavigateToGoal?.invoke(it.id) },
            onDreamClick = { onNavigateToDream?.invoke(it.id) }
        )

        Spacer(Modifier.height(24.dp))
        NavSpacer()
    }

    // Inspect Day Sheet
    if (inspectingDay != null) {
        AlmanacDaySheet(
            epochDay = inspectingDay!!,
            todayEpoch = todayEpoch,
            habits = habits,
            entries = entries,
            goals = goals,
            dreams = dreams,
            achievements = achievements,
            onDismiss = { inspectingDay = null }
        )
    }
}

@Composable
private fun StatTrioModule(label: String, value: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.SURFACE_2)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column {
            BasicText(
                text = label,
                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = value,
                style = TextStyle(color = accentColor, fontSize = 16.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
        }
    }
}

// =============================================================================
// DOT GRID CANVAS (Section 5.1 & 5.2)
// =============================================================================

@Composable
private fun AlmanacDotGrid(
    year: Int,
    lens: AlmanacLens,
    habits: List<HabitItem>,
    allHabits: List<HabitItem>,
    entries: List<Entry>,
    goals: List<Goal>,
    dreams: List<Dream>,
    achievements: List<Achievement>,
    todayEpoch: Long,
    sweepFraction: Float,
    onDayTapped: (Long) -> Unit
) {
    val context = LocalContext.current
    val earliestStartEpoch = remember(habits) {
        habits.minOfOrNull { it.startEpochDay } ?: todayEpoch
    }

    // Pre-calculate daily aggregates for the 12 months
    val yearAggregates = remember(year, lens, entries, habits) {
        val map = HashMap<Long, DayAggregate>()
        for (month in 1..12) {
            val ym = YearMonth.of(year, month)
            for (day in 1..ym.lengthOfMonth()) {
                val epoch = LocalDate.of(year, month, day).toEpochDay()
                val targetHabits = when (lens) {
                    is AlmanacLens.All -> habits
                    is AlmanacLens.HabitLens -> habits.filter { it.id == lens.habitId }
                    is AlmanacLens.DreamLens -> habits.filter { it.dreamId == lens.dreamId }
                }
                val agg = HabitMasterStore.computeDayAggregate(context, epoch, targetHabits, entries)
                map[epoch] = agg
            }
        }
        map
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        val labelW = 28.dp.value * (w / 340f).coerceIn(0.8f, 1.2f)
        val gridW = w - labelW - 4.dp.value
        val colPitch = gridW / 31f
        val rowPitch = h / 12f
        val dotRadius = (colPitch * 0.28f).coerceIn(2.2f, 3.5f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(year) {
                    detectTapGestures { offset ->
                        val col = ((offset.x - labelW) / colPitch).toInt()
                        val row = (offset.y / rowPitch).toInt()
                        if (col in 0 until 31 && row in 0 until 12) {
                            val month = row + 1
                            val day = col + 1
                            val ym = YearMonth.of(year, month)
                            if (day <= ym.lengthOfMonth()) {
                                val epoch = LocalDate.of(year, month, day).toEpochDay()
                                onDayTapped(epoch)
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val nowDayOfYear = LocalDate.now().dayOfYear
                val isCurrentYear = (year == LocalDate.now().year)
                val maxVisibleDayOfYear = if (isCurrentYear) (nowDayOfYear * sweepFraction).toInt() else totalDaysInYear(year)

                for (monthIdx in 0 until 12) {
                    val month = monthIdx + 1
                    val ym = YearMonth.of(year, month)
                    val daysInMonth = ym.lengthOfMonth()
                    val y = monthIdx * rowPitch + rowPitch / 2f

                    // Draw month label
                    val monthName = ym.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase().take(3)
                    // Simple dot matrix or short text indicator drawn into canvas or handled by row
                    // Here we draw month line dots
                    for (dayIdx in 0 until 31) {
                        val day = dayIdx + 1
                        val x = labelW + dayIdx * colPitch + colPitch / 2f

                        if (day > daysInMonth) {
                            // Blank cell for months shorter than 31 days
                            continue
                        }

                        val date = LocalDate.of(year, month, day)
                        val epoch = date.toEpochDay()
                        val dayOfYr = date.dayOfYear
                        val isSwept = dayOfYr <= maxVisibleDayOfYear
                        val isFuture = isCurrentYear && dayOfYr > nowDayOfYear
                        val isToday = (epoch == todayEpoch)

                        val agg = yearAggregates[epoch]
                        val hasNote = HabitMasterStore.getDayNote(context, epoch) != null

                        // State & Color determination
                        when {
                            isFuture || !isSwept -> {
                                // Dim graphite solid
                                drawCircle(
                                    color = Color(0xFF1B1C20),
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                            }
                            epoch < earliestStartEpoch && (agg?.entryCount ?: 0) == 0 -> {
                                // Before tracking started: Hollow hairline ring
                                drawCircle(
                                    color = Color(0xFF2B2E35),
                                    radius = dotRadius,
                                    center = Offset(x, y),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                            agg?.isRest == true -> {
                                // Rest day: Hollow brass-dim ring
                                drawCircle(
                                    color = Ink.AMBER.copy(alpha = 0.45f),
                                    radius = dotRadius,
                                    center = Offset(x, y),
                                    style = Stroke(width = 1.2.dp.toPx())
                                )
                            }
                            (agg?.completion ?: 0.0) <= 0.0 -> {
                                // Tracked, scheduled habits existed, nothing logged
                                drawCircle(
                                    color = Color(0xFF2A2C32),
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                            }
                            agg?.isGold == true -> {
                                // Gold day: Full brass + LED halo ring
                                drawCircle(
                                    color = Color(0xFFFFD08A).copy(alpha = 0.35f),
                                    radius = dotRadius + 2.5f,
                                    center = Offset(x, y)
                                )
                                drawCircle(
                                    color = Ink.AMBER,
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                            }
                            else -> {
                                // Brass in 5 steps based on completion fraction
                                val c = agg?.completion ?: 0.0
                                val alpha = when {
                                    c <= 0.25 -> 0.30f
                                    c <= 0.50 -> 0.50f
                                    c <= 0.75 -> 0.70f
                                    c < 1.00 -> 0.85f
                                    else -> 1.00f
                                }
                                drawCircle(
                                    color = Ink.AMBER.copy(alpha = alpha),
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                            }
                        }

                        // Today ring
                        if (isToday) {
                            drawCircle(
                                color = Ink.AMBER,
                                radius = dotRadius + 3.dp.toPx(),
                                center = Offset(x, y),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }

                        // Overlays (Achievement star, milestone diamond, period tick, note line)
                        if (agg?.hasAchievement == true) {
                            // Tiny 4-point star in LED gold at upper right
                            drawFourPointStar(Offset(x + dotRadius * 0.9f, y - dotRadius * 0.9f), 2.2.dp.toPx(), Color(0xFFFFD08A))
                        } else if (agg?.hasMilestone == true) {
                            // Tiny sage diamond
                            drawDiamond(Offset(x + dotRadius * 0.9f, y - dotRadius * 0.9f), 2.0.dp.toPx(), Ink.SAGE)
                        }

                        if (agg?.hasPeriodHabitLog == true) {
                            // 1.5dp tick under the dot
                            drawLine(
                                color = Ink.AMBER,
                                start = Offset(x - 1.5.dp.toPx(), y + dotRadius + 2.dp.toPx()),
                                end = Offset(x + 1.5.dp.toPx(), y + dotRadius + 2.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }

                        if (hasNote) {
                            // Tiny underline under dot
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(x - 2.dp.toPx(), y + dotRadius + 3.5.dp.toPx()),
                                end = Offset(x + 2.dp.toPx(), y + dotRadius + 3.5.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun totalDaysInYear(year: Int): Int = if (Year.isLeap(year.toLong())) 366 else 365

private fun DrawScope.drawFourPointStar(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.35f, center.y - size * 0.35f)
        lineTo(center.x + size, center.y)
        lineTo(center.x + size * 0.35f, center.y + size * 0.35f)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size * 0.35f, center.y + size * 0.35f)
        lineTo(center.x - size, center.y)
        lineTo(center.x - size * 0.35f, center.y - size * 0.35f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawDiamond(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size, center.y)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size, center.y)
        close()
    }
    drawPath(path, color)
}

// =============================================================================
// THE LINES VIEW (Section 5.4)
// =============================================================================

@Composable
private fun TheLinesView(
    selectedYear: Int,
    todayEpoch: Long,
    goals: List<Goal>,
    dreams: List<Dream>,
    achievements: List<Achievement>,
    entries: List<Entry>,
    habits: List<HabitItem>,
    onGoalClick: (Goal) -> Unit,
    onDreamClick: (Dream) -> Unit
) {
    val yearStart = LocalDate.of(selectedYear, 1, 1).toEpochDay()
    val yearEnd = LocalDate.of(selectedYear, 12, 31).toEpochDay()
    val yearSpan = (yearEnd - yearStart).toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.SURFACE_1)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Month headers: J F M A M J J A S O N D
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
            months.forEach { m ->
                BasicText(m, style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
            }
        }

        // Active Goals bullet bars
        goals.take(6).forEach { goal ->
            GoalLineRow(goal, selectedYear, todayEpoch, yearStart, yearSpan, onGoalClick)
            Spacer(Modifier.height(10.dp))
        }

        // Active Dreams lines
        dreams.take(4).forEach { dream ->
            DreamLineRow(dream, selectedYear, todayEpoch, yearStart, yearSpan, onDreamClick)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun GoalLineRow(
    goal: Goal,
    selectedYear: Int,
    todayEpoch: Long,
    yearStart: Long,
    yearSpan: Float,
    onClick: (Goal) -> Unit
) {
    val startDay = goal.startEpochDay
    val targetDay = goal.targetEpochDay ?: (goal.startEpochDay + 90)
    val span = (targetDay - startDay).toFloat().coerceAtLeast(1f)

    // Calculate progress
    val pace = remember(goal, todayEpoch) {
        ScoreEngine.calculateGoalPace(goal, todayEpoch, emptyList(), emptyList(), emptyList(), emptyList())
    }

    val paceTag = when (pace.status) {
        GoalPaceStatus.AHEAD -> "AHEAD ${pace.daysAhead?.roundToInt() ?: 0}D"
        GoalPaceStatus.BEHIND -> "BEHIND ${abs(pace.daysAhead?.roundToInt() ?: 0)}D"
        GoalPaceStatus.ON_TRACK -> "ON TRACK"
    }
    val paceColor = when (pace.status) {
        GoalPaceStatus.AHEAD -> Ink.SAGE
        GoalPaceStatus.BEHIND -> Ink.AMBER.copy(alpha = 0.8f)
        GoalPaceStatus.ON_TRACK -> Ink.TEXT_MUTED_V4
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(goal) }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text = goal.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
            BasicText(
                text = paceTag,
                style = TextStyle(color = paceColor, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(Modifier.height(4.dp))

        // Bullet bar Canvas
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val totalW = size.width
            val xStart = (((startDay - yearStart) / yearSpan).coerceIn(0f, 1f)) * totalW
            val xEnd = (((targetDay - yearStart) / yearSpan).coerceIn(0f, 1f)) * totalW

            val nowX = (((todayEpoch - yearStart) / yearSpan).coerceIn(0f, 1f)) * totalW

            // Background line
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(xStart, size.height / 2f),
                end = Offset(xEnd, size.height / 2f),
                strokeWidth = 3.dp.toPx()
            )

            // Progress filled bar
            val progressW = (xEnd - xStart) * pace.progress.toFloat()
            val nodeX = xStart + progressW
            drawLine(
                color = Ink.AMBER,
                start = Offset(xStart, size.height / 2f),
                end = Offset(nodeX, size.height / 2f),
                strokeWidth = 3.dp.toPx()
            )

            // Brass node
            drawCircle(
                color = Ink.AMBER,
                radius = 3.5.dp.toPx(),
                center = Offset(nodeX, size.height / 2f)
            )

            // Vertical NOW hairline
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(nowX, 0f),
                end = Offset(nowX, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun DreamLineRow(
    dream: Dream,
    selectedYear: Int,
    todayEpoch: Long,
    yearStart: Long,
    yearSpan: Float,
    onClick: (Dream) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(dream) }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text = dream.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono)
            )
            BasicText(
                text = "${dream.rungs.count { it.isDone }}/${dream.rungs.size} RUNGS",
                style = TextStyle(color = Ink.SAGE, fontSize = 9.sp, fontFamily = Fonts.mono)
            )
        }

        Spacer(Modifier.height(4.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val totalW = size.width
            val nowX = (((todayEpoch - yearStart) / yearSpan).coerceIn(0f, 1f)) * totalW

            // Base line
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(0f, size.height / 2f),
                end = Offset(totalW, size.height / 2f),
                strokeWidth = 1.5.dp.toPx()
            )

            // Draw diamonds for rungs
            val rungCount = dream.rungs.size.coerceAtLeast(1)
            dream.rungs.forEachIndexed { i, rung ->
                val frac = (i + 1).toFloat() / (rungCount + 1).toFloat()
                val rx = frac * totalW
                if (rung.isDone) {
                    drawDiamond(Offset(rx, size.height / 2f), 3.5.dp.toPx(), Ink.SAGE)
                } else {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = 2.dp.toPx(),
                        center = Offset(rx, size.height / 2f)
                    )
                }
            }

            // NOW hairline
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(nowX, 0f),
                end = Offset(nowX, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

// =============================================================================
// DAY SHEET (Section 5.5)
// =============================================================================

@Composable
private fun AlmanacDaySheet(
    epochDay: Long,
    todayEpoch: Long,
    habits: List<HabitItem>,
    entries: List<Entry>,
    goals: List<Goal>,
    dreams: List<Dream>,
    achievements: List<Achievement>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val date = remember(epochDay) { LocalDate.ofEpochDay(epochDay) }
    val dayTitle = remember(date) {
        val dow = date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase()
        val m = date.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase()
        "$dow ${date.dayOfMonth} $m"
    }

    val dayEntries = remember(entries, epochDay) { entries.filter { it.epochDay == epochDay } }
    val dayAgg = remember(epochDay, habits, entries) {
        HabitMasterStore.computeDayAggregate(context, epochDay, habits, entries)
    }

    var dayNote by remember(epochDay) { mutableStateOf(HabitMasterStore.getDayNote(context, epochDay) ?: "") }

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header: SUN 18 OCT · 118% [GOLD]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val pct = (dayAgg.overall * 100).roundToInt()
                    val tier = ScoreEngine.tier(dayAgg.overall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = dayTitle,
                            style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.mono)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (tier == rocks.ndot.widgets.instruments.habits.Tier.GOLD) Color(0xFFFFD08A).copy(alpha = 0.2f) else Ink.SURFACE_3)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            BasicText(
                                text = "${pct}% · ${tier.name}",
                                style = TextStyle(color = if (tier == rocks.ndot.widgets.instruments.habits.Tier.GOLD) Color(0xFFFFD08A) else Ink.AMBER, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

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

            Spacer(Modifier.height(14.dp))

            // Habit breakdown list for this day
            BasicText(
                text = "LOGGED PRACTICES",
                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
            )
            Spacer(Modifier.height(6.dp))

            if (dayEntries.isEmpty()) {
                BasicText(
                    text = "No activities recorded on this date.",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp)
                )
            } else {
                dayEntries.forEach { entry ->
                    val habit = habits.firstOrNull { it.id == entry.habitId }
                    val habitName = habit?.name ?: "Habit"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText("▪", style = TextStyle(color = Ink.AMBER, fontSize = 12.sp))
                            Spacer(Modifier.width(6.dp))
                            BasicText(
                                text = habitName,
                                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                        BasicText(
                            text = if (entry.kind == EntryKind.REST) "REST" else "${entry.quantity.toInt()} ${habit?.unit ?: ""}",
                            style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Day note field (optional text field; writes underline under dot)
            BasicText(
                text = "DAY NOTE (OPTIONAL)",
                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = dayNote,
                    onValueChange = {
                        dayNote = it
                        HabitMasterStore.setDayNote(context, epochDay, it)
                    },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.sans),
                    cursorBrush = SolidColor(Ink.AMBER),
                    decorationBox = { inner ->
                        if (dayNote.isBlank()) {
                            BasicText("Reflect on this day in one line...", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 13.sp))
                        }
                        inner()
                    }
                )
            }
        }
    }
}
