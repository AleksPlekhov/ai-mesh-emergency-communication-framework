package com.bitchat.android.ai.classifier

enum class MessagePriority {
    /** Life-safety emergency requiring immediate action (SOS, MAYDAY, mass-casualty). */
    CRITICAL,
    /** Urgent operational message needing prompt attention (evacuation, fire, flood). */
    HIGH,
    /** Standard mesh communication. */
    NORMAL,
    /** Non-urgent informational content. */
    NONE
}

data class ClassificationResult(
    val priority: MessagePriority,
    /** Confidence score in [0.0, 1.0]. */
    val confidence: Float,
    /**
     * Raw emergency category from the TFLite model
     * (e.g. "MEDICAL", "FIRE", "FLOOD"). Empty string for rule-based results.
     */
    val emergencyType: String = "",
    /** Human-readable reason for this classification (for debugging / audit). */
    val reasoning: String
)
