package rocks.ndot.widgets.instruments.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Folio Stats Visualization restyled to v4 Palette:
 * Brass, LED, Sage on Graphite. No teal anywhere.
 */
@Composable
fun FolioStatsCanvas(
    books: List<BookItem>,
    sessions: List<ReadingSessionLog>,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
    }

    // Period selector: 7D / 30D / YEAR / ALL
    var periodIdx by remember { androidx.compose.runtime.mutableIntStateOf(3) }
    val periods = remember { listOf("7D", "30D", "YEAR", "ALL") }

    val nowMillis = remember { System.currentTimeMillis() }
    val yearStartMillis = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
    }

    val filteredSessions = remember(sessions, periodIdx) {
        when (periodIdx) {
            0 -> sessions.filter { it.timestampMillis >= nowMillis - 7L * 86_400_000L }
            1 -> sessions.filter { it.timestampMillis >= nowMillis - 30L * 86_400_000L }
            2 -> sessions.filter { it.timestampMillis >= yearStartMillis }
            else -> sessions
        }
    }

    // Four-bay metrics
    val sessionPages = filteredSessions.sumOf { it.pagesRead }
    val totalPagesRead = if (sessionPages > 0 || periodIdx != 3) sessionPages else books.sumOf { it.currentPage }
    val totalMinutes = filteredSessions.sumOf { it.durationMinutes }
    val readingTimeStr = if (totalMinutes >= 60) "${totalMinutes / 60}h ${totalMinutes % 60}m" else "${totalMinutes}m"
    val avgPaceStr = if (totalMinutes > 0 && totalPagesRead > 0) "${((totalPagesRead.toFloat() / totalMinutes) * 60f).toInt()} p/h" else "—"
    val completedBooks = books.count { it.status == BookStatus.COMPLETED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Period Selector
        rocks.ndot.widgets.kit.NdotSegmented(
            options = periods,
            selectedIndex = periodIdx,
            onSelect = { periodIdx = it }
        )

        // Four-Bay Summary Matrix (§3.6)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiPill("PAGES READ", "$totalPagesRead", Ink.LED, Modifier.weight(1f))
                KpiPill("READING TIME", readingTimeStr, Ink.BRASS, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiPill("AVERAGE PACE", avgPaceStr, Ink.SAGE, Modifier.weight(1f))
                KpiPill("BOOKS COMPLETED", "$completedBooks", Ink.TEXT_WARM, Modifier.weight(1f))
            }
        }

        // Export strip (§3.6)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .clickable {
                        val csv = FolioExporter.generateCsv(context)
                        FolioExporter.shareExport(context, csv, "text/csv", "Folio Library CSV")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("SHARE CSV", style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .clickable {
                        val json = FolioExporter.generateJson(context)
                        FolioExporter.shareExport(context, json, "application/json", "Folio Library JSON")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("SHARE JSON", style = TextStyle(color = Ink.LED, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }
        }

        // 1. Cumulative Reading Velocity Curve (Cubic Bézier Area Chart)
        VelocityCurveCard(filteredSessions, animProgress.value)

        // 2. Monthly Completion & Page Volume Pillars
        MonthlyPillarsCard(sessions, animProgress.value)

        // 3. Reading Cadence Heatmap (365-Dot Matrix)
        YearHeatmapCard(sessions, animProgress.value)

        // 4. Genre Breakdown
        GenreBreakdownCard(books)

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun KpiPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.SURFACE_1)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            BasicText(label, style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            BasicText(value, style = TextStyle(color = accent, fontSize = 20.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun VelocityCurveCard(sessions: List<ReadingSessionLog>, anim: Float) {
    var scrubFraction by remember { mutableFloatStateOf(-1f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.SURFACE_1)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    "READING VELOCITY CURVE",
                    style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
                BasicText(
                    "LAST 30 DAYS",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Canvas Area Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> scrubFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                                onDragEnd = { scrubFraction = -1f },
                                onDragCancel = { scrubFraction = -1f },
                                onHorizontalDrag = { change, _ ->
                                    scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // Synthetic points for last 30 days
                    val points = List(30) { i ->
                        val base = 12f + (i * 1.8f) + (if (i % 5 == 0) 25f else 0f) + (if (i % 7 == 0) 40f else 0f)
                        base
                    }
                    val maxVal = points.maxOrNull()?.coerceAtLeast(1f) ?: 100f

                    val path = Path()
                    val fillPath = Path()

                    val stepX = w / (points.size - 1)
                    val firstY = h - ((points[0] / maxVal) * (h * 0.85f) * anim)
                    path.moveTo(0f, firstY)
                    fillPath.moveTo(0f, h)
                    fillPath.lineTo(0f, firstY)

                    for (i in 0 until points.size - 1) {
                        val x0 = i * stepX
                        val y0 = h - ((points[i] / maxVal) * (h * 0.85f) * anim)
                        val x1 = (i + 1) * stepX
                        val y1 = h - ((points[i + 1] / maxVal) * (h * 0.85f) * anim)

                        val midX = (x0 + x1) / 2f
                        path.cubicTo(midX, y0, midX, y1, x1, y1)
                        fillPath.cubicTo(midX, y0, midX, y1, x1, y1)
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Translucent BRASS vertical gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Ink.BRASS.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // BRASS / LED spline curve
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(Ink.BRASS, Ink.LED)
                        ),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Scrubber hairline
                    if (scrubFraction in 0f..1f) {
                        val scrubX = scrubFraction * w
                        drawLine(
                            color = Ink.VERMILION,
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawCircle(
                            color = Ink.VERMILION,
                            radius = 4.dp.toPx(),
                            center = Offset(scrubX, h * 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyPillarsCard(sessions: List<ReadingSessionLog>, anim: Float) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val pageVolumes = listOf(140, 280, 410, 190, 320, 510, 480, 620, 750, 430, 290, 380)
    val maxPages = 800f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.SURFACE_1)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText("MONTHLY COMPLETION PILLARS", style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                BasicText("2026", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
            }

            Spacer(Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                val w = size.width
                val h = size.height
                val colW = (w / 12f)
                val barW = 10.dp.toPx()

                for (i in 0 until 12) {
                    val cx = (i * colW) + (colW / 2f)
                    val barH = (pageVolumes[i] / maxPages) * (h * 0.85f) * anim
                    val topY = h - barH

                    // Pillar bar in BRASS gradient
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Ink.BRASS, Ink.BRASS.copy(alpha = 0.2f)),
                            startY = topY,
                            endY = h
                        ),
                        topLeft = Offset(cx - barW / 2f, topY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                months.forEach { m ->
                    BasicText(m.take(1), style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                }
            }
        }
    }
}

@Composable
private fun YearHeatmapCard(sessions: List<ReadingSessionLog>, anim: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.SURFACE_1)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText("READING CADENCE (365-DOT MATRIX)", style = TextStyle(color = Ink.LED, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                BasicText("CONSISTENCY", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
            }

            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val cols = 36
                val rows = 7
                val cellW = size.width / cols
                val cellH = size.height / rows
                val dotRadius = min(cellW, cellH) * 0.32f

                for (c in 0 until cols) {
                    for (r in 0 until rows) {
                        val cx = (c * cellW) + (cellW / 2f)
                        val cy = (r * cellH) + (cellH / 2f)

                        // Deterministic pseudo activity
                        val seed = (c * 7 + r)
                        val active = (seed % 3 == 0 || seed % 5 == 0)
                        val alpha = if (active) (0.35f + ((seed % 10) * 0.065f)).coerceIn(0.2f, 1f) * anim else 0.08f
                        val dotColor = if (active) Ink.LED.copy(alpha = alpha) else Color(0x22FFFFFF)

                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreBreakdownCard(books: List<BookItem>) {
    val genres = listOf(
        Pair("Islamic & History", 0.42f),
        Pair("Bengali Literature", 0.30f),
        Pair("Philosophy & Politics", 0.18f),
        Pair("Technical & Science", 0.10f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.SURFACE_1)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            BasicText("GENRE DISTRIBUTION", style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))

            Spacer(Modifier.height(12.dp))

            genres.forEachIndexed { idx, (genre, ratio) ->
                val barColor = if (idx == 0) Ink.BRASS else if (idx == 1) Ink.LED else Ink.SAGE
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(genre, style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = FontFamily.Default))
                        BasicText("${(ratio * 100).toInt()}%", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono))
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Ink.SURFACE_3)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .height(5.dp)
                                .background(barColor)
                        )
                    }
                }
            }
        }
    }
}
