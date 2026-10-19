package rocks.ndot.widgets.instruments.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScoreEngineTest {

    @Test
    fun testCreditCurveAndTiersSpecificationVectors() {
        // Test vectors from section 2.3 of the Master Build Brief:
        // Habit: Read 20 pages
        // Logged | r | credit | Tier
        // 0   | 0.0 | 0.000 | NONE
        // 10  | 0.5 | 0.500 | PARTIAL
        // 20  | 1.0 | 1.000 | MET
        // 30  | 1.5 | 1.351 | STRONG
        // 120 min / 60 min | 2.0 | 1.600 | GOLD
        // 50  | 2.5 | 1.793 | GOLD
        // 60  | 3.0 | 1.951 | GOLD
        // 400 | 20.0| 2.000 | GOLD (capped)

        val vectors = listOf(
            Triple(0.0, 0.000, Tier.NONE),
            Triple(0.5, 0.500, Tier.PARTIAL),
            Triple(1.0, 1.000, Tier.MET),
            Triple(1.5, 1.351, Tier.STRONG),
            Triple(2.0, 1.600, Tier.GOLD),
            Triple(2.5, 1.793, Tier.GOLD),
            Triple(3.0, 1.951, Tier.GOLD),
            Triple(20.0, 2.000, Tier.GOLD)
        )

        for ((r, expectedCredit, expectedTier) in vectors) {
            val actualCredit = ScoreEngine.credit(r)
            val actualTier = ScoreEngine.tier(r)

            assertTrue(
                "For r=$r expected credit $expectedCredit but was $actualCredit",
                abs(actualCredit - expectedCredit) < 0.002
            )
            assertEquals("For r=$r expected tier $expectedTier but was $actualTier", expectedTier, actualTier)
        }
    }

    @Test
    fun testBricksEconomyCalculation() {
        val standardHabit = HabitItem(
            id = "h1",
            name = "Read",
            target = 20.0,
            weight = Weight.STANDARD,
            startEpochDay = 20000L
        )

        // total = 0 -> 0 bricks
        assertEquals(0, ScoreEngine.periodBricks(standardHabit, 0.0))
        // total = 10 -> 0.5 credit -> 10 * 1.0 * 0.5 = 5 bricks
        assertEquals(5, ScoreEngine.periodBricks(standardHabit, 10.0))
        // total = 20 -> 1.0 credit -> 10 * 1.0 * 1.0 = 10 bricks
        assertEquals(10, ScoreEngine.periodBricks(standardHabit, 20.0))
        // total = 40 (2x) -> 1.6 credit -> 16 bricks
        assertEquals(16, ScoreEngine.periodBricks(standardHabit, 40.0))

        // Marginal brick addition:
        val marginal = ScoreEngine.marginalBricks(standardHabit, beforeTotal = 10.0, afterTotal = 20.0)
        assertEquals(5, marginal)
    }

    @Test
    fun testStreakNeverMissTwice() {
        val habit = HabitItem(
            id = "h1",
            name = "Exercise",
            target = 1.0,
            daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
            startEpochDay = 100L
        )

        // Consecutive 5 days qualifying -> streak 5
        val entries = (100L..104L).map { day ->
            Entry(
                id = "e_$day",
                habitId = "h1",
                epochDay = day,
                quantity = 1.0,
                createdAtMillis = day * 86400000L
            )
        }
        val (streak, _) = ScoreEngine.calculateHabitStreak(habit, entries, currentEpochDay = 104L)
        assertEquals(5, streak)

        // One missed day (105 missed) followed by qualified day (106) -> streak is forgiven, keeps count (5)
        val entriesWithForgivenMiss = entries + Entry(
            id = "e_106",
            habitId = "h1",
            epochDay = 106L,
            quantity = 1.0,
            createdAtMillis = 106L * 86400000L
        )
        val (streakAfterForgiven, _) = ScoreEngine.calculateHabitStreak(habit, entriesWithForgivenMiss, currentEpochDay = 106L)
        // Kept its count without incrementing for the missed period -> 5 + 1 = 6
        assertEquals(6, streakAfterForgiven)

        // Two consecutive missed days (107, 108) resets streak to 0
        val (streakAfterTwoMisses, _) = ScoreEngine.calculateHabitStreak(habit, entriesWithForgivenMiss, currentEpochDay = 108L)
        assertEquals(0, streakAfterTwoMisses)
    }

    @Test
    fun testGoalPaceStatus() {
        val goal = Goal(
            id = "g1",
            name = "Course",
            finish = FinishLine.CHECKLIST,
            startEpochDay = 100L,
            targetEpochDay = 200L, // span 100 days
            milestones = (1..10).map { i ->
                Milestone(
                    id = "m$i",
                    title = "Module $i",
                    kind = MilestoneKind.TICK,
                    doneEpochDay = if (i <= 6) 130L else null // 6 out of 10 done at day 130 (progress 0.60)
                )
            }
        )

        // At day 130: elapsed = 30, planned = 30/100 = 0.30, progress = 0.60 -> ahead by (0.60 - 0.30)*100 = 30 days
        val pace = ScoreEngine.calculateGoalPace(
            goal = goal,
            todayEpochDay = 130L,
            manualProgress = emptyList(),
            readings = emptyList(),
            linkedHabits = emptyList(),
            entries = emptyList()
        )
        assertEquals(0.60, pace.progress, 0.001)
        assertEquals(GoalPaceStatus.AHEAD, pace.status)
        assertTrue(pace.daysAhead!! >= 29.0)
    }
}
