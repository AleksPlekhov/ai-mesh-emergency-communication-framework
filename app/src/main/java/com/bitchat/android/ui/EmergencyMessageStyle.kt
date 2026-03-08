package com.bitchat.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.ColorScheme

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
private const val CONFIDENCE_THRESHOLD = 0.30f

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
 * All colors are derived from [colorScheme] where a semantic token exists, and from
 * explicit light/dark-aware values where Material3 has no suitable token.
 *
 *  MEDICAL / CRITICAL  → colorScheme.error   (red in both themes)
 *  RESOURCE_REQUEST    → colorScheme.primary  (brand green matches "resources available")
 *  FIRE / FLOOD / …    → explicit bright (dark) or muted (light) pair
 */
fun emergencyBorderColor(
    classification: ClassificationResult,
    colorScheme: ColorScheme,
    isDarkTheme: Boolean
): Color? {
    if (!shouldShowEmergencyBadge(classification)) return null
    return when (classification.emergencyType) {
        "MEDICAL"          -> colorScheme.error
        "FIRE"             -> if (isDarkTheme) Color(0xFFFF6B35) else Color(0xFFBF360C)
        "COLLAPSE"         -> if (isDarkTheme) Color(0xFFEF5350) else Color(0xFFB71C1C)
        "FLOOD"            -> if (isDarkTheme) Color(0xFF42A5F5) else Color(0xFF0D47A1)
        "SECURITY"         -> if (isDarkTheme) Color(0xFFCE93D8) else Color(0xFF6A1B9A)
        "WEATHER"          -> if (isDarkTheme) Color(0xFF4DB6AC) else Color(0xFF00695C)
        "MISSING_PERSON"   -> if (isDarkTheme) Color(0xFFFFD54F) else Color(0xFFF57F17)
        "INFRASTRUCTURE"   -> if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFE65100)
        "RESOURCE_REQUEST" -> colorScheme.primary
        else -> when (classification.priority) {
            MessagePriority.CRITICAL -> colorScheme.error
            MessagePriority.HIGH     -> if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100)
            else                     -> null
        }
    }
}

/**
 * Small one-line badge that appears below every classified message text.
 *
 * • Emergency  → colored emoji badge  e.g.  🏥 MEDICAL · 97%
 * • No match   → dim "UNDEFINED" in muted onSurface color
 *
 * Nothing is rendered if [classification] is null (message not yet processed).
 */
@Composable
fun EmergencyBadge(classification: ClassificationResult) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark      = isSystemInDarkTheme()
    val color       = emergencyBorderColor(classification, colorScheme, isDark)

    if (color != null) {
        // ── Emergency / warning badge ─────────────────────────────────────
        val (emoji, label) = emojiAndLabel(classification)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
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
}

/**
 * Returns the stripe/badge color for a known emergency type string.
 * Mirrors the logic in [emergencyBorderColor] but works with a raw type string.
 * Used by [EmergencyFeedSheet] which groups results by type.
 */
fun categoryBorderColor(
    emergencyType: String,
    colorScheme: ColorScheme,
    isDarkTheme: Boolean
): Color = when (emergencyType) {
    "MEDICAL"          -> colorScheme.error
    "FIRE"             -> if (isDarkTheme) Color(0xFFFF6B35) else Color(0xFFBF360C)
    "COLLAPSE"         -> if (isDarkTheme) Color(0xFFEF5350) else Color(0xFFB71C1C)
    "FLOOD"            -> if (isDarkTheme) Color(0xFF42A5F5) else Color(0xFF0D47A1)
    "SECURITY"         -> if (isDarkTheme) Color(0xFFCE93D8) else Color(0xFF6A1B9A)
    "WEATHER"          -> if (isDarkTheme) Color(0xFF4DB6AC) else Color(0xFF00695C)
    "MISSING_PERSON"   -> if (isDarkTheme) Color(0xFFFFD54F) else Color(0xFFF57F17)
    "INFRASTRUCTURE"   -> if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFE65100)
    "RESOURCE_REQUEST" -> colorScheme.primary
    else               -> colorScheme.error
}

/**
 * Maps an emergency type string to its display emoji and short label.
 * Used by [EmergencyFeedSheet] and other views that know the type but not the full result.
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
