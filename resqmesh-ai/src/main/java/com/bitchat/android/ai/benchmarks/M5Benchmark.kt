/**
 * ResQMesh AI — M5 Vision Benchmark (v2 — fixed)
 *
 * Fixes vs v1:
 *   - Runs on IO coroutine (no ANR risk)
 *   - Full pipeline latency: JPEG decode → resize → preprocess → inference
 *   - Works on Android 10–16 (uses app-specific external storage)
 *   - LABELS order comment — must verify against your model
 *
 * Measures:
 *   1. Full pipeline latency per device (200 runs)
 *   2. Per-photo accuracy: predicted vs true label
 *   3. Bandwidth reduction: photo file size ÷ text report size
 *
 * Setup:
 *   OPTION A — adb push (easiest):
 *     adb push ./test_photos/ /sdcard/Android/data/com.bitchat.droid/files/resqmesh_test/
 *
 *   OPTION B — manually copy via Files app on device to:
 *     Android/data/com.bitchat.droid/files/resqmesh_test/
 *
 *   Photo naming (prefix = true category):
 *     fire_01.jpg     fire_02.jpg     fire_03.jpg
 *     flood_01.jpg    flood_02.jpg    flood_03.jpg
 *     weather_01.jpg  weather_02.jpg  weather_03.jpg
 *     security_01.jpg security_02.jpg security_03.jpg
 *     normal_01.jpg   normal_02.jpg   normal_03.jpg
 *
 *   Call from onCreate():
 *     lifecycleScope.launch { runM5Benchmark(this@MainActivity) }
 *
 *   Logcat filter: M5_BENCH
 *
 * ⚠️  IMPORTANT: Verify LABELS order matches your model's output indices!
 *     Check VisionTFLiteClassifier — the order used during training.
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val TAG = "M5_BENCH"

// ── CONFIG ────────────────────────────────────────────────────────────────────
private const val MODEL_FILE_NAME   = "emergency_vision_model.tflite"
private const val INPUT_SIZE        = 224
private const val NUM_CLASSES       = 5
private const val NUM_LATENCY_RUNS  = 200
private const val WARMUP_RUNS       = 10

/**
 * ⚠️ Order verified against emergency_vision_model label map:
 * {"0":"fire","1":"flood","2":"normal","3":"security","4":"weather"}
 */
private val LABELS = listOf("fire", "flood", "normal", "security", "weather")

// ── Model loading ─────────────────────────────────────────────────────────────
private fun loadModel(context: Context): ByteBuffer {
    val afd = context.assets.openFd(MODEL_FILE_NAME)
    return FileInputStream(afd.fileDescriptor).channel.map(
        FileChannel.MapMode.READ_ONLY,
        afd.startOffset,
        afd.declaredLength
    )
}

// ── Full pipeline: decode JPEG file → resize → normalize → ByteBuffer ─────────
// This is the realistic path — same as production ImageSceneAnalyzer
private fun decodeAndPreprocess(filePath: String): ByteBuffer? {
    val raw = BitmapFactory.decodeFile(filePath) ?: return null
    val scaled = Bitmap.createScaledBitmap(raw, INPUT_SIZE, INPUT_SIZE, true)
    if (scaled !== raw) raw.recycle()

    val buf = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
    buf.order(ByteOrder.nativeOrder())
    val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
    scaled.recycle()

    for (px in pixels) {
        buf.putFloat(((px shr 16 and 0xFF) - 127.5f) / 127.5f)  // R
        buf.putFloat(((px shr  8 and 0xFF) - 127.5f) / 127.5f)  // G
        buf.putFloat(((px        and 0xFF) - 127.5f) / 127.5f)  // B
    }
    buf.rewind()
    return buf
}

// ── Inference only (for latency benchmark after preprocess) ───────────────────
private fun runInference(interpreter: Interpreter, input: ByteBuffer): Pair<Int, Float> {
    input.rewind()
    val output = Array(1) { FloatArray(NUM_CLASSES) }
    interpreter.run(input, output)
    val probs  = output[0]
    val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
    return Pair(maxIdx, probs[maxIdx])
}

// ── Text report size (mirrors SceneToEmergencyMapper) ────────────────────────
private fun textReportBytes(label: String, conf: Float): Int {
    val text = when (label) {
        "fire"     -> "[📷] 🔥 FIRE: Fire detected on scene. Requires immediate response."
        "flood"    -> "[📷] 🌊 FLOOD: Flooding detected. Water levels rising."
        "weather"  -> "[📷] ⛈ WEATHER: Severe weather conditions observed."
        "security" -> "[📷] 🚨 SECURITY: Security threat detected on scene."
        else       -> "[📷] Scene: No emergency detected. (conf=${conf.times(100).toInt()}%)"
    }
    return text.toByteArray(Charsets.UTF_8).size
}

