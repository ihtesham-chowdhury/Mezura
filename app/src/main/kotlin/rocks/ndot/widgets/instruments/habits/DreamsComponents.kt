package rocks.ndot.widgets.instruments.habits

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.kit.DotMatrixText
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.GlassPanel
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.TextAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// =============================================================================
// DREAMS TAB CONTENT (Section 4.1)
// =============================================================================

@Composable
fun DreamsTabContent(
    dreams: List<Dream>,
    goals: List<Goal>,
    achievements: List<Achievement>,
    habits: List<HabitItem>,
    entries: List<Entry>,
    todayEpoch: Long,
    onOpenDream: (Dream) -> Unit,
    onOpenGoal: (Goal) -> Unit,
    onNewItem: () -> Unit,
    onViewAchievement: (Achievement) -> Unit
) {
    var subTab by remember { mutableStateOf("ACTIVE") } // ACTIVE | HALL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        // Sub-filter: ACTIVE | HALL
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("ACTIVE", "HALL").forEach { tab ->
                    val isSel = subTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSel) Ink.AMBER else Color.Transparent)
                            .clickable { subTab = tab }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        BasicText(
                            text = tab,
                            style = TextStyle(
                                color = if (isSel) Ink.GROUND else Ink.TEXT_WARM,
                                fontSize = 10.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (subTab == "ACTIVE") {
            // 1. Active Dreams
            if (dreams.isEmpty() && goals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.SURFACE_2)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText("NO AMBITIONS RECORDED YET", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = Fonts.mono))
                        Spacer(Modifier.height(8.dp))
                        BasicText("Tap '+ NEW' to set a Habit, Goal or Dream.", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 12.sp))
                    }
                }
            } else {
                dreams.forEach { dream ->
                    DreamCard(dream, habits, entries, todayEpoch, onOpenDream)
                    Spacer(Modifier.height(12.dp))
                }

                // Goals on their own (goals with no dream)
                val unlinkedGoals = goals.filter { it.dreamId.isNullOrBlank() }
                if (unlinkedGoals.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = "GOALS ON THEIR OWN",
                        style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    unlinkedGoals.forEach { goal ->
                        GoalCard(goal, todayEpoch, onOpenGoal)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        } else {
            // HALL OF ACHIEVEMENTS (Section 4.6)
            HallOfAchievementsView(achievements, onViewAchievement)
        }

        Spacer(Modifier.height(32.dp))
    }
}

// -----------------------------------------------------------------------------
// DREAM CARD (Section 4.1)
// -----------------------------------------------------------------------------

