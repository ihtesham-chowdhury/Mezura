package rocks.ndot.widgets.common

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise/sunset from the NOAA solar-position approximation - the same algorithm the web
 * version's Sundial widget uses (ndot-widgets.html's `solarEvents()`), ported to Kotlin so
 * both builds agree on the math. No network call, no dependency: just latitude, longitude,
 * and a date.
 */
object SolarMath {

    /** Either both [sunrise] and [sunset] are set, or [polarDay]/[polarNight] describes why not. */
    data class SolarEvents(
        val sunriseUtcMillis: Long? = null,
        val sunsetUtcMillis: Long? = null,
        val polarDay: Boolean = false,
        val polarNight: Boolean = false,
    )

    /** [date] is truncated to that calendar day in UTC; [lat]/[lng] in degrees. */
    fun eventsFor(date: Calendar, lat: Double, lng: Double): SolarEvents {
        val utc = date.clone() as Calendar
        utc.timeZone = TimeZone.getTimeZone("UTC")
        val year = utc.get(Calendar.YEAR)
        val startOfYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, Calendar.JANUARY, 0, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayOfYear = ((utc.timeInMillis - startOfYear.timeInMillis) / 86_400_000L).toInt()

        val g = (2 * PI / 365.0) * (dayOfYear - 1 + 0.5)
        val eqTimeMinutes = 229.18 * (
            0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
                0.014615 * cos(2 * g) - 0.040849 * sin(2 * g)
            )
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) -
            0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)

        val latRad = lat * PI / 180.0
        val cosH = cos(90.833 * PI / 180.0) / (cos(latRad) * cos(decl)) - tan(latRad) * tan(decl)
        if (cosH > 1) return SolarEvents(polarNight = true)
        if (cosH < -1) return SolarEvents(polarDay = true)
        val haDegrees = acos(cosH) * 180.0 / PI

        val dayStartMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun atMinutesUtc(minutesFromMidnightUtc: Double): Long =
            dayStartMillis + (minutesFromMidnightUtc * 60_000.0).toLong()

        val sunriseMin = 720 - 4 * (lng + haDegrees) - eqTimeMinutes
        val sunsetMin = 720 - 4 * (lng - haDegrees) - eqTimeMinutes
        return SolarEvents(
            sunriseUtcMillis = atMinutesUtc(sunriseMin),
            sunsetUtcMillis = atMinutesUtc(sunsetMin),
        )
    }

    /** State of the dial right now: 0 = full night, 1 = full day, with a soft rise/set window. */
    data class DialState(
        val fill: Float,
        val isPolar: Boolean,
        val polarIsDay: Boolean,
        val nextEventMillis: Long,
        val untilNextMillis: Long,
        val risingNotSetting: Boolean,
    )

    private const val TRANSITION_WINDOW_MILLIS = 10 * 60_000L // matches the web widget's 10-minute lens sweep

    fun dialStateNow(nowMillis: Long, lat: Double, lng: Double, tz: TimeZone): DialState {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMillis }
        val today = eventsFor(cal, lat, lng)
        if (today.polarDay || today.polarNight) {
            return DialState(
                fill = if (today.polarDay) 1f else 0f,
                isPolar = true,
                polarIsDay = today.polarDay,
                nextEventMillis = nowMillis,
                untilNextMillis = 0L,
                risingNotSetting = true,
            )
        }
        val rise = today.sunriseUtcMillis!!
        val set = today.sunsetUtcMillis!!
        val fill: Float
        val next: Long
        val rising: Boolean
        when {
            nowMillis < rise - TRANSITION_WINDOW_MILLIS -> {
                fill = 0f; next = rise; rising = true
            }
            nowMillis < rise -> {
                fill = ((nowMillis - (rise - TRANSITION_WINDOW_MILLIS)).toFloat() / TRANSITION_WINDOW_MILLIS)
                next = rise; rising = true
            }
            nowMillis < set - TRANSITION_WINDOW_MILLIS -> {
                fill = 1f; next = set; rising = false
            }
            nowMillis < set -> {
                fill = 1f - ((nowMillis - (set - TRANSITION_WINDOW_MILLIS)).toFloat() / TRANSITION_WINDOW_MILLIS)
                next = set; rising = false
            }
            else -> {
                val tomorrow = Calendar.getInstance(tz).apply { timeInMillis = nowMillis + 86_400_000L }
                val tomorrowEvents = eventsFor(tomorrow, lat, lng)
                fill = 0f
                next = tomorrowEvents.sunriseUtcMillis ?: (nowMillis + 86_400_000L)
                rising = true
            }
        }
        return DialState(
            fill = fill.coerceIn(0f, 1f),
            isPolar = false,
            polarIsDay = false,
            nextEventMillis = next,
            untilNextMillis = (next - nowMillis).coerceAtLeast(0L),
            risingNotSetting = rising,
        )
    }
}
