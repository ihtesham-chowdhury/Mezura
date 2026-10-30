package rocks.ndot.widgets.instruments.sequence

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import rocks.ndot.widgets.app.Tool
import rocks.ndot.widgets.instruments.trace.ActivityType
import rocks.ndot.widgets.instruments.trace.SessionOutcome
import rocks.ndot.widgets.instruments.trace.TraceEvent
import rocks.ndot.widgets.instruments.trace.TraceLedger
import java.io.File
import java.util.UUID

enum class SequenceState {
    IDLE,
    RUNNING_STEP,
    PAUSED,
    AWAITING_USER_LOG,
    COMPLETED,
    STOPPED
}

data class SequenceStep(
    val id: String = UUID.randomUUID().toString(),
    val tool: Tool,
    val title: String,
    val durationMinutes: Int,
    val variant: String? = null, // e.g. "pocket", "module", "classic", "cyclic_sigh"
    val descriptor: String? = null
)

data class Routine(
    val id: String,
    val name: String,
    val descriptor: String,
    val steps: List<SequenceStep>,
    val isCustom: Boolean = false
) {
    val estimatedTotalMinutes: Int get() = steps.sumOf { it.durationMinutes }
    val stepCount: Int get() = steps.size
}

data class ActiveSequenceRun(
    val runId: String = UUID.randomUUID().toString(),
    val routineId: String,
    val routineName: String,
    val steps: List<SequenceStep>,
    var currentStepIndex: Int = 0,
    var state: SequenceState = SequenceState.IDLE,
    val startedAtMillis: Long = System.currentTimeMillis(),
    var currentStepStartedAtMillis: Long = System.currentTimeMillis()
) {
    val currentStep: SequenceStep? get() = steps.getOrNull(currentStepIndex)
    val isComplete: Boolean get() = currentStepIndex >= steps.size
    val completedStepCount: Int get() = currentStepIndex.coerceAtMost(steps.size)
}

object SequenceEngine {

    private const val PREFS = "sequence_prefs"
    private const val KEY_ACTIVE_RUN = "active_run_json"
    private const val KEY_ROUTINES = "saved_routines_json"

    val DEFAULT_ROUTINES = listOf(
        Routine(
            id = "deep_work",
            name = "Deep Work",
            descriptor = "Breathe · Focus · Recover",
            steps = listOf(
                SequenceStep(tool = Tool.BREATHING, title = "Breathing Reset", durationMinutes = 3, variant = "sigh", descriptor = "Cyclic Sigh"),
                SequenceStep(tool = Tool.POMODORO, title = "Focus Block", durationMinutes = 45, variant = "pocket", descriptor = "Pocket Timer"),
                SequenceStep(tool = Tool.HOURGLASS, title = "Recovery Rest", durationMinutes = 5, variant = "dot", descriptor = "Hourglass")
            )
        ),
        Routine(
            id = "study",
            name = "Study",
            descriptor = "Study 45m · Breathe 5m · Break 5m",
            steps = listOf(
                SequenceStep(tool = Tool.POMODORO, title = "Study Block", durationMinutes = 45, variant = "pocket", descriptor = "Focus"),
                SequenceStep(tool = Tool.BREATHING, title = "Breathing Reset", durationMinutes = 5, variant = "sigh", descriptor = "Cyclic Sigh"),
                SequenceStep(tool = Tool.HOURGLASS, title = "Rest Break", durationMinutes = 5, variant = "dot", descriptor = "Recovery")
            )
        ),
        Routine(
            id = "reading",
            name = "Reading",
            descriptor = "Immerse · Log · Recover",
            steps = listOf(
                SequenceStep(tool = Tool.FOLIO, title = "Reading Time", durationMinutes = 25, descriptor = "Active Book"),
                SequenceStep(tool = Tool.HOURGLASS, title = "Digest Rest", durationMinutes = 5, variant = "dot", descriptor = "Recovery")
            )
        ),
        Routine(
            id = "reset",
            name = "Reset",
            descriptor = "Breathe · Ground",
            steps = listOf(
                SequenceStep(tool = Tool.BREATHING, title = "Deep Breathing", durationMinutes = 5, variant = "sigh", descriptor = "Cyclic Sigh"),
                SequenceStep(tool = Tool.HOURGLASS, title = "Quiet Pause", durationMinutes = 5, variant = "dot", descriptor = "Rest")
            )
        )
    )

