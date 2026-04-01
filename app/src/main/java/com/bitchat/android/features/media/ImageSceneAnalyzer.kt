package com.bitchat.android.features.media

import android.content.Context
import android.util.Log
import com.bitchat.android.ai.vision.SceneAnalysisResult
import com.bitchat.android.ai.vision.SceneToEmergencyMapper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "ImageSceneAnalyzer"

/**
 * Analyzes a photo file with ML Kit Image Labeling (bundled, fully offline) and
 * maps the resulting labels to an emergency category via [SceneToEmergencyMapper].
 *
 * Uses the existing [ImageUtils.loadBitmapWithExifOrientation] so EXIF rotation
 * is already corrected before the image reaches the model.
 *
 * Return contract (see [SceneAnalysisResult]):
 *  • description.isNotEmpty() + emergencyType.isNotEmpty()  → recognized category (FIRE, FLOOD…)
 *  • description.isNotEmpty() + emergencyType.isEmpty()     → labels found, no known category
 *  • description.isEmpty()                                  → ML Kit hard failure / no labels
 *
 * The caller ([PhotoReportButton]) uses these three states to decide whether to
 * populate the TextField or fall back to plain image send.
 */
object ImageSceneAnalyzer {

    /**
     * @param context  Android context (required by [InputImage]).
     * @param imagePath Absolute path to a JPEG/PNG already downscaled by [ImageUtils].
     * @return [SceneAnalysisResult] — never throws; returns empty result on failure.
     */
    suspend fun analyze(context: Context, imagePath: String): SceneAnalysisResult {
        return try {
            val bitmap = ImageUtils.loadBitmapWithExifOrientation(imagePath)
                ?: return SceneAnalysisResult("", "", 0f).also {
                    Log.w(TAG, "Could not decode bitmap from $imagePath")
                }

            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // Bundled labeler: model ships with the APK, no network required.
            // Confidence threshold 0.5 keeps only meaningful detections and
            // reduces noise from distant/ambiguous background objects.
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

            Log.d(TAG, "Labels: ${rawLabels.take(5).joinToString { "${it.first}(${it.second})" }}")
            SceneToEmergencyMapper.mapLabels(rawLabels)

        } catch (e: Exception) {
            Log.w(TAG, "Analysis failed for $imagePath", e)
            SceneAnalysisResult("", "", 0f)
        }
    }
}
