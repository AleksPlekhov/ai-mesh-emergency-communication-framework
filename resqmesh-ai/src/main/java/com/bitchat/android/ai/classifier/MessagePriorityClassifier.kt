package com.bitchat.android.ai.classifier

/**
 * Common interface for all message priority classification backends.
 *
 * Implementations must be thread-safe; [classify] may be called from any thread.
 * Call [close] when the classifier is no longer needed to release native resources.
 */
interface MessagePriorityClassifier {

    /**
     * Classify a single message text and return a [ClassificationResult].
     *
     * @param messageText Raw message content.
     * @param metadata    Optional key-value context (e.g. "sender_role" → "incident_commander").
     */
    fun classify(
        messageText: String,
        metadata: Map<String, String> = emptyMap()
    ): ClassificationResult

    /** Release any held resources (models, native handles, etc.). */
    fun close() {}
}
