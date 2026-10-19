package rocks.ndot.widgets.instruments.habits

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =============================================================================
// ENUMS & TYPES (Section 2.1)
// =============================================================================

enum class Measure {
    DONE,       // tick mark; target = 1.0
    COUNT,      // pages, words, duas, items
    MINUTES     // time in minutes
}

enum class Period {
    DAY,        // measured per local day
    WEEK,       // measured per local week
    MONTH       // measured per calendar month
}

enum class Weight(val factor: Double) {
    LIGHT(0.7),
    STANDARD(1.0),
    CORE(1.5)
}

enum class AutoSource {
    NONE,
    FOLIO_PAGES,
    FOLIO_MINUTES,
    FOLIO_BOOKS_COMPLETED,
    FOCUS_MINUTES,
    BREATHING_MINUTES,
    METRONOME_MINUTES
}

enum class EntryKind {
    LOG,
    REST        // rest day: neutral, doesn't break streaks
}

enum class FinishLine {
    CHECKLIST,  // list of milestone steps
    COUNT,      // reach a numerical total (e.g. 100 duas)
    METRIC      // reach a target score/reading (e.g. IELTS 8.0)
}

enum class GoalStatus {
    ACTIVE,
    PAUSED,
    ACHIEVED,
    ARCHIVED
}

enum class DreamStatus {
    ACTIVE,
    ACHIEVED,
    ARCHIVED
}

sealed class DreamHorizon {
    object Someday : DreamHorizon()
    data class Year(val year: Int) : DreamHorizon()
    data class Date(val epochDay: Long) : DreamHorizon()

    fun label(): String = when (this) {
        is Someday -> "SOMEDAY"
        is Year -> "$year"
        is Date -> "DATE"
    }
}

enum class MilestoneKind {
    TICK,
    COUNT_AT,
    METRIC_AT,
    GOAL,
    EVIDENCE
}

enum class LandmarkStyle(val label: String, val description: String) {
    TOWER("Tower", "Slender glass-blue tower with brass crown"),
    ACADEMY("Academy", "Cream stone hall with clock and pediment"),
    LIBRARY("Library", "Wide warm-brick hall with tall arched windows"),
    OBSERVATORY("Observatory", "Slate cylinder with brass celestial dome"),
    ATELIER("Atelier", "Gabled studio with north-light window"),
    DOMED_HALL("Domed Hall", "Sand-toned hall with drum and rounded dome")
}

enum class Tier {
    NONE,
    PARTIAL,
    MET,
    STRONG,
    GOLD
}

enum class GoalPaceStatus {
    AHEAD,
    ON_TRACK,
    BEHIND
}

enum class AchievementKind {
    MILESTONE,
    GOAL,
    DREAM,
    STREAK
}

// =============================================================================
// DATA MODELS (Section 2.1)
// =============================================================================

data class TargetChange(
    val effectiveFromEpochDay: Long,
    val target: Double,
    val minimum: Double? = null,
    val stretch: Double? = null
)

data class HabitItem(
    val id: String,
    val name: String,
    val glyphKey: String = "book",
    val measure: Measure = Measure.DONE,
    val unit: String = "pages",
    val target: Double = 1.0,
    val minimum: Double? = null,
    val stretch: Double? = null,
    val period: Period = Period.DAY,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon, 7=Sun
    val weight: Weight = Weight.STANDARD,
    val autoSource: AutoSource = AutoSource.NONE,
    val goalId: String? = null,
    val dreamId: String? = null,
    val contributionRatio: Double = 1.0,
    val quickSteps: List<Double> = emptyList(),
    val targetHistory: List<TargetChange> = emptyList(),
    val startEpochDay: Long,
    val archivedEpochDay: Long? = null
) {
    val isArchived: Boolean get() = archivedEpochDay != null
}

