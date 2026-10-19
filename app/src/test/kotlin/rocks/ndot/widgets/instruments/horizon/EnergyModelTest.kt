package rocks.ndot.widgets.instruments.horizon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyModelTest {

    @Test
    fun testAlertnessRange() {
        for (i in 0..160) {
            val tau = i / 10.0
            val a = EnergyModel.alertness(tau, 16.0, 0.0)
            assertTrue("Alertness must be in [0.05, 1.0], was $a at tau $tau", a in 0.05..1.0)
        }
    }

    @Test
    fun testPeakAndDipWindows() {
        val wakeMin = 7 * 60 // 07:00
        val sleepMin = 23 * 60 // 23:00
        val windows = EnergyModel.findWindows(wakeMin, sleepMin, 0.0)

        assertTrue("Should detect windows", windows.isNotEmpty())
        val peak = windows.firstOrNull { it.type == WindowType.PEAK_FOCUS }
        assertNotNull("Peak Focus window must be detected", peak)
        assertTrue("Peak duration >= 30min", (peak!!.endMin - peak.startMin) >= 30)

        val dip = windows.firstOrNull { it.type == WindowType.DIP_WINDOW }
        assertNotNull("Dip window must be detected", dip)
        assertTrue("Dip center in afternoon", dip!!.startMin >= wakeMin + 4 * 60)
    }

    @Test
    fun testCaffeineCutoff() {
        val sleepMin = 23 * 60 // 23:00
        val cutoff = EnergyModel.caffeineCutoffMin(sleepMin, 9.0)
        assertEquals(14 * 60, cutoff) // 14:00
    }

    @Test
    fun testSuggestSlotFindsOptimalSlot() {
        val wakeMin = 7 * 60
        val sleepMin = 23 * 60
        val slot = EnergyModel.suggestSlot(60, wakeMin, sleepMin, emptyList(), 0.0)
        // Late-morning peak is around 3h after wake = 10:00 (600 min)
        assertTrue("Suggested slot should be around morning peak", slot in (wakeMin + 90)..(wakeMin + 240))
    }
}
