package rocks.ndot.widgets.instruments.folio

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NdotSheet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val VB_W = 380f
private const val VB_H = 430f

private const val WHEEL_CX = 182f
private const val WHEEL_CY = 272f
private const val WHEEL_R = 101f
private const val DETENT_DEGREES = 10f

private const val FADER_TOP_Y = 228f
private const val FADER_BOT_Y = 354f
private const val FADER_GLOW_FLOOR_Y = 374f
private const val FADER_SPAN = FADER_BOT_Y - FADER_TOP_Y // 126f

private const val GAUGE_CX = 314f
private const val GAUGE_CY = 64f

private const val HAPTIC_CX = 55f
private const val HAPTIC_CY = 198f

private const val SOUND_CX = 333f
private const val SOUND_CY = 166f

/**
 * The Counter — Precision Mechanical Reading Instrument for Mezura (V3 Architecture).
 *
 * Implements the photorealistic hardware industrial design architecture:
 * 1. Recessed 3-digit split-flap counter (0 0 3) occupying 94% of cavity with / 300 PAGES readout strictly outside to the right.
 *    Uses condensed mechanical typography matching physical split-flap reels.
 * 2. Top-right recessed lathe-turned sunray antique brass analog progress gauge with red sweep needle and 21 calibration ticks.
 * 3. Center multi-ring jog wheel:
 *    - 120 outer polygon steel gear teeth.
 *    - Compact -44° bolted gunmetal brake ear-lug (no collision with SOUND).
 *    - +45° machined steel spring lever with 6 knurled ribs (+1 page) and etched +1 markings outside gear teeth.
 *    - 3D beveled satin champagne-brass collar with dual glowing neon-orange-red 12 o'clock index slits and ambient warm light spill.
 *    - Warm satin-titanium conical brushed platter with 4-lobe anisotropic specular highlight.
 *    - Liquid-metal concave kidney-bean thumb dimple (zero cartoon lines, soft 3D blurred depth).
 *    - Persistent rotary gesture engine that NEVER resets angle on release or page change.
 * 4. Micro chrome bat-handle toggles:
 *    - Left: HAPTIC at (55, 198) with Electric Cyan (#1AF2FF) LED pip at (78, 233).
 *    - Right: SOUND at (333, 166) with Emerald Green (#26FF78) LED pip at (309, 167) and SOUND label centered at (309, 190).
 * 5. Fast-scrub glowing fader:
 *    - Illuminated slot extends to floor (Y=374) so molten orange core is always visible even at Page 0!
 *    - Blazing molten orange-gold light bar (#FFF5B8 core -> #FF4D00 bloom).
 *    - Heavy 3D chamfered gunmetal knob with glowing orange slit and pointer arrow.
 *    - Reliable drag tracking that never cancels or locks up.
 * 6. Clean chassis plate matching Inspiration_2 reference (no bottom-left rocker switch).
 */
