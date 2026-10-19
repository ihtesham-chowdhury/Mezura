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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.kit.DotMatrixText
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.TextAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

// =============================================================================
// LOG SHEET (Section 3.1)
// =============================================================================

@Composable
fun HabitLogSheet(
    habit: HabitItem,
    epochDay: Long,
    existingEntry: Entry?,
    autoQuantity: Double,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, note: String?) -> Unit
) {
    val initialQuantity = existingEntry?.quantity ?: (if (habit.measure == Measure.DONE) 1.0 else 0.0)
    var quantity by remember { mutableDoubleStateOf(initialQuantity) }
    var note by remember { mutableStateOf(existingEntry?.note ?: "") }

    val totalEffort = quantity + autoQuantity
    val ratio = if (habit.target > 0.0) totalEffort / habit.target else 0.0
    val credit = ScoreEngine.credit(ratio)
    val tier = ScoreEngine.tier(ratio)
    val pct = (ratio * 100).roundToInt()
    val bricksEarned = ScoreEngine.periodBricks(habit, totalEffort)

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    BasicText(
                        text = "LOG PRACTICE",
                        style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = habit.name,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_3)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("×", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Auto-sourced badge if applicable
            if (autoQuantity > 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink.SURFACE_2)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "FROM FOLIO · ${autoQuantity.toInt()} ${habit.unit} recorded",
                            style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono)
                        )
                        BasicText("AUTO", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Stepper & Numeric Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decrement
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_2)
                        .clickable {
                            val step = if (habit.measure == Measure.MINUTES) 5.0 else 1.0
                            quantity = (quantity - step).coerceAtLeast(0.0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("−", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 22.sp, fontFamily = Fonts.mono))
                }

                Spacer(Modifier.width(20.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DotMatrixText(
                        text = if (habit.measure == Measure.MINUTES) "${quantity.toInt()}m" else "${quantity.toInt()}",
                        dotPitch = 2.4.dp,
                        litColor = Ink.TEXT_WARM,
                        glow = false
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        text = "/ ${habit.target.toInt()} ${habit.unit}",
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono)
                    )
                }

                Spacer(Modifier.width(20.dp))

                // Increment
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_2)
                        .clickable {
                            val step = if (habit.measure == Measure.MINUTES) 5.0 else 1.0
                            quantity += step
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("+", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 22.sp, fontFamily = Fonts.mono))
                }
            }

            // Quick-step chips (+5, +15, +30)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val chips = if (habit.measure == Measure.MINUTES) listOf(5, 15, 30) else listOf(1, 5, 10)
                chips.forEach { c ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Ink.SURFACE_3)
                            .clickable { quantity += c.toDouble() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicText("+$c", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Live Preview Card (Section 3.1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.SURFACE_2)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        BasicText(
                            text = "${totalEffort.toInt()} ${habit.unit} · $pct% · ${tier.name}",
                            style = TextStyle(color = if (tier == Tier.GOLD) Color(0xFFFFD08A) else Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = "Credit: ${String.format(Locale.US, "%.2f", credit)}",
                            style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Ink.SURFACE_3)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicText(
                            text = "+$bricksEarned BRICKS",
                            style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Note input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.sans),
                    cursorBrush = SolidColor(Ink.AMBER),
                    decorationBox = { inner ->
                        if (note.isBlank()) {
                            BasicText("Optional note...", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 13.sp))
                        }
                        inner()
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            // Save Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable { onSave(quantity, note.takeIf { it.isNotBlank() }) },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "SAVE EFFORT",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

// =============================================================================
// HABIT EDITOR SHEET (Section 3.2)
// =============================================================================

@Composable
fun HabitEditorSheet(
    habitToEdit: HabitItem? = null,
    onDismiss: () -> Unit,
    onSave: (HabitItem) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var name by remember { mutableStateOf(habitToEdit?.name ?: "") }
    var glyphKey by remember { mutableStateOf(habitToEdit?.glyphKey ?: "book") }
    var measure by remember { mutableStateOf(habitToEdit?.measure ?: Measure.COUNT) }
    var unit by remember { mutableStateOf(habitToEdit?.unit ?: if (measure == Measure.MINUTES) "min" else "pages") }
    var target by remember { mutableDoubleStateOf(habitToEdit?.target ?: if (measure == Measure.MINUTES) 25.0 else 20.0) }
    var minimum by remember { mutableStateOf(habitToEdit?.minimum?.toInt()?.toString() ?: "") }
    var stretch by remember { mutableStateOf(habitToEdit?.stretch?.toInt()?.toString() ?: "") }
    var period by remember { mutableStateOf(habitToEdit?.period ?: Period.DAY) }
    var daysOfWeek by remember { mutableStateOf(habitToEdit?.daysOfWeek ?: setOf(1, 2, 3, 4, 5, 6, 7)) }
    var weight by remember { mutableStateOf(habitToEdit?.weight ?: Weight.STANDARD) }
    var autoSource by remember { mutableStateOf(habitToEdit?.autoSource ?: AutoSource.NONE) }

    val scrollState = rememberScrollState()

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = if (habitToEdit == null) "NEW HABIT" else "EDIT HABIT",
                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_3)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("×", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Name
            BasicText("NAME", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp),
                    cursorBrush = SolidColor(Ink.AMBER),
                    decorationBox = { inner ->
                        if (name.isBlank()) BasicText("e.g. Read pages, Study, Handwriting...", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 13.sp))
                        inner()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Measure: DONE · COUNT · MINUTES
            BasicText("MEASURE", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(Measure.DONE, Measure.COUNT, Measure.MINUTES).forEach { m ->
                    val isSel = measure == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.AMBER else Ink.SURFACE_2)
                            .clickable {
                                measure = m
                                if (m == Measure.MINUTES) unit = "min"
                                else if (m == Measure.COUNT) unit = "pages"
                                else unit = "done"
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = m.name,
                            style = TextStyle(color = if (isSel) Ink.GROUND else Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Target & Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText("TARGET", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_2)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = "${target.toInt()}",
                            onValueChange = { it.toDoubleOrNull()?.let { v -> target = v } },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(Ink.AMBER)
                        )
                    }
                }

                if (measure == Measure.COUNT) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText("UNIT", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ink.SURFACE_2)
                                .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            BasicTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp),
                                cursorBrush = SolidColor(Ink.AMBER)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Period: PER DAY · PER WEEK · PER MONTH
            BasicText("PERIOD", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(Period.DAY, Period.WEEK, Period.MONTH).forEach { p ->
                    val isSel = period == p
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.AMBER else Ink.SURFACE_2)
                            .clickable { period = p }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = p.name,
                            style = TextStyle(color = if (isSel) Ink.GROUND else Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono)
                        )
                    }
                }
            }

            // Scheduled Days (if DAY)
            if (period == Period.DAY) {
                Spacer(Modifier.height(12.dp))
                BasicText("SCHEDULED DAYS", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
                    (1..7).forEach { dayInt ->
                        val isSel = dayInt in daysOfWeek
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSel) Ink.AMBER else Ink.SURFACE_2)
                                .clickable {
                                    daysOfWeek = if (isSel) daysOfWeek - dayInt else daysOfWeek + dayInt
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = dayNames[dayInt - 1],
                                style = TextStyle(color = if (isSel) Ink.GROUND else Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Importance (Weight): LIGHT · STANDARD · CORE
            BasicText("IMPORTANCE", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(Weight.LIGHT, Weight.STANDARD, Weight.CORE).forEach { w ->
                    val isSel = weight == w
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.AMBER else Ink.SURFACE_2)
                            .clickable { weight = w },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(
                                text = w.name,
                                style = TextStyle(
                                    color = if (isSel) Ink.GROUND else Ink.TEXT_WARM,
                                    fontSize = 11.sp,
                                    fontFamily = Fonts.mono,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            BasicText(
                                text = "${w.factor}×",
                                style = TextStyle(
                                    color = if (isSel) Ink.GROUND.copy(alpha = 0.8f) else Ink.TEXT_MUTED_V4,
                                    fontSize = 9.sp,
                                    fontFamily = Fonts.mono
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Explainer line (Section 3.2)
            val minVal = minimum.toDoubleOrNull() ?: target
            BasicText(
                text = "${target.toInt()} $unit earns a full day. Anything over ${minVal.toInt()} keeps your streak.",
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, lineHeight = 15.sp)
            )

            Spacer(Modifier.height(18.dp))

            // Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.AMBER)
                    .clickable {
                        if (name.isNotBlank()) {
                            val habit = (habitToEdit ?: HabitItem(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                startEpochDay = LocalDate.now().toEpochDay()
                            )).copy(
                                name = name.trim(),
                                glyphKey = glyphKey,
                                measure = measure,
                                unit = unit,
                                target = target.coerceAtLeast(1.0),
                                minimum = minimum.toDoubleOrNull(),
                                stretch = stretch.toDoubleOrNull(),
                                period = period,
                                daysOfWeek = daysOfWeek,
                                weight = weight,
                                autoSource = autoSource
                            )
                            onSave(habit)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "SAVE HABIT",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }

            if (habitToEdit != null && onDelete != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_2)
                        .clickable { onDelete(habitToEdit.id) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "DELETE HABIT",
                        style = TextStyle(color = Color(0xFFFF6B6B), fontSize = 12.sp, fontFamily = Fonts.mono)
                    )
                }
            }
        }
    }
}