data class Entry(
    val id: String,
    val habitId: String,
    val epochDay: Long,
    val quantity: Double,
    val kind: EntryKind = EntryKind.LOG,
    val note: String? = null,
    val createdAtMillis: Long
)

data class Milestone(
    val id: String,
    val title: String,
    val kind: MilestoneKind,
    val threshold: Double? = null,
    val goalId: String? = null,
    val doneEpochDay: Long? = null,
    val evidence: Evidence? = null
) {
    val isDone: Boolean get() = doneEpochDay != null
}

data class Evidence(
    val note: String? = null,
    val imageFile: String? = null,
    val url: String? = null
)

data class Goal(
    val id: String,
    val name: String,
    val why: String? = null,
    val glyphKey: String = "code",
    val finish: FinishLine = FinishLine.CHECKLIST,
    val countTotal: Double? = null,
    val countUnit: String? = null,
    val metricBaseline: Double? = null,
    val metricTarget: Double? = null,
    val metricUnit: String? = null,
    val metricHigherIsBetter: Boolean = true,
    val startEpochDay: Long,
    val targetEpochDay: Long? = null,
    val dreamId: String? = null,
    val milestones: List<Milestone> = emptyList(),
    val status: GoalStatus = GoalStatus.ACTIVE,
    val achievedEpochDay: Long? = null,
    val evidence: Evidence? = null
)

data class GoalProgress(
    val id: String,
    val goalId: String,
    val epochDay: Long,
    val delta: Double,
    val note: String? = null
)

data class MetricReading(
    val id: String,
    val goalId: String,
    val epochDay: Long,
    val value: Double,
    val note: String? = null
)

data class Checkpoint(
    val id: String,
    val epochDay: Long,
    val rating: Int? = null,
    val evidence: Evidence? = null
)

data class CheckpointSeries(
    val everyMonths: Int = 1,
    val nextDueEpochDay: Long = 0L,
    val items: List<Checkpoint> = emptyList(),
    val prompt: String = "Photograph your practice"
)

data class Dream(
    val id: String,
    val name: String,
    val why: String? = null,
    val glyphKey: String = "dome",
    val landmark: LandmarkStyle = LandmarkStyle.ACADEMY,
    val horizon: DreamHorizon = DreamHorizon.Someday,
    val rungs: List<Milestone> = emptyList(),
    val status: DreamStatus = DreamStatus.ACTIVE,
    val checkpoints: CheckpointSeries? = null,
    val pinned: Boolean = false,
    val achievedEpochDay: Long? = null
)

data class Achievement(
    val id: String,
    val kind: AchievementKind,
    val refId: String,
    val title: String,
    val epochDay: Long,
    val plannedDays: Int?,
    val actualDays: Int?,
    val deltaDays: Int?,
    val bricks: Int,
    val evidence: Evidence? = null,
    val landmark: LandmarkStyle? = null
)

data class DayAggregate(
    val epochDay: Long,
    val scheduledCount: Int = 0,
    val completion: Double,     // 0.0 to 1.0 (clamped average)
    val overall: Double,        // overall credit weighted average
    val entryCount: Int,
    val totalBricks: Int,
    val isRest: Boolean,
    val isGold: Boolean,
    val isMended: Boolean,
    val hasAchievement: Boolean = false,
    val hasMilestone: Boolean = false,
    val hasDeadline: Boolean = false,
    val hasPeriodHabitLog: Boolean = false,
    val restCount: Int = 0,
    val events: List<String> = emptyList()
)

// =============================================================================
// SCORE ENGINE CALCULATIONS (Sections 2.2 - 2.6)
// =============================================================================

object ScoreEngine {

    object Economy {
        const val BASE_BRICKS = 10
        const val PERFECT_DAY_BONUS = 15
        const val MILESTONE_BONUS = 20
        const val DREAM_RUNG_BONUS = 40
        const val DREAM_ACHIEVED_BONUS = 500
        const val MAX_STREAK_BONUS = 10