@Composable
fun CounterView(
    book: BookItem,
    onSaveProgress: (Int) -> Unit,
    onLogSession: (ReadingSessionLog) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onStartFocusReading: (() -> Unit)? = null,
    onEditBook: (() -> Unit)? = null,
    onLogPastSession: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var draftPage by remember(book.id) { mutableIntStateOf(book.currentPage) }
    var lastCommitPage by remember(book.id) { mutableIntStateOf(book.currentPage) }

    // Toggle states for HAPTIC and SOUND with status LEDs
    var hapticEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }

    // Physical animation for the +1 lever
    val leverAnim = remember { Animatable(0f) }

    // Direct page jump sheet
    var showJumpSheet by remember { mutableStateOf(false) }
    var jumpInput by remember(draftPage) { mutableStateOf("$draftPage") }

    // Precompute the 120 outer gear teeth path once
    val gearTeethPath = remember { buildGearTeethPath(WHEEL_CX, WHEEL_CY) }

    // Debounced commit to BookStore
    LaunchedEffect(draftPage) {
        delay(350L)
        if (draftPage != lastCommitPage) {
            lastCommitPage = draftPage
            onSaveProgress(draftPage)
        }
    }

    // Feedback engine matching hardware spec
    var lastSoundTime by remember { mutableLongStateOf(0L) }
    var lastHapticTime by remember { mutableLongStateOf(0L) }

    fun triggerFeedback(kind: String = "tick") {
        val now = System.currentTimeMillis()
        if (hapticEnabled && now - lastHapticTime > 22L) {
            lastHapticTime = now
            Feedback.haptic(context, if (kind == "toggle") Feedback.Haptic.CLICK else Feedback.Haptic.TICK)
        }
        if (soundEnabled && now - lastSoundTime > 16L) {
            lastSoundTime = now
            val snd = when (kind) {
                "flap" -> Feedback.Sound.KEY_UP
                "toggle" -> Feedback.Sound.KEY_METAL
                else -> Feedback.Sound.DETENT
            }
            Feedback.play(context, snd)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Header: The Counter, book title and author
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = "The Counter",
                style = TextStyle(
                    color = Ink.TEXT_WARM,
                    fontSize = 17.sp,
                    fontFamily = Fonts.sans,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "${book.title.uppercase()} · ${book.author.uppercase()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Ink.TEXT_MUTED_V4,
                    fontSize = 10.sp,
                    fontFamily = Fonts.mono
                )
            )
        }

        // 2. Mechanical Chassis Card (viewBox 380 x 430)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .aspectRatio(VB_W / VB_H, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(26.dp))
        ) {
            val plateW = constraints.maxWidth.toFloat()
            val plateH = constraints.maxHeight.toFloat()
            val scale = min(plateW / VB_W, plateH / VB_H)
            val offsetX = (plateW - VB_W * scale) / 2f
            val offsetY = (plateH - VB_H * scale) / 2f

            // Wheel rotation physics (persisted permanently across recompositions!)
            var wheelAngle by remember { mutableFloatStateOf(0f) }
            var lastWheelTouchAngle by remember { mutableFloatStateOf(0f) }
            var wheelAngleAccum by remember { mutableFloatStateOf(0f) }
            var lastMoveTime by remember { mutableLongStateOf(0L) }

            // Active drag mode: 0=none, 1=fader, 2=wheel
            var activeDragMode by remember { mutableIntStateOf(0) }

            val totalP = max(1, book.totalPages)
            val progressRatio = (draftPage.toFloat() / totalP).coerceIn(0f, 1f)
            val faderKnobY = FADER_BOT_Y - progressRatio * FADER_SPAN

            // Use updated state getters so the gesture coroutines NEVER cancel on state changes
            val currentTotalP by rememberUpdatedState(totalP)
            val currentFaderKnobY by rememberUpdatedState(faderKnobY)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Tap gestures: Split-Flap well, Haptic/Sound toggles, Spring Lever (+1)
                    // Note: Keyed on Unit so tap detection is never recreated mid-use
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            val vx = (tapOffset.x - offsetX) / scale
                            val vy = (tapOffset.y - offsetY) / scale

                            // 1. Split flap bay tap (x: 34..240, y: 28..144) -> open jump sheet
                            if (vx in 34f..240f && vy in 28f..144f) {
                                jumpInput = "$draftPage"
                                showJumpSheet = true
                                triggerFeedback("tick")
                                return@detectTapGestures
                            }

                            // 2. Haptic toggle switch tap (55, 198)
                            if (hypot(vx - HAPTIC_CX, vy - HAPTIC_CY) <= 24f || (vx in 36f..90f && vy in 180f..245f)) {
                                hapticEnabled = !hapticEnabled
                                triggerFeedback("toggle")
                                return@detectTapGestures
                            }

                            // 3. Sound toggle switch tap (333, 166)
                            if (hypot(vx - SOUND_CX, vy - SOUND_CY) <= 24f || (vx in 295f..355f && vy in 150f..205f)) {
                                soundEnabled = !soundEnabled
                                triggerFeedback("toggle")
                                return@detectTapGestures
                            }

                            // 4. Spring lever tap (+1 page at +45 deg)
                            val leverTipX = WHEEL_CX + 110f * cos(45f * PI / 180f).toFloat()
                            val leverTipY = WHEEL_CY + 110f * sin(45f * PI / 180f).toFloat()
                            if (hypot(vx - leverTipX, vy - leverTipY) <= 28f || (vx in 230f..300f && vy in 320f..380f)) {
                                if (draftPage < currentTotalP) {
                                    draftPage++
                                    wheelAngle += DETENT_DEGREES
                                    triggerFeedback("flap")
                                }
                                coroutineScope.launch {
                                    leverAnim.snapTo(1f)
                                    leverAnim.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 850f))
                                }
                                return@detectTapGestures
                            }
                        }
                    }
                    // Unified drag gesture with STRICT drag-only routing
                    // CRITICAL FIX: Keyed on Unit! Does NOT cancel when faderKnobY or draftPage changes!
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val vx = (startOffset.x - offsetX) / scale
                                val vy = (startOffset.y - offsetY) / scale

                                // FADER: Generous touch target centered on the fader knob handle
                                val inFaderKnob = vx in 280f..360f && abs(vy - currentFaderKnobY) <= 32f
                                if (inFaderKnob) {
                                    activeDragMode = 1
                                    return@detectDragGestures
                                }

                                // WHEEL: rotary gesture on the center wheel assembly
                                if (hypot(vx - WHEEL_CX, vy - WHEEL_CY) <= WHEEL_R) {
                                    activeDragMode = 2
                                    lastWheelTouchAngle = atan2(vy - WHEEL_CY, vx - WHEEL_CX) * (180f / PI.toFloat())
                                    lastMoveTime = System.currentTimeMillis()
                                    return@detectDragGestures
                                }

                                activeDragMode = 0
                            },
                            onDrag = { change, _ ->
                                val vx = (change.position.x - offsetX) / scale
                                val vy = (change.position.y - offsetY) / scale

                                when (activeDragMode) {
                                    1 -> {
                                        // STRICT Fader drag
                                        change.consume()
                                        val clampedY = vy.coerceIn(FADER_TOP_Y, FADER_BOT_Y)
                                        val ratio = (FADER_BOT_Y - clampedY) / FADER_SPAN
                                        val targetP = (ratio * currentTotalP).roundToInt().coerceIn(0, currentTotalP)
                                        if (targetP != draftPage) {
                                            val pageDelta = targetP - draftPage
                                            wheelAngle += pageDelta * DETENT_DEGREES
                                            draftPage = targetP
                                            triggerFeedback("flap")
                                        }
                                    }
                                    2 -> {
                                        // WHEEL rotary pan with iPod-style velocity curve
                                        change.consume()
                                        val currentTouchAngle = atan2(vy - WHEEL_CY, vx - WHEEL_CX) * (180f / PI.toFloat())
                                        var delta = currentTouchAngle - lastWheelTouchAngle
                                        while (delta > 180f) delta -= 360f
                                        while (delta < -180f) delta += 360f

                                        // Ignore impossible jumps if finger crosses center pixel
                                        if (abs(delta) > 85f) {
                                            lastWheelTouchAngle = currentTouchAngle
                                            return@detectDragGestures
                                        }

                                        val now = System.currentTimeMillis()
                                        val dt = max(1L, now - lastMoveTime)
                                        val angularVelocity = abs(delta) / dt.toFloat() // deg/ms
                                        val velocityMultiplier = if (angularVelocity > 0.28f) {
                                            1f + ((angularVelocity - 0.28f) * 3.5f).pow(1.45f)
                                        } else 1f

                                        // Angle is persisted permanently - NEVER snaps back!
                                        wheelAngle += delta
                                        wheelAngleAccum += delta * velocityMultiplier

                                        if (abs(wheelAngleAccum) >= DETENT_DEGREES) {
                                            val steps = (wheelAngleAccum / DETENT_DEGREES).toInt()
                                            val newP = (draftPage + steps).coerceIn(0, currentTotalP)
                                            if (newP != draftPage) {
                                                draftPage = newP
                                                triggerFeedback("tick")
                                            }
                                            wheelAngleAccum -= steps * DETENT_DEGREES
                                        }

                                        lastWheelTouchAngle = currentTouchAngle
                                        lastMoveTime = now
                                    }
                                }
                            },
                            onDragEnd = {
                                activeDragMode = 0
                                // wheelAngle is PRESERVED, never reset!
                            },
                            onDragCancel = {
                                activeDragMode = 0
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    withTransform({
                        translate(offsetX, offsetY)
                        scale(scale, scale, Offset.Zero)
                    }) {
                        // 1. Chassis Plate & Corner Fasteners
                        drawChassisExact()
                        drawFastenersExact()

                        // 2. Recessed 3-Digit Split-Flap Display ("0 0 3" + outside "/ 300 PAGES")
                        drawSplitFlapCounterExact(
                            currentPage = draftPage,
                            totalPages = book.totalPages,
                            isPercentage = (book.trackMode == BookTrackMode.PERCENTAGE)
                        )

                        // 3. Top-Right Sunray Antique Brass Progress Gauge
                        drawBrassGaugeExact(progress = progressRatio)

                        // 4. Center Rotary Jog Wheel & +1 Spring Lever
                        drawJogWheelExact(
                            wheelAngle = wheelAngle,
                            leverDeflection = leverAnim.value,
                            gearPath = gearTeethPath
                        )

                        // 5. Left (HAPTIC) and Right (SOUND) Micro Toggles
                        drawTogglesExact(
                            hapticEnabled = hapticEnabled,
                            soundEnabled = soundEnabled
                        )

                        // 6. Fast-Scrub Glowing Fader (Strict Drag-Only)
                        drawFaderExact(
                            knobY = faderKnobY
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Bottom Action Buttons Bar matching hardware instrument design
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CLOSE button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                    .clickable {
                        if (draftPage != lastCommitPage) {
                            onSaveProgress(draftPage)
                        }
                        onClose()
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "CLOSE",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // EDIT button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.SURFACE_2)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                        .clickable { onEditBook?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "EDIT",
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }

                // LOG PAST button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.SURFACE_2)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                        .clickable { onLogPastSession?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "LOG PAST",
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }

                // Primary CONTINUE READING button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.BRASS)
                        .clickable {
                            if (draftPage != lastCommitPage) {
                                onSaveProgress(draftPage)
                            }
                            onStartFocusReading?.invoke()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        BasicText(
                            text = "CONTINUE",
                            style = TextStyle(color = Ink.GROUND, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
                        )
                        BasicText(
                            text = "READING",
                            style = TextStyle(color = Ink.GROUND, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
                        )
                    }
                }
            }
        }

        // Direct Page Jump Sheet
        if (showJumpSheet) {
            NdotSheet(onDismiss = { showJumpSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                    BasicText(
                        text = if (isPct) "JUMP TO PERCENTAGE (%)" else "JUMP TO PAGE",
                        style = TextStyle(color = Ink.AMBER, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ink.SURFACE_3)
                                .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = jumpInput,
                                onValueChange = { jumpInput = it.filter { c -> c.isDigit() } },
                                textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold),
                                cursorBrush = SolidColor(Ink.AMBER),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ink.AMBER)
                                .clickable {
                                    val target = jumpInput.toIntOrNull()?.coerceIn(0, book.totalPages) ?: draftPage
                                    draftPage = target
                                    Feedback.lock(context)
                                    showJumpSheet = false
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            BasicText("JUMP", style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(-10, -1, 1, 10).forEach { delta ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Ink.SURFACE_2)
                                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val cur = jumpInput.toIntOrNull() ?: draftPage
                                        val next = (cur + delta).coerceIn(0, book.totalPages)
                                        jumpInput = "$next"
                                        Feedback.tick(context)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(if (delta > 0) "+$delta" else "$delta", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 11.sp, fontFamily = Fonts.mono))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Legacy alias to ensure no existing call site breaks */
@Composable
fun VernierCaliperView(
    book: BookItem,
    onSaveProgress: (Int) -> Unit,
    onLogSession: (ReadingSessionLog) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onStartFocusReading: (() -> Unit)? = null,
    onEditBook: (() -> Unit)? = null,
    onLogPastSession: (() -> Unit)? = null
) {
    CounterView(
        book = book,
        onSaveProgress = onSaveProgress,
        onLogSession = onLogSession,
        onClose = onClose,
        modifier = modifier,
        onStartFocusReading = onStartFocusReading,
        onEditBook = onEditBook,
        onLogPastSession = onLogPastSession
    )
}

// =============================================================================
// PIXEL-EXACT HARDWARE DRAWING FUNCTIONS (380 x 430 COORDINATE SPACE)
// =============================================================================

private fun DrawScope.drawChassisExact() {
    // 1. Outer Bevel Rim (x=6, y=6, w=368, h=418, rx=26)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF6C7078), Color(0xFF3D4046), Color(0xFF16171A), Color(0xFF0C0D0F)),
            start = Offset(6f, 6f),
            end = Offset(374f, 424f)
        ),
        topLeft = Offset(6f, 6f),
        size = Size(368f, 418f),
        cornerRadius = CornerRadius(26f, 26f),
        style = Stroke(width = 2f)
    )

    // 2. Sandblasted Gunmetal Faceplate (Warm, rich matte finish)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF3C3E44), Color(0xFF2B2D32), Color(0xFF1F2125), Color(0xFF16171A)),
            start = Offset(8f, 8f),
            end = Offset(372f, 422f)
        ),
        topLeft = Offset(8f, 8f),
        size = Size(364f, 414f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = Color(0xFF4C5059),
        topLeft = Offset(8f, 8f),
        size = Size(364f, 414f),
        cornerRadius = CornerRadius(24f, 24f),
        style = Stroke(width = 0.75f)
    )

    // 3. Subtle micro-brushed texture lines
    for (i in 0 until 18) {
        val y = 14f + i * 22f
        drawLine(
            color = Color.White.copy(alpha = 0.022f),
            start = Offset(14f, y),
            end = Offset(366f, y + 10f),
            strokeWidth = 0.6f
        )
    }

    // 4. Laser-Etched Bottom Center Title: "The Counter"
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5E626A.toInt()
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }
        canvas.nativeCanvas.drawText("The Counter", 190f, 414f, paint)
    }
}

