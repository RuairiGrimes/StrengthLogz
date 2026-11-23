package com.strengthlogz.mobile.model

enum class TrainingMode(
    val id: String,
    val label: String,
    val shortLabel: String,
    val tagline: String
) {
    BULK(
        id = "BULK",
        label = "Bulk",
        shortLabel = "Bulk",
        tagline = "Prioritize absolute strength gains, higher weekly volume & progressive overload"
    ),
    MAINGAIN(
        id = "MAINGAIN",
        label = "Maingain",
        shortLabel = "Maingain",
        tagline = "Maintain strength with steady micro-progression & balanced volume"
    ),
    CUT(
        id = "CUT",
        label = "Cut",
        shortLabel = "Cut",
        tagline = "Protect strength retention, emphasize relative strength (× BW & DOTS) & manage fatigue"
    );

    companion object {
        fun fromString(raw: String?): TrainingMode {
            val clean = raw?.trim()?.uppercase() ?: return MAINGAIN
            return entries.firstOrNull { it.id == clean } ?: MAINGAIN
        }
    }
}

data class ModeScoreWeights(
    val strength: Double,
    val volume: Double,
    val consistency: Double,
    val balance: Double
)

data class BwPaceFeedback(
    val status: String,
    val isOnTarget: Boolean,
    val detail: String
)

object TrainingModePolicy {
    const val CUT_RETENTION_FLOOR_RATIO = 0.95
    const val CUT_RETENTION_FLOOR_PCT = 95.0

    fun weeklyMuscleTargets(mode: TrainingMode): LinkedHashMap<String, Int> = when (mode) {
        TrainingMode.BULK -> linkedMapOf(
            "Chest" to 12,
            "Back" to 14,
            "Legs" to 12,
            "Shoulders" to 10,
            "Arms" to 10,
            "Core" to 4
        )
        TrainingMode.MAINGAIN -> linkedMapOf(
            "Chest" to 10,
            "Back" to 12,
            "Legs" to 10,
            "Shoulders" to 9,
            "Arms" to 9,
            "Core" to 4
        )
        TrainingMode.CUT -> linkedMapOf(
            "Chest" to 7,
            "Back" to 8,
            "Legs" to 7,
            "Shoulders" to 6,
            "Arms" to 6,
            "Core" to 4
        )
    }

    fun scoreWeights(mode: TrainingMode): ModeScoreWeights = when (mode) {
        TrainingMode.BULK -> ModeScoreWeights(strength = 0.35, volume = 0.25, consistency = 0.20, balance = 0.20)
        TrainingMode.MAINGAIN -> ModeScoreWeights(strength = 0.30, volume = 0.25, consistency = 0.25, balance = 0.20)
        TrainingMode.CUT -> ModeScoreWeights(strength = 0.35, volume = 0.20, consistency = 0.25, balance = 0.20)
    }

    fun tierLabel(mode: TrainingMode, overallScore: Int): String {
        if (overallScore <= 0) return "No Activity"
        return when (mode) {
            TrainingMode.BULK -> when {
                overallScore >= 88 -> "Peak Growth"
                overallScore >= 74 -> "Strong Gains"
                overallScore >= 58 -> "Steady Build"
                overallScore >= 40 -> "Building Base"
                else -> "Low Stimulus"
            }
            TrainingMode.MAINGAIN -> when {
                overallScore >= 88 -> "Peak Recomp"
                overallScore >= 74 -> "Strong Momentum"
                overallScore >= 58 -> "Steady Progress"
                overallScore >= 40 -> "Building Base"
                else -> "Low Stimulus"
            }
            TrainingMode.CUT -> when {
                overallScore >= 88 -> "Elite Retention"
                overallScore >= 74 -> "Strong Cut"
                overallScore >= 58 -> "Solid Hold"
                overallScore >= 40 -> "Managing Fatigue"
                else -> "Low Stimulus"
            }
        }
    }

