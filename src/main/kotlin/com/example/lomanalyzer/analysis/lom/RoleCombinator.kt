package com.example.lomanalyzer.analysis.lom

/**
 * 8-cell matrix from v6 §18.3.
 *
 * | Session \ Reference    | HIGH_ABS_BASE              | LOW_ABS_BASE           |
 * |------------------------|----------------------------|------------------------|
 * | AUTHORITATIVE_LOM      | AUTHORITATIVE_LOM_CONFIRMED | AUTHORITATIVE_LOM_LOCAL |
 * | SLEEPING_GIANT         | SLEEPING_GIANT_CONFIRMED    | SLEEPING_GIANT_LOCAL    |
 * | TOPIC_DRIVER           | TOPIC_DRIVER_WITH_BASE      | TOPIC_DRIVER            |
 * | BACKGROUND             | BACKGROUND_LARGE            | BACKGROUND              |
 */
enum class CombinedRole {
    AUTHORITATIVE_LOM_CONFIRMED,
    AUTHORITATIVE_LOM_LOCAL,
    SLEEPING_GIANT_CONFIRMED,
    SLEEPING_GIANT_LOCAL,
    TOPIC_DRIVER_WITH_BASE,
    TOPIC_DRIVER,
    BACKGROUND_LARGE,
    BACKGROUND,
    BASELINE_UNKNOWN,
}

enum class ReferenceQuadrant {
    HIGH_ABS_BASE,
    LOW_ABS_BASE,
}

object RoleCombinator {
    fun combine(
        sessionRole: SessionRole,
        referenceQuadrant: ReferenceQuadrant,
        isBaselineUnknown: Boolean = false,
    ): CombinedRole {
        if (isBaselineUnknown) return CombinedRole.BASELINE_UNKNOWN

        return when (sessionRole) {
            SessionRole.AUTHORITATIVE_LOM -> when (referenceQuadrant) {
                ReferenceQuadrant.HIGH_ABS_BASE -> CombinedRole.AUTHORITATIVE_LOM_CONFIRMED
                ReferenceQuadrant.LOW_ABS_BASE -> CombinedRole.AUTHORITATIVE_LOM_LOCAL
            }
            SessionRole.SLEEPING_GIANT -> when (referenceQuadrant) {
                ReferenceQuadrant.HIGH_ABS_BASE -> CombinedRole.SLEEPING_GIANT_CONFIRMED
                ReferenceQuadrant.LOW_ABS_BASE -> CombinedRole.SLEEPING_GIANT_LOCAL
            }
            SessionRole.TOPIC_DRIVER -> when (referenceQuadrant) {
                ReferenceQuadrant.HIGH_ABS_BASE -> CombinedRole.TOPIC_DRIVER_WITH_BASE
                ReferenceQuadrant.LOW_ABS_BASE -> CombinedRole.TOPIC_DRIVER
            }
            SessionRole.BACKGROUND -> when (referenceQuadrant) {
                ReferenceQuadrant.HIGH_ABS_BASE -> CombinedRole.BACKGROUND_LARGE
                ReferenceQuadrant.LOW_ABS_BASE -> CombinedRole.BACKGROUND
            }
        }
    }

    fun classifyReference(iBaseAbs: Double, tauRefBase: Double): ReferenceQuadrant =
        if (iBaseAbs >= tauRefBase) ReferenceQuadrant.HIGH_ABS_BASE
        else ReferenceQuadrant.LOW_ABS_BASE

    fun deriveFlag(combined: CombinedRole): String = combined.name
}