        fun goalAchievedBase(plannedDays: Int?): Int {
            return 100 + min(plannedDays ?: 90, 200)
        }

        fun earlyFinishBonus(goalBase: Int, deltaDays: Int?, plannedDays: Int?): Int {
            if (deltaDays == null || plannedDays == null || deltaDays <= 0 || plannedDays <= 0) return 0
            val fraction = min(0.5, deltaDays.toDouble() / plannedDays.toDouble())
            return floor(goalBase * fraction).toInt()
        }
    }

    /**
     * The diminishing-returns credit formula (Section 2.3).
     * r = logged / target
     * r <= 0 -> 0.0
     * r < 1.0 -> r
     * r >= 1.0 -> minOf(2.0, 1.0 + 0.6 * log2(r))
     */
    fun credit(r: Double): Double = when {
        r <= 0.0 -> 0.0
        r < 1.0 -> r
        else -> {
            val log2r = ln(r) / ln(2.0)
            min(2.0, 1.0 + 0.6 * log2r)
        }
    }

    /** Tier tag assignment (Section 2.3) */
    fun tier(r: Double): Tier = when {
        r >= 2.0 -> Tier.GOLD
        r >= 1.5 -> Tier.STRONG
        r >= 1.0 -> Tier.MET
        r > 0.0  -> Tier.PARTIAL
        else     -> Tier.NONE
    }

    /** Determines if periodTotal qualifies to extend/keep the streak (Section 2.3) */
    fun qualifies(total: Double, minimum: Double?, target: Double): Boolean {
        val thresh = minimum ?: target
        return total >= thresh && thresh > 0.0
    }

    /**
     * Marginal brick formula (Section 2.5).
     * periodBricks(habit, total) = floor(BASE * wf * credit(total / target))
     */
    fun periodBricks(habit: HabitItem, total: Double): Int {
        val r = if (habit.target > 0.0) total / habit.target else 0.0
        val c = credit(r)
        return floor(Economy.BASE_BRICKS * habit.weight.factor * c).toInt()
    }

    fun marginalBricks(habit: HabitItem, beforeTotal: Double, afterTotal: Double): Int {
        return max(0, periodBricks(habit, afterTotal) - periodBricks(habit, beforeTotal))
    }

    // -------------------------------------------------------------------------
    // STREAKS (Section 2.4 - Kind, honest, never-miss-twice)
    // -------------------------------------------------------------------------

    /**
     * Calculates the habit's streak up to `currentEpochDay`.
     * Consecutive scheduled periods that qualify.
     * One missed period is forgiven if the next scheduled period qualifies.
     * Two consecutive misses reset it to 0. Rest-day entries are neutral.
     */
    fun calculateHabitStreak(
        habit: HabitItem,
        entries: List<Entry>,
        currentEpochDay: Long,
        autoQuantities: Map<Long, Double> = emptyMap()
    ): Pair<Int, Int> { // (currentStreak, bestStreak)
        if (habit.period != Period.DAY) {
            return Pair(0, 0)
        }

        val entriesByDay = entries.filter { it.habitId == habit.id }.groupBy { it.epochDay }
        val startDay = habit.startEpochDay
        if (currentEpochDay < startDay) return Pair(0, 0)

        var streak = 0
        var bestStreak = 0
        var lastMissForgiven = false

        for (d in startDay..currentEpochDay) {
            val dow = java.time.LocalDate.ofEpochDay(d).dayOfWeek.value
            val isScheduled = dow in habit.daysOfWeek
            if (!isScheduled) continue

            val dayEntries = entriesByDay[d] ?: emptyList()
            val isRest = dayEntries.any { it.kind == EntryKind.REST }
            if (isRest) continue

            val total = dayEntries.filter { it.kind == EntryKind.LOG }.sumOf { it.quantity } + (autoQuantities[d] ?: 0.0)
            val qualifies = qualifies(total, habit.minimum, habit.target)

            val isTodayPending = (d == currentEpochDay) && !qualifies

            if (isTodayPending) {
                // Today has not qualified yet. If yesterday was a miss, streak is broken.
                // If yesterday was good, the streak is kept pending today's outcome.
                if (lastMissForgiven) {
                    streak = 0
                }
            } else if (qualifies) {
                streak++
                lastMissForgiven = false
                bestStreak = max(bestStreak, streak)
            } else {
                // Scheduled day missed
                if (lastMissForgiven) {
                    streak = 0
                    lastMissForgiven = false
                } else {
                    lastMissForgiven = true
                }
            }
        }

        bestStreak = max(bestStreak, streak)
        return Pair(streak, bestStreak)
    }