    private var activeRun: ActiveSequenceRun? = null
    private var selectedRoutineId: String = "deep_work"
    private var cachedRoutines: List<Routine>? = null

    var onStepAdvanced: ((SequenceStep?) -> Unit)? = null

    fun prepareAndStartStep(context: Context, step: SequenceStep) {
        when (step.tool) {
            Tool.POMODORO -> {
                val isBreak = step.title.contains("break", ignoreCase = true) ||
                              step.title.contains("rest", ignoreCase = true) ||
                              step.descriptor?.contains("break", ignoreCase = true) == true ||
                              step.descriptor?.contains("rest", ignoreCase = true) == true ||
                              step.variant == "break"
                if (isBreak) {
                    rocks.ndot.widgets.widgets.TimerEngine.setBreakMinutes(context, step.durationMinutes)
                    rocks.ndot.widgets.widgets.TimerEngine.switchTo(context, rocks.ndot.widgets.widgets.TimerEngine.Phase.BREAK)
                } else {
                    rocks.ndot.widgets.widgets.TimerEngine.setFocusMinutes(context, step.durationMinutes)
                    rocks.ndot.widgets.widgets.TimerEngine.switchTo(context, rocks.ndot.widgets.widgets.TimerEngine.Phase.FOCUS)
                }
                rocks.ndot.widgets.widgets.TimerEngine.start(context)
            }
            Tool.BREATHING -> {
                val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                sp.edit().putLong("active_step_breath_sec", step.durationMinutes * 60L).apply()
            }
            Tool.HOURGLASS -> {
                rocks.ndot.widgets.widgets.HourglassEngine.start(context, step.durationMinutes * 60_000L)
            }
            else -> Unit
        }
    }

