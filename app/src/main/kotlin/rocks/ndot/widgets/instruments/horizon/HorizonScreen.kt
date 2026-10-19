package rocks.ndot.widgets.instruments.horizon

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.kit.DotMatrixText
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.GlassPanel
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.NdotSegmented
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ndotScreenInsets
import rocks.ndot.widgets.widgets.TimerEngine
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.roundToInt

private const val MAX_BLOCKS = 12
private const val HORIZON_PREFS = "horizon_prefs"

private val BLOCK_COLORS = listOf(
    Ink.AMBER,
    Ink.BRASS,
    Ink.LED,
    Ink.SAGE,
    Ink.VERMILION,
    Color(0xFF7E8799)
)

@Composable
fun HorizonScreen() {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    // Current clock time state updated every 5s
    var nowMinutes by remember {
        val now = LocalTime.now()
        mutableIntStateOf(now.hour * 60 + now.minute)
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            nowMinutes = now.hour * 60 + now.minute
            delay(5000L)
        }
    }

    // Horizon Settings & Preferences
    var wakeMin by remember { mutableIntStateOf(loadWakeMin(context)) }
    var sleepMin by remember { mutableIntStateOf(loadSleepMin(context)) }
    var isAnchoredToday by remember { mutableStateOf(isWakeAnchoredToday(context)) }
    var showEnergyWave by remember {
        mutableStateOf(AppPrefs.getDefault(context, "horizon_show_wave", "true") == "true")
    }
    var scrubbedMin by remember { mutableStateOf<Int?>(null) }
    var chronotype by remember { mutableStateOf(loadChronotype(context)) }
    var caffeineHours by remember { mutableDoubleStateOf(loadCaffeineHours(context)) }

    // Blocks (support up to 12)
    var blocks by remember { mutableStateOf(loadBlocks(context)) }

    // Sheets & Dialogs
    var editingBlock by remember { mutableStateOf<HorizonBlock?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    // Ghost block during press-and-drag in Day Ribbon mode
    var ghostStartMin by remember { mutableStateOf<Int?>(null) }
    var ghostEndMin by remember { mutableStateOf<Int?>(null) }

    // Alertness Windows & Model
    val windows = remember(wakeMin, sleepMin, chronotype) {
        EnergyModel.findWindows(wakeMin, sleepMin, chronotype.shiftHours)
    }
    val caffeineCutMin = remember(sleepMin, caffeineHours) {
        EnergyModel.caffeineCutoffMin(sleepMin, caffeineHours)
    }

    // Current Alertness
    val wakingMins = (sleepMin - wakeMin).coerceAtLeast(60)
    val wakingHours = wakingMins / 60.0
    val tauNow = ((nowMinutes - wakeMin).coerceAtLeast(0)) / 60.0
    val currentAlertness = EnergyModel.alertness(tauNow, wakingHours, chronotype.shiftHours)
    val capacityPct = (currentAlertness * 100).roundToInt()

    // Status label & elapsed
    val elapsedMins = (nowMinutes - wakeMin).coerceIn(0, wakingMins)
    val elapsedPct = (elapsedMins * 100 / wakingMins)
    val remainingMins = (sleepMin - nowMinutes).coerceAtLeast(0)
    val remainingHoursStr = "${remainingMins / 60}H ${remainingMins % 60}M"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .ndotScreenInsets()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    DotMatrixText(
                        text = "DAY HORIZON",
                        dotPitch = 2.4.dp,
                        litColor = Ink.TEXT_WARM,
                        glow = false
                    )
                    Spacer(Modifier.height(2.dp))
                    val wakeStr = String.format("%02d:%02d", wakeMin / 60, wakeMin % 60)
                    val sleepStr = String.format("%02d:%02d", sleepMin / 60, sleepMin % 60)
                    BasicText(
                        text = if (isAnchoredToday) "ANCHORED $wakeStr–$sleepStr · ${(wakingMins / 60)}H DAY" else "WAKE $wakeStr–$sleepStr · ${(wakingMins / 60)}H DAY",
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 〰 Energy Wave Toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (showEnergyWave) Ink.AMBER.copy(alpha = 0.25f) else Ink.SURFACE_2)
                            .border(1.dp, if (showEnergyWave) Ink.AMBER else Ink.HAIRLINE_V4, CircleShape)
                            .clickable {
                                showEnergyWave = !showEnergyWave
                                Feedback.tick(context)
                                AppPrefs.setDefault(context, "horizon_show_wave", showEnergyWave.toString(), "Horizon Energy Wave")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("〰", style = TextStyle(color = if (showEnergyWave) Ink.AMBER else Ink.TEXT_MUTED_V4, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    }

                    // ANCHOR WAKE Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isAnchoredToday) Ink.SURFACE_3 else Ink.AMBER)
                            .border(1.dp, if (isAnchoredToday) Ink.HAIRLINE_V4 else Ink.AMBER, RoundedCornerShape(999.dp))
                            .clickable {
                                val roundedNow = (nowMinutes / 5) * 5
                                wakeMin = roundedNow
                                isAnchoredToday = true
                                anchorWakeToday(context, roundedNow)
                                Feedback.lock(context)
                                Toast.makeText(context, "Wake anchored to ${String.format("%02d:%02d", roundedNow / 60, roundedNow % 60)}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = if (isAnchoredToday) "ANCHORED" else "ANCHOR WAKE",
                            style = TextStyle(
                                color = if (isAnchoredToday) Ink.TEXT_MUTED_V4 else Ink.GROUND,
                                fontSize = 10.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // ? Guide Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_2)
                            .clickable { showInfoSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("?", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.mono))
                    }

                    // Settings Button (⚙)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_2)
                            .clickable { showSettingsSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("⚙", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 15.sp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Horizon Sub-Bar: Forecast Heuristic & + ADD BLOCK
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val bandLabel = when {
                        capacityPct >= 80 -> "PEAK FOCUS · $capacityPct%"
                        capacityPct >= 60 -> "STEADY ALERTNESS · $capacityPct%"
                        capacityPct >= 45 -> "POST-LUNCH DIP · $capacityPct%"
                        else -> "WIND-DOWN · $capacityPct%"
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (capacityPct >= 75) Ink.AMBER else Ink.LED)
                    )
                    BasicText(
                        text = bandLabel,
                        style = TextStyle(color = if (capacityPct >= 75) Ink.AMBER else Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }

                if (blocks.size < MAX_BLOCKS) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Ink.SURFACE_2)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                            .clickable {
                                val candidateStart = ((nowMinutes / 15) * 15).coerceIn(wakeMin, (sleepMin - 30).coerceAtLeast(wakeMin))
                                val newBlock = HorizonBlock(
                                    id = UUID.randomUUID().toString(),
                                    label = "DEEP WORK",
                                    startMin = candidateStart,
                                    endMin = (candidateStart + 45).coerceAtMost(sleepMin),
                                    colorIdx = blocks.size % BLOCK_COLORS.size
                                )
                                editingBlock = newBlock
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = "+ ADD BLOCK (${blocks.size}/$MAX_BLOCKS)",
                            style = TextStyle(color = Ink.AMBER, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Body Area: Fused Simultaneous Day Ribbon + Energy Wave
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DayHorizonFusedView(
                    wakeMin = wakeMin,
                    sleepMin = sleepMin,
                    nowMinutes = nowMinutes,
                    wakingMins = wakingMins,
                    wakingHours = wakingHours,
                    chronotypeShift = chronotype.shiftHours,
                    density = density,
                    blocks = blocks,
                    windows = windows,
                    caffeineCutMin = caffeineCutMin,
                    capacityPct = capacityPct,
                    showEnergyWave = showEnergyWave,
                    ghostStartMin = ghostStartMin,
                    ghostEndMin = ghostEndMin,
                    onDragGhost = { s, e -> ghostStartMin = s; ghostEndMin = e },
                    onNewBlock = { editingBlock = it },
                    onEditBlock = { editingBlock = it },
                    onOpenGuide = { showInfoSheet = true }
                )
            }

            Spacer(Modifier.height(6.dp))

            // Footer Status Line
            val nextBlock = blocks.filter { it.startMin > nowMinutes }.minByOrNull { it.startMin }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "$elapsedPct% ELAPSED · $remainingHoursStr REMAINING",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono)
                )

                if (nextBlock != null) {
                    val inMins = nextBlock.startMin - nowMinutes
                    BasicText(
                        text = "NEXT · ${nextBlock.label} IN ${inMins}M",
                        style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono)
                    )
                }
            }

            NavSpacer()
        }

        // Edit Block Sheet
        editingBlock?.let { b ->
            EditBlockSheet(
                block = b,
                wakeMin = wakeMin,
                sleepMin = sleepMin,
                existingBlocks = blocks.filter { it.id != b.id },
                chronotypeShift = chronotype.shiftHours,
                onDismiss = { editingBlock = null },
                onSave = { updated ->
                    val mutable = blocks.filter { it.id != updated.id }.toMutableList()
                    mutable.add(updated)
                    blocks = mutable
                    saveBlocks(context, blocks)
                    editingBlock = null
                    Feedback.tick(context)
                },
                onDelete = {
                    blocks = blocks.filter { it.id != b.id }
                    saveBlocks(context, blocks)
                    editingBlock = null
                    Feedback.tick(context)
                }
            )
        }

        // Settings Sheet (⚙)
        if (showSettingsSheet) {
            NdotSheet(onDismiss = { showSettingsSheet = false }) {
                BasicText("DAY HORIZON SETTINGS", style = TextStyle(color = Ink.AMBER, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(14.dp))

                // Wake & Sleep Steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        BasicText("WAKING WINDOW", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                        val wStr = String.format("%02d:%02d", wakeMin / 60, wakeMin % 60)
                        val sStr = String.format("%02d:%02d", sleepMin / 60, sleepMin % 60)
                        BasicText("$wStr – $sStr", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ink.SURFACE_3)
                                .clickable {
                                    wakeMin = (wakeMin - 15).coerceAtLeast(4 * 60)
                                    saveWakeMin(context, wakeMin)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            BasicText("−15m WAKE", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 10.sp, fontFamily = Fonts.mono))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ink.SURFACE_3)
                                .clickable {
                                    wakeMin = (wakeMin + 15).coerceAtMost(sleepMin - 120)
                                    saveWakeMin(context, wakeMin)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            BasicText("+15m WAKE", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 10.sp, fontFamily = Fonts.mono))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Chronotype Selector
                BasicText("CHRONOTYPE PREFERENCE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chronotype.entries.forEach { ct ->
                        val isSel = chronotype == ct
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Ink.AMBER else Ink.SURFACE_3)
                                .clickable {
                                    chronotype = ct
                                    saveChronotype(context, ct)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = ct.label,
                                style = TextStyle(
                                    color = if (isSel) Ink.GROUND else Ink.TEXT_WARM,
                                    fontSize = 11.sp,
                                    fontFamily = Fonts.mono,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Caffeine Hours Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        BasicText("CAFFEINE CUTOFF", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                        BasicText("${caffeineHours.toInt()}h before sleep", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = Fonts.mono))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Ink.SURFACE_3)
                                .clickable {
                                    caffeineHours = (caffeineHours - 1).coerceAtLeast(6.0)
                                    saveCaffeineHours(context, caffeineHours)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("−", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp))
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Ink.SURFACE_3)
                                .clickable {
                                    caffeineHours = (caffeineHours + 1).coerceAtMost(12.0)
                                    saveCaffeineHours(context, caffeineHours)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("+", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp))
                        }
                    }
                }
            }
        }

        // Comprehensive "What is Horizon?" Guide Sheet
        if (showInfoSheet) {
            WhatIsHorizonGuideSheet(
                chronotype = chronotype,
                onChronotypeChange = { ct ->
                    chronotype = ct
                    saveChronotype(context, ct)
                },
                blocksCount = blocks.size,
                onAddStarterPlan = {
                    val starter = listOf(
                        HorizonBlock(UUID.randomUUID().toString(), "DEEP WORK", wakeMin + 120, wakeMin + 210, 0),
                        HorizonBlock(UUID.randomUUID().toString(), "READING", wakeMin + 300, wakeMin + 345, 1),
                        HorizonBlock(UUID.randomUUID().toString(), "WALK & RESET", wakeMin + 420, wakeMin + 465, 3)
                    )
                    blocks = starter
                    saveBlocks(context, blocks)
                    showInfoSheet = false
                    Toast.makeText(context, "Loaded starter day plan", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showInfoSheet = false }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Fused Simultaneous Day Ribbon + Energy Wave Instrument
// -----------------------------------------------------------------------------

@Composable
private fun DayHorizonFusedView(
    wakeMin: Int,
    sleepMin: Int,
    nowMinutes: Int,
    wakingMins: Int,
    wakingHours: Double,
    chronotypeShift: Double,
    density: Float,
    blocks: List<HorizonBlock>,
    windows: List<AlertnessWindow>,
    caffeineCutMin: Int,
    capacityPct: Int,
    showEnergyWave: Boolean,
    ghostStartMin: Int?,
    ghostEndMin: Int?,
    onDragGhost: (Int?, Int?) -> Unit,
    onNewBlock: (HorizonBlock) -> Unit,
    onEditBlock: (HorizonBlock) -> Unit,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val availableW = constraints.maxWidth.toFloat()
            val availableH = constraints.maxHeight.toFloat()
            val pxPerMin = availableH / wakingMins.toFloat()
            val ribbonW = 52f * density

            val ribbonLeft = if (showEnergyWave) {
                (availableW * 0.36f - ribbonW / 2f).coerceAtLeast(availableW * 0.28f)
            } else {
                (availableW - ribbonW) / 2f
            }
            val ribbonRight = ribbonLeft + ribbonW

            // Touch Gestures on ribbon for press-and-drag block creation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(blocks.size, wakeMin, sleepMin) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (offset.x in (ribbonLeft - 16f)..(ribbonRight + 16f) && blocks.size < MAX_BLOCKS) {
                                    val minAtY = (wakeMin + (offset.y / pxPerMin).toInt()).coerceIn(wakeMin, sleepMin)
                                    val s = (minAtY / 15) * 15
                                    onDragGhost(s, s + 30)
                                    Feedback.tick(context)
                                }
                            },
                            onDragEnd = {
                                val s = ghostStartMin
                                val e = ghostEndMin
                                if (s != null && e != null && (e - s) >= 15) {
                                    val newBlock = HorizonBlock(
                                        id = UUID.randomUUID().toString(),
                                        label = "FOCUS",
                                        startMin = s,
                                        endMin = e,
                                        colorIdx = blocks.size % BLOCK_COLORS.size
                                    )
                                    onNewBlock(newBlock)
                                }
                                onDragGhost(null, null)
                            },
                            onDragCancel = { onDragGhost(null, null) },
                            onDrag = { change, _ ->
                                val minAtY = (wakeMin + (change.position.y / pxPerMin).toInt()).coerceIn(wakeMin, sleepMin)
                                val roundedMin = (minAtY / 15) * 15
                                ghostStartMin?.let { s ->
                                    if (roundedMin > s) {
                                        if (ghostEndMin != roundedMin) {
                                            onDragGhost(s, roundedMin)
                                            Feedback.tick(context)
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 1. Circadian underlay zones behind ribbon
                    windows.forEach { win ->
                        val topY = (win.startMin - wakeMin) * pxPerMin
                        val botY = (win.endMin - wakeMin) * pxPerMin
                        val col = if (win.type == WindowType.PEAK_FOCUS) Ink.AMBER.copy(alpha = 0.08f) else Color(0xFF7E8799).copy(alpha = 0.08f)
                        drawRoundRect(
                            color = col,
                            topLeft = Offset(ribbonLeft - 6.dp.toPx(), topY),
                            size = Size(ribbonW + 12.dp.toPx(), (botY - topY).coerceAtLeast(4f)),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }

                    // 2. Day Ribbon Body & Glass
                    drawDayRibbon(
                        ribbonLeft = ribbonLeft,
                        ribbonRight = ribbonRight,
                        ribbonW = ribbonW,
                        totalH = availableH,
                        wakeMin = wakeMin,
                        sleepMin = sleepMin,
                        nowMin = nowMinutes,
                        pxPerMin = pxPerMin
                    )

                    // 3. Caffeine Cutoff Line across screen
                    if (caffeineCutMin in wakeMin..sleepMin) {
                        val cutY = (caffeineCutMin - wakeMin) * pxPerMin
                        drawLine(
                            color = Ink.AMBER.copy(alpha = 0.45f),
                            start = Offset(0f, cutY),
                            end = Offset(availableW, cutY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }

                    // 4. Active Blocks inside ribbon
                    blocks.forEach { b ->
                        val byTop = (b.startMin - wakeMin) * pxPerMin
                        val byBottom = (b.endMin - wakeMin) * pxPerMin
                        val bColor = BLOCK_COLORS[b.colorIdx % BLOCK_COLORS.size]

                        drawRoundRect(
                            color = bColor.copy(alpha = 0.35f),
                            topLeft = Offset(ribbonLeft + 2.dp.toPx(), byTop),
                            size = Size(ribbonW - 4.dp.toPx(), (byBottom - byTop).coerceAtLeast(4f)),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        drawRoundRect(
                            color = bColor.copy(alpha = 0.85f),
                            topLeft = Offset(ribbonLeft + 2.dp.toPx(), byTop),
                            size = Size(ribbonW - 4.dp.toPx(), (byBottom - byTop).coerceAtLeast(4f)),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    // Ghost block while dragging
                    if (ghostStartMin != null && ghostEndMin != null) {
                        val gyTop = (ghostStartMin - wakeMin) * pxPerMin
                        val gyBottom = (ghostEndMin - wakeMin) * pxPerMin
                        drawRoundRect(
                            color = Ink.AMBER.copy(alpha = 0.5f),
                            topLeft = Offset(ribbonLeft + 2.dp.toPx(), gyTop),
                            size = Size(ribbonW - 4.dp.toPx(), (gyBottom - gyTop).coerceAtLeast(4f)),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // 5. If showEnergyWave is true, render oscilloscope energy wave on the right
                    if (showEnergyWave) {
                        val waveLeft = ribbonRight + 12f * density
                        val waveRight = availableW - 10f * density
                        val waveW = (waveRight - waveLeft).coerceAtLeast(20f)

                        // Vertical Alertness Level Grid Lines (25%, 50%, 75%, 100%)
                        listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { lvl ->
                            val gx = waveLeft + lvl * waveW
                            drawLine(
                                color = Color.White.copy(alpha = 0.07f),
                                start = Offset(gx, 0f),
                                end = Offset(gx, availableH),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                            )
                        }

                        // Block shaded bands across wave
                        blocks.forEach { b ->
                            val by1 = (b.startMin - wakeMin) * pxPerMin
                            val by2 = (b.endMin - wakeMin) * pxPerMin
                            val bCol = BLOCK_COLORS[b.colorIdx % BLOCK_COLORS.size]
                            drawRoundRect(
                                color = bCol.copy(alpha = 0.12f),
                                topLeft = Offset(waveLeft, by1),
                                size = Size(waveW, (by2 - by1).coerceAtLeast(2.dp.toPx())),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }

                        // 7 Echo Waves (amplitude scaled)
                        for (k in 1..7) {
                            val factor = 1f - (k / 8f)
                            val echoAlpha = (0.22f * factor).coerceIn(0.03f, 0.22f)
                            val dotRadius = 1.1.dp.toPx()

                            var m = wakeMin
                            while (m <= sleepMin) {
                                val y = (m - wakeMin) * pxPerMin
                                val tau = (m - wakeMin) / 60.0
                                val a = EnergyModel.alertness(tau, wakingHours, chronotypeShift)
                                val x = waveLeft + (a.toFloat() * factor) * waveW
                                drawCircle(
                                    color = Ink.AMBER.copy(alpha = echoAlpha),
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                                m += 6
                            }
                        }

                        // Primary Alertness Wave
                        var m = wakeMin
                        while (m <= sleepMin) {
                            val y = (m - wakeMin) * pxPerMin
                            val tau = (m - wakeMin) / 60.0
                            val a = EnergyModel.alertness(tau, wakingHours, chronotypeShift)
                            val x = waveLeft + a.toFloat() * waveW
                            val isElapsed = m < nowMinutes
                            val mainAlpha = if (isElapsed) 0.40f else 1.0f
                            val dotColor = lerp(Color.White, Color(0xFFFF9F5A), a.toFloat()).copy(alpha = mainAlpha)
                            drawCircle(
                                color = dotColor,
                                radius = 1.7.dp.toPx(),
                                center = Offset(x, y)
                            )
                            m += 4
                        }
                    }

                    // 6. NOW Playhead Laser Line & Glowing Pill across ribbon
                    val nowFrac = ((nowMinutes - wakeMin).toFloat() / wakingMins.toFloat()).coerceIn(0f, 1f)
                    val nowY = nowFrac * availableH

                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(0f, nowY),
                        end = Offset(availableW, nowY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val pillW = 22.dp.toPx()
                    val pillH = 10.dp.toPx()
                    val pillCenter = (ribbonLeft + ribbonRight) / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.40f),
                        topLeft = Offset(pillCenter - pillW / 2 - 2.dp.toPx(), nowY - pillH / 2 - 2.dp.toPx()),
                        size = Size(pillW + 4.dp.toPx(), pillH + 4.dp.toPx()),
                        cornerRadius = CornerRadius(pillH, pillH)
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(pillCenter - pillW / 2, nowY - pillH / 2),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(pillH, pillH)
                    )
                }

                // Gutter Callouts
                val leftGutterWidthDp = ((ribbonLeft - 6f * density) / density).coerceAtLeast(0f).dp
                val rightGutterLeftDp = ((ribbonRight + 6f * density) / density).dp
                val rightGutterWidthDp = ((availableW - ribbonRight - 6f * density) / density).coerceAtLeast(0f).dp

                val sortedBlocks = remember(blocks) { blocks.sortedBy { it.startMin } }
                sortedBlocks.forEachIndexed { idx, b ->
                    val bY = ((b.startMin - wakeMin) * pxPerMin).coerceIn(0f, availableH - 22f * density)
                    val bTimeStr = String.format("%02d:%02d", b.startMin / 60, b.startMin % 60)
                    val bColor = BLOCK_COLORS[b.colorIdx % BLOCK_COLORS.size]

                    if (showEnergyWave || idx % 2 == 0) {
                        // Left Gutter (end aligned)
                        Row(
                            modifier = Modifier
                                .offset { IntOffset(x = 0, y = bY.toInt()) }
                                .width(leftGutterWidthDp)
                                .clickable { onEditBlock(b) }
                                .padding(end = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DotMatrixText(
                                text = bTimeStr,
                                dotPitch = 1.8.dp,
                                litColor = bColor,
                                glow = false
                            )
                            Spacer(Modifier.width(4.dp))
                            BasicText(
                                text = b.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = Ink.TEXT_WARM,
                                    fontSize = 11.sp,
                                    fontFamily = Fonts.mono,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(Modifier.width(3.dp))
                            BasicText("─►", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp))
                        }
                    } else {
                        // Right Gutter (start aligned when wave is hidden)
                        Row(
                            modifier = Modifier
                                .offset { IntOffset(x = (ribbonRight + 6f * density).toInt(), y = bY.toInt()) }
                                .width(rightGutterWidthDp)
                                .clickable { onEditBlock(b) }
                                .padding(start = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText("◄─", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp))
                            Spacer(Modifier.width(3.dp))
                            BasicText(
                                text = b.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = Ink.TEXT_WARM,
                                    fontSize = 11.sp,
                                    fontFamily = Fonts.mono,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(Modifier.width(4.dp))
                            DotMatrixText(
                                text = bTimeStr,
                                dotPitch = 1.8.dp,
                                litColor = bColor,
                                glow = false
                            )
                        }
                    }
                }

                // Empty plan hint
                if (blocks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_2)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        BasicText(
                            text = "Press & drag on ribbon or tap + ADD BLOCK",
                            style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 11.sp, fontFamily = Fonts.sans)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Bottom Telemetry Strip (2 GlassPanels)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Forecast heuristic tile
            GlassPanel(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenGuide() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    val bandLabel = when {
                        capacityPct >= 80 -> "HIGH ALERTNESS"
                        capacityPct >= 60 -> "STEADY FOCUS"
                        capacityPct >= 45 -> "POST-LUNCH DIP"
                        else -> "WIND-DOWN / REST"
                    }
                    BasicText("FOCUS FORECAST (HEURISTIC)", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(2.dp))
                    DotMatrixText(
                        text = bandLabel,
                        dotPitch = 1.9.dp,
                        litColor = if (capacityPct >= 75) Ink.AMBER else Ink.TEXT_WARM,
                        glow = false
                    )
                }
            }

            // Active or Next Block tile with focus timer launch
            val activeBlock = blocks.firstOrNull { nowMinutes in it.startMin..it.endMin }
            val nextBlock = blocks.filter { it.startMin > nowMinutes }.minByOrNull { it.startMin }

            GlassPanel(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (activeBlock != null) {
                            TimerEngine.start(context)
                            Toast.makeText(context, "Timer launched for ${activeBlock.label}", Toast.LENGTH_SHORT).show()
                        } else if (nextBlock != null) {
                            onEditBlock(nextBlock)
                        } else if (blocks.size < MAX_BLOCKS) {
                            val candidateStart = ((nowMinutes / 15) * 15).coerceIn(wakeMin, (sleepMin - 30).coerceAtLeast(wakeMin))
                            onNewBlock(
                                HorizonBlock(
                                    id = UUID.randomUUID().toString(),
                                    label = "FOCUS",
                                    startMin = candidateStart,
                                    endMin = (candidateStart + 45).coerceAtMost(sleepMin),
                                    colorIdx = blocks.size % BLOCK_COLORS.size
                                )
                            )
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (activeBlock != null) {
                        val remaining = activeBlock.endMin - nowMinutes
                        BasicText("ACTIVE · ${activeBlock.label} (${remaining}M)", style = TextStyle(color = Ink.AMBER, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(2.dp))
                        DotMatrixText(
                            text = "START FOCUS",
                            dotPitch = 1.9.dp,
                            litColor = Ink.AMBER,
                            glow = true
                        )
                    } else if (nextBlock != null) {
                        val inMins = nextBlock.startMin - nowMinutes
                        BasicText("UPCOMING BLOCK", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                        Spacer(Modifier.height(2.dp))
                        DotMatrixText(
                            text = "${nextBlock.label} IN ${inMins}M",
                            dotPitch = 1.9.dp,
                            litColor = Ink.TEXT_WARM,
                            glow = false
                        )
                    } else {
                        BasicText("SCHEDULE OPEN", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                        Spacer(Modifier.height(2.dp))
                        DotMatrixText(
                            text = "+ PLAN BLOCK",
                            dotPitch = 1.9.dp,
                            litColor = Ink.TEXT_WARM,
                            glow = false
                        )
                    }
                }
            }
        }
    }
}


// -----------------------------------------------------------------------------
// Canvas Drawing: Frosted Day Ribbon
// -----------------------------------------------------------------------------

private fun DrawScope.drawDayRibbon(
    ribbonLeft: Float,
    ribbonRight: Float,
    ribbonW: Float,
    totalH: Float,
    wakeMin: Int,
    sleepMin: Int,
    nowMin: Int,
    pxPerMin: Float
) {
    val cornerR = ribbonW / 2f

    // Ambient glow behind ribbon
    drawRoundRect(
        color = Ink.AMBER.copy(alpha = 0.06f),
        topLeft = Offset(ribbonLeft - 4.dp.toPx(), 0f),
        size = Size(ribbonW + 8.dp.toPx(), totalH),
        cornerRadius = CornerRadius(cornerR, cornerR)
    )

    // Frosted glass base: white 9% over SURFACE_1
    drawRoundRect(
        color = Color.White.copy(alpha = 0.09f),
        topLeft = Offset(ribbonLeft, 0f),
        size = Size(ribbonW, totalH),
        cornerRadius = CornerRadius(cornerR, cornerR)
    )

    // Smoked elapsed section above NOW
    val wakingMins = (sleepMin - wakeMin).coerceAtLeast(60)
    val nowFrac = ((nowMin - wakeMin).toFloat() / wakingMins.toFloat()).coerceIn(0f, 1f)
    val nowY = nowFrac * totalH

    if (nowY > 0f) {
        val elapsedPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(ribbonLeft, 0f, ribbonRight, nowY),
                    topLeft = CornerRadius(cornerR, cornerR),
                    topRight = CornerRadius(cornerR, cornerR),
                    bottomLeft = CornerRadius(0f, 0f),
                    bottomRight = CornerRadius(0f, 0f)
                )
            )
        }
        drawPath(elapsedPath, color = Ink.GROUND.copy(alpha = 0.55f))
    }

    // Specular diagonal rim
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.06f)),
            start = Offset(ribbonLeft, 0f),
            end = Offset(ribbonRight, totalH)
        ),
        topLeft = Offset(ribbonLeft, 0f),
        size = Size(ribbonW, totalH),
        cornerRadius = CornerRadius(cornerR, cornerR),
        style = Stroke(width = 1.25.dp.toPx())
    )

    // Horizontal Ticks: Hour, Half-hour, 15-min
    var m = wakeMin
    while (m <= sleepMin) {
        val y = (m - wakeMin) * pxPerMin
        val isHour = (m % 60 == 0)
        val isHalf = (m % 30 == 0)

        val tickLen = when {
            isHour -> 16.dp.toPx()
            isHalf -> 10.dp.toPx()
            else -> 6.dp.toPx()
        }
        val isElapsed = m < nowMin
        val tickAlpha = if (isElapsed) 0.35f else if (isHour) 0.55f else 0.30f

        // Left tick
        drawLine(
            color = Color.White.copy(alpha = tickAlpha),
            start = Offset(ribbonLeft + 2.dp.toPx(), y),
            end = Offset(ribbonLeft + 2.dp.toPx() + tickLen, y),
            strokeWidth = 1.dp.toPx()
        )
        // Right tick
        drawLine(
            color = Color.White.copy(alpha = tickAlpha),
            start = Offset(ribbonRight - 2.dp.toPx() - tickLen, y),
            end = Offset(ribbonRight - 2.dp.toPx(), y),
            strokeWidth = 1.dp.toPx()
        )

        m += 15
    }
}

// -----------------------------------------------------------------------------
// Edit Block Sheet
// -----------------------------------------------------------------------------

@Composable
private fun EditBlockSheet(
    block: HorizonBlock,
    wakeMin: Int,
    sleepMin: Int,
    existingBlocks: List<HorizonBlock>,
    chronotypeShift: Double,
    onDismiss: () -> Unit,
    onSave: (HorizonBlock) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(block.label) }
    var startMin by remember { mutableIntStateOf(block.startMin) }
    var endMin by remember { mutableIntStateOf(block.endMin) }
    var colorIdx by remember { mutableIntStateOf(block.colorIdx) }

    val quickChips = listOf("DEEP WORK", "READ", "BREAK", "GYM", "ADMIN", "FOCUS", "REST")

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            BasicText("EDIT PLAN BLOCK", style = TextStyle(color = Ink.AMBER, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(14.dp))

            // Label Field
            BasicText("LABEL", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = label,
                    onValueChange = { label = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                    cursorBrush = SolidColor(Ink.AMBER),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Quick Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickChips.take(4).forEach { chip ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (label == chip) Ink.AMBER else Ink.SURFACE_3)
                            .clickable { label = chip }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicText(chip, style = TextStyle(color = if (label == chip) Ink.GROUND else Ink.TEXT_WARM, fontSize = 9.sp, fontFamily = Fonts.mono))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Color Swatches
            BasicText("BLOCK COLOR", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BLOCK_COLORS.forEachIndexed { i, col ->
                    val isSel = (i == colorIdx)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(if (isSel) 2.dp else 0.dp, if (isSel) Color.White else Color.Transparent, CircleShape)
                            .clickable { colorIdx = i }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Steppers for Start and End (±15m)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    BasicText("TIME SPAN", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    val sStr = String.format("%02d:%02d", startMin / 60, startMin % 60)
                    val eStr = String.format("%02d:%02d", endMin / 60, endMin % 60)
                    BasicText("$sStr – $eStr (${endMin - startMin}m)", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_3)
                            .clickable {
                                val newS = (startMin - 15).coerceAtLeast(wakeMin)
                                if (endMin - newS >= 15) startMin = newS
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText("−15m", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_3)
                            .clickable {
                                val newE = (endMin + 15).coerceAtMost(sleepMin)
                                if (newE - startMin >= 15) endMin = newE
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText("+15m", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Suggest Best Slot Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.AMBER, RoundedCornerShape(10.dp))
                    .clickable {
                        val dur = endMin - startMin
                        val best = EnergyModel.suggestSlot(dur, wakeMin, sleepMin, existingBlocks, chronotypeShift)
                        startMin = best
                        endMin = best + dur
                        Feedback.success(context)
                        Toast.makeText(context, "Moved to peak circadian window", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("⚡ MOVE TO BEST ENERGY SLOT", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }

            Spacer(Modifier.height(10.dp))

            // Context Action Button (Timer Launch)
            val isDeep = label.contains("WORK", ignoreCase = true) || label.contains("FOCUS", ignoreCase = true)
            if (isDeep) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink.SURFACE_2)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(10.dp))
                        .clickable {
                            TimerEngine.start(context)
                            Toast.makeText(context, "Started Pomodoro Focus for $label", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("START POMODORO FOCUS TIMER", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(10.dp))
            }

            // Actions: DELETE / SAVE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextAction("DELETE", Ink.VERMILION, onDelete)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink.AMBER)
                        .clickable {
                            onSave(block.copy(label = label.trim().ifEmpty { "FOCUS" }, startMin = startMin, endMin = endMin, colorIdx = colorIdx))
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    BasicText("SAVE BLOCK", style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// WHAT IS HORIZON? GUIDE SHEET
// -----------------------------------------------------------------------------

@Composable
private fun WhatIsHorizonGuideSheet(
    chronotype: Chronotype,
    onChronotypeChange: (Chronotype) -> Unit,
    blocksCount: Int,
    onAddStarterPlan: () -> Unit,
    onDismiss: () -> Unit
) {
    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            DotMatrixText(
                text = "WHAT IS HORIZON?",
                dotPitch = 2.4.dp,
                litColor = Ink.AMBER,
                glow = false
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "CIRCADIAN DAY PLANNING & HEURISTIC ENGINE",
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(16.dp))

            // 1. Purpose
            BasicText("1. THE PURPOSE", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "Horizon helps you place the right kind of work into your day, see what's next, and adjust your plan around your actual biological energy and schedule.",
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = Fonts.sans)
            )

            Spacer(Modifier.height(14.dp))

            // 2. Day Ribbon & Energy Wave
            BasicText("2. THE TWO VIEWS", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "• DAY RIBBON: A vertical timeline covering your waking window. Major ticks represent hours; the illuminated playhead tracks current time. Gutter callouts show your plan blocks.\n• ENERGY WAVE: An oscilloscope curve modeling your daily focus capacity. Drag across the wave to inspect alertness forecast at any hour.",
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = Fonts.sans)
            )

            Spacer(Modifier.height(14.dp))

            // 3. Model & Honest Heuristic Notice
            BasicText("3. HONEST HEURISTIC NOTICE", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "The curve is inspired by Borbély's Two-Process Sleep Regulation Model (1982) combining circadian rhythms with homeostatic sleep pressure. It is a mathematical planning heuristic, NOT a clinical sensor reading of brain activity. Real energy varies with sleep quality, diet, caffeine, and stress.",
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = Fonts.sans)
            )

            Spacer(Modifier.height(14.dp))

            // 4. Anchor Wake
            BasicText("4. ANCHOR WAKE", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "Tap ANCHOR WAKE in the morning to lock today's waking baseline to your real clock time. This recalculates alertness peaks and caffeine cutoff without moving or destroying your scheduled blocks.",
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = Fonts.sans)
            )

            Spacer(Modifier.height(14.dp))

            // 5. Interactive Chronotype Preference
            BasicText("5. CHRONOTYPE PREFERENCE", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chronotype.entries.forEach { ct ->
                    val isSel = chronotype == ct
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.AMBER else Ink.SURFACE_3)
                            .clickable { onChronotypeChange(ct) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = ct.label,
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

            Spacer(Modifier.height(6.dp))
            val ctDesc = when (chronotype) {
                Chronotype.EARLY -> "Early Bird shifts peak cognitive alertness 1 hour earlier into the morning."
                Chronotype.INTERMEDIATE -> "Intermediate reflects standard population circadian alertness timing."
                Chronotype.LATE -> "Night Owl shifts peak cognitive alertness 1.25 hours later into the afternoon."
            }
            BasicText(
                text = ctDesc,
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.sans)
            )

            if (blocksCount == 0) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink.SURFACE_3)
                        .border(1.dp, Ink.AMBER, RoundedCornerShape(10.dp))
                        .clickable { onAddStarterPlan() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("+ LOAD STARTER PLAN (3 BLOCKS)", style = TextStyle(color = Ink.AMBER, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.SURFACE_3)
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("CLOSE GUIDE", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Preferences & Storage Helpers
// -----------------------------------------------------------------------------

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(HORIZON_PREFS, Context.MODE_PRIVATE)

private fun loadWakeMin(context: Context): Int = prefs(context).getInt("wakeMin", 7 * 60)
private fun saveWakeMin(context: Context, min: Int) = prefs(context).edit().putInt("wakeMin", min).apply()

private fun loadSleepMin(context: Context): Int = prefs(context).getInt("sleepMin", 23 * 60)
private fun saveSleepMin(context: Context, min: Int) = prefs(context).edit().putInt("sleepMin", min).apply()

private fun loadCaffeineHours(context: Context): Double = prefs(context).getFloat("caffeineHours", 9.0f).toDouble()
private fun saveCaffeineHours(context: Context, hours: Double) = prefs(context).edit().putFloat("caffeineHours", hours.toFloat()).apply()

private fun loadChronotype(context: Context): Chronotype {
    val name = prefs(context).getString("chronotype", Chronotype.INTERMEDIATE.name)
    return runCatching { Chronotype.valueOf(name ?: "") }.getOrDefault(Chronotype.INTERMEDIATE)
}
private fun saveChronotype(context: Context, ct: Chronotype) {
    prefs(context).edit().putString("chronotype", ct.name).apply()
}

private fun isWakeAnchoredToday(context: Context): Boolean {
    val today = LocalDate.now().toEpochDay()
    val anchorDay = prefs(context).getLong("anchorDay", 0L)
    return today == anchorDay
}

private fun anchorWakeToday(context: Context, wakeMinute: Int) {
    prefs(context).edit()
        .putLong("anchorDay", LocalDate.now().toEpochDay())
        .putInt("wakeMin", wakeMinute)
        .apply()
}

private fun loadBlocks(context: Context): List<HorizonBlock> {
    val str = prefs(context).getString("blocks", "[]") ?: "[]"
    val arr = runCatching { JSONArray(str) }.getOrDefault(JSONArray())
    val list = mutableListOf<HorizonBlock>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(
            HorizonBlock(
                id = o.optString("id", UUID.randomUUID().toString()),
                label = o.optString("label", "FOCUS"),
                startMin = o.optInt("startMin", 9 * 60),
                endMin = o.optInt("endMin", 10 * 60),
                colorIdx = o.optInt("colorIdx", 0)
            )
        )
    }
    return list
}

private fun saveBlocks(context: Context, blocks: List<HorizonBlock>) {
    val arr = JSONArray()
    blocks.forEach { b ->
        val o = JSONObject().apply {
            put("id", b.id)
            put("label", b.label)
            put("startMin", b.startMin)
            put("endMin", b.endMin)
            put("colorIdx", b.colorIdx)
        }
        arr.put(o)
    }
    prefs(context).edit().putString("blocks", arr.toString()).apply()
}
