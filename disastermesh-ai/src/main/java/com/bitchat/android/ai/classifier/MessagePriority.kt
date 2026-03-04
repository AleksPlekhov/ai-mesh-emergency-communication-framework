package com.bitchat.android.ai.classifier

enum class MessagePriority {
    /** Life-safety emergency requiring immediate action (SOS, MAYDAY, mass-casualty). */
    CRITICAL,
    /** Urgent operational message needing prompt attention (evacuation, fire, flood). */
    HIGH,
    /** Standard mesh communication. */
    NORMAL,
    /** Non-urgent informational content. */
    LOW
}

data class ClassificationResult(
    val priority: MessagePriority,
    /** Confidence score in [0.0, 1.0]. */
    val confidence: Float,
    /** Human-readable reason for this classification (for debugging / audit). */
    val reasoning: String
)
