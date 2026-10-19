package rocks.ndot.widgets.instruments.metronome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.app.KeepAwake
import rocks.ndot.widgets.app.LightChrome
import rocks.ndot.widgets.kit.ChipColors
import rocks.ndot.widgets.kit.ChipRow
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.StepKey
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle
import rocks.ndot.widgets.kit.rememberFrameTicker
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private enum class MetroDesign(val id: String, val label: String) { STUDIO("studio", "Studio"), PENDULUM("pendulum", "Pendulum") }

/**
 * The metronome, as a studio desk unit or a wooden pendulum. Tempo is set the way each object
 * sets it: turning the Studio's dial, or sliding the pendulum's weight up and down its rod.
 * Both take tap tempo. It plays only while you're looking at it - leaving the screen stops it.
 */
@Composable
fun MetronomeScreen() {
    val context = LocalContext.current
    var design by remember {
        mutableStateOf(
            if (AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_METRONOME_STYLE, "studio") == "pendulum") MetroDesign.PENDULUM else MetroDesign.STUDIO
        )
    }
    var bpm by remember { mutableIntStateOf(AppPrefs.metronomeBpm(context)) }
    var beats by remember { mutableIntStateOf(AppPrefs.metronomeBeats(context)) }
    var subdivision by remember { mutableIntStateOf(AppPrefs.metronomeSubdivision(context)) }
    var voice by remember { mutableStateOf(Metronome.Voice.byId(AppPrefs.metronomeSound(context))) }
    var beatHaptic by remember { mutableStateOf(AppPrefs.metronomeBeatHaptic(context)) }
    var playing by remember { mutableStateOf(Metronome.isPlaying) }
    val taps = remember { ArrayList<Long>() }

    // Everything the audio thread reads lives on the engine; keep it in step with the screen.
    SideEffect {
        Metronome.bpm = bpm
        Metronome.beats = beats
        Metronome.subdivision = subdivision
        Metronome.voice = voice
    }

    fun save() = AppPrefs.saveMetronome(context, bpm, beats, subdivision, voice.id, beatHaptic)

    fun setPlaying(on: Boolean) {
        if (on) Metronome.start() else Metronome.stop()
        playing = on
    }

    fun tap() {
        val now = System.currentTimeMillis()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        while (taps.size > 5) taps.removeAt(0)
        if (taps.size >= 2) {
            val interval = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
            bpm = (60_000.0 / interval).roundToInt().coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { setPlaying(false); save() }
    DisposableEffect(Unit) {
        onDispose {
            Metronome.stop()
            AppPrefs.saveMetronome(context, Metronome.bpm, Metronome.beats, Metronome.subdivision, Metronome.voice.id, beatHaptic)
        }
    }
    KeepAwake(playing)
    LightChrome(design == MetroDesign.STUDIO)

    // A buzz on every beat, heavier on the one, if asked for.
    LaunchedEffect(playing, beatHaptic) {
        var last = Long.MIN_VALUE
        while (playing && beatHaptic) {
            withFrameNanos { }
            val pos = Metronome.position() ?: continue
            val beat = floor(pos).toLong()
            if (beat != last) {
                last = beat
                Feedback.haptic(context, if (beat % Metronome.beats == 0L) Feedback.Haptic.HEAVY else Feedback.Haptic.TICK)
            }
        }
    }

    val light = design == MetroDesign.STUDIO
    val label = if (light) Color(0x8C000000) else Color(0x99F3E6CF)
    val background = if (light) Brush.verticalGradient(listOf(Color(0xFFE9E9E7), Color(0xFFE2E2E0)))
    else Brush.radialGradient(listOf(Color(0xFF3A2E25), Color(0xFF120E0B)), radius = 1500f)

    Column(Modifier.fillMaxSize().background(background)) {
        ToolHeader("METRONOME", label) {
            TextAction("SOUND · ${voice.label}", label, {
                voice = Metronome.Voice.entries[(voice.ordinal + 1) % Metronome.Voice.entries.size]
                save()
            })
            Spacer(Modifier.width(6.dp))
            TextAction(if (beatHaptic) "BUZZ · ON" else "BUZZ · OFF", label, {
                beatHaptic = !beatHaptic
                save()
            })
        }
        ChipRow(
            items = MetroDesign.entries.map { it.label },
            selected = design.ordinal,
            colors = if (light) ChipColors.Light else ChipColors(Color(0x14FFFFFF), Color(0xFFE8DCC6), Color(0xFFC9A45A), Color(0xFF1D140C)),
            onSelect = {
                design = MetroDesign.entries[it]
                AppPrefs.setDesign(context, "metronome", design.id)
                Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
            },
            onLongPress = {
                val picked = MetroDesign.entries[it]
                design = picked
                AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_METRONOME_STYLE, picked.id, "${picked.label} style for Metronome")
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (design) {
                MetroDesign.STUDIO -> StudioFace(
                    bpm = bpm, beats = beats, subdivision = subdivision, playing = playing,
                    onBpm = { bpm = it },
                    onSubdivision = { subdivision = if (subdivision >= 4) 1 else subdivision + 1; save() },
                    onSignature = { beats = if (beats >= 7) 1 else beats + 1; save() },
                    onTap = ::tap,
                    onPlay = { setPlaying(!playing); save() },
                )
                MetroDesign.PENDULUM -> PendulumFace(
                    bpm = bpm, beats = beats, playing = playing,
                    onBpm = { bpm = it },
                    onBeats = { beats = it; save() },
                    onTap = ::tap,
                    onPlay = { setPlaying(!playing); save() },
                )
            }
        }
        NavSpacer()
    }
}

/** The beat within the bar and how freshly it struck (1 on the click, fading to 0). */
private fun beatState(beats: Int): Pair<Int, Float> {
    val pos = Metronome.position() ?: return -1 to 0f
    val whole = floor(pos)
    val beat = (whole.toLong() % beats.coerceAtLeast(1)).toInt()
    val pulse = (1f - ((pos - whole).toFloat() * 3.5f)).coerceIn(0f, 1f)
    return beat to pulse
}

@Composable
private fun StudioFace(
    bpm: Int, beats: Int, subdivision: Int, playing: Boolean,
    onBpm: (Int) -> Unit, onSubdivision: () -> Unit, onSignature: () -> Unit, onTap: () -> Unit, onPlay: () -> Unit,
) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(MetronomeArt.Region.NONE) }
    val ticker = rememberFrameTicker(playing)
    val currentBpm by rememberUpdatedState(bpm)
    val setBpm by rememberUpdatedState(onBpm)
    val handlers by rememberUpdatedState(listOf(onSubdivision, onSignature, onTap, onPlay))

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        val k = min(maxWidth.value / MetronomeArt.STUDIO_W, maxHeight.value / MetronomeArt.STUDIO_H)
        Canvas(
            Modifier
                .size((MetronomeArt.STUDIO_W * k).dp, (MetronomeArt.STUDIO_H * k).dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val scale = size.width / MetronomeArt.STUDIO_W
                        val region = MetronomeArt.studioHit(down.position.x / scale, down.position.y / scale)
                        if (region == MetronomeArt.Region.NONE) return@awaitEachGesture
                        down.consume()
                        pressed = region
                        when (region) {
                            MetronomeArt.Region.DIAL -> {
                                // Turning the dial: one BPM per three degrees, a detent for each.
                                Feedback.haptic(context, Feedback.Haptic.TICK)
                                var lastAngle = MetronomeArt.studioAngle(down.position.x / scale, down.position.y / scale)
                                var carry = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val angle = MetronomeArt.studioAngle(change.position.x / scale, change.position.y / scale)
                                    var delta = angle - lastAngle
                                    if (delta > PI) delta -= (2 * PI).toFloat()
                                    if (delta < -PI) delta += (2 * PI).toFloat()
                                    lastAngle = angle
                                    carry += delta * 180f / PI.toFloat() / 3f
                                    val steps = carry.toInt()
                                    if (steps != 0) {
                                        carry -= steps
                                        val next = (currentBpm + steps).coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)
                                        if (next != currentBpm) {
                                            setBpm(next)
                                            Feedback.play(context, Feedback.Sound.DETENT)
                                            Feedback.haptic(context, Feedback.Haptic.TICK)
                                        }
                                    }
                                    change.consume()
                                }
                            }
                            MetronomeArt.Region.TAP -> {
                                // Tap tempo counts the moment the finger lands.
                                Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.CLICK)
                                handlers[2]()
                                waitForUpOrCancellation()
                            }
                            else -> {
                                Feedback.press(context, Feedback.Sound.KEY_DOWN, Feedback.Haptic.CLICK)
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    when (region) {
                                        MetronomeArt.Region.SUBDIVISION -> handlers[0]()
                                        MetronomeArt.Region.SIGNATURE -> handlers[1]()
                                        MetronomeArt.Region.MINUS -> setBpm((currentBpm - 1).coerceAtLeast(Metronome.MIN_BPM))
                                        MetronomeArt.Region.PLUS -> setBpm((currentBpm + 1).coerceAtMost(Metronome.MAX_BPM))
                                        MetronomeArt.Region.PLAY -> handlers[3]()
                                        else -> Unit
                                    }
                                    Feedback.play(context, Feedback.Sound.KEY_UP)
                                }
                            }
                        }
                        pressed = MetronomeArt.Region.NONE
                    }
                }
        ) {
            ticker.longValue
            val (beat, pulse) = if (playing) beatState(beats) else (-1 to 0f)
            val state = MetronomeArt.StudioState(
                bpm = bpm,
                signature = "$beats/4",
                subdivision = subdivision,
                beats = beats,
                beat = beat,
                pulse = pulse,
                playing = playing,
                pressed = pressed,
                dialTurn = bpm * 3f * PI.toFloat() / 180f,
            )
            drawIntoCanvas { MetronomeArt.drawStudio(it.nativeCanvas, size.width / MetronomeArt.STUDIO_W, state) }
        }
    }
}

