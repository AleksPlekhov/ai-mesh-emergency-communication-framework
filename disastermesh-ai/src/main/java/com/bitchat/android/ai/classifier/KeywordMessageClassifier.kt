package com.bitchat.android.ai.classifier

/**
 * Rule-based message priority classifier using FEMA / ICS keyword matching.
 *
 * Serves as the default fallback when no TFLite model is available.
 * Classification is deterministic, requires no model assets, and runs entirely on-device.
 *
 * Each keyword is now also mapped to the closest [EmergencyType] string so the UI
 * can display a specific badge (e.g. "🏥 MEDICAL · 95%") even without the TFLite model.
 */
class KeywordMessageClassifier : MessagePriorityClassifier {

    override fun classify(
        messageText: String,
        metadata: Map<String, String>
    ): ClassificationResult {
        val text = messageText.uppercase()

        // ── CRITICAL: imminent life-safety ─────────────────────────────────
        for ((keyword, emergencyType) in CRITICAL_KEYWORDS) {
            if (text.contains(keyword)) {
                return ClassificationResult(
                    priority      = MessagePriority.CRITICAL,
                    confidence    = 0.95f,
                    emergencyType = emergencyType,
                    reasoning     = "Keyword match: \"$keyword\""
                )
            }
        }

        // ── HIGH: urgent operational ────────────────────────────────────────
        for ((keyword, emergencyType) in HIGH_KEYWORDS) {
            if (text.contains(keyword)) {
                return ClassificationResult(
                    priority      = MessagePriority.HIGH,
                    confidence    = 0.85f,
                    emergencyType = emergencyType,
                    reasoning     = "Keyword match: \"$keyword\""
                )
            }
        }

        // ── LOW: clearly non-urgent markers ────────────────────────────────
        val lowHit = LOW_KEYWORDS.firstOrNull { text.contains(it) }
        if (lowHit != null) {
            return ClassificationResult(
                priority   = MessagePriority.LOW,
                confidence = 0.75f,
                reasoning  = "Keyword match: \"$lowHit\""
            )
        }

        return ClassificationResult(
            priority   = MessagePriority.NORMAL,
            confidence = 0.60f,
            reasoning  = "No priority keyword found"
        )
    }

    companion object {
        /**
         * Each entry is (keyword, emergencyType).
         * emergencyType matches the TFLite label strings so both classifiers
         * produce consistent badge labels.
         */
        private val CRITICAL_KEYWORDS = listOf(
            "CARDIAC ARREST"          to "MEDICAL",
            "NOT BREATHING"           to "MEDICAL",
            "UNCONSCIOUS"             to "MEDICAL",
            "MASS CASUALTY"           to "MEDICAL",
            "MCI"                     to "MEDICAL",
            "FLASH FLOOD WARNING"     to "FLOOD",
            "IMMEDIATE EVACUATION"    to "FIRE",
            "ACTIVE SHOOTER"          to "SECURITY",
            "SHELTER IN PLACE"        to "SECURITY",
            "CODE RED"                to "SECURITY",
            "BOMB"                    to "SECURITY",
            "EXPLOSION"               to "COLLAPSE",
            "COLLAPSED"               to "COLLAPSE",
            "SOS"                     to "MEDICAL",
            "MAYDAY"                  to "MEDICAL"
        )

        private val HIGH_KEYWORDS = listOf(
            "FIRE"                    to "FIRE",
            "FLOOD"                   to "FLOOD",
            "EARTHQUAKE"              to "COLLAPSE",
            "MISSING PERSON"          to "MISSING_PERSON",
            "SEARCH AND RESCUE"       to "MISSING_PERSON",
            "INJURED"                 to "MEDICAL",
            "INJURIES"                to "MEDICAL",
            "MEDICAL"                 to "MEDICAL",
            "GAS LEAK"                to "INFRASTRUCTURE",
            "HAZMAT"                  to "INFRASTRUCTURE",
            "POWER OUTAGE"            to "INFRASTRUCTURE",
            "CRITICAL INFRASTRUCTURE" to "INFRASTRUCTURE",
            "EVACUATE"                to "FIRE",
            "EVACUATION"              to "FIRE",
            "URGENT"                  to "MEDICAL",
            "NEED HELP"               to "RESOURCE_REQUEST",
            "HELP NEEDED"             to "RESOURCE_REQUEST",
            "REQUESTING ASSISTANCE"   to "RESOURCE_REQUEST",
            "EMERGENCY"               to "MEDICAL",
            "ICS-213"                 to "INFRASTRUCTURE",
            "INCIDENT COMMAND"        to "INFRASTRUCTURE"
        )

        private val LOW_KEYWORDS = listOf(
            "TEST", "TESTING", "PING", "CHECK-IN", "STATUS OK",
            "ALL CLEAR", "ROUTINE", "FYI", "INFO ONLY"
        )
    }
}
