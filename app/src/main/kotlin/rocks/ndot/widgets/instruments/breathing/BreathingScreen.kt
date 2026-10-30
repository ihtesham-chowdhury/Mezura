package rocks.ndot.widgets.instruments.breathing

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.app.KeepAwake
import rocks.ndot.widgets.kit.DotMatrixText
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ndotScreenInsets
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private data class PhyllotaxisDot(
    val index: Int,
    val cosAngle: Float,
    val sinAngle: Float,
    val q: Float,       // normalized distance [0, 1]
    val rBaseFraction: Float
)

/**
 * Pre-computes the 540 phyllotaxis points once.
 */
private val phyllotaxisCoords: List<PhyllotaxisDot> by lazy {
    val total = 540
    val goldenAngleRad = 137.508 * (PI / 180.0)
    List(total) { i ->
        val angle = i * goldenAngleRad
        val q = i.toFloat() / total.toFloat()
        val rFrac = sqrt(q)
        PhyllotaxisDot(
            index = i,
            cosAngle = cos(angle).toFloat(),
            sinAngle = sin(angle).toFloat(),
            q = q,
            rBaseFraction = rFrac
        )
    }
}

private fun smoothstep(x: Float): Float {
    val cx = x.coerceIn(0f, 1f)
    return cx * cx * (3f - 2f * cx)
}