@Composable
private fun PendulumFace(
    bpm: Int, beats: Int, playing: Boolean,
    onBpm: (Int) -> Unit, onBeats: (Int) -> Unit, onTap: () -> Unit, onPlay: () -> Unit,
) {
    val context = LocalContext.current
    // The arm keeps swinging for a moment after stopping, settling back to rest.
    var restAngle by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    // Where the arm was on the last frame it swung - written while drawing, so not state.
    val lastSwing = remember { FloatArray(1) }
    val ticker = rememberFrameTicker(playing || settling)
    val setBpm by rememberUpdatedState(onBpm)
    val play by rememberUpdatedState(onPlay)
    val currentBpm by rememberUpdatedState(bpm)
    val cream = Color(0xFFF3E6CF)

    LaunchedEffect(playing) {
        if (!playing) restAngle = lastSwing[0]
        if (!playing && abs(restAngle) > 0.001f) {
            settling = true
            while (abs(restAngle) > 0.002f) {
                withFrameNanos { }
                restAngle *= 0.88f
            }
            restAngle = 0f
            settling = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val g = MetronomeArt.pendulumGeometry(size.width.toFloat(), size.height.toFloat())
                        val nearWeight = abs(down.position.y - g.weightY(currentBpm)) < 34f * g.u &&
                            abs(down.position.x - g.cx) < g.bw * 0.8f
                        if (nearWeight) {
                            // Slide the weight: up the rod is slower, down towards the pivot faster.
                            down.consume()
                            Feedback.haptic(context, Feedback.Haptic.TICK)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val next = g.bpmForY(change.position.y)
                                if (next != currentBpm) {
                                    setBpm(next)
                                    Feedback.play(context, Feedback.Sound.DETENT)
                                    Feedback.haptic(context, Feedback.Haptic.TICK)
                                }
                                change.consume()
                            }
                        } else {
                            val up = waitForUpOrCancellation()
                            if (up != null && abs(up.position.x - g.cx) < g.bw * 1.2f) {
                                Feedback.press(context, Feedback.Sound.CLACK, Feedback.Haptic.CLICK)
                                play()
                            }
                        }
                    }
                }
        ) {
            ticker.longValue
            val pos = if (playing) Metronome.position() else null
            val angle = if (pos != null) {
                // The beat lands at each end of the swing, where the arm slows and turns.
                (0.36f * cos(PI * pos).toFloat()).also { lastSwing[0] = it }
            } else restAngle
            val flash = if (pos != null) (1f - ((pos - floor(pos)).toFloat() * 4f)).coerceIn(0f, 1f) else 0f
            drawIntoCanvas { MetronomeArt.drawPendulum(it.nativeCanvas, size.width, size.height, bpm, angle, flash) }
        }

        BasicText(
            bpm.toString(),
            style = TextStyle(fontFamily = Fonts.serif, fontWeight = FontWeight.Light, fontSize = 52.sp, color = cream, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        BasicText(
            "${Metronome.tempoName(bpm)} · $beats/4",
            style = TextStyle(fontFamily = Fonts.serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = cream.copy(alpha = 0.6f), textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            StepKey("−", { onBpm((bpm - 1).coerceAtLeast(Metronome.MIN_BPM)) })
            BrassButton("TAP") {
                Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.CLICK)
                onTap()
            }
            BrassButton(if (playing) "STOP" else "START", filled = true) {
                Feedback.press(context, Feedback.Sound.CLACK, Feedback.Haptic.CLICK)
                onPlay()
            }
            StepKey("+", { onBpm((bpm + 1).coerceAtMost(Metronome.MAX_BPM)) })
        }
        Spacer(Modifier.height(10.dp))
        ChipRow(
            items = listOf("2/4", "3/4", "4/4", "5/4", "6/4"),
            selected = beats - 2,
            colors = ChipColors(Color(0x14FFFFFF), Color(0xFFE8DCC6), Color(0xFFC9A45A), Color(0xFF1D140C)),
            onSelect = {
                Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                onBeats(it + 2)
            },
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BrassButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (filled) Color(0xFFC9A45A) else Color(0x14FFFFFF))
            .border(1.dp, Color(0x59C9A45A), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        BasicText(text, style = labelStyle(if (filled) Color(0xFF1D140C) else Color(0xFFF3E6CF), 12.sp, FontWeight.SemiBold))
    }
}