private fun DrawScope.drawFastenersExact() {
    // 4 Corner Recessed Phillips Screws
    val cornerScrews = listOf(
        Triple(24f, 24f, 36f),
        Triple(356f, 24f, 118f),
        Triple(24f, 406f, 62f),
        Triple(356f, 406f, 142f)
    )
    cornerScrews.forEach { (cx, cy, rot) ->
        val center = Offset(cx, cy)
        drawCircle(color = Color(0xFF141518), radius = 9.2f, center = center)
        drawCircle(color = Color(0xFF4A4E56), radius = 9.2f, center = center, style = Stroke(width = 0.9f))
        drawCircle(color = Color(0xFF3B3E46), radius = 7.2f, center = center)
        drawCircle(color = Color(0xFF0E0F12), radius = 7.2f, center = center, style = Stroke(width = 0.8f))

        rotate(rot, pivot = center) {
            drawLine(color = Color(0xFF151619), start = Offset(cx - 4.2f, cy), end = Offset(cx + 4.2f, cy), strokeWidth = 1.7f, cap = StrokeCap.Round)
            drawLine(color = Color(0xFF151619), start = Offset(cx, cy - 4.2f), end = Offset(cx, cy + 4.2f), strokeWidth = 1.7f, cap = StrokeCap.Round)
        }
    }

    // 8 Perimeter Spherical Steel Rivets
    val rivets = listOf(
        Pair(118f, 17f), Pair(260f, 17f),
        Pair(22f, 150f), Pair(22f, 248f),
        Pair(356f, 150f), Pair(356f, 248f),
        Pair(108f, 412f), Pair(266f, 412f)
    )
    rivets.forEach { (rx, ry) ->
        val center = Offset(rx, ry)
        drawCircle(color = Color(0xFF111215), radius = 3.6f, center = center)
        drawCircle(color = Color(0xFF555962), radius = 2.7f, center = center - Offset(0.4f, 0.4f))
        drawCircle(color = Color(0xFFA8AEB8), radius = 1.1f, center = center - Offset(1.1f, 1.1f))
    }
}

