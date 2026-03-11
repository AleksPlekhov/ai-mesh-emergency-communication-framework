package com.bitchat.android.ai.classifier

import android.content.Context
import android.util.Log

private const val TAG = "MessageClassifier"

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
            val tflite = runCatching { TFLiteMessageClassifier(context) }.getOrElse { e ->
                Log.e(TAG, "TFLite init failed, falling back to keyword-only: ${e.message}")
                null
            }
            if (tflite != null) {
                Log.i(TAG, "Mode: COMPOSITE (keyword-first + TFLite fallback)")
                CompositeMessageClassifier(keyword, tflite)
            } else {
                Log.e(TAG, "Mode: KEYWORD-ONLY (TFLite failed to load)")
                keyword
            }
        } else {
            Log.e(TAG, "Mode: KEYWORD-ONLY (no TFLite model asset found)")
            keyword
        }
    }
}
