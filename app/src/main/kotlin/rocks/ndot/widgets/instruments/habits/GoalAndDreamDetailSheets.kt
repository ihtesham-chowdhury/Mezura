package rocks.ndot.widgets.instruments.habits

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.kit.DotMatrixText
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.TextAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// =============================================================================
// GOAL DETAIL SHEET (Section 4.3)
// =============================================================================

@Composable
fun GoalDetailSheet(
    goal: Goal,
    todayEpoch: Long,
    onDismiss: () -> Unit,
    onToggleMilestone: (milestoneId: String) -> Unit,
    onAddProgress: (delta: Double) -> Unit,
    onAddMetricReading: (value: Double) -> Unit,
    onMarkAchieved: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val pace = remember(goal, todayEpoch) {
        ScoreEngine.calculateGoalPace(goal, todayEpoch, emptyList(), emptyList(), emptyList(), emptyList())
    }

    var showReadingInput by remember { mutableStateOf(false) }
    var readingInput by remember { mutableStateOf("") }

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .verticalScroll(scrollState)
        ) {
            // Header: Name, dates, edit, close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("GOAL", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.width(6.dp))
                        if (goal.targetEpochDay != null) {
                            val startStr = LocalDate.ofEpochDay(goal.startEpochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase()
                            val endStr = LocalDate.ofEpochDay(goal.targetEpochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase()
                            BasicText("$startStr → $endStr", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = goal.name,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction("EDIT", Ink.AMBER, onEdit)
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
            }

            if (!goal.why.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = "Why: ${goal.why}",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Pace Module (Section 4.3): Bullet bar + ahead/behind tag
            val paceTag = when (pace.status) {
                GoalPaceStatus.AHEAD -> "AHEAD ${pace.daysAhead?.roundToInt() ?: 0} DAYS"
                GoalPaceStatus.BEHIND -> "BEHIND ${abs(pace.daysAhead?.roundToInt() ?: 0)} DAYS"
                GoalPaceStatus.ON_TRACK -> "ON TRACK"
            }
            val paceColor = when (pace.status) {
                GoalPaceStatus.AHEAD -> Ink.SAGE
                GoalPaceStatus.BEHIND -> Ink.AMBER
                GoalPaceStatus.ON_TRACK -> Ink.TEXT_MUTED_V4
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_2)
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(paceTag, style = TextStyle(color = paceColor, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        BasicText("${(pace.progress * 100).roundToInt()}% COMPLETE", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 10.sp, fontFamily = Fonts.mono))
                    }

                    Spacer(Modifier.height(8.dp))

                    // Linear bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Ink.SURFACE_3)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = pace.progress.toFloat().coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(Ink.AMBER)
                        )
                    }

                    if (pace.estimatedFinishEpochDay != null) {
                        Spacer(Modifier.height(6.dp))
                        val estStr = LocalDate.ofEpochDay(pace.estimatedFinishEpochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)).uppercase()
                        BasicText(
                            text = "AT CURRENT PACE · $estStr",
                            style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Body by finish line: CHECKLIST / COUNT / METRIC
            when (goal.finish) {
                FinishLine.CHECKLIST -> {
                    BasicText("MILESTONES", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(8.dp))
                    goal.milestones.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ink.SURFACE_2)
                                .clickable { onToggleMilestone(m.id) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                text = m.title,
                                style = TextStyle(
                                    color = if (m.isDone) Ink.TEXT_MUTED_V4 else Ink.TEXT_WARM,
                                    fontSize = 13.sp,
                                    fontWeight = if (m.isDone) FontWeight.Normal else FontWeight.Medium
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (m.isDone) Ink.SAGE else Ink.SURFACE_3),
                                contentAlignment = Alignment.Center
                            ) {
                                if (m.isDone) {
                                    BasicText("✓", style = TextStyle(color = Ink.GROUND, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                FinishLine.COUNT -> {
                    BasicText("PROGRESS COUNT", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentVal = (pace.progress * (goal.countTotal ?: 100.0)).roundToInt()
                        DotMatrixText(
                            text = "$currentVal / ${(goal.countTotal ?: 100.0).toInt()}",
                            dotPitch = 2.4.dp,
                            litColor = Ink.TEXT_WARM,
                            glow = false
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1, 5, 10).forEach { delta ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Ink.SURFACE_2)
                                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                                    .clickable { onAddProgress(delta.toDouble()) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                BasicText("+$delta", style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
                FinishLine.METRIC -> {
                    BasicText("METRIC READINGS", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            BasicText("BASELINE: ${goal.metricBaseline ?: 0.0} ${goal.metricUnit ?: ""}", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                            BasicText("TARGET: ${goal.metricTarget ?: 1.0} ${goal.metricUnit ?: ""}", style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        }
                        TextAction("+ ADD READING", Ink.AMBER, { showReadingInput = true })
                    }

                    if (showReadingInput) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Ink.SURFACE_2)
                                    .padding(8.dp)
                            ) {
                                BasicTextField(
                                    value = readingInput,
                                    onValueChange = { readingInput = it },
                                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.mono),
                                    cursorBrush = SolidColor(Ink.AMBER)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            TextAction("SAVE", Ink.AMBER, {
                                readingInput.toDoubleOrNull()?.let { v ->
                                    onAddMetricReading(v)
                                    readingInput = ""
                                    showReadingInput = false
                                }
                            })
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Prominent Brass MARK ACHIEVED Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable { onMarkAchieved() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "MARK ACHIEVED",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_2)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                BasicText("DELETE GOAL", style = TextStyle(color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }
        }
    }
}

// =============================================================================
// DREAM DETAIL SHEET (Section 4.2)
// =============================================================================

@Composable
fun DreamDetailSheet(
    dream: Dream,
    habits: List<HabitItem>,
    entries: List<Entry>,
    todayEpoch: Long,
    onDismiss: () -> Unit,
    onToggleRung: (rungId: String) -> Unit,
    onMarkAchieved: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .verticalScroll(scrollState)
        ) {
            // Header: Name, horizon, edit, close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("DREAM", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.width(6.dp))
                        BasicText(dream.horizon.label(), style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    }
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = dream.name,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction("EDIT", Ink.AMBER, onEdit)
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
            }

            if (!dream.why.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                BasicText("Why: ${dream.why}", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
            }

            Spacer(Modifier.height(16.dp))

            // The Ladder: vertical timeline of rungs (Section 4.2)
            BasicText("LADDER OF RUNGS", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(8.dp))

            dream.rungs.forEachIndexed { i, rung ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink.SURFACE_2)
                        .clickable { onToggleRung(rung.id) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (rung.isDone) Ink.SAGE else Ink.SURFACE_3),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rung.isDone) {
                                BasicText("✓", style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            } else {
                                BasicText("${i + 1}", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            BasicText(
                                text = rung.title,
                                style = TextStyle(color = if (rung.isDone) Ink.TEXT_MUTED_V4 else Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            )
                            if (rung.doneEpochDay != null) {
                                val dStr = LocalDate.ofEpochDay(rung.doneEpochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                                BasicText("Reached on $dStr", style = TextStyle(color = Ink.SAGE, fontSize = 9.sp, fontFamily = Fonts.mono))
                            }
                        }
                    }

                    BasicText(
                        text = rung.kind.name,
                        style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Linked habits section
            val linkedHabits = habits.filter { it.dreamId == dream.id }
            BasicText("FEEDING HABITS (${linkedHabits.size})", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(6.dp))
            if (linkedHabits.isEmpty()) {
                BasicText("No habits linked to this dream yet.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
            } else {
                linkedHabits.forEach { h ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_2)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(h.name, style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        BasicText("${h.target.toInt()} ${h.unit} / ${h.period.name}", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Mark Dream Achieved
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable { onMarkAchieved() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "MARK DREAM ACHIEVED",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_2)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                BasicText("DELETE DREAM", style = TextStyle(color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }
        }
    }
}

// =============================================================================
// GOAL EDITOR SHEET
// =============================================================================

@Composable
fun GoalEditorSheet(
    goalToEdit: Goal?,
    todayEpoch: Long,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var name by remember { mutableStateOf(goalToEdit?.name ?: "") }
    var why by remember { mutableStateOf(goalToEdit?.why ?: "") }
    var targetDays by remember {
        mutableStateOf(
            if (goalToEdit?.targetEpochDay != null) ((goalToEdit.targetEpochDay - todayEpoch).toInt()).coerceAtLeast(1) else 90
        )
    }
    var finish by remember { mutableStateOf(goalToEdit?.finish ?: FinishLine.CHECKLIST) }
    var milestones by remember {
        mutableStateOf(
            goalToEdit?.milestones ?: listOf(
                Milestone(UUID.randomUUID().toString(), "Phase 1 Foundations", MilestoneKind.TICK),
                Milestone(UUID.randomUUID().toString(), "Phase 2 Core Project", MilestoneKind.TICK),
                Milestone(UUID.randomUUID().toString(), "Phase 3 Completion", MilestoneKind.TICK)
            )
        )
    }
    var countTotalStr by remember { mutableStateOf((goalToEdit?.countTotal ?: 100.0).toInt().toString()) }

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = if (goalToEdit == null) "NEW GOAL" else "EDIT GOAL",
                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

            Spacer(Modifier.height(14.dp))

            // Goal Title
            BasicText("GOAL NAME", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp),
                    cursorBrush = SolidColor(Ink.AMBER),
                    decorationBox = { inner ->
                        if (name.isBlank()) BasicText("e.g. Finish Course, Earn Certificate...", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 13.sp))
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // Why (Description / Purpose)
            BasicText("WHAT IS THIS GOAL ABOUT? (WHY / DESCRIPTION)", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = why,
                    onValueChange = { why = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp),
                    cursorBrush = SolidColor(Ink.AMBER),
                    decorationBox = { inner ->
                        if (why.isBlank()) BasicText("Optional motivation, context or reason", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 12.sp))
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // Target Date
            BasicText("TARGET HORIZON / DURATION", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(30 to "30 DAYS", 60 to "60 DAYS", 90 to "90 DAYS", 180 to "6 MO", 365 to "1 YR").forEach { (days, label) ->
                    val isSel = targetDays == days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.AMBER else Ink.SURFACE_2)
                            .clickable { targetDays = days }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(color = if (isSel) Ink.GROUND else Ink.TEXT_WARM, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            val targetDateFormatted = LocalDate.ofEpochDay(todayEpoch + targetDays).format(DateTimeFormatter.ofPattern("EEEE · d MMMM yyyy", Locale.ENGLISH))
            Spacer(Modifier.height(4.dp))
            BasicText("Finish by $targetDateFormatted", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))

            Spacer(Modifier.height(14.dp))

            // Milestones
            BasicText("MILESTONES (${milestones.size})", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(6.dp))
            milestones.forEachIndexed { i, m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_2)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = m.title,
                            onValueChange = { newT ->
                                milestones = milestones.mapIndexed { idx, item -> if (idx == i) item.copy(title = newT) else item }
                            },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp),
                            cursorBrush = SolidColor(Ink.AMBER),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (milestones.size > 1) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Ink.SURFACE_3)
                                .clickable {
                                    milestones = milestones.filterIndexed { idx, _ -> idx != i }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("×", style = TextStyle(color = Color(0xFFFF5252), fontSize = 16.sp))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .clickable {
                        milestones = milestones + Milestone(UUID.randomUUID().toString(), "Next Milestone", MilestoneKind.TICK)
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("+ ADD MILESTONE", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable {
                        val saved = Goal(
                            id = goalToEdit?.id ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "Goal" },
                            why = why.ifBlank { null },
                            finish = finish,
                            startEpochDay = goalToEdit?.startEpochDay ?: todayEpoch,
                            targetEpochDay = todayEpoch + targetDays,
                            milestones = milestones,
                            countTotal = countTotalStr.toDoubleOrNull() ?: 100.0
                        )
                        onSave(saved)
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText("SAVE GOAL", style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            if (goalToEdit != null && onDelete != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_2)
                        .clickable { onDelete(goalToEdit.id) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("DELETE GOAL", style = TextStyle(color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// =============================================================================
// DREAM EDITOR SHEET
// =============================================================================

@Composable
fun DreamEditorSheet(
    dreamToEdit: Dream?,
    todayEpoch: Long,
    onDismiss: () -> Unit,
    onSave: (Dream) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var name by remember { mutableStateOf(dreamToEdit?.name ?: "") }
    var why by remember { mutableStateOf(dreamToEdit?.why ?: "") }
    val currentYear = LocalDate.ofEpochDay(todayEpoch).year
    var selectedHorizonYear by remember {
        mutableStateOf(
            when (val h = dreamToEdit?.horizon) {
                is DreamHorizon.Year -> h.year
                else -> currentYear + 2
            }
        )
    }
    var rungs by remember {
        mutableStateOf(
            dreamToEdit?.rungs ?: listOf(
                Milestone(UUID.randomUUID().toString(), "Foundational Stage", MilestoneKind.TICK),
                Milestone(UUID.randomUUID().toString(), "Intermediate Breakthrough", MilestoneKind.TICK),
                Milestone(UUID.randomUUID().toString(), "Mastery & Realization", MilestoneKind.EVIDENCE)
            )
        )
    }

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = if (dreamToEdit == null) "NEW DREAM" else "EDIT DREAM",
                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

            Spacer(Modifier.height(14.dp))

            // Dream Title
            BasicText("DREAM OR LONG PROJECT NAME", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp),
                    cursorBrush = SolidColor(Ink.AMBER),
                    decorationBox = { inner ->
                        if (name.isBlank()) BasicText("e.g. Master Calligraphy, Build Startup, Write Novel", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 13.sp))
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // Why (Vision / Context)
            BasicText("WHY DOES THIS DREAM MATTER TO YOU?", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = why,
                    onValueChange = { why = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp),
                    cursorBrush = SolidColor(Ink.AMBER),
                    decorationBox = { inner ->
                        if (why.isBlank()) BasicText("Describe the meaning and vision behind this ambition", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 12.sp))
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // Target Horizon Year
            BasicText("TARGET HORIZON", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(currentYear + 1, currentYear + 2, currentYear + 3, currentYear + 5, 2035).forEach { yr ->
                    val isSel = selectedHorizonYear == yr
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.AMBER else Ink.SURFACE_2)
                            .clickable { selectedHorizonYear = yr }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "$yr",
                            style = TextStyle(color = if (isSel) Ink.GROUND else Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Rungs / Stepping Stones
            BasicText("LADDER RUNGS (${rungs.size})", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(6.dp))
            rungs.forEachIndexed { i, r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_2)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = r.title,
                            onValueChange = { newT ->
                                rungs = rungs.mapIndexed { idx, item -> if (idx == i) item.copy(title = newT) else item }
                            },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp),
                            cursorBrush = SolidColor(Ink.AMBER),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (rungs.size > 1) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Ink.SURFACE_3)
                                .clickable {
                                    rungs = rungs.filterIndexed { idx, _ -> idx != i }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("×", style = TextStyle(color = Color(0xFFFF5252), fontSize = 16.sp))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .clickable {
                        rungs = rungs + Milestone(UUID.randomUUID().toString(), "Next Milestone", MilestoneKind.TICK)
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("+ ADD RUNG", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable {
                        val saved = Dream(
                            id = dreamToEdit?.id ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "Dream" },
                            why = why.ifBlank { null },
                            horizon = DreamHorizon.Year(selectedHorizonYear),
                            landmark = dreamToEdit?.landmark ?: LandmarkStyle.ACADEMY,
                            rungs = rungs
                        )
                        onSave(saved)
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText("SAVE DREAM", style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            if (dreamToEdit != null && onDelete != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_2)
                        .clickable { onDelete(dreamToEdit.id) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("DELETE DREAM", style = TextStyle(color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// =============================================================================
// NEW ITEM WIZARD MODAL (Section 4.4)
// =============================================================================

@Composable
fun NewItemChoiceModal(
    onDismiss: () -> Unit,
    onChooseHabit: () -> Unit,
    onChooseGoal: () -> Unit,
    onChooseDream: () -> Unit
) {
    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            BasicText("CREATE NEW", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText("Choose what shape your effort will take.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp))

            Spacer(Modifier.height(16.dp))

            // 1. Habit Choice
            ChoiceCard(
                title = "HABIT",
                subtitle = "Something you repeat (read 20 pages, study 15 minutes)",
                glyph = "▪",
                onClick = onChooseHabit
            )
            Spacer(Modifier.height(10.dp))

            // 2. Goal Choice
            ChoiceCard(
                title = "GOAL",
                subtitle = "Something with a finish line (finish course, IELTS band 8)",
                glyph = "◆",
                onClick = onChooseGoal
            )
            Spacer(Modifier.height(10.dp))

            // 3. Dream Choice
            ChoiceCard(
                title = "DREAM",
                subtitle = "A long direction made of rungs and habits (calligraphy, DeepMind)",
                glyph = "★",
                onClick = onChooseDream
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    glyph: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.SURFACE_2)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Ink.SURFACE_3),
            contentAlignment = Alignment.Center
        ) {
            BasicText(glyph, style = TextStyle(color = Ink.AMBER, fontSize = 16.sp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            BasicText(title, style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.mono))
            Spacer(Modifier.height(2.dp))
            BasicText(subtitle, style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
        }
    }
}
