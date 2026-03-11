package com.bitchat.android.ai.emergency

import com.bitchat.android.ai.classifier.ClassificationResult
import com.bitchat.android.ai.classifier.MessagePriority

// ── Confidence threshold ───────────────────────────────────────────────────────
//
//  For the TFLite classifier every message gets one of 9 categories.  Random
//  chance is ~11 %.  We show a badge when the model is at least 2.7× more
//  confident than chance (30 %), catching real emergencies while still
//  filtering out clearly random noise.
//
//  CRITICAL / HIGH priority always bypass this check (see shouldShowEmergencyBadge)
//  so well-known categories (MEDICAL, FIRE, FLOOD, SECURITY) show regardless.
//
//  For the keyword classifier the confidence is hardcoded (0.95 / 0.85) and the
//  emergencyType is always set, so they always pass.
//
const val EMERGENCY_CONFIDENCE_THRESHOLD = 0.25f

/**
 * Returns true when this result should render a colored border + badge.
 *
 *  • TFLite path  — specific emergencyType + confidence ≥ threshold
 *  • Keyword path — CRITICAL or HIGH priority (keyword was matched)
 *
 * This is the canonical predicate for emergency visibility — use it everywhere
 * (message filter, badge count, Feed sheet) to ensure consistency.
 */
fun shouldShowEmergencyBadge(classification: ClassificationResult): Boolean = when {
    classification.emergencyType.isNotEmpty() && classification.confidence >= EMERGENCY_CONFIDENCE_THRESHOLD -> true
    classification.priority == MessagePriority.CRITICAL -> true
    classification.priority == MessagePriority.HIGH     -> true
    else                                                -> false
}

/**
 * Returns true when the classifier has a tentative emergency type but confidence is
 * below the confirmed threshold.
 *
 * These are displayed as "❓ POSSIBLE [TYPE] · X%" with a muted dimmed border,
 * indicating a possible detection that did not pass the confidence gate.
 *
 * Mutually exclusive with [shouldShowEmergencyBadge] — only one can be true.
 */
fun shouldShowPossibleBadge(classification: ClassificationResult): Boolean =
    classification.emergencyType.isNotEmpty() && !shouldShowEmergencyBadge(classification)

/**
 * Maps an emergency type string to its display emoji and short label.
 *
 * Used by UI layers that know the category name but not the full [ClassificationResult].
 * Returns a fallback `"⚠️" to emergencyType` for unrecognised types.
 */
fun categoryEmojiAndLabel(emergencyType: String): Pair<String, String> = when (emergencyType) {
    "MEDICAL"          -> "🏥" to "MEDICAL"
    "FIRE"             -> "🔥" to "FIRE"
    "FLOOD"            -> "🌊" to "FLOOD"
    "COLLAPSE"         -> "🏚" to "COLLAPSE"
    "SECURITY"         -> "🚨" to "SECURITY"
    "WEATHER"          -> "⛈" to "WEATHER"
    "MISSING_PERSON"   -> "🔍" to "MISSING PERSON"
    "INFRASTRUCTURE"   -> "🔧" to "INFRASTRUCTURE"
    "RESOURCE_REQUEST" -> "📦" to "RESOURCES"
    else               -> "⚠️" to emergencyType
}
