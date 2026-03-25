package com.bitchat.android.ai

/**
 * ResQMesh AI — TFLite Latency Benchmark
 *
 * Paste this function anywhere in your MainActivity.kt or a new
 * BenchmarkActivity.kt. Call it from onCreate() or a button click.
 *
 * Requirements:
 *   - emergency_model.tflite in src/main/assets/
 *   - Add to build.gradle (app): implementation 'org.tensorflow:tensorflow-lite:2.14.0'
 *
 * Output: printed to Logcat with tag "RESQMESH_BENCH"
 */

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val TAG = "RESQMESH_BENCH"
private const val MAX_LEN = 50
private const val NUM_RUNS = 200

// ── Test messages (mix of lengths and categories) ─────────────────────────────
private val TEST_MESSAGES = listOf(
    "fire",
    "help",
    "fire in my building people trapped on upper floors",
    "water rising flash flood warning river already overflowing completely",
    "child not breathing choking turning blue person having seizure heart attack",
    "building collapsed buried rubble earthquake survivors calling for help urgently",
    "shots fired active shooter staff hiding hostage situation armed men stampede",
    "tornado spotted hailstorm blizzard hurricane landfall incoming extreme weather",
    "missing person last seen downtown yesterday morning search overdue unaccounted",
    "power lines sparking gas main ruptured infrastructure failure blackout cell towers down",
    "need food water medicine shelter for 200 people requesting supplies urgently blankets",
    "SOS medical emergency cardiac arrest need defibrillator now send ambulance immediately"
)

// ── Simple tokenizer (mirrors Python tokenizer logic) ─────────────────────────
// In real app this comes from your EmergencyClassifier.kt word_index map
// For benchmark we use a dummy tokenizer — latency result is identical
private fun tokenize(text: String): IntArray {
    val tokens = text.lowercase().trim().split(" ")
    val ids = IntArray(MAX_LEN) { 0 }
    tokens.take(MAX_LEN).forEachIndexed { i, _ -> ids[i] = (1..2156).random() }
    return ids
}

// ── Load model from assets ────────────────────────────────────────────────────
private fun loadModelFile(context: Context): ByteBuffer {
    val assetFileDescriptor = context.assets.openFd("emergency_model.tflite")
    val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    return fileChannel.map(
        FileChannel.MapMode.READ_ONLY,
        assetFileDescriptor.startOffset,
        assetFileDescriptor.declaredLength
    )
}

// ── Main benchmark function — call from onCreate() ───────────────────────────
fun runLatencyBenchmark(context: Context) {
    Log.i(TAG, "=== ResQMesh TFLite Latency Benchmark ===")

    val modelBuffer = loadModelFile(context)
    val interpreter = Interpreter(modelBuffer)

    // Check input dtype
    val inputTensor = interpreter.getInputTensor(0)
    val outputTensor = interpreter.getOutputTensor(0)
    Log.i(TAG, "Input shape:  ${inputTensor.shape().toList()}")
    Log.i(TAG, "Input dtype:  ${inputTensor.dataType()}")
    Log.i(TAG, "Output shape: ${outputTensor.shape().toList()}")

    val results = mutableListOf<Long>()

    // Warmup — 10 runs not counted
    repeat(10) {
        val msg = TEST_MESSAGES.random()
        val ids = tokenize(msg)
        val inputBuffer = ByteBuffer.allocateDirect(MAX_LEN * 4).apply {
            order(ByteOrder.nativeOrder())
            ids.forEach { putFloat(it.toFloat()) }  // FLOAT32 input
            rewind()
        }
        val output = Array(1) { FloatArray(9) }
        interpreter.run(inputBuffer, output)
    }

    // Benchmark runs
    for (run in 0 until NUM_RUNS) {
        val msg = TEST_MESSAGES[run % TEST_MESSAGES.size]
        val ids = tokenize(msg)

        val inputBuffer = ByteBuffer.allocateDirect(MAX_LEN * 4).apply {
            order(ByteOrder.nativeOrder())
            ids.forEach { putFloat(it.toFloat()) }
            rewind()
        }
        val output = Array(1) { FloatArray(9) }

        val t0 = System.nanoTime()
        interpreter.run(inputBuffer, output)
        val elapsed = System.nanoTime() - t0

        results.add(elapsed)
    }

    interpreter.close()

    // ── Stats ──────────────────────────────────────────────────────────────────
    val sortedMs = results.map { it / 1_000_000.0 }.sorted()
    val mean     = sortedMs.average()
    val p50      = sortedMs[sortedMs.size / 2]
    val p95      = sortedMs[(sortedMs.size * 0.95).toInt()]
    val p99      = sortedMs[(sortedMs.size * 0.99).toInt()]
    val min      = sortedMs.first()
    val max      = sortedMs.last()

    Log.i(TAG, "")
    Log.i(TAG, "=== RESULTS (N=$NUM_RUNS runs) ===")
    Log.i(TAG, "Mean:   ${"%.2f".format(mean)} ms")
    Log.i(TAG, "P50:    ${"%.2f".format(p50)} ms")
    Log.i(TAG, "P95:    ${"%.2f".format(p95)} ms")
    Log.i(TAG, "P99:    ${"%.2f".format(p99)} ms")
    Log.i(TAG, "Min:    ${"%.2f".format(min)} ms")
    Log.i(TAG, "Max:    ${"%.2f".format(max)} ms")
    Log.i(TAG, "")
    Log.i(TAG, "=== FOR PAPER ===")
    Log.i(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    Log.i(TAG, "Android: ${android.os.Build.VERSION.RELEASE}")
    Log.i(TAG, "Mean latency: ${"%.1f".format(mean)} ms")
    Log.i(TAG, "P95  latency: ${"%.1f".format(p95)} ms")
    Log.i(TAG, "=================")
}