    fun classifyBodyWeightPace(mode: TrainingMode, weeklyRateKg: Double): BwPaceFeedback = when (mode) {
        TrainingMode.BULK -> when {
            weeklyRateKg in 0.15..0.50 -> BwPaceFeedback(
                status = "On-pace lean bulk",
                isOnTarget = true,
                detail = "Controlled surplus (+0.15 to +0.50 kg/wk) ideal for strength and hypertrophy."
            )
            weeklyRateKg > 0.75 -> BwPaceFeedback(
                status = "Fast surplus — watch fat gain",
                isOnTarget = false,
                detail = "Gaining faster than +0.75 kg/wk; consider trimming surplus slightly."
            )
            weeklyRateKg > 0.50 -> BwPaceFeedback(
                status = "Brisk surplus",
                isOnTarget = false,
                detail = "Slightly above lean bulk pace (+0.15 to +0.50 kg/wk)."
            )
            weeklyRateKg < -0.10 -> BwPaceFeedback(
                status = "Weight dropping in Bulk mode",
                isOnTarget = false,
                detail = "Body weight is trending down; fuel recovery to support surplus overload."
            )
            else -> BwPaceFeedback(
                status = "Slow/maintenance surplus",
                isOnTarget = true,
                detail = "Near maintenance; push intake slightly if absolute E1RMs stall."
            )
        }
        TrainingMode.MAINGAIN -> when {
            weeklyRateKg in -0.20..0.20 -> BwPaceFeedback(
                status = "Steady maingain weight",
                isOnTarget = true,
                detail = "Weight holding within ±0.20 kg/wk — ideal for steady recomp and micro-gains."
            )
            weeklyRateKg > 0.20 -> BwPaceFeedback(
                status = "Slight surplus drift",
                isOnTarget = false,
                detail = "Trending above +0.20 kg/wk; tighten intake or switch to Bulk mode."
            )
            else -> BwPaceFeedback(
                status = "Slight deficit drift",
                isOnTarget = false,
                detail = "Trending below -0.20 kg/wk; add recovery calories or switch to Cut mode."
            )
        }
        TrainingMode.CUT -> when {
            weeklyRateKg in -0.85..-0.25 -> BwPaceFeedback(
                status = "On-pace sustainable cut",
                isOnTarget = true,
                detail = "Controlled deficit (-0.25 to -0.85 kg/wk) preserves lean mass and relative strength."
            )
            weeklyRateKg < -1.10 -> BwPaceFeedback(
                status = "Aggressive cut pace — watch strength & recovery",
                isOnTarget = false,
                detail = "Losing faster than -1.10 kg/wk increases fatigue and lean-tissue risk."
            )
            weeklyRateKg < -0.85 -> BwPaceFeedback(
                status = "Brisk cut pace",
                isOnTarget = false,
                detail = "Slightly faster than -0.85 kg/wk; keep top sets heavy and fatigue low."
            )
            weeklyRateKg > 0.30 -> BwPaceFeedback(
                status = "Weight trending up in Cut mode",
                isOnTarget = false,
                detail = "Scale trend is climbing above +0.30 kg/wk in Cut mode."
            )
            else -> BwPaceFeedback(
                status = "Gradual / maintenance cut",
                isOnTarget = true,
                detail = "Gentle deficit or diet break — great for locking in relative strength."
            )
        }
    }

    fun isExpectedCutDip(
        mode: TrainingMode,
        currentVal: Double,
        prevVal: Double,
        currentRel: Double = 0.0,
        prevRel: Double = 0.0
    ): Boolean {
        if (currentVal >= prevVal || prevVal <= 0.0) return false
        val relHeldOrUp = currentRel > 0.0 && prevRel > 0.0 && when (mode) {
            TrainingMode.CUT -> currentRel >= prevRel - 0.005
            TrainingMode.MAINGAIN -> currentRel >= prevRel
            TrainingMode.BULK -> false
        }
        return when (mode) {
            TrainingMode.CUT -> (currentVal / prevVal >= CUT_RETENTION_FLOOR_RATIO) || relHeldOrUp
            TrainingMode.MAINGAIN -> (currentVal / prevVal >= 0.985) || relHeldOrUp
            TrainingMode.BULK -> false
        }
    }
}