@Composable
fun BreathingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedProtocolId = remember {
        AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_BREATH_PROTOCOL, BreathingProtocols.CYCLIC_SIGH.id)
    }
    var currentProtocol by remember {
        mutableStateOf(BreathingProtocols.byId(savedProtocolId))
    }
    val engine = remember { BreathingEngine(currentProtocol) }
    var engineState by remember { mutableStateOf(engine.state) }
    var sessionRemainingMs by remember { mutableLongStateOf(engine.sessionRemainingMs) }
    var completedCycles by remember { mutableIntStateOf(0) }

    // Visual & State Flags
    var eyesClosedMode by remember { mutableStateOf(false) }
    var showEvidenceSheet by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(0) } // 3, 2, 1, 0
    var burstAnimProgress by remember { mutableFloatStateOf(0f) }
    var showCompletionCard by remember { mutableStateOf(false) }

    // Session log history for 7-day dots
    var recentSessionsDays by remember {
        mutableStateOf(loadRecentSessionDays(context))
    }

    // Sequence Step Duration and Auto-Initiation
    LaunchedEffect(Unit) {
        val run = rocks.ndot.widgets.instruments.sequence.SequenceEngine.getActiveRun(context)
        if (run != null && (run.state == rocks.ndot.widgets.instruments.sequence.SequenceState.RUNNING_STEP || run.state == rocks.ndot.widgets.instruments.sequence.SequenceState.IDLE) && run.currentStep?.tool == rocks.ndot.widgets.app.Tool.BREATHING) {
            val step = run.currentStep!!
            val stepSeconds = step.durationMinutes * 60L
            engine.setDuration(stepSeconds)
            if (engine.state == BreathingEngine.State.IDLE) {
                engine.start(context)
            }
            engineState = engine.state
            sessionRemainingMs = engine.sessionRemainingMs
            completedCycles = engine.completedCycles
        }
    }

    KeepAwake(engineState == BreathingEngine.State.RUNNING)

    // Smooth Frame Loop for breathing engine & animation
    var animTimeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(engineState) {
        if (engineState == BreathingEngine.State.RUNNING) {
            var lastNanos = System.nanoTime()
            while (engineState == BreathingEngine.State.RUNNING) {
                withFrameNanos { nowNanos ->
                    val deltaMs = ((nowNanos - lastNanos) / 1_000_000L).coerceIn(1L, 100L)
                    lastNanos = nowNanos
                    animTimeSec += deltaMs / 1000f

                    engine.tick(context, deltaMs)
                    engineState = engine.state
                    sessionRemainingMs = engine.sessionRemainingMs
                    completedCycles = engine.completedCycles

                    if (engineState == BreathingEngine.State.COMPLETED) {
                        val durationSec = (currentProtocol.defaultDurationSeconds - sessionRemainingMs / 1000).toInt()
                        saveSessionLog(context, currentProtocol.id, durationSec)
                        recentSessionsDays = loadRecentSessionDays(context)
                        showCompletionCard = true
                        Feedback.success(context)

                        val run = rocks.ndot.widgets.instruments.sequence.SequenceEngine.getActiveRun(context)
                        if (run != null && (run.state == rocks.ndot.widgets.instruments.sequence.SequenceState.RUNNING_STEP || run.state == rocks.ndot.widgets.instruments.sequence.SequenceState.PAUSED)) {
                            rocks.ndot.widgets.instruments.sequence.SequenceEngine.onStepCompleted(context, metadata = "${currentProtocol.name} · $completedCycles cycles")
                        } else {
                            val now = System.currentTimeMillis()
                            rocks.ndot.widgets.instruments.trace.TraceLedger.recordEvent(
                                context,
                                rocks.ndot.widgets.instruments.trace.TraceEvent(
                                    activityType = rocks.ndot.widgets.instruments.trace.ActivityType.BREATHING,
                                    sourceInstrument = "breathing",
                                    title = currentProtocol.name,
                                    startedAtMillis = now - (durationSec * 1000L),
                                    endedAtMillis = now,
                                    durationSeconds = durationSec.toLong(),
                                    outcome = rocks.ndot.widgets.instruments.trace.SessionOutcome.COMPLETED,
                                    metadata = "$completedCycles cycles completed"
                                )
                            )
                        }
                    }
                }
            }
        } else {
            // Idle gentle breathing ticker
            var lastNanos = System.nanoTime()
            while (engineState == BreathingEngine.State.IDLE || engineState == BreathingEngine.State.PAUSED) {
                withFrameNanos { nowNanos ->
                    val deltaMs = ((nowNanos - lastNanos) / 1_000_000L).coerceIn(1L, 100L)
                    lastNanos = nowNanos
                    animTimeSec += deltaMs / 1000f
                }
            }
        }
    }

    // Helper to start with 3-2-1 countdown
    fun startWithCountdown() {
        if (countdownValue > 0) return
        scope.launch {
            countdownValue = 3
            Feedback.tick(context)
            delay(800L)
            countdownValue = 2
            Feedback.tick(context)
            delay(800L)
            countdownValue = 1
            Feedback.tick(context)
            delay(800L)
            countdownValue = 0
            engine.start(context)
            engineState = engine.state
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .ndotScreenInsets()
            .pointerInput(eyesClosedMode) {
                if (eyesClosedMode) {
                    detectTapGestures { eyesClosedMode = false }
                }
            }
    ) {
        val uiAlpha = if (eyesClosedMode) 0.12f else 1f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(uiAlpha)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Bar 56dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "AUTONOMIC PACER",
                    style = TextStyle(
                        color = Color(0xFFF2F6FA),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Fonts.sans,
                        letterSpacing = 1.6.sp
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Eyes Closed Mode Toggle (🌙)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (eyesClosedMode) Ink.AMBER else Ink.SURFACE_2)
                            .clickable { eyesClosedMode = !eyesClosedMode },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("🌙", style = TextStyle(fontSize = 14.sp))
                    }

                    // Reset Button (↺)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_2)
                            .clickable {
                                engine.reset()
                                engineState = engine.state
                                sessionRemainingMs = engine.sessionRemainingMs
                                completedCycles = 0
                                countdownValue = 0
                                showCompletionCard = false
                                Feedback.tick(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("↺", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Protocol Chips Row (Cyclic Sigh, Coherent 5.5, Box, 4-7-8)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BreathingProtocols.ALL.forEach { proto ->
                    val isSelected = currentProtocol.id == proto.id
                    val label = when (proto.id) {
                        BreathingProtocols.CYCLIC_SIGH.id -> "CYCLIC"
                        BreathingProtocols.COHERENT.id -> "COHERE"
                        BreathingProtocols.BOX.id -> "BOX 4·4"
                        BreathingProtocols.RELAX_478.id -> "4-7-8"
                        else -> proto.name.uppercase()
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(
                                if (isSelected) {
                                    Brush.horizontalGradient(listOf(Color(0xFF5AD2EA), Color(0xFF7CE8FC)))
                                } else {
                                    Brush.horizontalGradient(listOf(Color(0xFF121820), Color(0xFF161E28)))
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xAAFFFFFF) else Color(0xFF263240),
                                shape = RoundedCornerShape(19.dp)
                            )
                            .pointerInput(proto.id) {
                                detectTapGestures(
                                    onLongPress = {
                                        AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_BREATH_PROTOCOL, proto.id, proto.name)
                                        Feedback.lock(context)
                                    },
                                    onTap = {
                                        if (currentProtocol.id != proto.id) {
                                            currentProtocol = proto
                                            engine.switchProtocol(proto)
                                            engineState = engine.state
                                            sessionRemainingMs = engine.sessionRemainingMs
                                            countdownValue = 0
                                            Feedback.tick(context)
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Precision Hardware Status Pip (replaces awkward unicode squiggles/boxes)
                        Box(
                            modifier = Modifier
                                .size(5.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFF081820) else Color(0xFF4A6075)
                                )
                        )
                        Spacer(Modifier.width(5.dp))
                        BasicText(
                            text = label,
                            maxLines = 1,
                            style = TextStyle(
                                color = if (isSelected) Color(0xFF06131A) else Color(0xFFB8C7D6),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Fonts.sans,
                                letterSpacing = 0.4.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Evidence Pill (Tap opens citation sheet)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.SURFACE_1)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(999.dp))
                    .clickable { showEvidenceSheet = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("ⓘ ", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
                    BasicText(
                        text = currentProtocol.evidencePill,
                        style = TextStyle(
                            color = Ink.TEXT_MUTED_V4,
                            fontSize = 10.sp,
                            fontFamily = Fonts.mono
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // The Bloom Orb Canvas Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TheBloomOrbCanvas(
                    engine = engine,
                    engineState = engineState,
                    protocol = currentProtocol,
                    animTimeSec = animTimeSec,
                    eyesClosedMode = eyesClosedMode,
                    countdownValue = countdownValue,
                    onTapIdleOrb = {
                        if (engineState == BreathingEngine.State.IDLE) {
                            startWithCountdown()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Optical Clear Center (§4.1): Phase & Countdown Readout rendered BELOW the bloom orb
            val currentPhase = engine.currentPhase
            val targetPhaseColor = when (currentPhase.type) {
                BreathPhaseType.INHALE, BreathPhaseType.INHALE_TOPUP -> Ink.BREATH_IN
                BreathPhaseType.HOLD -> Ink.BREATH_HOLD
                BreathPhaseType.EXHALE -> Ink.BREATH_OUT
            }
            val phaseRemainingSec = ceil((currentPhase.durationMs - engine.phaseElapsedMs) / 1000f).toInt().coerceAtLeast(1)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (countdownValue > 0) {
                    DotMatrixText(
                        text = "GET READY · $countdownValue",
                        dotPitch = 2.4.dp,
                        litColor = Ink.TEXT_WARM,
                        glow = true
                    )
                } else if (engineState == BreathingEngine.State.RUNNING || engineState == BreathingEngine.State.PAUSED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DotMatrixText(
                            text = currentPhase.label,
                            dotPitch = 2.4.dp,
                            litColor = targetPhaseColor,
                            glow = false
                        )
                        DotMatrixText(
                            text = "$phaseRemainingSec",
                            dotPitch = 3.6.dp,
                            litColor = targetPhaseColor,
                            glow = true
                        )
                    }
                } else {
                    BasicText(
                        text = "TAP ORB OR START TO BEGIN",
                        style = TextStyle(
                            color = Ink.TEXT_MUTED_V4,
                            fontSize = 11.sp,
                            fontFamily = Fonts.mono,
                            letterSpacing = 1.4.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Session Progress: mm:ss · CYCLE n / total
            val totalSec = sessionRemainingMs / 1000L
            val mins = totalSec / 60L
            val secs = totalSec % 60L
            val timeFmt = String.format("%02d:%02d", mins, secs)
            val cycleEst = max(1, (currentProtocol.defaultDurationSeconds / (currentProtocol.cycleDurationMs / 1000)).toInt())

            BasicText(
                text = "$timeFmt · CYCLE $completedCycles / $cycleEst",
                style = TextStyle(
                    color = Ink.TEXT_MUTED_V4,
                    fontSize = 13.sp,
                    fontFamily = Fonts.mono,
                    letterSpacing = 1.sp
                )
            )

            Spacer(Modifier.height(14.dp))

            // Controls: ( - ) [ START / PAUSE ] ( + )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stepper -
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_2)
                        .clickable {
                            val newDur = (engine.sessionDurationSeconds - 60L).coerceAtLeast(60L)
                            engine.setDuration(newDur)
                            sessionRemainingMs = engine.sessionRemainingMs
                            Feedback.tick(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("−", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(Modifier.width(20.dp))

                // Start / Pause Pill
                var isHoldingPause by remember { mutableStateOf(false) }
                val startPillBrush = Brush.horizontalGradient(
                    listOf(Ink.BREATH_IN, Ink.BREATH_HOLD)
                )

                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (engineState == BreathingEngine.State.RUNNING) Ink.SURFACE_3 else Ink.BREATH_IN)
                        .border(1.dp, if (engineState == BreathingEngine.State.RUNNING) Ink.HAIRLINE_V4 else Ink.BREATH_IN, RoundedCornerShape(999.dp))
                        .pointerInput(engineState) {
                            detectTapGestures(
                                onTap = {
                                    if (engineState == BreathingEngine.State.RUNNING) {
                                        engine.pause()
                                        engineState = engine.state
                                        Feedback.tick(context)
                                    } else if (engineState == BreathingEngine.State.PAUSED) {
                                        engine.resume(context)
                                        engineState = engine.state
                                        Feedback.tick(context)
                                    } else {
                                        startWithCountdown()
                                    }
                                },
                                onLongPress = {
                                    if (engineState == BreathingEngine.State.RUNNING) {
                                        engine.reset()
                                        engineState = engine.state
                                        sessionRemainingMs = engine.sessionRemainingMs
                                        Feedback.lock(context)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = when (engineState) {
                            BreathingEngine.State.RUNNING -> "PAUSE"
                            BreathingEngine.State.PAUSED -> "RESUME"
                            else -> "START"
                        },
                        style = TextStyle(
                            color = if (engineState == BreathingEngine.State.RUNNING) Ink.TEXT_WARM else Ink.GROUND,
                            fontSize = 14.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(Modifier.width(20.dp))

                // Stepper +
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_2)
                        .clickable {
                            val newDur = (engine.sessionDurationSeconds + 60L).coerceAtMost(3600L)
                            engine.setDuration(newDur)
                            sessionRemainingMs = engine.sessionRemainingMs
                            Feedback.tick(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("+", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Tiny 7-day Session Dots Row (● ● ● ○ ○ ○ ○ last 7 days)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentSessionsDays.forEach { hasSession ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (hasSession) Ink.BREATH_IN else Ink.SURFACE_3)
                    )
                }
                Spacer(Modifier.width(4.dp))
                BasicText(
                    text = "last 7 days",
                    style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                )
            }

            NavSpacer()
        }

        // Evidence Citation Sheet (§4.2)
        if (showEvidenceSheet) {
            NdotSheet(onDismiss = { showEvidenceSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = currentProtocol.name.uppercase(),
                            style = TextStyle(color = Ink.BREATH_IN, fontSize = 14.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Ink.SURFACE_3)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            BasicText(
                                text = "EVIDENCE",
                                style = TextStyle(color = Ink.BRASS, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    BasicText(
                        text = currentProtocol.evidenceSheetText,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = FontFamily.Default, lineHeight = 20.sp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            BasicText(
                                text = "SAFETY & PRECAUTIONS",
                                style = TextStyle(color = Ink.BRASS, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                text = "Breathe gently and comfortably. If you ever feel dizzy, lightheaded, or uncomfortable, stop immediately and return to natural spontaneous breathing. This instrument is for personal focus and relaxation, not intended as medical advice or treatment.",
                                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.sans, lineHeight = 16.sp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // Completion Moment Card
        if (showCompletionCard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showCompletionCard = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Ink.SURFACE_2)
                        .border(1.dp, Ink.BREATH_IN, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText("SESSION COMPLETE", style = TextStyle(color = Ink.BREATH_IN, fontSize = 15.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                    BasicText("${currentProtocol.name} · $completedCycles cycles", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.BREATH_IN)
                            .clickable { showCompletionCard = false }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        BasicText("CONTINUE", style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// The Bloom Phyllotaxis Canvas (§6.2, §6.3)
// -----------------------------------------------------------------------------

@Composable
private fun TheBloomOrbCanvas(
    engine: BreathingEngine,
    engineState: BreathingEngine.State,
    protocol: BreathingProtocol,
    animTimeSec: Float,
    eyesClosedMode: Boolean,
    countdownValue: Int,
    onTapIdleOrb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    // Determine phase color & progress u
    val currentPhase = engine.currentPhase
    val targetPhaseColor = when (currentPhase.type) {
        BreathPhaseType.INHALE, BreathPhaseType.INHALE_TOPUP -> Ink.BREATH_IN
        BreathPhaseType.HOLD -> Ink.BREATH_HOLD
        BreathPhaseType.EXHALE -> Ink.BREATH_OUT
    }

    // Phase progress u in [0, 1]
    val u = engine.phaseFraction
    val isBox = protocol.id == BreathingProtocols.BOX.id
    val isSigh = protocol.id == BreathingProtocols.CYCLIC_SIGH.id
    val is478 = protocol.id == BreathingProtocols.RELAX_478.id

    // Countdown active
    val isCountingDown = countdownValue > 0

    // Phase remaining seconds
    val phaseRemainingSec = ceil((currentPhase.durationMs - engine.phaseElapsedMs) / 1000f).toInt().coerceAtLeast(1)

    Box(
        modifier = modifier.pointerInput(engineState) {
            detectTapGestures {
                if (engineState == BreathingEngine.State.IDLE) onTapIdleOrb()
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val minDim = min(w, h)
            val rMin = 0.28f * minDim
            val rMax = 0.44f * minDim
            val sRatio = rMax / rMin
            val L = if (protocol.id == BreathingProtocols.COHERENT.id) 0.25f else 0.35f

            // Radial Glow Behind Orb
            val glowColor = if (eyesClosedMode) targetPhaseColor.copy(alpha = 0.08f) else targetPhaseColor.copy(alpha = 0.22f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = rMax * 1.35f
                ),
                radius = rMax * 1.35f,
                center = center
            )

            // Guide Ring (72 dots, white 18%) at Rmax
            val guideDots = 72
            val guideColor = Color.White.copy(alpha = 0.16f)
            for (g in 0 until guideDots) {
                val ga = g * (2 * PI / guideDots)
                val gx = center.x + rMax * cos(ga).toFloat()
                val gy = center.y + rMax * sin(ga).toFloat()
                drawCircle(color = guideColor, radius = 1.2f * density, center = Offset(gx, gy))
            }

            // Cyclic Sigh top-up notch at 80% mark on the guide ring
            if (isSigh) {
                val notchAngle = -PI.toFloat() / 2f + 0.80f * 2 * PI.toFloat()
                val nx = center.x + rMax * cos(notchAngle)
                val ny = center.y + rMax * sin(notchAngle)
                drawCircle(color = Ink.BREATH_IN, radius = 3.dp.toPx(), center = Offset(nx, ny))
            }

            // Guide Ring Tracer Dot
            if (engineState == BreathingEngine.State.RUNNING) {
                val tracerAngle = -PI.toFloat() / 2f + u * 2 * PI.toFloat()
                val tx = center.x + rMax * cos(tracerAngle)
                val ty = center.y + rMax * sin(tracerAngle)
                drawCircle(color = targetPhaseColor, radius = 3.5.dp.toPx(), center = Offset(tx, ty))
                drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = Offset(tx, ty))
            }

            // Phyllotaxis 540-dot Bloom
            val coords = phyllotaxisCoords
            val isIdle = (engineState == BreathingEngine.State.IDLE || engineState == BreathingEngine.State.PAUSED)

            for (dot in coords) {
                val q = dot.q
                val i = dot.index

                val k_i = when {
                    isCountingDown -> 1.0f
                    isIdle -> {
                        // Gentle idle breathe at 4% amp and 0.2 Hz
                        1.0f + 0.04f * sin(animTimeSec * 2f * PI.toFloat() * 0.2f)
                    }
                    currentPhase.type == BreathPhaseType.INHALE -> {
                        val input = (u * (1f + L) - L * q)
                        1f + (sRatio - 1f) * smoothstep(input)
                    }
                    currentPhase.type == BreathPhaseType.INHALE_TOPUP -> {
                        // Extra bloom for top-up
                        val baseK = 1f + (sRatio - 1f) * 0.80f
                        baseK + (sRatio - 1f) * 0.20f * smoothstep(u)
                    }
                    currentPhase.type == BreathPhaseType.HOLD -> {
                        // Gentle shimmer on hold
                        sRatio + 0.012f * sin(animTimeSec * 1.3f + i * 0.37f)
                    }
                    else -> { // EXHALE
                        val input = (u * (1f + L) - L * (1f - q))
                        sRatio - (sRatio - 1f) * smoothstep(input)
                    }
                }

                var r = rMin * dot.rBaseFraction * k_i

                // 4-7-8 Swirl during hold
                var cosA = dot.cosAngle
                var sinA = dot.sinAngle
                if (is478 && currentPhase.type == BreathPhaseType.HOLD) {
                    val rot = animTimeSec * 0.15f * 2 * PI.toFloat()
                    val originalAngle = i * (137.508 * PI / 180.0) + rot
                    cosA = cos(originalAngle).toFloat()
                    sinA = sin(originalAngle).toFloat()
                }

                // Superellipse Box morph during hold
                if (isBox && currentPhase.type == BreathPhaseType.HOLD) {
                    val p = 3.5f // exponent
                    val denom = (abs(cosA).pow(p) + abs(sinA).pow(p)).pow(1f / p).coerceAtLeast(0.01f)
                    r /= denom
                }

                val dx = center.x + cosA * r
                val dy = center.y + sinA * r

                // Dot Appearance: size & alpha
                val dotRadius = (1.2f + 2.3f * sqrt(1f - q) * k_i.pow(0.4f)) * density
                val baseAlpha = (0.45f + 0.55f * sqrt(1f - q) + 0.08f * sin(animTimeSec * 1.7f + i * 0.37f)).coerceIn(0.15f, 1f)
                val dotAlpha = if (eyesClosedMode) baseAlpha * 0.35f else baseAlpha

                // Color gradient from center (targetPhaseColor) toward edge (55% brightness)
                val edgeColor = targetPhaseColor.copy(alpha = 0.55f)
                val col = lerp(targetPhaseColor, edgeColor, q).copy(alpha = dotAlpha)

                drawCircle(color = col, radius = dotRadius, center = Offset(dx, dy))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Session History Helpers (7-Day Row)
// -----------------------------------------------------------------------------

private fun loadRecentSessionDays(context: Context): List<Boolean> {
    val prefs = context.getSharedPreferences("ndot_breathing", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("breath_sessions", "[]") ?: "[]"
    val arr = runCatching { JSONArray(jsonStr) }.getOrDefault(JSONArray())

    val todayEpoch = LocalDate.now().toEpochDay()
    val sessionDays = mutableSetOf<Long>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        sessionDays.add(obj.optLong("epochDay", 0L))
    }

    return (6 downTo 0).map { dayOffset ->
        sessionDays.contains(todayEpoch - dayOffset)
    }
}

private fun saveSessionLog(context: Context, protocolId: String, seconds: Int) {
    val prefs = context.getSharedPreferences("ndot_breathing", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("breath_sessions", "[]") ?: "[]"
    val arr = runCatching { JSONArray(jsonStr) }.getOrDefault(JSONArray())

    val entry = JSONObject().apply {
        put("epochDay", LocalDate.now().toEpochDay())
        put("protocolId", protocolId)
        put("seconds", seconds)
    }
    arr.put(entry)
    prefs.edit().putString("breath_sessions", arr.toString()).apply()
}
