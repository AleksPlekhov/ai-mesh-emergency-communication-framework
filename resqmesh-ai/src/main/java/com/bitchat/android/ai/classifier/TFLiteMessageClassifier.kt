package com.bitchat.android.ai.classifier

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "TFLiteClassifier"

/**
 * TFLite-backed emergency message classifier.
 *
 * Uses:
 *  - [MODEL_ASSET]     — trained TFLite model (emergency_model.tflite)
 *  - [TOKENIZER_ASSET] — Keras word-level tokenizer config (tokenizer.json)
 *  - [LABELS_ASSET]    — index → category name map (label_map.json)
 *
 * Pipeline (mirrors what Keras did at training time):
 *   1. Strip punctuation → lowercase → split on whitespace
 *   2. Map each word to its integer ID from word_index (unknown → OOV index 1)
 *   3. Pad with zeros / truncate to the model's input sequence length
 *   4. Run TFLite interpreter  →  float[1][9] output logits
 *   5. Softmax → argmax → look up label → map to [MessagePriority]
 */
class TFLiteMessageClassifier(context: Context) : MessagePriorityClassifier {

    private val interpreter: Interpreter
    private val seqLen: Int
    private val wordIndex: Map<String, Int>   // word → int id
    private val labels: Map<Int, String>       // class idx → category name (e.g. "MEDICAL")

    init {
        interpreter = loadInterpreter(context)
        // Query the actual input sequence length directly from the loaded model tensor
        seqLen = interpreter.getInputTensor(0).shape()[1]
        wordIndex = loadWordIndex(context)
        labels = loadLabels(context)
        Log.d(TAG, "Loaded — seqLen=$seqLen, vocab=${wordIndex.size}, labels=${labels.values}")
    }

    override fun classify(
        messageText: String,
        metadata: Map<String, String>
    ): ClassificationResult {
        val inputIds = ClassifierUtils.tokenize(messageText, wordIndex, seqLen)

        // The model's input tensor is FLOAT32 (confirmed from crash log).
        // Word IDs are integers, but must be wrapped in a Float array before
        // being handed to the TFLite interpreter.
        val input  = Array(1) { FloatArray(seqLen) { i -> inputIds[i].toFloat() } }
        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, output)

        val probs      = ClassifierUtils.softmax(output[0])
        val maxIdx     = probs.indices.maxByOrNull { probs[it] } ?: 0
        val confidence = probs[maxIdx]
        val category   = labels[maxIdx] ?: "UNKNOWN"

        return ClassificationResult(
            priority      = ClassifierUtils.mapToPriority(category),
            confidence    = confidence,
            emergencyType = category,
            reasoning     = "TFLite[$category] conf=${String.format("%.0f", confidence * 100)}%"
        )
    }

    override fun close() {
        runCatching { interpreter.close() }
    }

    // ── Asset loaders ─────────────────────────────────────────────────────────

    private fun loadWordIndex(context: Context): Map<String, Int> {
        val json   = context.assets.open(TOKENIZER_ASSET).bufferedReader().readText()
        val config = JSONObject(json).getJSONObject("config")
        val raw    = JSONObject(config.getString("word_index"))
        return buildMap { raw.keys().forEach { word -> put(word, raw.getInt(word)) } }
    }

    private fun loadLabels(context: Context): Map<Int, String> {
        val json = context.assets.open(LABELS_ASSET).bufferedReader().readText()
        val obj  = JSONObject(json)
        return buildMap { obj.keys().forEach { key -> put(key.toInt(), obj.getString(key)) } }
    }

    companion object {
        const val MODEL_ASSET     = "emergency_model.tflite"
        const val TOKENIZER_ASSET = "tokenizer.json"
        const val LABELS_ASSET    = "label_map.json"

        fun isModelAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET).close()
            true
        }.getOrDefault(false)

        private fun loadInterpreter(context: Context): Interpreter {
            val afd    = context.assets.openFd(MODEL_ASSET)
            val buffer: MappedByteBuffer = FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
            return Interpreter(buffer)
        }
    }
}
