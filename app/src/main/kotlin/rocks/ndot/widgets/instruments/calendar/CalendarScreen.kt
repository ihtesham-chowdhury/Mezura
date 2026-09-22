package rocks.ndot.widgets.instruments.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.app.LightChrome
import rocks.ndot.widgets.kit.ChipColors
import rocks.ndot.widgets.kit.ChipRow
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private enum class CalDesign(val id: String, val label: String) { GLASS("glass", "Glass"), PAPER("paper", "Paper") }

private const val PAGES = 2401
private const val CENTER = 1200

private val GlassRed = Color(0xFFFF3B30)
private val Coral = Color(0xFFE86A6A)

/**
 * A calendar for looking at, not for scheduling: a month and a year, and how far away any day
 * is. Glass floats a frosted panel over a softly moving room, the way a visionOS window sits in
 * your space; Paper is a bold desk card with today's date written large.
 */
@Composable
fun CalendarScreen() {
    val context = LocalContext.current
    var design by remember { mutableStateOf(if (AppPrefs.design(context, "calendar", "glass") == "paper") CalDesign.PAPER else CalDesign.GLASS) }
    var yearMode by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    val locale = Locale.getDefault()
    val firstDay = remember { WeekFields.of(locale).firstDayOfWeek }
    val monthPager = rememberPagerState(initialPage = CENTER) { PAGES }
    val yearPager = rememberPagerState(initialPage = CENTER) { PAGES }
    val scope = rememberCoroutineScope()
    val thisMonth = YearMonth.from(today)

    // Keep the selection inside whichever month is showing: today if it's this month, else the
    // same day of the month.
    LaunchedEffect(monthPager) {
        snapshotFlow { monthPager.settledPage }.collect { page ->
            val month = thisMonth.plusMonths((page - CENTER).toLong())
            if (YearMonth.from(selected) != month) {
                selected = if (month == thisMonth) today else month.atDay(selected.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            }
        }
    }

    LightChrome(design == CalDesign.PAPER)
    val glass = design == CalDesign.GLASS
    val label = if (glass) Color(0xB3FFFFFF) else Color(0x80000000)

    Box(Modifier.fillMaxSize()) {
        if (glass) AmbientRoom(Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(Color(0xFFE9E6E1)))

        Column(Modifier.fillMaxSize()) {
            ToolHeader("CALENDAR", label) {
                TextAction(if (yearMode) "MONTH" else "YEAR", label, {
                    if (yearMode) {
                        yearMode = false
                    } else {
                        yearMode = true
                        scope.launch { yearPager.scrollToPage(CENTER + (selected.year - today.year)) }
                    }
                })
                Spacer(Modifier.width(6.dp))
                TextAction("TODAY", label, {
                    selected = today
                    yearMode = false
                    scope.launch { monthPager.animateScrollToPage(CENTER) }
                })
            }
            ChipRow(
                items = CalDesign.entries.map { it.label },
                selected = design.ordinal,
                colors = if (glass) ChipColors(Color(0x26FFFFFF), Color(0xE6FFFFFF), Color(0xFFFFFFFF), Color(0xFF1A1A1A)) else ChipColors.Light,
                onSelect = {
                    design = CalDesign.entries[it]
                    AppPrefs.setDesign(context, "calendar", design.id)
                    Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                },
            )
            Spacer(Modifier.height(10.dp))

            AnimatedContent(
                targetState = yearMode,
                transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f)).togetherWith(fadeOut(tween(120))) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "calendarMode",
            ) { showYear ->
                if (showYear) {
                    HorizontalPager(state = yearPager, modifier = Modifier.fillMaxSize()) { page ->
                        val year = today.year + (page - CENTER)
                        YearPage(year, today, glass, firstDay, locale,
                            onPrev = { scope.launch { yearPager.animateScrollToPage(page - 1) } },
                            onNext = { scope.launch { yearPager.animateScrollToPage(page + 1) } },
                        ) { month ->
                            Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.CLICK)
                            yearMode = false
                            val target = CENTER + ((month.year - today.year) * 12 + (month.monthValue - today.monthValue))
                            scope.launch { monthPager.scrollToPage(target) }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        HorizontalPager(state = monthPager, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
                            val month = thisMonth.plusMonths((page - CENTER).toLong())
                            val pick: (LocalDate) -> Unit = {
                                Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                                selected = it
                            }
                            if (glass) {
                                GlassMonth(month, today, selected, firstDay, locale, monthPager, pick)
                            } else {
                                PaperMonth(month, today, selected, firstDay, locale, pick)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        DayInfo(selected, today, locale, glass)
                    }
                }
            }
            NavSpacer()
        }
    }
}

/** The weekday order for this locale, starting from its first day. */
private fun weekdays(firstDay: DayOfWeek): List<DayOfWeek> = (0 until 7).map { firstDay.plus(it.toLong()) }

/** 42 cells: the month, padded with the neighbouring months' days, always six weeks tall. */
private fun cells(month: YearMonth, firstDay: DayOfWeek): List<LocalDate> {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - firstDay.value) + 7) % 7
    val start = first.minusDays(lead.toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

// ============================== GLASS ==============================

/** A dim room at dusk, out of focus: warm lamp, cool window, a plant, a floor. It drifts slowly. */
@Composable
private fun AmbientRoom(modifier: Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            delay(40)
            t = (System.nanoTime() - start) / 1e9f
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A2521), Color(0xFF3B332B), Color(0xFF2E2822))))
        fun blob(x: Float, y: Float, r: Float, color: Color, phase: Float) {
            val dx = sin(t * 0.21f + phase) * w * 0.025f
            val dy = cos(t * 0.17f + phase * 1.3f) * h * 0.015f
            val c = Offset(x * w + dx, y * h + dy)
            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = c, radius = r * w), radius = r * w, center = c)
        }
        blob(0.18f, 0.22f, 0.62f, Color(0x8CE0A458), 0f)
        blob(0.92f, 0.30f, 0.55f, Color(0x59C9D6E3), 1.7f)
        blob(0.95f, 0.66f, 0.42f, Color(0x804F6B3A), 3.1f)
        blob(0.50f, 1.05f, 0.85f, Color(0x808A6A45), 4.4f)
        blob(0.28f, 0.78f, 0.36f, Color(0x991A1512), 5.2f)
        blob(0.62f, 0.12f, 0.30f, Color(0x40FFE9C4), 2.4f)
    }
}