    /**
     * Global show-up streak (flame in header): consecutive days with at least one qualifying entry
     */
    fun calculateShowUpStreak(
        habits: List<HabitItem>,
        entries: List<Entry>,
        currentEpochDay: Long,
        autoQuantities: (habitId: String, epochDay: Long) -> Double = { _, _ -> 0.0 }
    ): Int {
        var streak = 0
        var d = currentEpochDay
        val habitsById = habits.associateBy { it.id }
        val entriesByDay = entries.groupBy { it.epochDay }

        // Check today
        val todayEntries = entriesByDay[d] ?: emptyList()
        val anyQualToday = habits.any { h ->
            val hEntries = todayEntries.filter { it.habitId == h.id }
            val q = hEntries.filter { it.kind == EntryKind.LOG }.sumOf { it.quantity } + autoQuantities(h.id, d)
            qualifies(q, h.minimum, h.target)
        }
        if (!anyQualToday) {
            d--
        }

        while (true) {
            val dayEntries = entriesByDay[d] ?: emptyList()
            val anyQual = habits.any { h ->
                val hEntries = dayEntries.filter { it.habitId == h.id }
                val q = hEntries.filter { it.kind == EntryKind.LOG }.sumOf { it.quantity } + autoQuantities(h.id, d)
                qualifies(q, h.minimum, h.target)
            }
            if (anyQual) {
                streak++
                d--
            } else {
                break
            }
        }
        return streak
    }

    // -------------------------------------------------------------------------
    // GOAL PROGRESS & PACE (Section 2.6)
    // -------------------------------------------------------------------------

    data class GoalPace(
        val progress: Double,               // 0.0 to 1.0
        val plannedProgress: Double?,       // 0.0 to 1.0 if target date exists
        val daysAhead: Double?,             // positive = ahead, negative = behind
        val status: GoalPaceStatus,
        val estimatedFinishEpochDay: Long?  // at current pace, if >= 7 days of data
    )

