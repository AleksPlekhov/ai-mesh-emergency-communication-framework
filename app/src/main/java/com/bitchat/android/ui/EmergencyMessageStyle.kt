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
import com.bitchat.android.ai.emergency.categoryEmojiAndLabel
import com.bitchat.android.ai.emergency.shouldShowEmergencyBadge
import com.bitchat.android.ai.emergency.shouldShowPossibleBadge

/**
 * Left-border color for a classified emergency message, or `null` for plain messages.
 *
 *  • Confirmed emergency  → full category color (opaque)
 *  • Possible / uncertain → same category color at 40% alpha (dim stripe)
 *  • No badge             → null (caller falls back to muted grey)
 *
 * All colors are derived from [colorScheme] where a semantic token exists, and from
 * explicit light/dark-aware values where Material3 has no suitable token.
 */
fun emergencyBorderColor(
    classification: ClassificationResult,
    colorScheme: ColorScheme,
    isDarkTheme: Boolean
): Color? {
    val isConfirmed = shouldShowEmergencyBadge(classification)
    val isPossible  = !isConfirmed && shouldShowPossibleBadge(classification)
    if (!isConfirmed && !isPossible) return null

    val base: Color = when (classification.emergencyType) {
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
            else                     -> return null
        }
    }
    return if (isPossible) base.copy(alpha = 0.4f) else base
}

/**
 * Small one-line badge that appears below every classified message text.
 *
 * • Confirmed emergency  → colored emoji badge  e.g.  "🏥 MEDICAL · 97%"
 * • Uncertain detection  → grey badge           e.g.  "❓ POSSIBLE FLOOD · 24%"
 * • No match             → nothing rendered
 */
@Composable
fun EmergencyBadge(classification: ClassificationResult) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark      = isSystemInDarkTheme()
    val pct         = (classification.confidence * 100).toInt()

    when {
        shouldShowEmergencyBadge(classification) -> {
            // ── Confirmed emergency ───────────────────────────────────────
            val color = emergencyBorderColor(classification, colorScheme, isDark) ?: return
            val (emoji, label) = emojiAndLabel(classification)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            ) {
                Text(
                    text = "$emoji $label · $pct%",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
        }
        shouldShowPossibleBadge(classification) -> {
            // ── Uncertain detection ───────────────────────────────────────
            val muted = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
            val (_, label) = emojiAndLabel(classification)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            ) {
                Text(
                    text = "❓ POSSIBLE $label · $pct%",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = muted
                )
            }
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