private val panelShape = RoundedCornerShape(34.dp)

private fun Modifier.glassPanel(): Modifier = this
    .clip(panelShape)
    .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))))
    .border(1.dp, Brush.linearGradient(listOf(Color(0x80FFFFFF), Color(0x14FFFFFF), Color(0x40FFFFFF))), panelShape)

@Composable
private fun GlassMonth(
    month: YearMonth, today: LocalDate, selected: LocalDate, firstDay: DayOfWeek, locale: Locale,
    pager: PagerState, onPick: (LocalDate) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassPanel().padding(horizontal = 14.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "${month.month.getDisplayName(JTextStyle.FULL_STANDALONE, locale)} ${month.year}",
                style = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Color.White),
                modifier = Modifier.weight(1f),
            )
            GlassChevron("‹") { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
            Spacer(Modifier.width(8.dp))
            GlassChevron("›") { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            weekdays(firstDay).forEach {
                BasicText(
                    it.getDisplayName(JTextStyle.SHORT, locale).uppercase(locale),
                    style = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        cells(month, firstDay).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val inMonth = YearMonth.from(day) == month
                    val isToday = day == today
                    val isSelected = day == selected && !isToday
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clip(CircleShape)
                            .background(when { isToday -> GlassRed; isSelected -> Color(0x33FFFFFF); else -> Color.Transparent })
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            day.dayOfMonth.toString(),
                            style = TextStyle(
                                fontFamily = Fonts.sans,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 17.sp,
                                color = if (inMonth) Color.White else Color(0x59FFFFFF),
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassChevron(symbol: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0x33FFFFFF))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(symbol, style = TextStyle(fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Medium))
    }
}

// ============================== PAPER ==============================

@Composable
private fun PaperMonth(
    month: YearMonth, today: LocalDate, selected: LocalDate, firstDay: DayOfWeek, locale: Locale, onPick: (LocalDate) -> Unit,
) {
    val shown = if (YearMonth.from(selected) == month) selected else month.atDay(1)
    Column(
        Modifier.padding(horizontal = 22.dp).fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(6.dp), ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF7F7F6))
    ) {
        // The header: the date, large, on a slab of colour.
        Column(Modifier.fillMaxWidth().background(Coral).padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        shown.dayOfMonth.toString(),
                        style = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.Bold, fontSize = 84.sp, color = Color.White, lineHeight = 84.sp),
                    )
                    BasicText(
                        shown.month.getDisplayName(JTextStyle.FULL_STANDALONE, locale).lowercase(locale),
                        style = TextStyle(fontFamily = Fonts.sans, fontSize = 24.sp, color = Color.White),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(
                        shown.dayOfWeek.getDisplayName(JTextStyle.FULL, locale).lowercase(locale),
                        style = TextStyle(fontFamily = Fonts.sans, fontSize = 15.sp, color = Color(0xE6FFFFFF)),
                    )
                    BasicText(
                        shown.year.toString(),
                        style = TextStyle(fontFamily = Fonts.sans, fontSize = 15.sp, color = Color(0x99FFFFFF)),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            BasicText(
                "•  " + relative(shown, today),
                style = TextStyle(fontFamily = Fonts.sans, fontSize = 13.sp, color = Color(0xCCFFFFFF)),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                weekdays(firstDay).forEach {
                    BasicText(
                        it.getDisplayName(JTextStyle.SHORT, locale).uppercase(locale),
                        style = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Coral, textAlign = TextAlign.Center),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            cells(month, firstDay).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val inMonth = YearMonth.from(day) == month
                        val isToday = day == today
                        val color = when {
                            !inMonth -> Color(0xFFDADADA)
                            isToday -> Coral
                            day.isBefore(today) -> Color(0xFFB3B3B3)
                            else -> Color(0xFF3A3A3A)
                        }
                        Box(
                            Modifier.weight(1f).height(38.dp)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(day) }
                                .drawBehind {
                                    if (day == selected && inMonth) {
                                        drawCircle(Coral.copy(alpha = 0.14f), radius = size.minDimension * 0.46f)
                                    }
                                    if (isToday) {
                                        val y = size.height * 0.84f
                                        drawLine(Coral, Offset(size.width * 0.36f, y), Offset(size.width * 0.64f, y), strokeWidth = 1.5.dp.toPx())
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                day.dayOfMonth.toString(),
                                style = TextStyle(fontFamily = Fonts.sans, fontSize = 15.sp, color = color,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================== YEAR ==============================

@Composable
private fun YearPage(
    year: Int, today: LocalDate, glass: Boolean, firstDay: DayOfWeek, locale: Locale,
    onPrev: () -> Unit, onNext: () -> Unit, onMonth: (YearMonth) -> Unit,
) {
    val ink = if (glass) Color.White else Color(0xFF2A2A2A)
    val accent = if (glass) GlassRed else Coral
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(year.toString(), style = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = if (glass) Color.White else Coral), modifier = Modifier.weight(1f))
            if (glass) {
                GlassChevron("‹", onPrev); Spacer(Modifier.width(8.dp)); GlassChevron("›", onNext)
            } else {
                TextAction("‹", Color(0xFF444444), onPrev); Spacer(Modifier.width(6.dp)); TextAction("›", Color(0xFF444444), onNext)
            }
        }
        Spacer(Modifier.height(8.dp))
        (1..12).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val month = YearMonth.of(year, m)
                    val tile = if (glass) Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x1FFFFFFF)).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
                    else Modifier.shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF7F7F6))
                    Column(
                        Modifier.weight(1f).then(tile)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onMonth(month) }
                            .padding(8.dp),
                    ) {
                        BasicText(
                            month.month.getDisplayName(JTextStyle.SHORT_STANDALONE, locale).uppercase(locale),
                            style = labelStyle(if (month == YearMonth.from(today)) accent else ink, 10.sp, FontWeight.Bold),
                        )
                        Spacer(Modifier.height(4.dp))
                        cells(month, firstDay).chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    val inMonth = YearMonth.from(day) == month
                                    val isToday = day == today
                                    Box(
                                        Modifier.weight(1f).height(13.dp).drawBehind {
                                            if (isToday) drawCircle(accent, radius = size.minDimension * 0.55f)
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (inMonth) {
                                            BasicText(
                                                day.dayOfMonth.toString(),
                                                style = TextStyle(fontSize = 7.5.sp, color = if (isToday) Color.White else ink.copy(alpha = if (glass) 0.85f else 0.75f), textAlign = TextAlign.Center),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        val dayOfYear = if (year == today.year) today.dayOfYear else null
        if (dayOfYear != null) {
            BasicText(
                "DAY $dayOfYear OF ${today.lengthOfYear()} · ${(dayOfYear * 100 / today.lengthOfYear())}% OF $year GONE",
                style = labelStyle(if (glass) Color(0xB3FFFFFF) else Color(0x99000000), 10.sp).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}

// ============================== DAY INFO ==============================

/** "in 12 days", "today", "214 days ago" - the calendar's one piece of arithmetic. */
private fun relative(day: LocalDate, today: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(today, day)
    return when {
        diff == 0L -> "today"
        diff == 1L -> "tomorrow"
        diff == -1L -> "yesterday"
        diff > 0 -> "in $diff days"
        else -> "${-diff} days ago"
    }
}

@Composable
private fun DayInfo(day: LocalDate, today: LocalDate, locale: Locale, glass: Boolean) {
    val week = day.get(WeekFields.of(locale).weekOfWeekBasedYear())
    val color = if (glass) Color(0xD9FFFFFF) else Color(0xFF3A3A3A)
    val dim = if (glass) Color(0x8CFFFFFF) else Color(0x80000000)
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        BasicText(
            "${day.dayOfWeek.getDisplayName(JTextStyle.FULL, locale)}, ${day.dayOfMonth} ${day.month.getDisplayName(JTextStyle.FULL, locale)} ${day.year}",
            style = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = color),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            "DAY ${day.dayOfYear} OF ${day.lengthOfYear()} · WEEK $week · ${relative(day, today).uppercase(locale)}",
            style = labelStyle(dim, 10.5.sp),
        )
    }
}
