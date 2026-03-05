package com.bitchat.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.ai.classifier.ClassificationResult
import com.bitchat.android.ai.classifier.MessagePriority

// ── Confidence threshold ───────────────────────────────────────────────────────
//
//  For the TFLite classifier every message gets one of 9 categories.  Random
//  chance is ~11 %.  We show a badge when the model is at least 4× more
//  confident than chance (45 %), which is meaningful signal while still letting
//  less dominant categories (FIRE, FLOOD, etc.) surface.
//
//  For the keyword classifier the confidence is hardcoded (0.95 / 0.85) and the
//  emergencyType is always set, so they always pass.
//
private const val CONFIDENCE_THRESHOLD = 0.45f

/**
 * Returns true when this result should render a colored border + badge.
 *
 *  • TFLite path  — specific emergencyType + confidence ≥ threshold
 *  • Keyword path — CRITICAL or HIGH priority (keyword was matched)
 */
fun shouldShowEmergencyBadge(classification: ClassificationResult): Boolean = when {
    classification.emergencyType.isNotEmpty() && classification.confidence >= CONFIDENCE_THRESHOLD -> true
    classification.priority == MessagePriority.CRITICAL -> true
    classification.priority == MessagePriority.HIGH     -> true
    else                                                -> false
}

/**
 * Left-border color for a classified emergency message, or `null` for plain messages.
 *
 * Color palette is designed to be recognisable at a glance on both light and dark themes:
 *  • Warm reds / oranges for life-safety  (MEDICAL, FIRE, COLLAPSE)
 *  • Cool blues / purples for hazards     (FLOOD, SECURITY, WEATHER)
 *  • Earthy tones for logistics           (INFRASTRUCTURE, RESOURCES, MISSING)
 */
fun emergencyBorderColor(classification: ClassificationResult): Color? {
    if (!shouldShowEmergencyBadge(classification)) return null
    return when (classification.emergencyType) {
        "MEDICAL"          -> Color(0xFFFF3B30)   // iOS red
        "FIRE"             -> Color(0xFFFF6B35)   // deep orange
        "COLLAPSE"         -> Color(0xFFB71C1C)   // dark red
        "FLOOD"            -> Color(0xFF007AFF)   // iOS blue
        "SECURITY"         -> Color(0xFF9B59B6)   // purple
        "WEATHER"          -> Color(0xFF00BFA5)   // teal
        "MISSING_PERSON"   -> Color(0xFFFFB300)   // amber
        "INFRASTRUCTURE"   -> Color(0xFFE67E22)   // orange-brown
        "RESOURCE_REQUEST" -> Color(0xFF43A047)   // green
        else -> when (classification.priority) {
            MessagePriority.CRITICAL -> Color(0xFFFF3B30)
            MessagePriority.HIGH     -> Color(0xFFFF9500)
            else                     -> null
        }
    }
}

/**
 * Small one-line badge that appears below the message text for classified emergencies.
 *
 * Example render:  🏥 MEDICAL · 97%
 *
 * The badge colour mirrors the border so the two elements feel coupled.
 * Nothing is rendered if the classification doesn't meet the threshold.
 */
@Composable
fun EmergencyBadge(classification: ClassificationResult) {
    val color = emergencyBorderColor(classification) ?: return   // early-out for plain messages
    val (emoji, label) = emojiAndLabel(classification)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text = "$emoji $label · ${(classification.confidence * 100).toInt()}%",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/** Maps a [ClassificationResult] to its display emoji and short label. */
private fun emojiAndLabel(classification: ClassificationResult): Pair<String, String> =
    when (classification.emergencyType) {
        "MEDICAL"          -> "🏥" to "MEDICAL"
        "FIRE"             -> "🔥" to "FIRE"
        "FLOOD"            -> "🌊" to "FLOOD"
        "COLLAPSE"         -> "🏚" to "COLLAPSE"
        "SECURITY"         -> "🚨" to "SECURITY"
        "WEATHER"          -> "⛈" to "WEATHER"
        "MISSING_PERSON"   -> "🔍" to "MISSING PERSON"
        "INFRASTRUCTURE"   -> "🔧" to "INFRASTRUCTURE"
        "RESOURCE_REQUEST" -> "📦" to "RESOURCES"
        else -> when (classification.priority) {
            MessagePriority.CRITICAL -> "⚠️" to "CRITICAL"
            MessagePriority.HIGH     -> "🚨" to "EMERGENCY"
            else                     -> "ℹ️" to "NOTICE"
        }
    }
