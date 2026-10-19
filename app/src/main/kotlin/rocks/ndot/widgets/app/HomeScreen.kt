package rocks.ndot.widgets.app

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.instruments.sequence.ActiveSequenceRun
import rocks.ndot.widgets.instruments.sequence.Routine
import rocks.ndot.widgets.instruments.sequence.SequenceEngine
import rocks.ndot.widgets.instruments.sequence.SequenceState
import rocks.ndot.widgets.instruments.sequence.SequenceStep
import rocks.ndot.widgets.instruments.trace.ActivityType
import rocks.ndot.widgets.instruments.trace.TraceLedger
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.GlassPanel
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.TextAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import rocks.ndot.widgets.kit.NdotSheet
import java.util.UUID

/**
 * Home Hub: Sequence + Trace (Part II).
 *
 * Sequence is the action layer; Trace is the evidence layer.
 * Absolutely NO duplicate timer on Home. The active step navigates
 * to the real instrument where the authoritative clock is displayed.
 */
@Composable
fun HomeScreen(
    onOpen: (Tool) -> Unit,
    onSettings: () -> Unit,
    onViewTrace: () -> Unit = {}
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now().toEpochDay() }
    val dateStr = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.ENGLISH)).uppercase()
    }

    var activeRun by remember { mutableStateOf(SequenceEngine.getActiveRun(context)) }
    var routines by remember { mutableStateOf(SequenceEngine.getRoutines(context)) }
    var selectedRoutine by remember { mutableStateOf(SequenceEngine.getSelectedRoutine(context)) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }
    var showNewRoutineSheet by remember { mutableStateOf(false) }

    // Trace snapshot data
    val traceSummary = remember(today) { TraceLedger.getDaySummary(context, today) }

    LightChrome(false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(14.dp))

        // 1. Compact Header: Mezura wordmark, date, settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                BasicText(
                    text = "MEZURA",
                    style = TextStyle(
                        color = Ink.TEXT_WARM,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = dateStr,
                    style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Ink.SURFACE_2)
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                BasicText("⚙", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 15.sp))
            }
        }

        Spacer(Modifier.height(18.dp))

        // 2. Main Routine Module (The Visual Hero)
        if (activeRun != null && activeRun?.state != SequenceState.IDLE) {
            val run = activeRun!!
            if (run.isComplete || run.state == SequenceState.COMPLETED) {
                // Completed State
                SequenceCompletedCard(
                    run = run,
                    onDone = {
                        SequenceEngine.dismissCompletedSequence(context)
                        activeRun = null
                    },
                    onRunAgain = {
                        val restarted = SequenceEngine.startSequence(context, selectedRoutine)
                        activeRun = restarted
                        restarted.currentStep?.let { onOpen(it.tool) }
                    }
                )
            } else {
                // Active Sequence State (NO duplicate timer!)
                ActiveSequenceCard(
                    run = run,
                    onReturnToCurrent = {
                        run.currentStep?.let { onOpen(it.tool) }
                    },
                    onPause = {
                        SequenceEngine.pauseSequence(context)
                        activeRun = SequenceEngine.getActiveRun(context)
                    },
                    onResume = {
                        SequenceEngine.resumeSequence(context)
                        activeRun = SequenceEngine.getActiveRun(context)
                        run.currentStep?.let { onOpen(it.tool) }
                    },
                    onSkip = {
                        val next = SequenceEngine.skipCurrentStep(context)
                        activeRun = SequenceEngine.getActiveRun(context)
                        if (next != null) onOpen(next.tool)
                    },
                    onStop = {
                        SequenceEngine.stopSequence(context)
                        activeRun = null
                    }
                )
            }
        } else {
            // Resting State: Hero Routine Card
            RestingHeroRoutineCard(
                routine = selectedRoutine,
                onStart = {
                    val run = SequenceEngine.startSequence(context, selectedRoutine)
                    activeRun = run
                    Feedback.lock(context)
                    // Launch first real instrument immediately
                    run.currentStep?.let { step ->
                        onOpen(step.tool)
                    }
                },
                onEdit = {
                    editingRoutine = selectedRoutine
                }
            )
        }

        Spacer(Modifier.height(18.dp))

        // 3. Routine Selector (Section 3.1 C)
        if (activeRun == null || activeRun?.state == SequenceState.IDLE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("SELECT ROUTINE", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                TextAction("+ NEW ROUTINE", Ink.AMBER, { showNewRoutineSheet = true })
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                routines.forEach { r ->
                    val isSel = selectedRoutine.id == r.id
                    Box(
                        modifier = Modifier
                            .width(105.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) Ink.SURFACE_3 else Ink.SURFACE_1)
                            .border(1.dp, if (isSel) Ink.AMBER else Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .clickable {
                                selectedRoutine = r
                                SequenceEngine.selectRoutine(r.id)
                                Feedback.tick(context)
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(
                                text = r.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = if (isSel) Ink.AMBER else Ink.TEXT_WARM,
                                    fontSize = 11.sp,
                                    fontFamily = Fonts.mono,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            BasicText(
                                text = "${r.estimatedTotalMinutes}m",
                                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                            )
                        }
                    }
                }

                // + NEW routine chip
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_1)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                        .clickable {
                            showNewRoutineSheet = true
                            Feedback.tick(context)
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            text = "+ NEW",
                            style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = "CUSTOM",
                            style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }

        // 4. Compact Trace Snapshot (Section 3.1 D)
        CompactTraceSnapshot(
            summary = traceSummary,
            onViewTrace = onViewTrace
        )

        // 5. Practice status line (Part 2 Section 7.3)
        val activeHabits = remember(today) { rocks.ndot.widgets.instruments.habits.HabitMasterStore.getAllHabits(context).filter { !it.isArchived } }
        val scheduledToday = remember(activeHabits, today) {
            val isoDow = java.time.LocalDate.ofEpochDay(today).dayOfWeek.value
            activeHabits.filter { it.period == rocks.ndot.widgets.instruments.habits.Period.DAY && it.daysOfWeek.contains(isoDow) }
        }
        if (scheduledToday.isNotEmpty()) {
            val entries = remember(today) { rocks.ndot.widgets.instruments.habits.HabitMasterStore.getEntries(context).filter { it.epochDay == today } }
            val metCount = scheduledToday.count { h ->
                val q = entries.filter { it.habitId == h.id }.sumOf { it.quantity }
                q >= h.target
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(Tool.HABITS) },
                horizontalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = "PRACTICE · $metCount OF ${scheduledToday.size} MET",
                    style = TextStyle(
                        color = Ink.TEXT_FAINT_V4,
                        fontSize = 11.sp,
                        fontFamily = Fonts.mono
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        NavSpacer()
    }

    if (editingRoutine != null) {
        RoutineEditorSheet(
            routine = editingRoutine,
            onDismiss = { editingRoutine = null },
            onSave = { updated ->
                SequenceEngine.saveRoutine(context, updated)
                routines = SequenceEngine.getRoutines(context)
                selectedRoutine = updated
                editingRoutine = null
                Feedback.lock(context)
            },
            onDelete = { delId ->
                SequenceEngine.deleteRoutine(context, delId)
                routines = SequenceEngine.getRoutines(context)
                selectedRoutine = SequenceEngine.getSelectedRoutine(context)
                editingRoutine = null
                Feedback.tick(context)
            }
        )
    }

    if (showNewRoutineSheet) {
        RoutineEditorSheet(
            routine = null,
            onDismiss = { showNewRoutineSheet = false },
            onSave = { newR ->
                SequenceEngine.saveRoutine(context, newR)
                routines = SequenceEngine.getRoutines(context)
                selectedRoutine = newR
                showNewRoutineSheet = false
                Feedback.lock(context)
            }
        )
    }
}

// =============================================================================
// HERO MODULE: RESTING STATE (Section 3.1 B)
// =============================================================================

@Composable
private fun RestingHeroRoutineCard(
    routine: Routine,
    onStart: () -> Unit,
    onEdit: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("YOUR ROUTINE", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                    Spacer(Modifier.width(8.dp))
                    TextAction("EDIT", Ink.AMBER, onEdit)
                }
                BasicText(
                    text = "${routine.stepCount} STEPS · ABOUT ${routine.estimatedTotalMinutes} MIN",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                )
            }

            Spacer(Modifier.height(6.dp))

            BasicText(
                text = routine.name,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(2.dp))

            BasicText(
                text = routine.descriptor,
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp)
            )

            Spacer(Modifier.height(16.dp))

            // Step Rail: connected small instrument nodes with labels
            StepRailView(steps = routine.steps, activeIndex = -1)

            Spacer(Modifier.height(18.dp))

            // Primary START SEQUENCE Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable { onStart() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "START SEQUENCE",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )
            }
        }
    }
}

