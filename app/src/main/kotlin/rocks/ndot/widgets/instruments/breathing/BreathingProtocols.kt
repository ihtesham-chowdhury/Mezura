package rocks.ndot.widgets.instruments.breathing

enum class BreathPhaseType { INHALE, INHALE_TOPUP, HOLD, EXHALE }

data class BreathPhase(
    val type: BreathPhaseType,
    val durationMs: Long,
    val label: String,
)

data class BreathingProtocol(
    val id: String,
    val name: String,
    val subtitle: String,
    val defaultDurationSeconds: Long,
    val evidencePill: String,
    val evidenceSheetText: String,
    val phases: List<BreathPhase>,
) {
    val cycleDurationMs: Long get() = phases.sumOf { it.durationMs }
}

object BreathingProtocols {

    val CYCLIC_SIGH = BreathingProtocol(
        id = "physiological_sigh",
        name = "Cyclic Sigh",
        subtitle = "Stress & Mood Reset",
        defaultDurationSeconds = 300L, // 5 minutes
        evidencePill = "STANFORD · BALBAN ET AL. 2023 · 5 MIN",
        evidenceSheetText = "Clinical Trial: Balban, Neri, Huberman et al. (Cell Reports Medicine, 2023).\n\n" +
            "Mechanism: The double inhalation re-inflates collapsed pulmonary alveoli, increasing alveolar surface area to rapidly offload accumulated CO2. The prolonged 6-second exhalation triggers the baroreceptor reflex, engaging vagal cholinergic efferents that immediately decelerate heart rate and lower autonomic arousal.\n\n" +
            "Evidence: 5 minutes daily yielded greater improvements in positive affect and reductions in respiratory rate than mindfulness meditation over a 28-day trial.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 2000L, "INHALE"),
            BreathPhase(BreathPhaseType.INHALE_TOPUP, 1000L, "TOP-UP"),
            BreathPhase(BreathPhaseType.EXHALE, 6000L, "EXHALE"),
        ),
    )

    val COHERENT = BreathingProtocol(
        id = "coherent",
        name = "Coherent 5.5",
        subtitle = "Autonomic Resonance",
        defaultDurationSeconds = 600L, // 10 minutes
        evidencePill = "RESONANCE · LABORDE 2022 · 5.5/MIN",
        evidenceSheetText = "Clinical Framework: Lehrer & Gevirtz (2014); Laborde et al. (Frontiers in Public Health, 2022).\n\n" +
            "Mechanism: Breathing at ~0.1 Hz (5.5 breaths per minute) synchronizes respiration with the natural resonant oscillation of the arterial baroreflex system. This phase-aligns blood pressure fluctuations with respiratory sinus arrhythmia (RSA), maximizing Heart Rate Variability (HRV).\n\n" +
            "Evidence: Meta-analyses confirm coherent slow breathing enhances autonomic self-regulation, cognitive flexibility, and executive control under stress.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 5500L, "INHALE"),
            BreathPhase(BreathPhaseType.EXHALE, 5500L, "EXHALE"),
        ),
    )

    val BOX = BreathingProtocol(
        id = "box",
        name = "Box 4-4-4-4",
        subtitle = "Equal-Phase Focus",
        defaultDurationSeconds = 300L, // 5 minutes
        evidencePill = "EQUAL-PHASE · ZACCARO ET AL. 2018",
        evidenceSheetText = "Historical & Clinical Context: Pranayama tradition (Sama Vritti); reviewed in Zaccaro et al. (Frontiers in Human Neuroscience, 2018).\n\n" +
            "Mechanism: Equal 1:1:1:1 phase ratio balances sympathetic stabilization with post-exhalation CO2 tolerance training. Breath retention after exhale stabilizes blood gas balance and fosters focused parasympathetic tone without inducing drowsiness.\n\n" +
            "Evidence: Widely utilized in high-performance operational environments (aviation, first responders) to stabilize heart rate and narrow attentional focus prior to complex tasks.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 4000L, "INHALE"),
            BreathPhase(BreathPhaseType.HOLD, 4000L, "HOLD"),
            BreathPhase(BreathPhaseType.EXHALE, 4000L, "EXHALE"),
            BreathPhase(BreathPhaseType.HOLD, 4000L, "HOLD"),
        ),
    )

    val RELAX_478 = BreathingProtocol(
        id = "relax_478",
        name = "4-7-8",
        subtitle = "Wind-Down Pattern",
        defaultDurationSeconds = 300L, // 5 minutes
        evidencePill = "EXTENDED EXHALE · VIERRA 2022",
        evidenceSheetText = "Clinical Context: Popularized by Dr. Andrew Weil; studied in Vierra et al. (Physiological Reports, 2022).\n\n" +
            "Mechanism: The 7-second hold increases intrathoracic pressure and allows deep oxygen exchange, while the doubled 8-second slow exhalation strongly activates acetylcholine release onto the sinoatrial node, driving rapid down-regulation of sympathetic tone.\n\n" +
            "Evidence: Effective as an evening wind-down cue to reduce somatic hyperarousal and prime sleep onset latency.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 4000L, "INHALE"),
            BreathPhase(BreathPhaseType.HOLD, 7000L, "HOLD"),
            BreathPhase(BreathPhaseType.EXHALE, 8000L, "EXHALE"),
        ),
    )

    val ALL = listOf(CYCLIC_SIGH, COHERENT, BOX, RELAX_478)

    fun byId(id: String?): BreathingProtocol =
        ALL.firstOrNull { it.id == id } ?: CYCLIC_SIGH
}
