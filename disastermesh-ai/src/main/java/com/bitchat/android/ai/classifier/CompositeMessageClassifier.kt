package com.bitchat.android.ai.classifier

/**
 * Two-stage classifier that combines the precision of keyword matching with
 * the breadth of the TFLite neural classifier.
 *
 * Pipeline:
 *  1. Run [KeywordMessageClassifier] — deterministic, zero latency.
 *     If it finds a CRITICAL or HIGH keyword the result is returned immediately.
 *     This guarantees that well-known emergency phrases ("FIRE", "MAYDAY",
 *     "FLOOD", …) always surface the correct category regardless of TFLite
 *     model confidence distribution.
 *
 *  2. Fall back to [TFLiteMessageClassifier] for everything that didn't match
 *     a keyword — it can catch phrased emergencies the keyword list misses
 *     ("my leg is bleeding badly", "water is rising").
 *
 * This avoids the single-model bias where a TFLite model trained on unbalanced
 * data might score most emergency messages as MEDICAL.
 */
internal class CompositeMessageClassifier(
    private val keyword: KeywordMessageClassifier,
    private val tflite:  MessagePriorityClassifier   // TFLiteMessageClassifier at runtime
) : MessagePriorityClassifier {

    override fun classify(
        messageText: String,
        metadata: Map<String, String>
    ): ClassificationResult {
        // ── Stage 1: keyword matching ─────────────────────────────────────
        // Keyword results always carry an emergencyType and hard-coded
        // confidence (0.95 for CRITICAL, 0.85 for HIGH).  If a keyword fired
        // we trust it over the neural model — keyword precision is ~100%.
        val kw = keyword.classify(messageText, metadata)
        if (kw.priority == MessagePriority.CRITICAL || kw.priority == MessagePriority.HIGH) {
            return kw
        }

        // ── Stage 2: TFLite neural classifier ─────────────────────────────
        // Used for messages with no keyword hit; can detect emergencies
        // expressed in natural language beyond the keyword vocabulary.
        return tflite.classify(messageText, metadata)
    }

    override fun close() {
        runCatching { tflite.close() }
    }
}
