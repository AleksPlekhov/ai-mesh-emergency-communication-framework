package com.bitchat.android.features.media

import android.content.Context
import android.util.Log
import com.bitchat.android.ai.vision.SceneAnalysisResult
import com.bitchat.android.ai.vision.SceneToEmergencyMapper
import com.bitchat.android.ai.vision.VisionTFLiteClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "ImageSceneAnalyzer"

/**
 * Analyzes a photo file and maps the resulting labels to an emergency category.
 *
 * Strategy:
 *  1. **TFLite** — custom emergency vision model ([VisionTFLiteClassifier]).
 *     If the model asset is present and returns a confident emergency prediction
 *     (confidence >= 0.55, non-"normal"), that result is used immediately.
 *  2. **ML Kit** (fallback) — bundled Image Labeling, fully offline.
 *     Used when TFLite is unavailable, returns "normal", or is below threshold.
 *
 * Uses [ImageUtils.loadBitmapWithExifOrientation] so EXIF rotation is already
 * corrected before the image reaches either model.
 */
object ImageSceneAnalyzer {

    /**
     * @param context  Android context (required by models and [InputImage]).
     * @param imagePath Absolute path to a JPEG/PNG already downscaled by [ImageUtils].
     * @return [SceneAnalysisResult] — never throws; returns empty result on failure.
     */
    suspend fun analyze(context: Context, imagePath: String): SceneAnalysisResult {
        return try {
            val bitmap = ImageUtils.loadBitmapWithExifOrientation(imagePath)
                ?: return SceneAnalysisResult("", "", 0f).also {
                    Log.w(TAG, "Could not decode bitmap from $imagePath")
                }

            // ── Try TFLite custom model first ──────────────────────────────
            if (VisionTFLiteClassifier.isAvailable(context)) {
                try {
                    val classifier = VisionTFLiteClassifier(context)
                    val tfliteResult = classifier.classify(bitmap)
                    classifier.close()

                    if (tfliteResult.emergencyType.isNotEmpty() && tfliteResult.confidence >= 0.55f) {
                        Log.d(TAG, "TFLite result accepted: ${tfliteResult.emergencyType} (${tfliteResult.confidence})")
                        bitmap.recycle()
                        return tfliteResult
                    }
                    Log.d(TAG, "TFLite result not confident enough, falling back to ML Kit")
                } catch (e: Exception) {
                    Log.w(TAG, "TFLite inference failed, falling back to ML Kit", e)
                }
            }

            // ── Fall back to ML Kit Image Labeling ─────────────────────────
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.5f)
                    .build()
            )

            val rawLabels = suspendCoroutine<List<Pair<String, Float>>> { cont ->
                labeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        val pairs = labels
                            .sortedByDescending { it.confidence }
                            .map { it.text to it.confidence }
                        cont.resume(pairs)
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            }

            labeler.close()
            bitmap.recycle()

            Log.d(TAG, "ML Kit labels: ${rawLabels.joinToString { "${it.first}(${it.second})" }}")
            SceneToEmergencyMapper.mapLabels(rawLabels)

        } catch (e: Exception) {
            Log.w(TAG, "Analysis failed for $imagePath", e)
            SceneAnalysisResult("", "", 0f)
        }
    }
}
