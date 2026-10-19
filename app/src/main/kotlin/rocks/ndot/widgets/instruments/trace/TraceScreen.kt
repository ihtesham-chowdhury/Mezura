package rocks.ndot.widgets.instruments.trace

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.GlassPanel
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.StepKey
import rocks.ndot.widgets.kit.TextAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun TraceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now().toEpochDay() }
    var selectedDay by remember { mutableLongStateOf(today) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf<ActivityType?>(null) } // null = All

    val baseMonday = today - (LocalDate.now().dayOfWeek.value - 1) + weekOffset * 7
    val weekDays = remember(baseMonday) { (0 until 7).map { baseMonday + it } }

    val events = remember(selectedDay) { TraceLedger.getDayEvents(context, selectedDay) }
    val summary = remember(selectedDay) { TraceLedger.getDaySummary(context, selectedDay) }

    val filteredEvents = remember(events, selectedFilter) {
        if (selectedFilter == null) events
        else events.filter { it.activityType == selectedFilter }
    }

    val selectedDate = LocalDate.ofEpochDay(selectedDay)
    val dateHeaderStr = selectedDate.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.ENGLISH)).uppercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        // 1. Header: TRACE, selected date, Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_2)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("←", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    BasicText("TRACE", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                    Spacer(Modifier.height(2.dp))
                    BasicText(dateHeaderStr, style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                }
            }

            if (selectedDay != today) {
                TextAction("TODAY", Ink.AMBER, { selectedDay = today; weekOffset = 0 })
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2. Day Selector Week Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepKey("◂", { weekOffset-- }, size = 32.dp)

            weekDays.forEach { day ->
                val date = LocalDate.ofEpochDay(day)
                val isSelected = (day == selectedDay)
                val isToday = (day == today)
                val dayName = date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase().take(3)
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
                        .clickable { selectedDay = day }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            text = dayName,
                            style = TextStyle(color = if (isSelected) Ink.GROUND else Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = dayNum,
                            style = TextStyle(
                                fontFamily = Fonts.condensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isSelected) Ink.GROUND else Ink.TEXT_WARM
                            )
                        )
                    }
                }
            }

            StepKey("▸", { weekOffset++ }, size = 32.dp)
        }

        Spacer(Modifier.height(14.dp))

        // 3. Day Summary Card
        val totalMins = summary.totalActiveDurationSeconds / 60
        val durationFormatted = if (totalMins >= 60) "${totalMins / 60}h ${totalMins % 60}m" else "${totalMins}m"

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
                    Column {
                        BasicText("TOTAL TRACKED TIME", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = durationFormatted,
                            style = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.mono)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_3)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = "${summary.totalSessionsCount} SESSIONS",
                            style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Activity Distribution Bar (Section 6.3)
                if (summary.totalActiveDurationSeconds > 0) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
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
                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                )
                                currentX += segW
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Legend & category breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActivityType.entries.filter { (summary.durationByType[it] ?: 0L) > 0 }.forEach { type ->
                            val durMins = (summary.durationByType[type] ?: 0L) / 60
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(type.color)
                                )
                                Spacer(Modifier.width(4.dp))
                                BasicText(
                                    text = "${type.label} ${durMins}m",
                                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                                )
                            }
                        }
                    }
                } else {
                    BasicText(
                        text = "No activities recorded on this day.",
                        style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 11.sp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 4. Category Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // "ALL" chip
            val isAllSel = selectedFilter == null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isAllSel) Ink.AMBER else Ink.SURFACE_2)
                    .clickable { selectedFilter = null }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                BasicText(
                    text = "ALL",
                    style = TextStyle(
                        color = if (isAllSel) Ink.GROUND else Ink.TEXT_WARM,
                        fontSize = 10.sp,
                        fontFamily = Fonts.mono,
                        fontWeight = if (isAllSel) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }

            // Category chips
            ActivityType.entries.forEach { type ->
                val isSel = selectedFilter == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSel) type.color else Ink.SURFACE_2)
                        .clickable { selectedFilter = type }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    BasicText(
                        text = type.label.uppercase(),
                        style = TextStyle(
                            color = if (isSel) Ink.GROUND else Ink.TEXT_WARM,
                            fontSize = 10.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 5. Chronological Event List (Section 6.3)
        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("NO EVENTS RECORDED", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    BasicText("Complete focus, reading or habit sessions to view activity.", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 11.sp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEvents, key = { it.eventId }) { event ->
                    TraceEventRow(event)
                }
                item {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        NavSpacer()
    }
}

@Composable
fun TraceEventRow(event: TraceEvent) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(event.activityType.color)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = event.formattedStartTime,
                            style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            text = event.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                    }

                    if (!event.metadata.isNullOrBlank() || !event.sequenceName.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        val extra = listOfNotNull(
                            event.sequenceName?.let { "Routine: $it" },
                            event.metadata
                        ).joinToString(" · ")
                        BasicText(
                            text = extra,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp)
                        )
                    }
                }
            }

            BasicText(
                text = event.formattedDuration,
                style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
        }
    }
}
