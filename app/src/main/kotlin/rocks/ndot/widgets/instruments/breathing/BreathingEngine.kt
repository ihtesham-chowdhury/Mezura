package rocks.ndot.widgets.instruments.breathing

import android.content.Context
import rocks.ndot.widgets.kit.Feedback

class BreathingEngine(
    var protocol: BreathingProtocol = BreathingProtocols.CYCLIC_SIGH,
    var sessionDurationSeconds: Long = protocol.defaultDurationSeconds,
) {
    enum class State { IDLE, RUNNING, PAUSED, COMPLETED }

    var state: State = State.IDLE
        private set

    var sessionRemainingMs: Long = sessionDurationSeconds * 1000L
        private set

    var phaseIndex: Int = 0
        private set

    var phaseElapsedMs: Long = 0L
        private set

    var completedCycles: Int = 0
        private set

    val currentPhase: BreathPhase
        get() = protocol.phases[phaseIndex.coerceIn(0, protocol.phases.size - 1)]

    val phaseFraction: Float
        get() {
            val dur = currentPhase.durationMs
            return if (dur > 0) (phaseElapsedMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
        }

    val currentInstruction: String
        get() = currentPhase.label

    fun switchProtocol(newProtocol: BreathingProtocol) {
        protocol = newProtocol
        sessionDurationSeconds = newProtocol.defaultDurationSeconds
        reset()
    }

    fun setDuration(seconds: Long) {
        sessionDurationSeconds = seconds.coerceIn(30L, 3600L)
        if (state == State.IDLE) {
            sessionRemainingMs = sessionDurationSeconds * 1000L
        }
    }

    fun start(context: Context) {
        if (state == State.IDLE || state == State.COMPLETED) {
            sessionRemainingMs = sessionDurationSeconds * 1000L
            phaseIndex = 0
            phaseElapsedMs = 0L
            completedCycles = 0
        }
        state = State.RUNNING
        triggerPhaseCue(context, currentPhase)
    }

    fun pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED
        }
    }

    fun resume(context: Context) {
        if (state == State.PAUSED) {
            state = State.RUNNING
            triggerPhaseCue(context, currentPhase)
        }
    }

    fun reset() {
        state = State.IDLE
        sessionRemainingMs = sessionDurationSeconds * 1000L
        phaseIndex = 0
        phaseElapsedMs = 0L
        completedCycles = 0
    }

    fun tick(context: Context, deltaMs: Long) {
        if (state != State.RUNNING) return

        sessionRemainingMs = (sessionRemainingMs - deltaMs).coerceAtLeast(0L)
        if (sessionRemainingMs <= 0L) {
            state = State.COMPLETED
            Feedback.play(context, Feedback.Sound.CHIME)
            Feedback.haptic(context, Feedback.Haptic.DONE)
            return
        }

        phaseElapsedMs += deltaMs
        val dur = currentPhase.durationMs
        if (phaseElapsedMs >= dur) {
            phaseElapsedMs -= dur
            val nextIndex = phaseIndex + 1
            if (nextIndex >= protocol.phases.size) {
                phaseIndex = 0
                completedCycles++
            } else {
                phaseIndex = nextIndex
            }
            triggerPhaseCue(context, currentPhase)
        }
    }

    private fun triggerPhaseCue(context: Context, phase: BreathPhase) {
        when (phase.type) {
            BreathPhaseType.INHALE -> Feedback.breathingInhale(context)
            BreathPhaseType.INHALE_TOPUP -> Feedback.breathingTopUp(context)
            BreathPhaseType.HOLD -> Feedback.breathingHold(context)
            BreathPhaseType.EXHALE -> Feedback.breathingExhale(context)
        }
    }
}