    fun calculateGoalPace(
        goal: Goal,
        todayEpochDay: Long,
        manualProgress: List<GoalProgress>,
        readings: List<MetricReading>,
        linkedHabits: List<HabitItem>,
        entries: List<Entry>,
        autoQuantities: (habitId: String, epochDay: Long) -> Double = { _, _ -> 0.0 }
    ): GoalPace {
        val p: Double = when (goal.finish) {
            FinishLine.CHECKLIST -> {
                if (goal.milestones.isEmpty()) 0.0
                else goal.milestones.count { it.isDone }.toDouble() / goal.milestones.size.toDouble()
            }
            FinishLine.COUNT -> {
                val totalRequired = goal.countTotal ?: 1.0
                val manualSum = manualProgress.filter { it.goalId == goal.id }.sumOf { it.delta }
                var habitSum = 0.0
                linkedHabits.filter { it.goalId == goal.id }.forEach { h ->
                    val hEntries = entries.filter { it.habitId == h.id && it.kind == EntryKind.LOG }
                    val qManual = hEntries.sumOf { it.quantity }
                    // Sum auto quantities across entries' days or range
                    val qAuto = hEntries.map { it.epochDay }.distinct().sumOf { autoQuantities(h.id, it) }
                    habitSum += (qManual + qAuto) * h.contributionRatio
                }
                val totalProgress = manualSum + habitSum
                (totalProgress / totalRequired).coerceIn(0.0, 1.0)
            }
            FinishLine.METRIC -> {
                val baseline = goal.metricBaseline ?: 0.0
                val target = goal.metricTarget ?: 1.0
                val goalReadings = readings.filter { it.goalId == goal.id }.sortedBy { it.epochDay }
                val latest = goalReadings.lastOrNull()?.value ?: baseline
                if (goal.metricHigherIsBetter) {
                    if (target > baseline) ((latest - baseline) / (target - baseline)).coerceIn(0.0, 1.0)
                    else if (latest >= target) 1.0 else 0.0
                } else {
                    if (baseline > target) ((baseline - latest) / (baseline - target)).coerceIn(0.0, 1.0)
                    else if (latest <= target) 1.0 else 0.0
                }
            }
        }

        if (goal.targetEpochDay == null) {
            return GoalPace(
                progress = p,
                plannedProgress = null,
                daysAhead = null,
                status = GoalPaceStatus.ON_TRACK,
                estimatedFinishEpochDay = null
            )
        }

        val span = (goal.targetEpochDay - goal.startEpochDay).toDouble().coerceAtLeast(1.0)
        val elapsed = (todayEpochDay - goal.startEpochDay).toDouble().coerceAtLeast(0.0)
        val plannedP = (elapsed / span).coerceIn(0.0, 1.0)
        val daysAhead = (p - plannedP) * span
        val tolerance = max(1.0, 0.03 * span)

        val status = when {
            daysAhead >= tolerance -> GoalPaceStatus.AHEAD
            daysAhead <= -tolerance -> GoalPaceStatus.BEHIND
            else -> GoalPaceStatus.ON_TRACK
        }

        // At current pace projection (only if >= 7 days of data and progress > 0)
        var estFinish: Long? = null
        if (elapsed >= 7 && p > 0.05 && p < 1.0) {
            val dailyRate = p / elapsed
            val remaining = 1.0 - p
            val daysRemaining = remaining / dailyRate
            estFinish = todayEpochDay + daysRemaining.roundToInt()
        }

        return GoalPace(
            progress = p,
            plannedProgress = plannedP,
            daysAhead = daysAhead,
            status = status,
            estimatedFinishEpochDay = estFinish
        )
    }

    // -------------------------------------------------------------------------
    // DREAM FUEL (Section 4.1)
    // -------------------------------------------------------------------------

    /**
     * Fuel = average min(credit, 1.2) over the last 30 days across linked habits' scheduled periods.
     */
    fun calculateDreamFuel(
        dream: Dream,
        linkedHabits: List<HabitItem>,
        entries: List<Entry>,
        currentEpochDay: Long,
        autoQuantities: (habitId: String, epochDay: Long) -> Double = { _, _ -> 0.0 }
    ): Double? {
        val dreamHabits = linkedHabits.filter { it.dreamId == dream.id }
        if (dreamHabits.isEmpty()) return null

        var totalScheduled = 0
        var totalCredit = 0.0

        for (d in (currentEpochDay - 29)..currentEpochDay) {
            val dow = java.time.LocalDate.ofEpochDay(d).dayOfWeek.value
            for (h in dreamHabits) {
                if (dow in h.daysOfWeek) {
                    totalScheduled++
                    val q = entries.filter { it.habitId == h.id && it.epochDay == d && it.kind == EntryKind.LOG }.sumOf { it.quantity } + autoQuantities(h.id, d)
                    val r = if (h.target > 0.0) q / h.target else 0.0
                    val c = min(credit(r), 1.2)
                    totalCredit += c
                }
            }
        }

        if (totalScheduled == 0) return 0.0
        return totalCredit / totalScheduled.toDouble()
    }
}