private fun DrawScope.drawSplitFlapCounterExact(
    currentPage: Int,
    totalPages: Int,
    isPercentage: Boolean
) {
    // 1. Outer Chamfered Cutout Frame (x=34, y=28, w=204, h=115, rx=15)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF08090B), Color(0xFF16181C), Color(0xFF464A52)),
            startY = 28f,
            endY = 143f
        ),
        topLeft = Offset(34f, 28f),
        size = Size(204f, 115f),
        cornerRadius = CornerRadius(15f, 15f)
    )
    drawRoundRect(
        color = Color(0xFF4C5059),
        topLeft = Offset(34f, 28f),
        size = Size(204f, 115f),
        cornerRadius = CornerRadius(15f, 15f),
        style = Stroke(width = 1.2f)
    )

    // 2. Deep Recessed Black Cavity (x=40, y=34, w=192, h=103, rx=11)
    drawRoundRect(
        color = Color(0xFF08090B),
        topLeft = Offset(40f, 34f),
        size = Size(192f, 103f),
        cornerRadius = CornerRadius(11f, 11f)
    )
    drawRoundRect(
        color = Color(0xFF030405),
        topLeft = Offset(40f, 34f),
        size = Size(192f, 103f),
        cornerRadius = CornerRadius(11f, 11f),
        style = Stroke(width = 2.4f)
    )

    // 3. Digits computation (3 digits e.g. "0 0 3")
    val displayNum = if (isPercentage) (currentPage * 100 / maxOf(1, totalPages)).coerceIn(0, 100) else currentPage
    val digitsStr = String.format("%03d", displayNum.coerceIn(0, 999))

    drawIntoCanvas { canvas ->
        // Authentic condensed mechanical split-flap typography
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEAECEF.toInt()
            textSize = 65f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        for (i in 0..2) {
            val cardX = 46f + i * 61.5f
            val cardY = 40f
            val cardW = 56f
            val cardH = 89f

            // Top Metallic Alignment Notch on Upper Cavity Bevel
            val notchPath = Path().apply {
                moveTo(cardX + cardW / 2f - 3.5f, cardY - 5f)
                lineTo(cardX + cardW / 2f + 3.5f, cardY - 5f)
                lineTo(cardX + cardW / 2f, cardY - 1f)
                close()
            }
            drawPath(notchPath, color = Color(0xFF828690))

            // Stacked Bottom Flap Edges (Simulates Mechanical Card Reel Depth)
            drawRoundRect(
                color = Color(0xFF2C2F36),
                topLeft = Offset(cardX + 2f, cardY + cardH + 1f),
                size = Size(cardW - 4f, 3f),
                cornerRadius = CornerRadius(1.5f, 1.5f)
            )
            drawRoundRect(
                color = Color(0xFF1C1E22),
                topLeft = Offset(cardX + 4f, cardY + cardH + 4f),
                size = Size(cardW - 8f, 2.5f),
                cornerRadius = CornerRadius(1f, 1f)
            )

            // Main Card Drop Shadow
            drawRoundRect(
                color = Color(0xFF030405),
                topLeft = Offset(cardX, cardY + 2f),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Upper Flap
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF32353C), Color(0xFF222429)),
                    startY = cardY,
                    endY = cardY + cardH / 2f - 1f
                ),
                topLeft = Offset(cardX, cardY),
                size = Size(cardW, cardH / 2f - 1f),
                cornerRadius = CornerRadius(5.5f, 5.5f)
            )
            drawRoundRect(
                color = Color(0xFF444852),
                topLeft = Offset(cardX, cardY),
                size = Size(cardW, cardH / 2f - 1f),
                cornerRadius = CornerRadius(5.5f, 5.5f),
                style = Stroke(width = 0.7f)
            )

            // Lower Flap
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1C20), Color(0xFF282B31)),
                    startY = cardY + cardH / 2f + 1f,
                    endY = cardY + cardH
                ),
                topLeft = Offset(cardX, cardY + cardH / 2f + 1f),
                size = Size(cardW, cardH / 2f - 1f),
                cornerRadius = CornerRadius(5.5f, 5.5f)
            )
            drawRoundRect(
                color = Color(0xFF343840),
                topLeft = Offset(cardX, cardY + cardH / 2f + 1f),
                size = Size(cardW, cardH / 2f - 1f),
                cornerRadius = CornerRadius(5.5f, 5.5f),
                style = Stroke(width = 0.7f)
            )

            // Crisp Condensed Split-Flap Numeral
            canvas.nativeCanvas.drawText(
                digitsStr[i].toString(),
                cardX + cardW / 2f,
                cardY + cardH / 2f + 23f,
                textPaint
            )

            // Horizontal Split Seam
            drawRect(
                color = Color(0xFF08090B),
                topLeft = Offset(cardX, cardY + cardH / 2f - 1.6f),
                size = Size(cardW, 3.2f)
            )

            // Left & Right Mechanical Axle Hinge Pins
            drawRoundRect(
                color = Color(0xFF545862),
                topLeft = Offset(cardX - 2.4f, cardY + cardH / 2f - 6f),
                size = Size(3.8f, 12f),
                cornerRadius = CornerRadius(1.2f, 1.2f)
            )
            drawRoundRect(
                color = Color(0xFF111215),
                topLeft = Offset(cardX - 2.4f, cardY + cardH / 2f - 6f),
                size = Size(3.8f, 12f),
                cornerRadius = CornerRadius(1.2f, 1.2f),
                style = Stroke(width = 0.7f)
            )

            drawRoundRect(
                color = Color(0xFF545862),
                topLeft = Offset(cardX + cardW - 1.4f, cardY + cardH / 2f - 6f),
                size = Size(3.8f, 12f),
                cornerRadius = CornerRadius(1.2f, 1.2f)
            )
            drawRoundRect(
                color = Color(0xFF111215),
                topLeft = Offset(cardX + cardW - 1.4f, cardY + cardH / 2f - 6f),
                size = Size(3.8f, 12f),
                cornerRadius = CornerRadius(1.2f, 1.2f),
                style = Stroke(width = 0.7f)
            )
        }

        // OUTSIDE RIGHT OF CAVITY: "/ 300 PAGES" ON THE GUNMETAL FACEPLATE
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC98F47.toInt()
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            letterSpacing = 0.02f
        }
        val pagesLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFAE7B3B.toInt()
            textSize = 11.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            letterSpacing = 0.12f
        }

        val totalText = if (isPercentage) "/ 100" else "/ $totalPages"
        val unitText = if (isPercentage) "%" else "PAGES"

        canvas.nativeCanvas.drawText(totalText, 248f, 119f, totalPaint)
        canvas.nativeCanvas.drawText(unitText, 259f, 138f, pagesLabelPaint)
    }
}

