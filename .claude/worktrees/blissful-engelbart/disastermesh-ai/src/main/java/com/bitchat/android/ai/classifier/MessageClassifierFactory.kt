package com.bitchat.android.ai.classifier

import android.content.Context

/**
 * Factory that selects the best available [MessagePriorityClassifier] at runtime.
 *
 * Priority order:
 * 1. [TFLiteMessageClassifier] — when `message_classifier.tflite` is present in assets.
 * 2. [KeywordMessageClassifier] — rule-based fallback (always available, no assets needed).
 */
object MessageClassifierFactory {

    fun create(context: Context): MessagePriorityClassifier {
        return if (TFLiteMessageClassifier.isModelAvailable(context)) {
            runCatching { TFLiteMessageClassifier(context) }
                .getOrElse { KeywordMessageClassifier() }
        } else {
            KeywordMessageClassifier()
        }
    }
}