@Composable
fun RoutineEditorSheet(
    routine: Routine?,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var name by remember { mutableStateOf(routine?.name ?: "Study & Break") }
    var descriptor by remember { mutableStateOf(routine?.descriptor ?: "") }
    var steps by remember {
        mutableStateOf(
            routine?.steps ?: listOf(
                SequenceStep(tool = Tool.POMODORO, title = "Study Block", durationMinutes = 45, variant = "pocket", descriptor = "Focus"),
                SequenceStep(tool = Tool.BREATHING, title = "Breathing Reset", durationMinutes = 5, variant = "sigh", descriptor = "Cyclic Sigh"),
                SequenceStep(tool = Tool.HOURGLASS, title = "Rest Break", durationMinutes = 5, variant = "dot", descriptor = "Recovery")
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    BasicText(
                        text = if (routine == null) "NEW ROUTINE" else "EDIT ROUTINE",
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = "${steps.size} steps · ${steps.sumOf { it.durationMinutes }}m total",
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono)
                    )
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

            // Routine Name
            BasicText("ROUTINE NAME", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // Steps Section
            BasicText("STEPS IN ORDER", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(6.dp))

            steps.forEachIndexed { index, step ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_2)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText("STEP ${index + 1}", style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                            if (steps.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Ink.SURFACE_3)
                                        .clickable {
                                            steps = steps.filterIndexed { i, _ -> i != index }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText("×", style = TextStyle(color = Color(0xFFFF5252), fontSize = 14.sp))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Title
                        BasicTextField(
                            value = step.title,
                            onValueChange = { newTitle ->
                                steps = steps.mapIndexed { i, s -> if (i == index) s.copy(title = newTitle) else s }
                            },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(Ink.AMBER),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Ink.SURFACE_3)
                                .padding(8.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Activity / Tool selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tools = listOf(
                                Tool.POMODORO to "Focus",
                                Tool.BREATHING to "Breathe",
                                Tool.HOURGLASS to "Break",
                                Tool.FOLIO to "Read"
                            )
                            tools.forEach { (t, tLabel) ->
                                val isSel = step.tool == t
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) Ink.AMBER else Ink.SURFACE_3)
                                        .clickable {
                                            steps = steps.mapIndexed { i, s -> if (i == index) s.copy(tool = t) else s }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = tLabel,
                                        style = TextStyle(
                                            color = if (isSel) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                            fontSize = 10.sp,
                                            fontFamily = Fonts.mono,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Duration selector chips
                        BasicText("DURATION", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(2, 5, 10, 15, 25, 45, 60).forEach { dur ->
                                val isDurSel = step.durationMinutes == dur
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDurSel) Ink.AMBER.copy(alpha = 0.3f) else Ink.SURFACE_3)
                                        .border(1.dp, if (isDurSel) Ink.AMBER else Color.Transparent, RoundedCornerShape(6.dp))
                                        .clickable {
                                            steps = steps.mapIndexed { i, s -> if (i == index) s.copy(durationMinutes = dur) else s }
                                        }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = "${dur}m",
                                        style = TextStyle(
                                            color = if (isDurSel) Ink.AMBER else Ink.TEXT_WARM,
                                            fontSize = 10.sp,
                                            fontFamily = Fonts.mono,
                                            fontWeight = if (isDurSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // + ADD STEP
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .clickable {
                        steps = steps + SequenceStep(
                            tool = Tool.HOURGLASS,
                            title = "Break",
                            durationMinutes = 5,
                            variant = "dot",
                            descriptor = "Rest"
                        )
                    }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("+ ADD STEP", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            Spacer(Modifier.height(20.dp))

            // SAVE ROUTINE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable {
                        val finalDescriptor = if (descriptor.isNotBlank()) descriptor else steps.joinToString(" · ") { "${it.title} ${it.durationMinutes}m" }
                        val finalRoutine = Routine(
                            id = routine?.id ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "Routine" },
                            descriptor = finalDescriptor,
                            steps = steps,
                            isCustom = true
                        )
                        onSave(finalRoutine)
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText("SAVE ROUTINE", style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            if (routine != null && onDelete != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_2)
                        .clickable { onDelete(routine.id) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("DELETE ROUTINE", style = TextStyle(color = Color(0xFFFF5252), fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// =============================================================================
// HERO MODULE: ACTIVE STATE (Section 3.2 - NO DUPLICATE TIMER)
// =============================================================================

@Composable
private fun ActiveSequenceCard(
    run: ActiveSequenceRun,
    onReturnToCurrent: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit
) {
    val step = run.currentStep
    val isPaused = run.state == SequenceState.PAUSED

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = if (isPaused) "SEQUENCE PAUSED" else "SEQUENCE IN PROGRESS",
                    style = TextStyle(color = if (isPaused) Ink.AMBER else Ink.SAGE, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
                BasicText(
                    text = "STEP ${run.completedStepCount + 1} OF ${run.steps.size}",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                )
            }

            Spacer(Modifier.height(6.dp))

            BasicText(
                text = run.routineName,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(2.dp))

            BasicText(
                text = "${step?.tool?.label ?: "Tool"} · ${step?.title ?: "Step"}",
                style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.SemiBold)
            )

            Spacer(Modifier.height(14.dp))

            // Step Rail with current active node
            StepRailView(steps = run.steps, activeIndex = run.currentStepIndex)

            Spacer(Modifier.height(18.dp))

            // Primary Button: RETURN TO CURRENT STEP (Navigates to real instrument!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable { onReturnToCurrent() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "RETURN TO CURRENT STEP",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Secondary Controls: Pause/Resume, Skip, Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextAction(if (isPaused) "RESUME" else "PAUSE", Ink.TEXT_WARM, { if (isPaused) onResume() else onPause() })
                TextAction("SKIP STEP", Ink.TEXT_WARM, onSkip)
                TextAction("STOP", Color(0xFFFF6B6B), onStop)
            }
        }
    }
}

// =============================================================================
// HERO MODULE: COMPLETED STATE
// =============================================================================

@Composable
private fun SequenceCompletedCard(
    run: ActiveSequenceRun,
    onDone: () -> Unit,
    onRunAgain: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText("SEQUENCE COMPLETE", style = TextStyle(color = Ink.SAGE, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            BasicText(run.routineName, style = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText("All ${run.steps.size} steps completed and recorded to Trace.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp))

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_3)
                        .clickable { onRunAgain() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("RUN AGAIN", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = Fonts.mono))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.AMBER)
                        .clickable { onDone() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("DONE", style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// =============================================================================
// STEP RAIL VIEW
// =============================================================================

@Composable
private fun StepRailView(steps: List<SequenceStep>, activeIndex: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { i, s ->
                val isDone = activeIndex >= 0 && i < activeIndex
                val isCurrent = i == activeIndex

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Node dot
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDone -> Ink.SAGE
                                    isCurrent -> Ink.AMBER
                                    else -> Ink.SURFACE_3
                                }
                            )
                    )

                    // Connecting line between steps
                    if (i < steps.size - 1) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (isDone) Ink.SAGE else Ink.SURFACE_3)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Step titles below nodes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            steps.forEachIndexed { i, s ->
                val isCurrent = i == activeIndex
                BasicText(
                    text = s.title.take(10),
                    style = TextStyle(
                        color = if (isCurrent) Ink.AMBER else Ink.TEXT_FAINT_V4,
                        fontSize = 9.sp,
                        fontFamily = Fonts.mono,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// =============================================================================
// COMPACT TRACE SNAPSHOT (Section 3.1 D)
// =============================================================================

@Composable
private fun CompactTraceSnapshot(
    summary: TraceLedger.DaySummary,
    onViewTrace: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("TODAY IN TRACE", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                TextAction("VIEW TRACE", Ink.AMBER, onViewTrace)
            }

            Spacer(Modifier.height(10.dp))

            // Segmented Activity Strip
            if (summary.totalActiveDurationSeconds > 0) {
                Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                    val totalW = size.width
                    var currentX = 0f
                    ActivityType.entries.forEach { type ->
                        val dur = summary.durationByType[type] ?: 0L
                        if (dur > 0) {
                            val segW = (dur.toFloat() / summary.totalActiveDurationSeconds.toFloat()) * totalW
                            drawRoundRect(
                                color = type.color,
                                topLeft = Offset(currentX, 0f),
                                size = Size(segW, size.height),
                                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                            )
                            currentX += segW
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Compact real metrics
                val focusMins = summary.focusDurationSeconds / 60
                val focusFormatted = if (focusMins >= 60) "${focusMins / 60}h ${focusMins % 60}m" else "${focusMins}m"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicText(
                        text = "Focus $focusFormatted · ${summary.totalSessionsCount} sessions",
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono)
                    )
                }
            } else {
                BasicText(
                    text = "Your activity will appear here as you practice.",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp)
                )
            }
        }
    }
}