private fun DrawScope.drawBrassGaugeExact(progress: Float) {
    val center = Offset(GAUGE_CX, GAUGE_CY)

    // Recessed Stepped Gunmetal Bezel Ring
    drawCircle(color = Color(0xFF141518), radius = 32.5f, center = center, style = Stroke(width = 3.2f))
    drawCircle(color = Color(0xFF4E525B), radius = 34f, center = center, style = Stroke(width = 1.1f))
    drawCircle(color = Color(0xFF9A7D42), radius = 28.5f, center = center, style = Stroke(width = 1.6f))
    drawCircle(color = Color(0xFF14110A).copy(alpha = 0.75f), radius = 26.5f, center = center, style = Stroke(width = 1.4f))

    // True Conical Sunray Antique Brass Dial Face
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color(0xFFCEB378),
                Color(0xFF8A6E36),
                Color(0xFFE8CF96),
                Color(0xFF725928),
                Color(0xFFD0B57A),
                Color(0xFF826630),
                Color(0xFFEBD39A),
                Color(0xFF6C5322),
                Color(0xFFCEB378)
            ),
            center = center
        ),
        radius = 26f,
        center = center
    )
    // Subtle radial light depth
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x38FFF0C3), Color(0x75120E06)),
            center = center - Offset(1f, 1.5f),
            radius = 26f
        ),
        radius = 26f,
        center = center
    )

    // Fine Concentric Inner Ring
    drawCircle(color = Color(0x605E4820), radius = 17f, center = center, style = Stroke(width = 0.45f))

    // 21 Delicate Hairline Calibration Ticks (-135° to +135°)
    for (idx in 0..20) {
        val deg = -135f + (idx / 20f) * 270f
        val isMajor = (idx % 5 == 0)
        rotate(deg, pivot = center) {
            drawLine(
                color = Color(0xFF38290F).copy(alpha = if (isMajor) 0.85f else 0.55f),
                start = Offset(center.x, center.y - (if (isMajor) 21.5f else 23.2f)),
                end = Offset(center.x, center.y - 25.5f),
                strokeWidth = if (isMajor) 1.1f else 0.7f
            )
        }
    }

    // Rotating Red Indicator Needle (-130 deg to +130 deg)
    val needleAngle = -130f + progress.coerceIn(0f, 1f) * 260f
    rotate(needleAngle, pivot = center) {
        // Needle Shadow on Brass Face
        drawLine(
            color = Color(0x70000000),
            start = Offset(center.x + 1f, center.y + 1.5f + 5f),
            end = Offset(center.x + 1f, center.y + 1.5f - 21.5f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
        // Dark Counterweight Tail
        drawLine(
            color = Color(0xFF2B2E34),
            start = Offset(center.x, center.y + 6.5f),
            end = Offset(center.x, center.y),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
        // Crimson Tapered Pointer
        val needlePath = Path().apply {
            moveTo(center.x - 1.7f, center.y)
            lineTo(center.x + 1.7f, center.y)
            lineTo(center.x, center.y - 22.5f)
            close()
        }
        drawPath(needlePath, color = Color(0xFFE53224))
    }

    // Compact Center Pivot Cap (Authentic small watch bushing)
    drawCircle(color = Color(0xFF4E525A), radius = 3.4f, center = center)
    drawCircle(color = Color(0xFF18191C), radius = 3.4f, center = center, style = Stroke(width = 0.9f))
    drawCircle(color = Color(0xFFB4BAC4), radius = 1.2f, center = center - Offset(0.8f, 0.8f))
}

private fun DrawScope.drawJogWheelExact(
    wheelAngle: Float,
    leverDeflection: Float,
    gearPath: Path
) {
    val center = Offset(WHEEL_CX, WHEEL_CY)

    // 1. Compact Top-Right Bolted Ear-Lug at -44 deg (Keeps clear of SOUND switch!)
    rotate(-44f, pivot = center) {
        drawRoundRect(
            color = Color(0xFF32353B),
            topLeft = Offset(center.x + 84f, center.y - 14f),
            size = Size(33f, 28f),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = Color(0xFF626772),
            topLeft = Offset(center.x + 84f, center.y - 14f),
            size = Size(33f, 28f),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = 1.1f)
        )
        drawCircle(color = Color(0xFF1F2125), radius = 8f, center = Offset(center.x + 103f, center.y))
        drawCircle(color = Color(0xFF5A5F6A), radius = 8f, center = Offset(center.x + 103f, center.y), style = Stroke(width = 1f))
        drawCircle(color = Color(0xFF3D4048), radius = 5.5f, center = Offset(center.x + 103f, center.y))
        drawCircle(color = Color(0xFF686D78).copy(alpha = 0.6f), radius = 2f, center = Offset(center.x + 101.5f, center.y - 1.5f))
    }

    // 2. Laser-Etched Markings for "+1 page" on Faceplate (Outside gear teeth!)
    drawIntoCanvas { canvas ->
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7A7F8A.toInt()
            textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7A7F8A.toInt()
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.nativeCanvas.drawText("+1", 266f, 327f, textPaint)
        canvas.nativeCanvas.drawText("+1", 227f, 357f, subPaint)
        canvas.nativeCanvas.drawText("page", 227f, 368f, subPaint)
    }
    val arrowPath = Path().apply {
        moveTo(267f, 334f)
        lineTo(275f, 332f)
        lineTo(274f, 347f)
        close()
    }
    drawPath(arrowPath, color = Color(0xFF7A7F8A))

    // 3. Spring-Loaded Ribbed Steel Lever Paddle at +45 deg (tapped deflects to +51 deg)
    val leverAngle = 45f + leverDeflection * 6f
    rotate(leverAngle, pivot = center) {
        drawRoundRect(
            color = Color(0xFF484C55),
            topLeft = Offset(center.x + 86f, center.y - 11f),
            size = Size(42f, 22f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFF141518),
            topLeft = Offset(center.x + 86f, center.y - 11f),
            size = Size(42f, 22f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.2f)
        )
        // 6 High-Contrast Knurled Silver-Steel Ridges on Lever Tip
        listOf(105f, 109f, 113f, 117f, 121f, 125f).forEach { lx ->
            drawLine(
                color = Color(0xFF17181B),
                start = Offset(center.x + lx, center.y - 9f),
                end = Offset(center.x + lx, center.y + 9f),
                strokeWidth = 1.4f
            )
            drawLine(
                color = Color(0xFFC2C7D2),
                start = Offset(center.x + lx + 1.1f, center.y - 9f),
                end = Offset(center.x + lx + 1.1f, center.y + 9f),
                strokeWidth = 1.4f
            )
        }
    }

    // 4. Outer Wheel Shadow Bowl
    drawCircle(color = Color(0xFF050608), radius = 102f, center = center + Offset(0f, 5f))

    // 5. Rotating 120-Tooth Outer Steel Gear Ring
    rotate(wheelAngle * 0.40f, pivot = center) {
        drawPath(gearPath, color = Color(0xFF5A5E68))
        drawPath(gearPath, color = Color(0xFF121316), style = Stroke(width = 0.85f))
    }

    // 6. Stepped Metallic Bezel Inside Gear Teeth
    drawCircle(color = Color(0xFF282A30), radius = 94.5f, center = center, style = Stroke(width = 4.2f))
    drawCircle(color = Color(0xFF828792), radius = 96.5f, center = center, style = Stroke(width = 0.9f))

    // 7. 3D Beveled Warm Satin Champagne-Brass Collar
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFE5CD96), Color(0xFFB89B5E), Color(0xFF6D582C), Color(0xFFC9AD70), Color(0xFF5C4922)),
            start = center - Offset(89f, 89f),
            end = center + Offset(89f, 89f)
        ),
        radius = 89.5f,
        center = center,
        style = Stroke(width = 7.8f)
    )
    // Specular Inner & Outer Brass Chamfer Highlights
    drawCircle(color = Color(0xFFDFC68C).copy(alpha = 0.55f), radius = 93.2f, center = center, style = Stroke(width = 0.6f))
    drawCircle(color = Color(0xFFFFEAB8).copy(alpha = 0.45f), radius = 85.8f, center = center, style = Stroke(width = 0.6f))

    // 8. Deep Shadow Trench Framing the Brushed Platter
    drawCircle(color = Color(0xFF0E0F12), radius = 84f, center = center, style = Stroke(width = 3.2f))
    drawCircle(color = Color(0xFF8A8E98), radius = 82.2f, center = center, style = Stroke(width = 0.8f))

    // 9. Warm Orange Ambient Light Spill at 12 O'Clock on Brass Collar
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x8CFF591A), Color(0x1FFF591A), Color(0x00FF591A)),
            center = Offset(center.x, center.y - 90f),
            radius = 24f
        ),
        topLeft = Offset(center.x - 24f, center.y - 100f),
        size = Size(48f, 20f),
        cornerRadius = CornerRadius(24f, 10f)
    )

    // 10. Conical Brushed Titanium-Gunmetal Platter with Liquid-Metal Dimple
    // Angle persists permanently in wheelAngle!
    rotate(wheelAngle, pivot = center) {
        // Authentic 4-lobe warm satin-titanium anisotropic specular reflection
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF7B7772),
                    Color(0xFF363432),
                    Color(0xFF918C86),
                    Color(0xFF292826),
                    Color(0xFF827D78),
                    Color(0xFF32302E),
                    Color(0xFF8E8983),
                    Color(0xFF262523),
                    Color(0xFF7B7772)
                ),
                center = center
            ),
            radius = 82f,
            center = center
        )

        // Repeating concentric lathe grooves
        listOf(74f, 66f, 58f, 50f, 42f, 34f, 26f, 18f, 10f).forEachIndexed { idx, r ->
            drawCircle(
                color = Color.White.copy(alpha = if (idx % 2 == 0) 0.04f else 0.02f),
                radius = r,
                center = center,
                style = Stroke(width = 0.8f)
            )
        }

        // Center Lathe Pivot Cap
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB0AAA2), Color(0xFF3E3C39), Color(0x003E3C39)),
                center = center,
                radius = 4.5f
            ),
            radius = 4.5f,
            center = center
        )

        // ================================================================
        // SCULPTED LIQUID-METAL CONCAVE KIDNEY THUMB DIMPLE (ZERO LINES!)
        // Matches target 3D photorealistic liquid-metal depth
        // ================================================================
        val dimpleCenter = Offset(center.x + 43f, center.y - 4f)

        // 1. Soft Outer Cushion Bevel Shadow
        val outerDimple = Path().apply {
            moveTo(dimpleCenter.x - 11f, dimpleCenter.y - 29f)
            cubicTo(dimpleCenter.x + 7f, dimpleCenter.y - 33f, dimpleCenter.x + 21f, dimpleCenter.y - 17f, dimpleCenter.x + 20f, dimpleCenter.y + 3f)
            cubicTo(dimpleCenter.x + 19f, dimpleCenter.y + 24f, dimpleCenter.x + 5f, dimpleCenter.y + 35f, dimpleCenter.x - 10f, dimpleCenter.y + 30f)
            cubicTo(dimpleCenter.x - 22f, dimpleCenter.y + 26f, dimpleCenter.x - 22f, dimpleCenter.y + 10f, dimpleCenter.x - 17f, dimpleCenter.y - 1f)
            cubicTo(dimpleCenter.x - 13f, dimpleCenter.y - 10f, dimpleCenter.x - 21f, dimpleCenter.y - 26f, dimpleCenter.x - 11f, dimpleCenter.y - 29f)
            close()
        }
        drawPath(
            outerDimple,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xE6171615), Color(0xE64A4744), Color(0xE69E9992)),
                start = dimpleCenter - Offset(15f, 25f),
                end = dimpleCenter + Offset(15f, 25f)
            )
        )

        // 2. Main Concave Liquid-Metal Bowl (Seamless gradient, NO stroke outline!)
        val innerDimple = Path().apply {
            moveTo(dimpleCenter.x - 10f, dimpleCenter.y - 27f)
            cubicTo(dimpleCenter.x + 6f, dimpleCenter.y - 31f, dimpleCenter.x + 19f, dimpleCenter.y - 16f, dimpleCenter.x + 18f, dimpleCenter.y + 3f)
            cubicTo(dimpleCenter.x + 17f, dimpleCenter.y + 22f, dimpleCenter.x + 4f, dimpleCenter.y + 32f, dimpleCenter.x - 9f, dimpleCenter.y + 28f)
            cubicTo(dimpleCenter.x - 20f, dimpleCenter.y + 24f, dimpleCenter.x - 20f, dimpleCenter.y + 9f, dimpleCenter.x - 15f, dimpleCenter.y - 1f)
            cubicTo(dimpleCenter.x - 11f, dimpleCenter.y - 9f, dimpleCenter.x - 19f, dimpleCenter.y - 24f, dimpleCenter.x - 10f, dimpleCenter.y - 27f)
            close()
        }
        drawPath(
            innerDimple,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF8A8680), Color(0xFF56534F), Color(0xFF262422), Color(0xFF121110)),
                center = dimpleCenter + Offset(4f, 2f),
                radius = 28f
            )
        )

        // 3. Deep Soft Shadow along Left/Top-Left Wall
        val shadowWall = Path().apply {
            moveTo(dimpleCenter.x - 10f, dimpleCenter.y - 25f)
            cubicTo(dimpleCenter.x - 1f, dimpleCenter.y - 27f, dimpleCenter.x + 4f, dimpleCenter.y - 22f, dimpleCenter.x, dimpleCenter.y - 12f)
            cubicTo(dimpleCenter.x - 4f, dimpleCenter.y - 2f, dimpleCenter.x - 4f, dimpleCenter.y + 10f, dimpleCenter.x + 1f, dimpleCenter.y + 21f)
            cubicTo(dimpleCenter.x - 4f, dimpleCenter.y + 26f, dimpleCenter.x - 14f, dimpleCenter.y + 22f, dimpleCenter.x - 16f, dimpleCenter.y + 11f)
            cubicTo(dimpleCenter.x - 18f, dimpleCenter.y + 2f, dimpleCenter.x - 14f, dimpleCenter.y - 5f, dimpleCenter.x - 12f, dimpleCenter.y - 12f)
            cubicTo(dimpleCenter.x - 11f, dimpleCenter.y - 17f, dimpleCenter.x - 16f, dimpleCenter.y - 23f, dimpleCenter.x - 10f, dimpleCenter.y - 25f)
            close()
        }
        drawPath(shadowWall, color = Color(0xC70B0A09))

        // 4. Soft Diffused Specular Sheen Inside Right Bowl
        rotate(-6f, pivot = dimpleCenter + Offset(5f, 2f)) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x60B5B0A8), Color(0x208C8780), Color(0x00262422)),
                    center = dimpleCenter + Offset(5f, 2f),
                    radius = 16f
                ),
                topLeft = dimpleCenter + Offset(5f - 8.5f, 2f - 16f),
                size = Size(17f, 32f),
                cornerRadius = CornerRadius(8.5f, 16f)
            )
        }
    }

    // 11. Dual Glowing Neon-Orange-Red 12 O'Clock Alignment Slits
    // Glow bloom
    drawRoundRect(
        color = Color(0x75FF4D1A),
        topLeft = Offset(center.x - 3f, center.y - 98f),
        size = Size(6f, 19f),
        cornerRadius = CornerRadius(3f, 3f)
    )
    // Upper slit
    drawRoundRect(
        color = Color(0xFFFF4D1A),
        topLeft = Offset(center.x - 1.7f, center.y - 96.5f),
        size = Size(3.4f, 5.2f),
        cornerRadius = CornerRadius(1.5f, 1.5f)
    )
    drawRoundRect(
        color = Color(0xFFFFF2B8),
        topLeft = Offset(center.x - 1.1f, center.y - 95.8f),
        size = Size(2.2f, 3.8f),
        cornerRadius = CornerRadius(1f, 1f)
    )
    // Lower slit
    drawRoundRect(
        color = Color(0xFFFF4D1A),
        topLeft = Offset(center.x - 1.7f, center.y - 88.2f),
        size = Size(3.4f, 7.4f),
        cornerRadius = CornerRadius(1.5f, 1.5f)
    )
    drawRoundRect(
        color = Color(0xFFFFF2B8),
        topLeft = Offset(center.x - 1.1f, center.y - 87.4f),
        size = Size(2.2f, 5.8f),
        cornerRadius = CornerRadius(1f, 1f)
    )
}

