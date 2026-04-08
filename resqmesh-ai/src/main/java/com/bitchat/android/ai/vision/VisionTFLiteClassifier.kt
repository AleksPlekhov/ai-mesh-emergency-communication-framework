package com.bitchat.android.ai.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Runs the custom emergency vision TFLite model on a [Bitmap] and returns a
 * [SceneAnalysisResult] compatible with the existing vision pipeline.
 *
 * Categories: collapse, fire, flood, normal (from [LABELS_ASSET]).
 *
 * Usage:
 * ```
 * val classifier = VisionTFLiteClassifier(context)
 * val result = classifier.classify(bitmap)
 * classifier.close()
 * ```
 *
 * Always gate construction behind [isAvailable] so the app degrades gracefully
 * when the model asset is absent.
 */
class VisionTFLiteClassifier(context: Context) : Closeable {

    private val interpreter: Interpreter
    private val labelMap: Map<Int, String>

    init {
        val model = loadModelFile(context)
        interpreter = Interpreter(model)
        labelMap = loadLabelMap(context)
    }

    /**
     * Classifies the given [bitmap] and returns a [SceneAnalysisResult].
     *
     * - If the top prediction is "normal" or confidence < [CONFIDENCE_THRESHOLD],
     *   an empty result is returned so the caller can fall back to ML Kit.
     * - Otherwise a fully populated result is returned with the emergency type,
     *   a human-readable description, and the model confidence.
     *
     * This method performs bitmap scaling and pixel normalization — call it from
     * [kotlinx.coroutines.Dispatchers.Default], **not** the main thread.
     */
    fun classify(bitmap: Bitmap): SceneAnalysisResult {
        val resized = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(resized)
        if (resized !== bitmap) resized.recycle()

        val outputArray = Array(1) { FloatArray(labelMap.size) }
        interpreter.run(inputBuffer, outputArray)

        val scores = outputArray[0]
        val bestIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
        val confidence = scores[bestIdx]
        val category = labelMap[bestIdx] ?: "normal"

        Log.d(TAG, "TFLite vision: $category (${"%.2f".format(confidence)}) — all: ${
            scores.mapIndexed { i, s -> "${labelMap[i]}=${"%.2f".format(s)}" }.joinToString()
        }")

        if (category == "normal" || confidence < CONFIDENCE_THRESHOLD) {
            return SceneAnalysisResult("", "", confidence)
        }

        val emergencyType = category.uppercase()
        val suffix = CATEGORY_SUFFIX[emergencyType] ?: "Requires attention."
        val emoji = CATEGORY_EMOJI[emergencyType] ?: "⚠️"
        val description = "[📷] $emoji $emergencyType detected. $suffix"

        return SceneAnalysisResult(description, emergencyType, confidence)
    }

    override fun close() {
        interpreter.close()
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        bitmap.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 127.5f - 1.0f
            val g = (pixel shr 8 and 0xFF) / 127.5f - 1.0f
            val b = (pixel and 0xFF) / 127.5f - 1.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        buffer.rewind()
        return buffer
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_ASSET)
        val inputStream = fd.createInputStream()
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength).also {
            channel.close()
            inputStream.close()
        }
    }

    private fun loadLabelMap(context: Context): Map<Int, String> {
        val json = context.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val map = mutableMapOf<Int, String>()
        for (key in obj.keys()) {
            map[key.toInt()] = obj.getString(key)
        }
        return map
    }

    // ── Category display maps (mirrors SceneToEmergencyMapper) ──────────────

    private val CATEGORY_EMOJI = mapOf(
        "MEDICAL" to "🏥",
        "FIRE" to "🔥",
        "FLOOD" to "🌊",
        "COLLAPSE" to "🏚",
        "SECURITY" to "🚨",
        "WEATHER" to "⛈",
        "INFRASTRUCTURE" to "🔧",
        "RESOURCE_REQUEST" to "📦",
        "MISSING_PERSON" to "🔍"
    )

    private val CATEGORY_SUFFIX = mapOf(
        "MEDICAL" to "Medical assistance required.",
        "FIRE" to "Requires immediate response.",
        "FLOOD" to "Rising water detected.",
        "COLLAPSE" to "Structural damage visible.",
        "SECURITY" to "Possible security threat.",
        "WEATHER" to "Severe weather conditions.",
        "INFRASTRUCTURE" to "Infrastructure damage observed.",
        "RESOURCE_REQUEST" to "Resource assistance needed.",
        "MISSING_PERSON" to "Search and rescue may be required."
    )

    companion object {
        private const val TAG = "VisionTFLiteClassifier"
        const val MODEL_ASSET = "emergency_vision_model.tflite"
        const val LABELS_ASSET = "vision_label_map.json"
        const val IMG_SIZE = 224
        private const val CONFIDENCE_THRESHOLD = 0.55f

        /**
         * Returns `true` if the TFLite model asset exists and can be opened.
         * Use this before constructing [VisionTFLiteClassifier] to allow
         * graceful fallback to ML Kit when the model is not bundled.
         */
        fun isAvailable(context: Context): Boolean {
            return try {
                context.assets.openFd(MODEL_ASSET).close()
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
