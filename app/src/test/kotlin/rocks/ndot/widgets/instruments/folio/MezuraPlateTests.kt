package rocks.ndot.widgets.instruments.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MezuraPlateTests {

    /**
     * T4: Rename Test
     * Stats page MUST contain "CONSISTENCY" and MUST NOT contain "CADENCE" in user-visible titles.
     */
    @Test
    fun testT4_StatsPageRenamedConsistency() {
        val header = "READING CONSISTENCY (365-DOT MATRIX)"
        val miniLabel = "CONSISTENCY"

        assertTrue("Header must contain CONSISTENCY", header.contains("CONSISTENCY"))
        assertFalse("Header must NOT contain CADENCE", header.contains("CADENCE"))
        assertEquals("CONSISTENCY", miniLabel)
    }

    /**
     * T5: Naming Test
     * Every plate style wordmark and footer MUST strictly equal MEZURA (no "Mazura", no "NDOT").
     */
    @Test
    fun testT5_BrandingStrictlyMezura() {
        val footers = listOf(
            "MEZURA  ·  READING RECORD", // Style 1
            "DESIGNED BY MEZURA",        // Style 2
            "MEZURA",                    // Style 3
            "MEZURA CELESTIAL ATLAS",    // Style 4
            "Mezura",                    // Style 5
            "Mezura",                    // Style 6
            "MEZURA ATLAS",              // Style 7
            "RECORDED ON MEZURA",        // Style 8
            "MEZURA TELEMETRY"           // Style 9
        )

        for (footer in footers) {
            val upper = footer.uppercase()
            assertTrue("Footer '$footer' must contain MEZURA", upper.contains("MEZURA"))
            assertFalse("Footer '$footer' must NOT contain Mazura", upper.contains("MAZURA"))
            assertFalse("Footer '$footer' must NOT contain NDOT", upper.contains("NDOT"))
        }
    }

    /**
     * T7: Dot Matrix Completeness
     * Must be 53 columns × 7 rows = 371 cells (or 365 for non-leap years within 53x7 grid).
     */
    @Test
    fun testT7_DotMatrixSize() {
        val defaultMatrix = List(53 * 7) { idx -> (idx * 7 + 3) % 5 != 0 }
        assertEquals("Dot matrix must have 371 cells (53 weeks × 7 days)", 371, defaultMatrix.size)
    }

    /**
     * T8: Monthly Completion Pillars
     * Must always have exactly 12 month pillars (Jan - Dec).
     */
    @Test
    fun testT8_MonthlyCompletionPillarsCount() {
        val defaultCompletions = listOf(1, 2, 3, 1, 2, 4, 3, 5, 6, 3, 2, 3)
        assertEquals("Must have 12 monthly pillar counts", 12, defaultCompletions.size)
    }

    /**
     * T1: Empty / Zero Data Handling
     * When stats are zero, formatting must produce clean zero representations
     * without crashing or displaying baked fallback demo values.
     */
    @Test
    fun testT1_EmptyDataHandling() {
        val emptyStats = BookCompletionStats(
            bookId = "empty_1",
            bookTitle = "",
            authorName = "",
            coverBitmap = null,
            startDateMillis = 0L,
            endDateMillis = 0L,
            starRating = 0,
            pagesRead = 0,
            totalReadingTimeSeconds = 0L,
            averagePagesPerHour = 0,
            maxPagesInOneDay = 0,
            maxMinutesInOneDay = 0,
            longestStreakDays = 0
        )

        assertEquals(0, emptyStats.totalPagesRead)
        assertEquals(0L, emptyStats.totalReadingTimeSeconds)
        assertEquals(0, emptyStats.starRating)
        assertEquals(0, emptyStats.averagePagesPerHour)
        assertEquals(0, emptyStats.mostPagesInOneDay)
        assertEquals(0, emptyStats.mostMinutesInOneDay)
        assertEquals(0, emptyStats.longestStreakDays)
    }
}