private fun DrawScope.drawTogglesExact(
    hapticEnabled: Boolean,
    soundEnabled: Boolean
) {
    // 1. LEFT TOGGLE: HAPTIC at (55, 198) with Electric Cyan LED at (78, 233)
    val hCenter = Offset(HAPTIC_CX, HAPTIC_CY)
    // Top & Bottom Mounting Rivets
    drawCircle(color = Color(0xFF555962), radius = 3.3f, center = Offset(hCenter.x, hCenter.y - 19.5f))
    drawCircle(color = Color(0xFF111215), radius = 3.3f, center = Offset(hCenter.x, hCenter.y - 19.5f), style = Stroke(width = 0.9f))
    drawCircle(color = Color(0xFFA8AEB8), radius = 1.1f, center = Offset(hCenter.x - 1f, hCenter.y - 20.5f))

    drawCircle(color = Color(0xFF555962), radius = 3.3f, center = Offset(hCenter.x, hCenter.y + 19.5f))
    drawCircle(color = Color(0xFF111215), radius = 3.3f, center = Offset(hCenter.x, hCenter.y + 19.5f), style = Stroke(width = 0.9f))
    drawCircle(color = Color(0xFFA8AEB8), radius = 1.1f, center = Offset(hCenter.x - 1f, hCenter.y + 18.5f))

    // Stepped Bushing & Hex Washer
    drawCircle(color = Color(0xFF17191C), radius = 11f, center = hCenter)
    drawCircle(color = Color(0xFF545862), radius = 11f, center = hCenter, style = Stroke(width = 1.2f))
    drawCircle(color = Color(0xFF2E3138), radius = 7.5f, center = hCenter)
    drawCircle(color = Color(0xFF0F1013), radius = 7.5f, center = hCenter, style = Stroke(width = 1f))
    drawCircle(color = Color(0xFF141518), radius = 4.2f, center = hCenter)

    // 3D Chrome Bat Handle
    val hTipY = if (hapticEnabled) hCenter.y - 8f else hCenter.y + 8f
    val hTipX = hCenter.x - 2.5f
    // Shadow
    drawLine(
        color = Color(0xA6070809),
        start = hCenter,
        end = Offset(hCenter.x - 4f, if (hapticEnabled) hCenter.y - 9f else hCenter.y + 9f),
        strokeWidth = 6.5f,
        cap = StrokeCap.Round
    )
    // Shaft
    drawLine(
        color = Color(0xFF9EA4B0),
        start = hCenter,
        end = Offset(hTipX, hTipY),
        strokeWidth = 4.8f,
        cap = StrokeCap.Round
    )
    // Ball tip
    drawCircle(color = Color(0xFFDCE1E8), radius = 3.4f, center = Offset(hTipX, hTipY))
    drawCircle(color = Color(0xFFFFFFFF), radius = 1.2f, center = Offset(hTipX - 0.8f, hTipY - 1f))

    // Laser-Etched "HAPTIC" Label Below Switch
    drawIntoCanvas { canvas ->
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7D838E.toInt()
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
        }
        canvas.nativeCanvas.drawText("HAPTIC", 51f, 236f, textPaint)
    }

    // Glowing Electric Cyan (#1AF2FF) LED Indicator Dot to Right of Label at (78, 233)
    val cyanLedCenter = Offset(78f, 233f)
    drawCircle(color = Color(0xFF12161A), radius = 4.5f, center = cyanLedCenter)
    drawCircle(color = Color(0xFF363B44), radius = 4.5f, center = cyanLedCenter, style = Stroke(width = 0.8f))
    if (hapticEnabled) {
        drawCircle(color = Color(0x651AF2FF), radius = 6.5f, center = cyanLedCenter)
        drawCircle(color = Color(0xFF1AF2FF), radius = 2.9f, center = cyanLedCenter)
        drawCircle(color = Color(0xFFE8FFFF), radius = 1.4f, center = cyanLedCenter)
    } else {
        drawCircle(color = Color(0xFF252930), radius = 2.9f, center = cyanLedCenter)
    }

    // 2. RIGHT TOGGLE: SOUND at (333, 166) with Emerald Green LED at (309, 167)
    val sCenter = Offset(SOUND_CX, SOUND_CY)
    // Top & Bottom Mounting Rivets
    drawCircle(color = Color(0xFF555962), radius = 3.3f, center = Offset(sCenter.x, sCenter.y - 19f))
    drawCircle(color = Color(0xFF111215), radius = 3.3f, center = Offset(sCenter.x, sCenter.y - 19f), style = Stroke(width = 0.9f))
    drawCircle(color = Color(0xFFA8AEB8), radius = 1.1f, center = Offset(sCenter.x - 1f, sCenter.y - 20f))

    drawCircle(color = Color(0xFF555962), radius = 3.3f, center = Offset(sCenter.x, sCenter.y + 21f))
    drawCircle(color = Color(0xFF111215), radius = 3.3f, center = Offset(sCenter.x, sCenter.y + 21f), style = Stroke(width = 0.9f))
    drawCircle(color = Color(0xFFA8AEB8), radius = 1.1f, center = Offset(sCenter.x - 1f, sCenter.y + 20f))

    // Stepped Bushing & Washer
    drawCircle(color = Color(0xFF17191C), radius = 11f, center = sCenter)
    drawCircle(color = Color(0xFF545862), radius = 11f, center = sCenter, style = Stroke(width = 1.2f))
    drawCircle(color = Color(0xFF2E3138), radius = 7.5f, center = sCenter)
    drawCircle(color = Color(0xFF0F1013), radius = 7.5f, center = sCenter, style = Stroke(width = 1f))
    drawCircle(color = Color(0xFF141518), radius = 4.2f, center = sCenter)

    // 3D Chrome Bat Handle
    val sTipY = if (soundEnabled) sCenter.y - 8f else sCenter.y + 8f
    val sTipX = sCenter.x + 2.5f
    // Shadow
    drawLine(
        color = Color(0xA6070809),
        start = sCenter,
        end = Offset(sCenter.x + 3.5f, if (soundEnabled) sCenter.y - 9f else sCenter.y + 9f),
        strokeWidth = 6.5f,
        cap = StrokeCap.Round
    )
    // Shaft
    drawLine(
        color = Color(0xFF9EA4B0),
        start = sCenter,
        end = Offset(sTipX, sTipY),
        strokeWidth = 4.8f,
        cap = StrokeCap.Round
    )
    drawCircle(color = Color(0xFFDCE1E8), radius = 3.4f, center = Offset(sTipX, sTipY))
    drawCircle(color = Color(0xFFFFFFFF), radius = 1.2f, center = Offset(sTipX - 0.8f, sTipY - 1f))

    // Glowing Emerald Green (#26FF78) LED Dot to Left of Switch at (309, 167)
    val greenLedCenter = Offset(309f, 167f)
    drawCircle(color = Color(0xFF12161A), radius = 4.5f, center = greenLedCenter)
    drawCircle(color = Color(0xFF363B44), radius = 4.5f, center = greenLedCenter, style = Stroke(width = 0.8f))
    if (soundEnabled) {
        drawCircle(color = Color(0x6526FF78), radius = 6.5f, center = greenLedCenter)
        drawCircle(color = Color(0xFF26FF78), radius = 2.9f, center = greenLedCenter)
        drawCircle(color = Color(0xFFEAFFF2), radius = 1.4f, center = greenLedCenter)
    } else {
        drawCircle(color = Color(0xFF252930), radius = 2.9f, center = greenLedCenter)
    }

    // "SOUND" Label Cleanly Centered Below Green LED at (309, 190) - Never collides with ear-lug!
    drawIntoCanvas { canvas ->
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7D838E.toInt()
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
        }
        canvas.nativeCanvas.drawText("SOUND", 309f, 190f, textPaint)
    }
}