    fun getRoutines(context: Context): List<Routine> {
        if (cachedRoutines != null) return cachedRoutines!!
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_ROUTINES, null)
        if (json.isNullOrBlank()) {
            cachedRoutines = DEFAULT_ROUTINES
            persistRoutines(context, DEFAULT_ROUTINES)
            return DEFAULT_ROUTINES
        }
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Routine>()
            for (i in 0 until arr.length()) {
                val ro = arr.getJSONObject(i)
                val sArr = ro.getJSONArray("steps")
                val steps = mutableListOf<SequenceStep>()
                for (j in 0 until sArr.length()) {
                    val so = sArr.getJSONObject(j)
                    steps.add(
                        SequenceStep(
                            id = so.getString("id"),
                            tool = Tool.byId(so.getString("tool")) ?: Tool.POMODORO,
                            title = so.getString("title"),
                            durationMinutes = so.getInt("durationMinutes"),
                            variant = so.optString("variant").takeIf { it.isNotBlank() },
                            descriptor = so.optString("descriptor").takeIf { it.isNotBlank() }
                        )
                    )
                }
                list.add(
                    Routine(
                        id = ro.getString("id"),
                        name = ro.getString("name"),
                        descriptor = ro.optString("descriptor", ""),
                        steps = steps,
                        isCustom = ro.optBoolean("isCustom", false)
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_ROUTINES else list
        } catch (_: Exception) {
            DEFAULT_ROUTINES
        }.also { cachedRoutines = it }
    }

    private fun persistRoutines(context: Context, routines: List<Routine>) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray()
        routines.forEach { r ->
            val ro = JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("descriptor", r.descriptor)
                put("isCustom", r.isCustom)
                val sArr = JSONArray()
                r.steps.forEach { s ->
                    sArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("tool", s.tool.id)
                        put("title", s.title)
                        put("durationMinutes", s.durationMinutes)
                        put("variant", s.variant)
                        put("descriptor", s.descriptor)
                    })
                }
                put("steps", sArr)
            }
            arr.put(ro)
        }
        sp.edit().putString(KEY_ROUTINES, arr.toString()).apply()
        cachedRoutines = routines
    }

    fun saveRoutine(context: Context, routine: Routine) {
        val current = getRoutines(context).toMutableList()
        val idx = current.indexOfFirst { it.id == routine.id }
        if (idx >= 0) {
            current[idx] = routine
        } else {
            current.add(routine)
        }
        persistRoutines(context, current)
        selectedRoutineId = routine.id
    }

    fun deleteRoutine(context: Context, routineId: String) {
        val current = getRoutines(context).filter { it.id != routineId }
        val finalRoutines = if (current.isEmpty()) DEFAULT_ROUTINES else current
        persistRoutines(context, finalRoutines)
        if (selectedRoutineId == routineId) {
            selectedRoutineId = finalRoutines.first().id
        }
    }

    fun getSelectedRoutine(context: Context? = null): Routine {
        val list = if (context != null) getRoutines(context) else (cachedRoutines ?: DEFAULT_ROUTINES)
        return list.firstOrNull { it.id == selectedRoutineId } ?: list.first()
    }

    fun selectRoutine(id: String) {
        selectedRoutineId = id
    }

    fun getActiveRun(context: Context): ActiveSequenceRun? {
        if (activeRun == null) {
            loadActiveRun(context)
        }
        return activeRun
    }

    private fun loadActiveRun(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_ACTIVE_RUN, null) ?: return
        try {
            val o = JSONObject(json)
            val stepsArr = o.getJSONArray("steps")
            val steps = mutableListOf<SequenceStep>()
            for (i in 0 until stepsArr.length()) {
                val so = stepsArr.getJSONObject(i)
                steps.add(
                    SequenceStep(
                        id = so.getString("id"),
                        tool = Tool.byId(so.getString("tool")) ?: Tool.POMODORO,
                        title = so.getString("title"),
                        durationMinutes = so.getInt("durationMinutes"),
                        variant = so.optString("variant").takeIf { it.isNotBlank() },
                        descriptor = so.optString("descriptor").takeIf { it.isNotBlank() }
                    )
                )
            }
            activeRun = ActiveSequenceRun(
                runId = o.getString("runId"),
                routineId = o.getString("routineId"),
                routineName = o.getString("routineName"),
                steps = steps,
                currentStepIndex = o.getInt("currentStepIndex"),
                state = runCatching { SequenceState.valueOf(o.getString("state")) }.getOrDefault(SequenceState.IDLE),
                startedAtMillis = o.getLong("startedAtMillis"),
                currentStepStartedAtMillis = o.getLong("currentStepStartedAtMillis")
            )
        } catch (_: Exception) {
            activeRun = null
        }
    }

    private fun persistActiveRun(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val run = activeRun
        if (run == null) {
            sp.edit().remove(KEY_ACTIVE_RUN).apply()
            return
        }
        val o = JSONObject().apply {
            put("runId", run.runId)
            put("routineId", run.routineId)
            put("routineName", run.routineName)
            put("currentStepIndex", run.currentStepIndex)
            put("state", run.state.name)
            put("startedAtMillis", run.startedAtMillis)
            put("currentStepStartedAtMillis", run.currentStepStartedAtMillis)

            val sArr = JSONArray()
            run.steps.forEach { s ->
                sArr.put(JSONObject().apply {
                    put("id", s.id)
                    put("tool", s.tool.id)
                    put("title", s.title)
                    put("durationMinutes", s.durationMinutes)
                    put("variant", s.variant)
                    put("descriptor", s.descriptor)
                })
            }
            put("steps", sArr)
        }
        sp.edit().putString(KEY_ACTIVE_RUN, o.toString()).apply()
    }

    fun startSequence(context: Context, routine: Routine): ActiveSequenceRun {
        val run = ActiveSequenceRun(
            routineId = routine.id,
            routineName = routine.name,
            steps = routine.steps,
            currentStepIndex = 0,
            state = SequenceState.RUNNING_STEP,
            startedAtMillis = System.currentTimeMillis(),
            currentStepStartedAtMillis = System.currentTimeMillis()
        )
        activeRun = run
        persistActiveRun(context)
        run.currentStep?.let { firstStep ->
            prepareAndStartStep(context, firstStep)
        }
        return run
    }

    /**
     * Called when the current instrument finishes naturally or user completes it.
     * Records one TraceEvent into TraceLedger and advances to next step.
     */
    fun onStepCompleted(context: Context, outcome: SessionOutcome = SessionOutcome.COMPLETED, metadata: String? = null): SequenceStep? {
        val run = activeRun ?: return null
        val currentStep = run.currentStep

        if (currentStep != null) {
            val endedAt = System.currentTimeMillis()
            val durSecs = (endedAt - run.currentStepStartedAtMillis).coerceAtLeast(0) / 1000L

            val actType = when (currentStep.tool) {
                Tool.POMODORO -> ActivityType.FOCUS
                Tool.FOLIO -> ActivityType.READING
                Tool.BREATHING -> ActivityType.BREATHING
                Tool.HOURGLASS -> ActivityType.BREAK
                Tool.METRONOME -> ActivityType.METRONOME
                else -> ActivityType.FOCUS
            }

            // Record into TraceLedger
            TraceLedger.recordEvent(
                context,
                TraceEvent(
                    eventId = "seq_${run.runId}_${run.currentStepIndex}",
                    activityType = actType,
                    sourceInstrument = currentStep.tool.id,
                    title = currentStep.title,
                    startedAtMillis = run.currentStepStartedAtMillis,
                    endedAtMillis = endedAt,
                    durationSeconds = if (durSecs > 10L) durSecs else (currentStep.durationMinutes * 60L),
                    outcome = outcome,
                    sequenceRunId = run.runId,
                    sequenceName = run.routineName,
                    metadata = metadata ?: currentStep.descriptor
                )
            )
        }

        // Advance step
        run.currentStepIndex++
        run.currentStepStartedAtMillis = System.currentTimeMillis()

        if (run.isComplete) {
            run.state = SequenceState.COMPLETED
            persistActiveRun(context)
            onStepAdvanced?.invoke(null)
            return null
        } else {
            run.state = SequenceState.RUNNING_STEP
            persistActiveRun(context)
            val nextStep = run.currentStep
            if (nextStep != null) {
                prepareAndStartStep(context, nextStep)
            }
            onStepAdvanced?.invoke(nextStep)
            return nextStep
        }
    }

    fun pauseSequence(context: Context) {
        val run = activeRun ?: return
        run.state = SequenceState.PAUSED
        persistActiveRun(context)
    }

    fun resumeSequence(context: Context) {
        val run = activeRun ?: return
        run.state = SequenceState.RUNNING_STEP
        persistActiveRun(context)
    }

    fun skipCurrentStep(context: Context): SequenceStep? {
        return onStepCompleted(context, outcome = SessionOutcome.SKIPPED, metadata = "Skipped step")
    }

    fun stopSequence(context: Context) {
        val run = activeRun ?: return
        val currentStep = run.currentStep
        if (currentStep != null) {
            val endedAt = System.currentTimeMillis()
            val durSecs = (endedAt - run.currentStepStartedAtMillis).coerceAtLeast(0) / 1000L
            if (durSecs > 30L) {
                val actType = when (currentStep.tool) {
                    Tool.POMODORO -> ActivityType.FOCUS
                    Tool.FOLIO -> ActivityType.READING
                    Tool.BREATHING -> ActivityType.BREATHING
                    Tool.HOURGLASS -> ActivityType.BREAK
                    Tool.METRONOME -> ActivityType.METRONOME
                    else -> ActivityType.FOCUS
                }
                TraceLedger.recordEvent(
                    context,
                    TraceEvent(
                        eventId = "seq_${run.runId}_${run.currentStepIndex}",
                        activityType = actType,
                        sourceInstrument = currentStep.tool.id,
                        title = currentStep.title,
                        startedAtMillis = run.currentStepStartedAtMillis,
                        endedAtMillis = endedAt,
                        durationSeconds = durSecs,
                        outcome = SessionOutcome.INTERRUPTED,
                        sequenceRunId = run.runId,
                        sequenceName = run.routineName,
                        metadata = "Stopped early"
                    )
                )
            }
        }
        activeRun = null
        persistActiveRun(context)
    }

    fun dismissCompletedSequence(context: Context) {
        activeRun = null
        persistActiveRun(context)
    }
}
