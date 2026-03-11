package com.bitchat.android.ai.classifier

/**
 * Rule-based message priority classifier using FEMA / ICS keyword matching.
 *
 * Serves as the default fallback when no TFLite model is available.
 * Classification is deterministic, requires no model assets, and runs entirely on-device.
 */
class KeywordMessageClassifier : MessagePriorityClassifier {

    override fun classify(
        messageText: String,
        metadata: Map<String, String>
    ): ClassificationResult {
        val text = messageText.uppercase()

        // --- CRITICAL: imminent life-safety ---
        val criticalHit = CRITICAL_KEYWORDS.firstOrNull { text.contains(it) }
        if (criticalHit != null) {
            return ClassificationResult(
                priority = MessagePriority.CRITICAL,
                confidence = 0.95f,
                reasoning = "Keyword match: \"$criticalHit\""
            )
        }

        // --- HIGH: urgent operational ---
        val highHit = HIGH_KEYWORDS.firstOrNull { text.contains(it) }
        if (highHit != null) {
            return ClassificationResult(
                priority = MessagePriority.HIGH,
                confidence = 0.85f,
                reasoning = "Keyword match: \"$highHit\""
            )
        }

        // --- LOW: clearly non-urgent markers ---
        val lowHit = LOW_KEYWORDS.firstOrNull { text.contains(it) }
        if (lowHit != null) {
            return ClassificationResult(
                priority = MessagePriority.LOW,
                confidence = 0.75f,
                reasoning = "Keyword match: \"$lowHit\""
            )
        }

        return ClassificationResult(
            priority = MessagePriority.NORMAL,
            confidence = 0.60f,
            reasoning = "No priority keyword found"
        )
    }

    companion object {
        private val CRITICAL_KEYWORDS = listOf(
            "SOS", "MAYDAY", "MASS CASUALTY", "MCI",
            "ACTIVE SHOOTER", "BOMB", "EXPLOSION", "COLLAPSED",
            "CARDIAC ARREST", "UNCONSCIOUS", "NOT BREATHING",
            "IMMEDIATE EVACUATION", "FLASH FLOOD WARNING",
            "SHELTER IN PLACE", "CODE RED"
        )

        private val HIGH_KEYWORDS = listOf(
            "EVACUATE", "EVACUATION", "FIRE", "FLOOD", "EARTHQUAKE",
            "INJURED", "INJURIES", "MISSING PERSON", "SEARCH AND RESCUE",
            "MEDICAL", "URGENT", "CRITICAL INFRASTRUCTURE",
            "POWER OUTAGE", "GAS LEAK", "HAZMAT",
            "ICS-213", "INCIDENT COMMAND", "REQUESTING ASSISTANCE",
            "NEED HELP", "HELP NEEDED", "EMERGENCY"
        )

        private val LOW_KEYWORDS = listOf(
            "TEST", "TESTING", "PING", "CHECK-IN", "STATUS OK",
            "ALL CLEAR", "ROUTINE", "FYI", "INFO ONLY"
        )
    }
}