private fun DrawScope.drawFaderExact(
    knobY: Float
) {
    // 1. Deep Recessed Vertical Bay (x=289, y=200, w=62, h=198, rx=13)
    drawRoundRect(
        color = Color(0xFF131518),
        topLeft = Offset(289f, 200f),
        size = Size(62f, 198f),
        cornerRadius = CornerRadius(13f, 13f)
    )
    drawRoundRect(
        color = Color(0xFF434750),
        topLeft = Offset(289f, 200f),
        size = Size(62f, 198f),
        cornerRadius = CornerRadius(13f, 13f),
        style = Stroke(width = 1.2f)
    )
    drawRoundRect(
        color = Color(0xFF191B1F),
        topLeft = Offset(293f, 204f),
        size = Size(54f, 190f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFF0B0C0E),
        topLeft = Offset(293f, 204f),
        size = Size(54f, 190f),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 1.5f)
    )

    // Top & Bottom Phillips Screws Inside Fader Bay
    listOf(213f, 383f).forEach { sy ->
        val sCenter = Offset(313f, sy)
        drawCircle(color = Color(0xFF4A4E56), radius = 4.8f, center = sCenter)
        drawCircle(color = Color(0xFF0D0E11), radius = 4.8f, center = sCenter, style = Stroke(width = 0.9f))
        drawLine(color = Color(0xFF16171A), start = sCenter - Offset(2.5f, 0f), end = sCenter + Offset(2.5f, 0f), strokeWidth = 1.2f)
        drawLine(color = Color(0xFF16171A), start = sCenter - Offset(0f, 2.5f), end = sCenter + Offset(0f, 2.5f), strokeWidth = 1.2f)
    }

    // 2. 21 Universal Graduation Ticks along Right Rail
    for (i in 0..20) {
        val ty = FADER_TOP_Y + (i / 20f) * FADER_SPAN
        val isMajor = (i % 5 == 0)
        val x1 = if (isMajor) 329f else 336f
        drawLine(
            color = if (isMajor) Color(0xFF9499A4) else Color(0xFF606570),
            start = Offset(x1, ty),
            end = Offset(344f, ty),
            strokeWidth = if (isMajor) 1.35f else 0.95f
        )
    }

    // 3. Dark Unlit Vertical Slot Track (Floor extends to Y=374)
    val totalSlotH = (FADER_GLOW_FLOOR_Y - FADER_TOP_Y) + 7f
    drawRoundRect(
        color = Color(0xFF08090B),
        topLeft = Offset(309.5f, FADER_TOP_Y - 5f),
        size = Size(7f, totalSlotH),
        cornerRadius = CornerRadius(3.5f, 3.5f)
    )
    drawRoundRect(
        color = Color(0xFF2D3037),
        topLeft = Offset(309.5f, FADER_TOP_Y - 5f),
        size = Size(7f, totalSlotH),
        cornerRadius = CornerRadius(3.5f, 3.5f),
        style = Stroke(width = 0.8f)
    )

    // 4. BLAZING MOLTEN NEON-ORANGE ILLUMINATED BAR BELOW FADER KNOB
    // Extends down to FADER_GLOW_FLOOR_Y (374) so it is ALWAYS visible even at Page 0!
    val barH = max(18f, FADER_GLOW_FLOOR_Y - knobY + 2f)

    // Broad outer glow bloom
    drawRoundRect(
        color = Color(0x35FF4800),
        topLeft = Offset(305.5f, knobY - 2f),
        size = Size(15f, barH + 4f),
        cornerRadius = CornerRadius(7.5f, 7.5f)
    )
    // Mid neon bloom
    drawRoundRect(
        color = Color(0x75FF6600),
        topLeft = Offset(307.5f, knobY - 1f),
        size = Size(11f, barH + 2f),
        cornerRadius = CornerRadius(5.5f, 5.5f)
    )
    // High-intensity molten core
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF5B8), Color(0xFFFF991A), Color(0xFFFF4D00)),
            startY = knobY,
            endY = knobY + barH
        ),
        topLeft = Offset(309.5f, knobY),
        size = Size(7f, barH),
        cornerRadius = CornerRadius(3.5f, 3.5f)
    )
    // White-gold central plasma hairline
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFE082), Color(0xFFFF991A)),
            startY = knobY + 2f,
            endY = knobY + barH - 4f
        ),
        topLeft = Offset(311.5f, knobY + 2f),
        size = Size(3f, max(4f, barH - 4f)),
        cornerRadius = CornerRadius(1.5f, 1.5f)
    )

    // 5. Glowing Neon-Orange Arrow Pointer Aimed at Graduation Ticks
    val arrowX = 342f
    val pointerPath = Path().apply {
        moveTo(arrowX + 3.5f, knobY)
        lineTo(arrowX - 3f, knobY - 3.8f)
        lineTo(arrowX - 3f, knobY + 3.8f)
        close()
    }
    drawCircle(color = Color(0x60FF5500), radius = 5.5f, center = Offset(arrowX, knobY))
    drawPath(pointerPath, color = Color(0xFFFF4D00))
    // Hot pointer core
    val pointerCorePath = Path().apply {
        moveTo(arrowX + 2f, knobY)
        lineTo(arrowX - 2f, knobY - 2.2f)
        lineTo(arrowX - 2f, knobY + 2.2f)
        close()
    }
    drawPath(pointerCorePath, color = Color(0xFFFFF2B8))
    drawLine(
        color = Color(0xFFFF991A),
        start = Offset(arrowX - 6f, knobY),
        end = Offset(arrowX + 1f, knobY),
        strokeWidth = 1.6f
    )

    // 6. STRICT DRAG-ONLY 3D CHAMFERED GUNMETAL FADER KNOB
    val knobLeft = 313f - 18f
    val knobTop = knobY - 11.5f
    // Drop shadow
    drawRoundRect(
        color = Color(0x95000000),
        topLeft = Offset(knobLeft, knobTop + 2.5f),
        size = Size(36f, 23f),
        cornerRadius = CornerRadius(4.5f, 4.5f)
    )
    // Heavy 3D Gunmetal Cap Block
    drawRoundRect(
        color = Color(0xFF2B2E34),
        topLeft = Offset(knobLeft, knobTop),
        size = Size(36f, 23f),
        cornerRadius = CornerRadius(4.5f, 4.5f)
    )
    drawRoundRect(
        color = Color(0xFF0F1013),
        topLeft = Offset(knobLeft, knobTop),
        size = Size(36f, 23f),
        cornerRadius = CornerRadius(4.5f, 4.5f),
        style = Stroke(width = 1.3f)
    )
    // Upper & Lower Chamfered Bevel Planes
    drawRoundRect(
        color = Color(0xFF5E636C),
        topLeft = Offset(knobLeft + 2f, knobTop + 2f),
        size = Size(32f, 5.5f),
        cornerRadius = CornerRadius(1.8f, 1.8f)
    )
    drawRoundRect(
        color = Color(0xFF1C1E22),
        topLeft = Offset(knobLeft + 2f, knobTop + 15.5f),
        size = Size(32f, 5.5f),
        cornerRadius = CornerRadius(1.8f, 1.8f)
    )
    // Center Raised Bridge with Glowing Orange Slit
    drawRoundRect(
        color = Color(0xFF3A3E46),
        topLeft = Offset(knobLeft + 1f, knobTop + 7.7f),
        size = Size(34f, 7.6f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = Color(0xFF141518),
        topLeft = Offset(knobLeft + 1f, knobTop + 7.7f),
        size = Size(34f, 7.6f),
        cornerRadius = CornerRadius(2f, 2f),
        style = Stroke(width = 0.8f)
    )
    // Glowing Orange Slit Bloom & Core
    drawRoundRect(
        color = Color(0x80FF991A),
        topLeft = Offset(knobLeft + 4.5f, knobTop + 9.5f),
        size = Size(27f, 3.8f),
        cornerRadius = CornerRadius(1.9f, 1.9f)
    )
    drawRoundRect(
        color = Color(0xFFFF7700),
        topLeft = Offset(knobLeft + 5.5f, knobTop + 10.2f),
        size = Size(25f, 2.6f),
        cornerRadius = CornerRadius(1.3f, 1.3f)
    )
    drawRoundRect(
        color = Color(0xFFFFF5B8),
        topLeft = Offset(knobLeft + 8f, knobTop + 10.8f),
        size = Size(20f, 1.4f),
        cornerRadius = CornerRadius(0.7f, 0.7f)
    )
}

