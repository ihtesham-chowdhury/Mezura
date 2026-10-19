package rocks.ndot.widgets.instruments.habits

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.app.LightChrome
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.GlassPanel
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.NdotSegmented
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.StepKey
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.labelStyle
import rocks.ndot.widgets.kit.ndotScreenInsets
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Flying brick particle state for habit completion delight moment (§10.2).
 */
private class FlyingBrick(
    val id: Long,
    val startX: Float,
    val startY: Float,
    val controlX: Float,
    val controlY: Float,
    val endX: Float,
    val endY: Float,
    val anim: Animatable<Float, *>
)

/**
 * Habits Instrument — 3 Top Segments: CITY | TODAY | DREAMS (Part I Section 3).
 */
@Composable
fun HabitsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var habits by remember { mutableStateOf(HabitMasterStore.getHabits(context)) }
    var entries by remember { mutableStateOf(HabitMasterStore.getEntries(context)) }
    var goals by remember { mutableStateOf(HabitMasterStore.getGoals(context)) }
    var dreams by remember { mutableStateOf(HabitMasterStore.getDreams(context)) }
    var achievements by remember { mutableStateOf(HabitMasterStore.getAchievements(context)) }

    fun refreshData() {
        habits = HabitMasterStore.getHabits(context)
        entries = HabitMasterStore.getEntries(context)
        goals = HabitMasterStore.getGoals(context)
        dreams = HabitMasterStore.getDreams(context)
        achievements = HabitMasterStore.getAchievements(context)
    }

    val today = remember { HabitMasterStore.today() }
    var selectedDay by remember { mutableLongStateOf(today) }
    var weekOffset by remember { mutableIntStateOf(0) }

    // Tab state: 0 = CITY, 1 = TODAY, 2 = DREAMS
    val defaultTab = remember { AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_HABITS_TAB, "today") }
    var selectedTab by remember {
        mutableIntStateOf(
            when (defaultTab) {
                "city" -> 0
                "dreams" -> 2
                else -> 1
            }
        )
    }

    var showTitlesSheet by remember { mutableStateOf(false) }
    var selectedBuilding by remember { mutableStateOf<CitySlot?>(null) }
    var newBuildingBanner by remember { mutableStateOf<String?>(null) }

    // Flying brick particles
    var particles by remember { mutableStateOf<List<FlyingBrick>>(emptyList()) }
    var summaryHeaderCenter by remember { mutableStateOf(Offset.Zero) }

    // Economy calculations from HabitMasterStore & ScoreEngine
    val lifetimeBricks = remember(entries, achievements) { HabitMasterStore.calculateLifetimeBricks(context) }
    val todayEarned = remember(entries) { HabitMasterStore.getTodayBricks(context) }
    val isGoldDay = remember(habits, entries, today) {
        val dow = LocalDate.ofEpochDay(today).dayOfWeek.value
        val scheduled = habits.filter { it.period == Period.DAY && dow in it.daysOfWeek }
        if (scheduled.isEmpty()) false
        else {
            var totalW = 0.0
            var creditW = 0.0
            scheduled.forEach { h ->
                val w = h.weight.factor
                totalW += w
                val qManual = entries.filter { it.habitId == h.id && it.epochDay == today && it.kind == EntryKind.LOG }.sumOf { it.quantity }
                val qAuto = HabitMasterStore.getAutoSourceQuantity(context, h.autoSource, today)
                val q = qManual + qAuto
                val r = if (h.target > 0.0) q / h.target else 0.0
                creditW += w * ScoreEngine.credit(r)
            }
            val overall = if (totalW > 0.0) creditW / totalW else 0.0
            overall >= 1.25
        }
    }
    val buildingsBuilt = remember(lifetimeBricks, today, isGoldDay) {
        CityModel.updateAndGetBuildingsBuiltWithLog(context, lifetimeBricks, today, isGoldDay)
    }
    val currentTitle = remember(buildingsBuilt) { CityTitle.forCount(buildingsBuilt) }
    val (progressBricks, nextCost) = remember(lifetimeBricks) { CityModel.bricksToNextBuilding(lifetimeBricks) }
    val currentYear = remember(today) { LocalDate.ofEpochDay(today).year }
    val goldDaysCount = remember(habits, entries, currentYear) {
        CityModel.countGoldDaysThisYear(habits, entries, currentYear) { hId, d ->
            val h = habits.firstOrNull { it.id == hId }
            if (h != null) HabitMasterStore.getAutoSourceQuantity(context, h.autoSource, d) else 0.0
        }
    }
    val composedSlots = remember(buildingsBuilt, dreams, goals, achievements) {
        CityModel.getComposedSlots(context, buildingsBuilt, dreams, goals, achievements)
    }

    // Show-up streak (flame in header)
    val showUpStreak = remember(habits, entries, today) {
        ScoreEngine.calculateShowUpStreak(habits, entries, today) { hId, d ->
            val h = habits.firstOrNull { it.id == hId }
            if (h != null) HabitMasterStore.getAutoSourceQuantity(context, h.autoSource, d) else 0.0
        }
    }

    // Modal Sheet states
    var logSheetHabit by remember { mutableStateOf<HabitItem?>(null) }
    var editingHabit by remember { mutableStateOf<HabitItem?>(null) }
    var showNewItemChoice by remember { mutableStateOf(false) }
    var showHabitWizard by remember { mutableStateOf(false) }
    var showGoalWizard by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<Goal?>(null) }
    var showDreamWizard by remember { mutableStateOf(false) }
    var editingDream by remember { mutableStateOf<Dream?>(null) }
    var inspectingGoal by remember { mutableStateOf<Goal?>(null) }
    var inspectingDream by remember { mutableStateOf<Dream?>(null) }
    var ceremonyAchievement by remember { mutableStateOf<Achievement?>(null) }

    // Undo toast state
    var undoEntryId by remember { mutableStateOf<String?>(null) }
    var undoMessage by remember { mutableStateOf<String?>(null) }

    fun triggerCompletionParticles(origin: Offset) {
        Feedback.pop(context)
        val target = if (summaryHeaderCenter != Offset.Zero) summaryHeaderCenter else Offset(200f, 160f)
        val newParticles = (0 until 4).map { i ->
            val spread = (Random.nextFloat() - 0.5f) * 120f
            val controlY = min(origin.y, target.y) - 80f - Random.nextFloat() * 60f
            val controlX = (origin.x + target.x) / 2f + spread
            FlyingBrick(
                id = System.nanoTime() + i,
                startX = origin.x,
                startY = origin.y,
                controlX = controlX,
                controlY = controlY,
                endX = target.x,
                endY = target.y,
                anim = Animatable(0f)
            )
        }
        particles = particles + newParticles
        newParticles.forEach { p ->
            scope.launch {
                p.anim.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
                particles = particles.filter { it.id != p.id }
            }
        }
    }

    LightChrome(false)

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Black)
            .ndotScreenInsets()
    ) {
        Column(Modifier.fillMaxSize()) {
            // App Bar with 3-Segment Control (CITY | TODAY | DREAMS)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NdotSegmented(
                    options = listOf("CITY", "TODAY", "DREAMS"),
                    selectedIndex = selectedTab,
                    onSelect = {
                        selectedTab = it
                        val key = when (it) {
                            0 -> "city"
                            2 -> "dreams"
                            else -> "today"
                        }
                        AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_HABITS_TAB, key, "Habits Tab")
                    },
                    onLongPressDefault = { idx ->
                        val key = when (idx) {
                            0 -> "city"
                            2 -> "dreams"
                            else -> "today"
                        }
                        AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_HABITS_TAB, key, "Habits Tab")
                    },
                    accent = Ink.AMBER
                )
                Spacer(Modifier.weight(1f))
                if (selectedTab != 0) {
                    TextAction("+ NEW", Ink.AMBER, { showNewItemChoice = true })
                }
            }

            // Body Content based on Tab
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> {
                        // 0: CITY TAB
                        CityTabContent(
                            lifetimeBricks = lifetimeBricks,
                            todayEarned = todayEarned,
                            buildingsBuilt = buildingsBuilt,
                            currentTitle = currentTitle,
                            progressBricks = progressBricks,
                            nextCost = nextCost,
                            globalStreak = showUpStreak,
                            habitsCount = habits.size,
                            doneTodayCount = habits.count { h ->
                                val q = HabitMasterStore.getDayTotalQuantity(context, h.id, today)
                                ScoreEngine.qualifies(q, h.minimum, h.target)
                            },
                            goldDaysCount = goldDaysCount,
                            composedSlots = composedSlots,
                            selectedBuilding = selectedBuilding,
                            onBuildingTapped = { selectedBuilding = it },
                            onGroundTapped = { selectedBuilding = null },
                            onOpenTitles = { showTitlesSheet = true },
                            onOpenDream = { inspectingDream = it },
                            onOpenAchievement = { ceremonyAchievement = it },
                            onShare = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "My Habit City has grown to ${currentTitle.label} with $lifetimeBricks bricks and $buildingsBuilt buildings on Ndot!"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share City"))
                            }
                        )
                    }
                    1 -> {
                        // 1: TODAY TAB (Section 3.1)
                        TodayTabContent(
                            habits = habits,
                            entries = entries,
                            dreams = dreams,
                            goals = goals,
                            todayEpoch = today,
                            selectedDay = selectedDay,
                            weekOffset = weekOffset,
                            showUpStreak = showUpStreak,
                            todayEarned = todayEarned,
                            onSelectDay = { selectedDay = it },
                            onPrevWeek = { weekOffset-- },
                            onNextWeek = { weekOffset++ },
                            onOpenLog = { habit -> logSheetHabit = habit },
                            onEditHabit = { habit -> editingHabit = habit },
                            onQuickAdd = { habit, delta, origin ->
                                val currentQ = HabitMasterStore.getDayTotalQuantity(context, habit.id, selectedDay)
                                val entry = HabitMasterStore.logEntry(context, habit.id, selectedDay, delta)
                                refreshData()
                                undoEntryId = entry.id
                                undoMessage = "+${delta.toInt()} ${habit.unit} logged"
                                triggerCompletionParticles(origin)
                                scope.launch {
                                    delay(4000)
                                    if (undoEntryId == entry.id) undoEntryId = null
                                }
                            },
                            onToggleDone = { habit, origin ->
                                HabitMasterStore.toggleDoneHabit(context, habit.id, selectedDay)
                                refreshData()
                                triggerCompletionParticles(origin)
                            },
                            onRestDay = { habit ->
                                HabitMasterStore.logEntry(context, habit.id, selectedDay, 0.0, EntryKind.REST)
                                refreshData()
                                Feedback.tick(context)
                            },
                            onSummaryPositioned = { summaryHeaderCenter = it }
                        )
                    }
                    2 -> {
                        // 2: DREAMS TAB (Section 4)
                        DreamsTabContent(
                            dreams = dreams,
                            goals = goals,
                            achievements = achievements,
                            habits = habits,
                            entries = entries,
                            todayEpoch = today,
                            onOpenDream = { inspectingDream = it },
                            onOpenGoal = { inspectingGoal = it },
                            onNewItem = { showNewItemChoice = true },
                            onViewAchievement = { ceremonyAchievement = it }
                        )
                    }
                }
            }

            // Undo Snackbar Chip
            if (undoEntryId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(undoMessage ?: "Action completed", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono))
                        TextAction("UNDO", Ink.AMBER, {
                            undoEntryId?.let { HabitMasterStore.removeEntry(context, it) }
                            undoEntryId = null
                            refreshData()
                            Feedback.tick(context)
                        })
                    }
                }
            }

            NavSpacer()
        }

        // Flying Brick Particles Overlay
        if (particles.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                for (p in particles) {
                    val t = p.anim.value
                    if (t in 0.001f..0.999f) {
                        val oneMinusT = 1f - t
                        val x = oneMinusT * oneMinusT * p.startX + 2f * oneMinusT * t * p.controlX + t * t * p.endX
                        val y = oneMinusT * oneMinusT * p.startY + 2f * oneMinusT * t * p.controlY + t * t * p.endY
                        drawRoundRect(
                            color = Color(0xFFC9694A), // Brick reddish-orange
                            topLeft = Offset(x - 6.dp.toPx(), y - 3.5.dp.toPx()),
                            size = Size(12.dp.toPx(), 7.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }

    // Modal Sheets
    if (logSheetHabit != null) {
        val h = logSheetHabit!!
        val autoQ = HabitMasterStore.getAutoSourceQuantity(context, h.autoSource, selectedDay)
        val existing = entries.firstOrNull { it.habitId == h.id && it.epochDay == selectedDay }
        HabitLogSheet(
            habit = h,
            epochDay = selectedDay,
            existingEntry = existing,
            autoQuantity = autoQ,
            onDismiss = { logSheetHabit = null },
            onSave = { q, note ->
                if (existing != null) HabitMasterStore.removeEntry(context, existing.id)
                HabitMasterStore.logEntry(context, h.id, selectedDay, q, EntryKind.LOG, note)
                refreshData()
                logSheetHabit = null
                Feedback.tick(context)
            }
        )
    }

    if (showHabitWizard || editingHabit != null) {
        HabitEditorSheet(
            habitToEdit = editingHabit,
            onDismiss = {
                showHabitWizard = false
                editingHabit = null
            },
            onSave = { saved ->
                HabitMasterStore.saveHabit(context, saved)
                refreshData()
                showHabitWizard = false
                editingHabit = null
                Feedback.lock(context)
            },
            onDelete = { id ->
                HabitMasterStore.deleteHabit(context, id)
                refreshData()
                showHabitWizard = false
                editingHabit = null
            }
        )
    }

    if (showNewItemChoice) {
        NewItemChoiceModal(
            onDismiss = { showNewItemChoice = false },
            onChooseHabit = {
                showNewItemChoice = false
                showHabitWizard = true
            },
            onChooseGoal = {
                showNewItemChoice = false
                showGoalWizard = true
            },
            onChooseDream = {
                showNewItemChoice = false
                showDreamWizard = true
            }
        )
    }

    if (showGoalWizard || editingGoal != null) {
        GoalEditorSheet(
            goalToEdit = editingGoal,
            todayEpoch = today,
            onDismiss = {
                showGoalWizard = false
                editingGoal = null
            },
            onSave = { saved ->
                HabitMasterStore.saveGoal(context, saved)
                refreshData()
                showGoalWizard = false
                editingGoal = null
                Feedback.lock(context)
            },
            onDelete = { delId ->
                HabitMasterStore.deleteGoal(context, delId)
                refreshData()
                showGoalWizard = false
                editingGoal = null
                Feedback.tick(context)
            }
        )
    }

    if (showDreamWizard || editingDream != null) {
        DreamEditorSheet(
            dreamToEdit = editingDream,
            todayEpoch = today,
            onDismiss = {
                showDreamWizard = false
                editingDream = null
            },
            onSave = { saved ->
                HabitMasterStore.saveDream(context, saved)
                refreshData()
                showDreamWizard = false
                editingDream = null
                Feedback.lock(context)
            },
            onDelete = { delId ->
                HabitMasterStore.deleteDream(context, delId)
                refreshData()
                showDreamWizard = false
                editingDream = null
                Feedback.tick(context)
            }
        )
    }

    if (inspectingGoal != null) {
        val g = inspectingGoal!!
        GoalDetailSheet(
            goal = g,
            todayEpoch = today,
            onDismiss = { inspectingGoal = null },
            onToggleMilestone = { mId ->
                HabitMasterStore.toggleGoalMilestone(context, g.id, mId)
                refreshData()
                inspectingGoal = goals.firstOrNull { it.id == g.id }
            },
            onAddProgress = { delta ->
                HabitMasterStore.addGoalProgress(context, g.id, delta)
                refreshData()
            },
            onAddMetricReading = { v ->
                HabitMasterStore.addMetricReading(context, g.id, v)
                refreshData()
            },
            onMarkAchieved = {
                val ach = HabitMasterStore.markGoalAchieved(context, g.id)
                refreshData()
                inspectingGoal = null
                ceremonyAchievement = ach
            },
            onEdit = {
                editingGoal = g
                inspectingGoal = null
            },
            onDelete = {
                HabitMasterStore.deleteGoal(context, g.id)
                inspectingGoal = null
                refreshData()
                Feedback.tick(context)
            }
        )
    }

    if (inspectingDream != null) {
        val d = inspectingDream!!
        DreamDetailSheet(
            dream = d,
            habits = habits,
            entries = entries,
            todayEpoch = today,
            onDismiss = { inspectingDream = null },
            onToggleRung = { rId ->
                HabitMasterStore.toggleDreamRung(context, d.id, rId)
                refreshData()
                inspectingDream = dreams.firstOrNull { it.id == d.id }
            },
            onMarkAchieved = {
                val ach = HabitMasterStore.markDreamAchieved(context, d.id)
                refreshData()
                inspectingDream = null
                ceremonyAchievement = ach
            },
            onEdit = {
                editingDream = d
                inspectingDream = null
            },
            onDelete = {
                HabitMasterStore.deleteDream(context, d.id)
                inspectingDream = null
                refreshData()
                Feedback.tick(context)
            }
        )
    }

    if (ceremonyAchievement != null) {
        AchievementCeremonySheet(
            achievement = ceremonyAchievement!!,
            onDone = { ceremonyAchievement = null }
        )
    }

    if (showTitlesSheet) {
        NdotSheet(onDismiss = { showTitlesSheet = false }) {
            BasicText("CITY TITLES", style = labelStyle(Ink.Text, 14.sp, FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText("Build structures through honest effort to elevate your settlement.", style = labelStyle(Ink.TEXT_MUTED_V4, 11.sp))
            Spacer(Modifier.height(16.dp))
            CityTitle.entries.forEach { titleItem ->
                val unlocked = buildingsBuilt >= titleItem.minBuildings
                val isCurrent = currentTitle == titleItem
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) Ink.SURFACE_3 else Ink.SURFACE_1)
                        .border(1.dp, if (isCurrent) Ink.AMBER else Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                titleItem.label.uppercase(),
                                style = labelStyle(
                                    if (unlocked) Ink.Text else Ink.Faint,
                                    13.sp,
                                    FontWeight.Bold
                                )
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(Ink.AMBER)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    BasicText("CURRENT", style = labelStyle(Ink.GROUND, 8.sp, FontWeight.Bold))
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        BasicText("${titleItem.minBuildings}+ buildings required", style = labelStyle(Ink.Faint, 10.sp))
                    }
                    if (unlocked) {
                        BasicText("✓", style = labelStyle(Ink.SAGE, 15.sp, FontWeight.Bold))
                    } else {
                        BasicText("LOCKED", style = labelStyle(Ink.Faint, 10.sp, FontWeight.Medium).copy(fontFamily = Fonts.mono))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// =============================================================================
// TODAY TAB CONTENT (Section 3.1)
// =============================================================================

@Composable
private fun TodayTabContent(
    habits: List<HabitItem>,
    entries: List<Entry>,
    dreams: List<Dream>,
    goals: List<Goal>,
    todayEpoch: Long,
    selectedDay: Long,
    weekOffset: Int,
    showUpStreak: Int,
    todayEarned: Int,
    onSelectDay: (Long) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenLog: (HabitItem) -> Unit,
    onEditHabit: (HabitItem) -> Unit,
    onQuickAdd: (HabitItem, Double, Offset) -> Unit,
    onToggleDone: (HabitItem, Offset) -> Unit,
    onRestDay: (HabitItem) -> Unit,
    onSummaryPositioned: (Offset) -> Unit
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val todayDate = LocalDate.ofEpochDay(todayEpoch)

    val dayOfWeekVal = todayDate.dayOfWeek.value // 1 = Mon, 7 = Sun
    val baseMonday = todayEpoch - (dayOfWeekVal - 1) + weekOffset * 7
    val weekDays = remember(baseMonday) { (0 until 7).map { baseMonday + it } }

    val dayHabits = remember(habits) { habits.filter { it.period == Period.DAY } }
    val periodHabits = remember(habits) { habits.filter { it.period == Period.WEEK || it.period == Period.MONTH } }

    val dowSelected = LocalDate.ofEpochDay(selectedDay).dayOfWeek.value
    val scheduledDayHabits = remember(dayHabits, dowSelected) {
        dayHabits.filter { dowSelected in it.daysOfWeek }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Week Strip (< Fri 16 Sat 17 [Sun 18] Mon 19 >)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepKey("◂", onPrevWeek, size = 32.dp)

                weekDays.forEach { day ->
                    val date = LocalDate.ofEpochDay(day)
                    val isSelected = (day == selectedDay)
                    val isToday = (day == todayEpoch)

                    val dayName = date.dayOfWeek.getDisplayName(JTextStyle.SHORT, locale).uppercase(locale)
                    val dayNum = date.dayOfMonth.toString()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Ink.AMBER else if (isToday) Color(0x18FFFFFF) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) Ink.AMBER else if (isToday) Ink.HAIRLINE_V4 else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectDay(day) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(
                                text = dayName.take(3),
                                style = labelStyle(if (isSelected) Ink.GROUND else Ink.TEXT_MUTED_V4, 9.sp, FontWeight.Medium)
                            )
                            Spacer(Modifier.height(2.dp))
                            BasicText(
                                text = dayNum,
                                style = TextStyle(
                                    fontFamily = Fonts.condensed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isSelected) Ink.GROUND else Ink.Text
                                )
                            )
                        }
                    }
                }

                StepKey("▸", onNextWeek, size = 32.dp)
            }
        }

        // 2. The Day Bar (Linear meter + flame streak counter)
        item {
            val metCount = scheduledDayHabits.count { h ->
                val q = HabitMasterStore.getDayTotalQuantity(context, h.id, selectedDay)
                ScoreEngine.qualifies(q, h.minimum, h.target)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.SURFACE_1)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(16.dp))
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        onSummaryPositioned(Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f))
                    }
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText("THE DAY BAR", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText("▲", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp))
                            Spacer(Modifier.width(4.dp))
                            BasicText("$showUpStreak", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Linear Segmented Day Bar Canvas
                    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                        val count = scheduledDayHabits.size.coerceAtLeast(1)
                        val totalW = size.width
                        val gap = 4.dp.toPx()
                        val segW = (totalW - gap * (count - 1)) / count

                        scheduledDayHabits.forEachIndexed { i, h ->
                            val x = i * (segW + gap)
                            val q = HabitMasterStore.getDayTotalQuantity(context, h.id, selectedDay)
                            val r = if (h.target > 0.0) q / h.target else 0.0
                            val c = ScoreEngine.credit(r)
                            val baseFill = kotlin.math.min(c, 1.0).toFloat()
                            val goldExtra = (c - 1.0).coerceIn(0.0, 1.0).toFloat()

                            // Base background
                            drawRoundRect(
                                color = Ink.SURFACE_3,
                                topLeft = Offset(x, 0f),
                                size = Size(segW, size.height),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )

                            // Brass fill up to 100%
                            if (baseFill > 0f) {
                                drawRoundRect(
                                    color = Ink.AMBER,
                                    topLeft = Offset(x, 0f),
                                    size = Size(segW * baseFill, size.height),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                            }

                            // Gold LED top highlight line if credit > 1.0
                            if (goldExtra > 0f) {
                                drawRoundRect(
                                    color = Color(0xFFFFD08A),
                                    topLeft = Offset(x, 0f),
                                    size = Size(segW * goldExtra, 3.dp.toPx()),
                                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val allMet = scheduledDayHabits.isNotEmpty() && metCount == scheduledDayHabits.size
                        BasicText(
                            text = if (allMet) "GOLD DAY · ALL MET" else "$metCount OF ${scheduledDayHabits.size} MET",
                            style = TextStyle(color = if (allMet) Color(0xFFFFD08A) else Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            text = "+$todayEarned BRICKS TODAY",
                            style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono)
                        )
                    }
                }
            }
        }

        // 3. Practice Header
        item {
            Spacer(Modifier.height(4.dp))
            BasicText("PRACTICE", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
        }

        // 4. Daily Habit Rows (QuantityTrack)
        if (scheduledDayHabits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_1)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("NO HABITS SCHEDULED TODAY", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono))
                }
            }
        } else {
            items(scheduledDayHabits, key = { it.id }) { habit ->
                HabitQuantityRow(
                    habit = habit,
                    epochDay = selectedDay,
                    onOpenLog = { onOpenLog(habit) },
                    onEdit = { onEditHabit(habit) },
                    onQuickAdd = { delta, origin -> onQuickAdd(habit, delta, origin) },
                    onToggleDone = { origin -> onToggleDone(habit, origin) },
                    onRestDay = { onRestDay(habit) }
                )
            }
        }

        // 5. Period Habits (THIS WEEK / THIS MONTH)
        if (periodHabits.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                BasicText("THIS PERIOD", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            }
            items(periodHabits, key = { it.id }) { habit ->
                PeriodHabitRow(habit = habit, onOpenLog = { onOpenLog(habit) })
            }
        }

        // 6. Dream Steps on Today (At most 3 rows)
        val activeDreams = dreams.filter { it.status == DreamStatus.ACTIVE }
        if (activeDreams.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                BasicText("DREAM STEPS", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            }
            items(activeDreams.take(3), key = { it.id }) { dream ->
                val nextRung = dream.rungs.firstOrNull { !it.isDone }
                if (nextRung != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_1)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .clickable {
                                HabitMasterStore.toggleDreamRung(context, dream.id, nextRung.id)
                                Feedback.tick(context)
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            BasicText(dream.name, style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            Spacer(Modifier.height(2.dp))
                            BasicText("Next: ${nextRung.title}", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp))
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Ink.SURFACE_3),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("✓", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 14.sp))
                        }
                    }
                }
            }
        }

        // 7. Motivational Quote Footer
        item {
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "\"The best form of motivation is progress.\"",
                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 11.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// HABIT QUANTITY ROW (Section 3.1)
// -----------------------------------------------------------------------------

@Composable
fun HabitQuantityRow(
    habit: HabitItem,
    epochDay: Long,
    onOpenLog: () -> Unit,
    onEdit: () -> Unit,
    onQuickAdd: (Double, Offset) -> Unit,
    onToggleDone: (Offset) -> Unit,
    onRestDay: () -> Unit
) {
    val context = LocalContext.current
    var rowCenter by remember { mutableStateOf(Offset.Zero) }

    val total = HabitMasterStore.getDayTotalQuantity(context, habit.id, epochDay)
    val ratio = if (habit.target > 0.0) total / habit.target else 0.0
    val tier = ScoreEngine.tier(ratio)
    val pct = (ratio * 100).roundToInt()

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                rowCenter = Offset(p.x + coords.size.width / 2f, p.y + coords.size.height / 2f)
            }
            .clickable { onOpenLog() },
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title row: Glyph, Name, Quantity vs Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_3),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("▪", style = TextStyle(color = Ink.AMBER, fontSize = 12.sp))
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = habit.name,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }

                if (habit.measure == Measure.DONE) {
                    val isDone = total >= 1.0
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDone) Ink.SAGE else Ink.SURFACE_3)
                            .clickable { onToggleDone(rowCenter) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            BasicText("✓", style = TextStyle(color = Ink.GROUND, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                } else {
                    BasicText(
                        text = "${total.toInt()} / ${habit.target.toInt()} ${habit.unit}",
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (habit.measure != Measure.DONE) {
                Spacer(Modifier.height(8.dp))

                // Track bar: 6dp rounded bar with ticks at 100% and 200%
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Ink.SURFACE_3)
                ) {
                    // Fill up to 100% in brass
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = ratio.toFloat().coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(Ink.AMBER)
                    )

                    // Fill beyond 100% in LED gold
                    if (ratio > 1.0) {
                        val goldFrac = ((ratio - 1.0) / 1.0).toFloat().coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = goldFrac)
                                .height(6.dp)
                                .background(Color(0xFFFFD08A))
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Tier Tag & Quick-add buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = "${tier.name} $pct%",
                            style = TextStyle(
                                color = if (tier == Tier.GOLD) Color(0xFFFFD08A) else if (tier == Tier.MET || tier == Tier.STRONG) Ink.SAGE else Ink.TEXT_MUTED_V4,
                                fontSize = 9.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (ratio < 1.0) {
                            val rem = (habit.target - total).toInt()
                            Spacer(Modifier.width(6.dp))
                            BasicText(
                                text = "· $rem ${habit.unit} TO TARGET",
                                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                            )
                        }
                    }

                    // Quick-add chips (+1, +5 or +5, +15)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val steps = if (habit.measure == Measure.MINUTES) listOf(5, 15) else listOf(1, 5)
                        steps.forEach { s ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Ink.SURFACE_3)
                                    .clickable { onQuickAdd(s.toDouble(), rowCenter) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                BasicText("+$s", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 10.sp, fontFamily = Fonts.mono))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// PERIOD HABIT ROW (Section 3.3)
// -----------------------------------------------------------------------------

@Composable
fun PeriodHabitRow(habit: HabitItem, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val total = HabitMasterStore.getDayTotalQuantity(context, habit.id, HabitMasterStore.today())
    val remaining = (habit.target - total).coerceAtLeast(0.0).toInt()

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenLog() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(habit.name, style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                BasicText("${total.toInt()} / ${habit.target.toInt()} ${habit.unit}", style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono))
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Ink.SURFACE_3)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (total / habit.target).toFloat().coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(Ink.AMBER)
                )
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "$remaining TO GO · ${habit.period.name}",
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// CITY TAB CONTENT (Section 6 - City Integration with Dream Towers & Landmarks)
// -----------------------------------------------------------------------------

@Composable
private fun CityTabContent(
    lifetimeBricks: Int,
    todayEarned: Int,
    buildingsBuilt: Int,
    currentTitle: CityTitle,
    progressBricks: Int,
    nextCost: Int,
    globalStreak: Int,
    habitsCount: Int,
    doneTodayCount: Int,
    goldDaysCount: Int,
    composedSlots: List<CitySlot>,
    selectedBuilding: CitySlot?,
    onBuildingTapped: (CitySlot) -> Unit,
    onGroundTapped: () -> Unit,
    onOpenTitles: () -> Unit,
    onOpenDream: (Dream) -> Unit,
    onOpenAchievement: (Achievement) -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Compact City Header Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.SURFACE_2)
                .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onOpenTitles)
                ) {
                    BasicText(
                        currentTitle.label.uppercase(),
                        style = TextStyle(
                            fontFamily = Fonts.mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Ink.AMBER,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicText("ⓘ", style = labelStyle(Ink.TEXT_MUTED_V4, 11.sp))
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        "$buildingsBuilt structures · $lifetimeBricks bricks",
                        style = labelStyle(Ink.TEXT_MUTED_V4, 10.sp).copy(fontFamily = Fonts.mono)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (goldDaysCount > 0) {
                        BasicText(
                            "★ $goldDaysCount",
                            style = TextStyle(color = Color(0xFFFFD08A), fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                    BasicText(
                        "$progressBricks/$nextCost",
                        style = labelStyle(Ink.AMBER, 11.sp, FontWeight.Bold).copy(fontFamily = Fonts.mono)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Living City Plate (Isometric Canvas)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F1014))
                .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(20.dp))
        ) {
            CityCanvas(
                slots = composedSlots,
                onBuildingTapped = onBuildingTapped,
                onGroundTapped = onGroundTapped,
                modifier = Modifier.fillMaxSize()
            )

            // Building Inspector Card (§6.5)
            selectedBuilding?.let { slot ->
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(
                                    slot.name.uppercase(),
                                    style = labelStyle(Ink.Text, 13.sp, FontWeight.Bold)
                                )
                                if (slot.isGilded) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFE8B25C).copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        BasicText(
                                            "✦ GILDED",
                                            style = TextStyle(color = Color(0xFFFFD08A), fontSize = 8.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            val detail = when (slot.slotKind) {
                                SlotKind.DREAM_TOWER -> slot.subtitle ?: "Dream Tower"
                                SlotKind.ACHIEVEMENT_LANDMARK -> slot.subtitle ?: "Achievement Landmark"
                                SlotKind.BUILDING -> {
                                    val builtStr = if (slot.dateBuiltStr != null) " · Built ${slot.dateBuiltStr}" else ""
                                    "Archetype: ${slot.archetype.name.replace('_', ' ')} · Slot #${slot.index + 1}$builtStr"
                                }
                            }
                            BasicText(detail, style = labelStyle(Ink.TEXT_MUTED_V4, 10.sp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (slot.slotKind == SlotKind.DREAM_TOWER && slot.refId != null) {
                                TextAction(
                                    text = "OPEN",
                                    color = Ink.AMBER,
                                    onClick = {
                                        val d = HabitMasterStore.getDreams(context).firstOrNull { it.id == slot.refId }
                                        if (d != null) onOpenDream(d)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                            } else if (slot.slotKind == SlotKind.ACHIEVEMENT_LANDMARK && slot.refId != null) {
                                TextAction(
                                    text = "OPEN",
                                    color = Ink.SAGE,
                                    onClick = {
                                        val a = HabitMasterStore.getAchievements(context).firstOrNull { it.id == slot.refId }
                                        if (a != null) onOpenAchievement(a)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            TextAction("CLOSE", Ink.Muted, onGroundTapped)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Bottom Summary & Share Row (§6.5: TODAY · 3 OF 5 MET · +46 bricks)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                "TODAY · $doneTodayCount OF $habitsCount MET · +$todayEarned bricks",
                style = labelStyle(Ink.TEXT_MUTED_V4, 11.sp).copy(fontFamily = Fonts.mono)
            )
            TextAction("SHARE", Ink.Muted, onShare)
        }

        Spacer(Modifier.height(6.dp))

        BasicText(
            "\"The best form of motivation is progress.\"",
            style = labelStyle(Ink.TEXT_FAINT_V4, 10.5.sp).copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
    }
}
