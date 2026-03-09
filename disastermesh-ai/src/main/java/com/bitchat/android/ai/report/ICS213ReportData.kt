package com.bitchat.android.ai.report

/**
 * Root data transfer object for an ICS-213 General Message report.
 *
 * All fields are plain Kotlin types — no Android framework dependencies —
 * so this class is independently unit-testable in the :disastermesh-ai module.
 */
data class ICS213ReportData(
    /** Tracking ID, e.g. "DM-20260308-2217". */
    val messageNumber: String,
    /** Destination, e.g. "Incident Commander". */
    val to: String = "Incident Commander",
    /** Operator's display name (no @ prefix), e.g. "Aleks". */
    val from: String,
    val subject: String = "Emergency Field Report",
    /** ISO date in UTC, e.g. "2026-03-08". */
    val date: String,
    /** HH:mm UTC, e.g. "22:17 UTC". */
    val time: String,
    val peersConnected: Int,
    /** Display name of the active mesh channel, e.g. "#mesh" or "#abc123". */
    val channel: String,
    /** Short version string, e.g. "CompositeClassifier v1.0". */
    val classifierVersion: String,
    /** Classified categories in priority order; empty categories are omitted. */
    val categories: List<ICS213Category>
) {
    /** Combined date-time string used in the report footer. */
    val dateTime: String get() = "$date  $time"

    /** Total incident count across all categories. */
    val totalMessages: Int get() = categories.sumOf { it.messages.size }
}

data class ICS213Category(
    /** Raw type key, e.g. "MEDICAL". */
    val name: String,
    /** Display emoji, e.g. "🏥". */
    val emoji: String,
    /**
     * Highest severity across all messages in this category.
     * One of "CRITICAL", "HIGH", or "ROUTINE".
     */
    val priority: String,
    val messages: List<ICS213Message>
)

data class ICS213Message(
    /** Sender nickname, e.g. "Anna". */
    val sender: String,
    /**
     * HH:mm:ss UTC of the first message, or "HH:mm:ss–HH:mm:ss" range
     * when multiple messages were consolidated into one entry.
     */
    val timestamp: String,
    /**
     * Message body, already truncated/consolidated.
     * Multiple messages from the same sender within a 5-minute window are
     * joined with " / ".
     */
    val text: String,
    /** Highest confidence percentage in this consolidated entry, 0-100. */
    val confidencePct: Int
)