@Composable
fun DreamCard(
    dream: Dream,
    habits: List<HabitItem>,
    entries: List<Entry>,
    todayEpoch: Long,
    onClick: (Dream) -> Unit
) {
    val fuel = remember(dream, habits, entries, todayEpoch) {
        ScoreEngine.calculateDreamFuel(dream, habits, entries, todayEpoch)
    }
    val doneRungs = dream.rungs.count { it.isDone }
    val totalRungs = dream.rungs.size
    val nextRung = dream.rungs.firstOrNull { !it.isDone }

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(dream) },
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: glyph, name, horizon chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_3),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("◆", style = TextStyle(color = Ink.AMBER, fontSize = 12.sp))
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = dream.name,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.SURFACE_3)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    BasicText(
                        text = dream.horizon.label(),
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                    )
                }
            }

            // Why sentence (muted)
            if (!dream.why.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = "Why: ${dream.why}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Ladder: horizontal timeline of rungs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ladder nodes
                Canvas(modifier = Modifier.weight(1f).height(16.dp)) {
                    val count = totalRungs.coerceAtLeast(1)
                    val w = size.width
                    val cy = size.height / 2f

                    // Base line
                    drawLine(
                        color = Color.White.copy(alpha = 0.15f),
                        start = Offset(6.dp.toPx(), cy),
                        end = Offset(w - 6.dp.toPx(), cy),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw rungs
                    for (i in 0 until count) {
                        val frac = if (count == 1) 0.5f else i.toFloat() / (count - 1).toFloat()
                        val rx = 6.dp.toPx() + frac * (w - 12.dp.toPx())
                        val isDone = i < doneRungs
                        val isCurrent = i == doneRungs

                        if (isDone) {
                            drawCircle(color = Ink.SAGE, radius = 5.dp.toPx(), center = Offset(rx, cy))
                        } else if (isCurrent) {
                            drawCircle(color = Ink.AMBER, radius = 6.dp.toPx(), center = Offset(rx, cy))
                            drawCircle(color = Ink.GROUND, radius = 3.dp.toPx(), center = Offset(rx, cy))
                        } else {
                            drawCircle(color = Ink.SURFACE_3, radius = 4.dp.toPx(), center = Offset(rx, cy))
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))
                BasicText(
                    text = "$doneRungs OF $totalRungs RUNGS",
                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Fuel line (Section 4.1)
            if (fuel != null) {
                val fuelPct = (fuel * 100).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("FUEL", style = TextStyle(color = Ink.AMBER, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Ink.SURFACE_3)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (fuel / 1.2).toFloat().coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(Ink.AMBER)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "LAST 30 DAYS · $fuelPct%",
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                    )
                }
            }

            // Next step line
            if (nextRung != null) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = "NEXT · ${nextRung.title}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// GOAL CARD (Section 4.1)
// -----------------------------------------------------------------------------

@Composable
fun GoalCard(
    goal: Goal,
    todayEpoch: Long,
    onClick: (Goal) -> Unit
) {
    val pace = remember(goal, todayEpoch) {
        ScoreEngine.calculateGoalPace(goal, todayEpoch, emptyList(), emptyList(), emptyList(), emptyList())
    }

    val paceTag = when (pace.status) {
        GoalPaceStatus.AHEAD -> "AHEAD ${pace.daysAhead?.roundToInt() ?: 0}D"
        GoalPaceStatus.BEHIND -> "BEHIND ${abs(pace.daysAhead?.roundToInt() ?: 0)}D"
        GoalPaceStatus.ON_TRACK -> "ON TRACK"
    }
    val paceColor = when (pace.status) {
        GoalPaceStatus.AHEAD -> Ink.SAGE
        GoalPaceStatus.BEHIND -> Ink.AMBER
        GoalPaceStatus.ON_TRACK -> Ink.TEXT_MUTED_V4
    }

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(goal) },
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = goal.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Ink.SURFACE_3)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    BasicText("GOAL", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 8.sp, fontFamily = Fonts.mono))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Ink.SURFACE_3)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = pace.progress.toFloat().coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(Ink.AMBER)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Pace info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(
                    text = paceTag,
                    style = TextStyle(color = paceColor, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
                if (goal.targetEpochDay != null) {
                    val dateStr = LocalDate.ofEpochDay(goal.targetEpochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase()
                    BasicText(
                        text = "FINISH BY $dateStr",
                        style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HALL OF ACHIEVEMENTS VIEW (Section 4.6)
// -----------------------------------------------------------------------------

@Composable
fun HallOfAchievementsView(
    achievements: List<Achievement>,
    onViewAchievement: (Achievement) -> Unit
) {
    if (achievements.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "Your first achievement will be framed here.",
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 13.sp, fontFamily = Fonts.mono)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            achievements.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { ach ->
                        Box(modifier = Modifier.weight(1f)) {
                            AchievementPlaqueCard(ach, onClick = { onViewAchievement(ach) })
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun AchievementPlaqueCard(
    achievement: Achievement,
    onClick: () -> Unit
) {
    val dateStr = LocalDate.ofEpochDay(achievement.epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)).uppercase()
    val deltaLabel = when {
        achievement.deltaDays != null && achievement.deltaDays > 0 -> "${achievement.deltaDays}D EARLY"
        achievement.deltaDays != null && achievement.deltaDays == 0 -> "ON TIME"
        achievement.actualDays != null && achievement.plannedDays != null -> "TOOK ${achievement.actualDays}D"
        else -> "ACHIEVED"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.SURFACE_2)
            .border(1.dp, Ink.AMBER.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("★", style = TextStyle(color = Ink.AMBER, fontSize = 12.sp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Ink.SURFACE_3)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    BasicText(deltaLabel, style = TextStyle(color = Ink.SAGE, fontSize = 8.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }

            Spacer(Modifier.height(8.dp))
            BasicText(
                text = achievement.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = dateStr,
                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono)
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "+${achievement.bricks} BRICKS",
                style = TextStyle(color = Ink.AMBER, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// ACHIEVEMENT CEREMONY SHEET (Section 4.5)
// -----------------------------------------------------------------------------

@Composable
fun AchievementCeremonySheet(
    achievement: Achievement,
    onDone: () -> Unit
) {
    val dateStr = LocalDate.ofEpochDay(achievement.epochDay).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))

    NdotSheet(onDismiss = onDone) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Engraved Brass Plaque Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.SURFACE_1)
                    .border(1.5.dp, Ink.AMBER, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("★  ★  ★", style = TextStyle(color = Ink.AMBER, fontSize = 14.sp))
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = "ACHIEVED",
                        style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = achievement.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = dateStr,
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bricks and Delta chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink.SURFACE_2)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText("BRICKS EARNED", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                        BasicText("+${achievement.bricks}", style = TextStyle(color = Ink.AMBER, fontSize = 18.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                    }
                }

                if (achievement.landmark != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_2)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText("LANDMARK UNLOCKED", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                            BasicText(achievement.landmark.label.uppercase(), style = TextStyle(color = Ink.SAGE, fontSize = 14.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Confirmation Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable { onDone() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "CONFIRM & PLACE IN HALL",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