// ── MAIN BENCHMARK ────────────────────────────────────────────────────────────
suspend fun runM5Benchmark(context: Context) = withContext(Dispatchers.IO) {

    Log.i(TAG, "=== ResQMesh M5 Vision Benchmark v2 ===")
    Log.i(TAG, "Device:  ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    Log.i(TAG, "Android: ${android.os.Build.VERSION.RELEASE}")
    Log.i(TAG, "Model:   $MODEL_FILE_NAME  |  Input: ${INPUT_SIZE}×${INPUT_SIZE}  |  Classes: $NUM_CLASSES")

    val interpreter = Interpreter(loadModel(context), Interpreter.Options().apply {
        setNumThreads(4)
    })

    // App-specific external storage — no permissions needed on any Android version,
    // immune to scoped storage restrictions (Android 10+).
    // Push photos via: adb push ./test_photos/ /sdcard/Android/data/<package>/files/resqmesh_test/
    val photoDir = File(context.getExternalFilesDir(null), "resqmesh_test")
    Log.i(TAG, "Photo dir: ${photoDir.absolutePath}")

    // ── PART 1: FULL PIPELINE LATENCY ─────────────────────────────────────────
    Log.i(TAG, "\n--- PART 1: Full Pipeline Latency (JPEG decode + resize + preprocess + inference) ---")

    // Find any photo for latency test, fallback to synthetic
    val anyPhoto = photoDir.listFiles()?.firstOrNull { it.extension.lowercase() in listOf("jpg","jpeg","png") }

    if (anyPhoto == null) {
        Log.w(TAG, "No photos in ${photoDir.absolutePath} — latency uses synthetic 224×224 bitmap")
        Log.w(TAG, "Push photos: adb push ./test_photos/ ${photoDir.absolutePath}/")
    }

    val latencies = LongArray(NUM_LATENCY_RUNS)

    // Warmup
    repeat(WARMUP_RUNS) {
        val buf = if (anyPhoto != null) decodeAndPreprocess(anyPhoto.absolutePath)
        else ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
        if (buf != null) runInference(interpreter, buf)
    }

    // Timed runs — full pipeline per run
    for (i in 0 until NUM_LATENCY_RUNS) {
        val t0 = System.nanoTime()
        val buf = if (anyPhoto != null) decodeAndPreprocess(anyPhoto.absolutePath)
        else ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).also { it.rewind() }
        if (buf != null) runInference(interpreter, buf)
        latencies[i] = System.nanoTime() - t0
    }

    val latMs = latencies.map { it / 1_000_000.0 }.sorted()
    Log.i(TAG, "\n=== LATENCY RESULTS (N=$NUM_LATENCY_RUNS, full pipeline) ===")
    Log.i(TAG, "Mean: ${"%.1f".format(latMs.average())} ms")
    Log.i(TAG, "P50:  ${"%.1f".format(latMs[latMs.size / 2])} ms")
    Log.i(TAG, "P95:  ${"%.1f".format(latMs[(latMs.size * 0.95).toInt()])} ms")
    Log.i(TAG, "P99:  ${"%.1f".format(latMs[(latMs.size * 0.99).toInt()])} ms")
    Log.i(TAG, "Min:  ${"%.1f".format(latMs.first())} ms")
    Log.i(TAG, "Max:  ${"%.1f".format(latMs.last())} ms")

    val meanMs = latMs.average()
    val p95Ms  = latMs[(latMs.size * 0.95).toInt()]

    // ── PART 2: ACCURACY + BANDWIDTH ──────────────────────────────────────────
    Log.i(TAG, "\n--- PART 2: Accuracy + Bandwidth Reduction ---")

    if (!photoDir.exists() || photoDir.listFiles().isNullOrEmpty()) {
        Log.w(TAG, "Photo dir empty or missing: ${photoDir.absolutePath}")
        Log.w(TAG, "Skipping accuracy + bandwidth test.")
        interpreter.close()
        return@withContext
    }

    val categories = LABELS.filter { it != "normal" } + listOf("normal")
    var totalCorrect = 0
    var totalPhotos  = 0
    val bandwidthRatios = mutableListOf<Double>()

    // tp/fp/fn per category
    data class Stats(var tp: Int = 0, var fp: Int = 0, var fn: Int = 0)
    val stats = categories.associateWith { Stats() }.toMutableMap()

    Log.i(TAG, "\n${"File".padEnd(30)} ${"True".padEnd(10)} ${"Pred".padEnd(12)} ${"Conf%".padEnd(7)} ${"Photo KB".padEnd(10)} ${"Text B".padEnd(8)}")
    Log.i(TAG, "-".repeat(80))

    for (trueLabel in categories) {
        val photos = photoDir.listFiles { f ->
            f.name.lowercase().startsWith(trueLabel) &&
                    f.extension.lowercase() in listOf("jpg", "jpeg", "png")
        }?.sortedBy { it.name } ?: emptyList()

        if (photos.isEmpty()) {
            Log.w(TAG, "No photos found for category: $trueLabel")
            continue
        }

        for (photo in photos) {
            val buf = decodeAndPreprocess(photo.absolutePath)
            if (buf == null) {
                Log.w(TAG, "  Cannot decode: ${photo.name}")
                continue
            }

            val (predIdx, conf) = runInference(interpreter, buf)
            val predLabel = LABELS.getOrElse(predIdx) { "unknown" }
            val correct   = predLabel == trueLabel

            if (correct) totalCorrect++ else {
                stats[trueLabel]!!.fn++
                stats[predLabel]?.fp = (stats[predLabel]?.fp ?: 0) + 1
            }
            stats[trueLabel]!!.tp += if (correct) 1 else 0
            totalPhotos++

            // Bandwidth
            val photoKB   = photo.length() / 1024.0
            val textBytes = textReportBytes(predLabel, conf)
            val ratio     = photo.length().toDouble() / textBytes
            bandwidthRatios.add(ratio)

            val mark = if (correct) "✓" else "✗"
            Log.i(TAG, "${photo.name.take(29).padEnd(30)} ${trueLabel.padEnd(10)} ${(predLabel + " $mark").padEnd(12)} ${"%.0f%%".format(conf * 100).padEnd(7)} ${"%.1f".format(photoKB).padEnd(10)} $textBytes")
        }
    }

    // ── Accuracy summary ───────────────────────────────────────────────────────
    Log.i(TAG, "\n=== ACCURACY RESULTS ===")
    Log.i(TAG, "Overall: $totalCorrect / $totalPhotos = ${"%.1f".format(totalCorrect * 100.0 / totalPhotos)}%\n")
    Log.i(TAG, "${"Category".padEnd(12)} ${"P".padEnd(8)} ${"R".padEnd(8)} ${"F1".padEnd(8)}")
    Log.i(TAG, "-".repeat(38))

    var macroF1 = 0.0
    for (cat in categories) {
        val s  = stats[cat] ?: Stats()
        val p  = if (s.tp + s.fp > 0) s.tp.toDouble() / (s.tp + s.fp) else 0.0
        val r  = if (s.tp + s.fn > 0) s.tp.toDouble() / (s.tp + s.fn) else 0.0
        val f1 = if (p + r > 0) 2 * p * r / (p + r) else 0.0
        macroF1 += f1
        Log.i(TAG, "${cat.padEnd(12)} ${"%.2f".format(p).padEnd(8)} ${"%.2f".format(r).padEnd(8)} ${"%.2f".format(f1)}")
    }
    Log.i(TAG, "-".repeat(38))
    Log.i(TAG, "${"Macro avg".padEnd(12)} ${"-".padEnd(8)} ${"-".padEnd(8)} ${"%.2f".format(macroF1 / categories.size)}")

    // ── Bandwidth summary ──────────────────────────────────────────────────────
    Log.i(TAG, "\n=== BANDWIDTH REDUCTION ===")
    if (bandwidthRatios.isNotEmpty()) {
        Log.i(TAG, "Mean: ${"%.0f".format(bandwidthRatios.average())}×")
        Log.i(TAG, "Min:  ${"%.0f".format(bandwidthRatios.min())}×")
        Log.i(TAG, "Max:  ${"%.0f".format(bandwidthRatios.max())}×")
    }

    // ── FOR PAPER ──────────────────────────────────────────────────────────────
    Log.i(TAG, "\n=== FOR PAPER ===")
    Log.i(TAG, "Device:   ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
    Log.i(TAG, "Latency:  Mean=${"%.1f".format(meanMs)} ms  P95=${"%.1f".format(p95Ms)} ms  (N=$NUM_LATENCY_RUNS, full pipeline)")
    Log.i(TAG, "Accuracy: ${"%.1f".format(totalCorrect * 100.0 / totalPhotos)}%  ($totalCorrect/$totalPhotos)  Macro-F1=${"%.2f".format(macroF1 / categories.size)}")
    if (bandwidthRatios.isNotEmpty()) {
        Log.i(TAG, "Bandwidth: ${"%.0f".format(bandwidthRatios.average())}× mean  (${"%.0f".format(bandwidthRatios.min())}×–${"%.0f".format(bandwidthRatios.max())}× range)")
    }
    Log.i(TAG, "=================")

    interpreter.close()
}