/** Precomputes 120 sharp metallic gear teeth around the central wheel */
private fun buildGearTeethPath(cx: Float, cy: Float): Path {
    val teeth = 120
    val outerR = 100.5f
    val innerR = 97.2f
    val path = Path()
    for (i in 0 until teeth) {
        val a1 = ((i * 2f) / (teeth * 2f)) * (2f * PI.toFloat())
        val a2 = ((i * 2f + 0.78f) / (teeth * 2f)) * (2f * PI.toFloat())
        val a3 = ((i * 2f + 1.05f) / (teeth * 2f)) * (2f * PI.toFloat())
        val a4 = ((i * 2f + 1.82f) / (teeth * 2f)) * (2f * PI.toFloat())
        val p1x = cx + innerR * cos(a1)
        val p1y = cy + innerR * sin(a1)
        val p2x = cx + outerR * cos(a2)
        val p2y = cy + outerR * sin(a2)
        val p3x = cx + outerR * cos(a3)
        val p3y = cy + outerR * sin(a3)
        val p4x = cx + innerR * cos(a4)
        val p4y = cy + innerR * sin(a4)
        if (i == 0) path.moveTo(p1x, p1y) else path.lineTo(p1x, p1y)
        path.lineTo(p2x, p2y)
        path.lineTo(p3x, p3y)
        path.lineTo(p4x, p4y)
    }
    path.close()
    return path
}
