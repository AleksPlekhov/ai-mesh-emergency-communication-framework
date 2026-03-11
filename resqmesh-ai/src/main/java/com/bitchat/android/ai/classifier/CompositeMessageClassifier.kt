package com.bitchat.android.ai.classifier

import android.util.Log
import com.bitchat.android.ai.emergency.EMERGENCY_CONFIDENCE_THRESHOLD

private const val TAG = "MessageClassifier"

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
        // ── Pre-filter: too short to carry emergency information ──────────
        // Avoids false positives on ".", ",", "h", "hey", etc.
        // Note: standalone KeywordMessageClassifier (used for priority-queue
        // urgency in sendMessage) is NOT gated here, so "SOS" still gets
        // CRITICAL priority even as a single word.
        val wordCount = messageText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        if (wordCount < 3) {
            return ClassificationResult(
                priority  = MessagePriority.NORMAL,
                confidence = 0f,
                reasoning = "Too short ($wordCount word(s)) — skipped"
            )
        }

        // ── Stage 1: keyword matching ─────────────────────────────────────
        // Keyword results always carry an emergencyType and hard-coded
        // confidence (0.95 for CRITICAL, 0.85 for HIGH).  If a keyword fired
        // we trust it over the neural model — keyword precision is ~100%.
        val kw = keyword.classify(messageText, metadata)
        if (kw.priority == MessagePriority.CRITICAL || kw.priority == MessagePriority.HIGH) {
            Log.d(TAG, "[KEYWORD] \"${messageText.take(60)}\" → " +
                    "${kw.priority} / ${kw.emergencyType} / conf=${kw.confidence} / ${kw.reasoning}")
            return kw
        }

        // ── Stage 2: TFLite neural classifier ─────────────────────────────
        // Used for messages with no keyword hit; can detect emergencies
        // expressed in natural language beyond the keyword vocabulary.
        val tf = tflite.classify(messageText, metadata)
        Log.d(TAG, "[TFLITE ] \"${messageText.take(60)}\" → " +
                "${tf.priority} / ${tf.emergencyType} / conf=${
                    String.format("%.0f", tf.confidence * 100)}% / ${tf.reasoning}")

        // ── Confidence gate: prevent TFLite priority from bypassing the badge threshold ──
        // mapToPriority() assigns CRITICAL to "MEDICAL"/"COLLAPSE" and HIGH to
        // "FIRE"/"FLOOD"/"SECURITY" regardless of confidence score.  Without this cap,
        // a 24%-confidence MEDICAL prediction would have priority=CRITICAL and bypass the
        // EMERGENCY_CONFIDENCE_THRESHOLD check in shouldShowEmergencyBadge(), showing a
        // badge that should be hidden.
        //
        // Keyword results (stage 1) intentionally keep their CRITICAL/HIGH priority because
        // they only fire when a known emergency keyword is present (hardcoded conf 0.85–0.95f).
        val safeTf = if (tf.confidence < EMERGENCY_CONFIDENCE_THRESHOLD
                        && tf.priority.ordinal < MessagePriority.NORMAL.ordinal) {
            tf.copy(priority = MessagePriority.NORMAL)
        } else tf
        return safeTf
    }

    override fun close() {
        runCatching { tflite.close() }
    }
}
