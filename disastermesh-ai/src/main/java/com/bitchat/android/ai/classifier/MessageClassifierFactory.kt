package com.bitchat.android.ai.classifier

import android.content.Context

/**
 * Factory that builds the best available [MessagePriorityClassifier] at runtime.
 *
 * When the TFLite model asset is present a [CompositeMessageClassifier] is returned:
 *  • Keyword matching runs first — deterministic, zero-latency, high-precision.
 *  • TFLite runs second — catches natural-language emergencies the keyword list misses.
 *
 * When no model asset is found, [KeywordMessageClassifier] is used directly.
 */
object MessageClassifierFactory {

    fun create(context: Context): MessagePriorityClassifier {
        val keyword = KeywordMessageClassifier()
        return if (TFLiteMessageClassifier.isModelAvailable(context)) {
            val tflite = runCatching { TFLiteMessageClassifier(context) }.getOrNull()
            if (tflite != null) CompositeMessageClassifier(keyword, tflite) else keyword
        } else {
            keyword
        }
    }
}
