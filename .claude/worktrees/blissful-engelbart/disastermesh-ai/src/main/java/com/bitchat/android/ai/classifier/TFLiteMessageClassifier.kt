package com.bitchat.android.ai.classifier

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "TFLiteClassifier"

/**
 * TFLite-backed message priority classifier.
 *
 * Expects a quantized INT8 or FLOAT32 model at [MODEL_ASSET_PATH] in the app's assets.
 * The model must accept a single float input tensor of shape [1, MAX_SEQ_LEN] (token IDs)
 * and produce a float output tensor of shape [1, 4] (logits for CRITICAL/HIGH/NORMAL/LOW).
 *
 * Drop a trained `message_classifier.tflite` into `disastermesh-ai/src/main/assets/` to activate.
 * Until then, [MessageClassifierFactory] routes to [KeywordMessageClassifier] automatically.
 */
class TFLiteMessageClassifier(context: Context) : MessagePriorityClassifier {

    private val interpreter: Interpreter = loadInterpreter(context)

    override fun classify(
        messageText: String,
        metadata: Map<String, String>
    ): ClassificationResult {
        val input = tokenize(messageText)
        val output = Array(1) { FloatArray(LABEL_COUNT) }

        interpreter.run(input, output)

        val logits = output[0]
        val maxIdx = logits.indices.maxByOrNull { logits[it] } ?: 0
        val confidence = softmax(logits)[maxIdx]

        return ClassificationResult(
            priority = PRIORITY_LABELS[maxIdx],
            confidence = confidence,
            reasoning = "TFLite model (confidence=${String.format("%.2f", confidence)})"
        )
    }

    override fun close() {
        runCatching { interpreter.close() }
    }

    // ---- private helpers ----

    private fun tokenize(text: String): Array<FloatArray> {
        // Minimal whitespace tokenizer — replace with a proper vocab lookup when a real model is
        // integrated. Each token maps to its index in a trivial char-level encoding for now.
        val tokens = FloatArray(MAX_SEQ_LEN) { 0f }
        text.lowercase().split(" ").take(MAX_SEQ_LEN).forEachIndexed { i, word ->
            tokens[i] = word.hashCode().and(0x7FFFFFFF).rem(VOCAB_SIZE).toFloat()
        }
        return arrayOf(tokens)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = logits.map { Math.exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    companion object {
        const val MODEL_ASSET_PATH = "message_classifier.tflite"
        private const val MAX_SEQ_LEN = 64
        private const val VOCAB_SIZE = 10_000
        private const val LABEL_COUNT = 4

        private val PRIORITY_LABELS = listOf(
            MessagePriority.CRITICAL,
            MessagePriority.HIGH,
            MessagePriority.NORMAL,
            MessagePriority.LOW
        )

        fun isModelAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET_PATH).close()
            true
        }.getOrDefault(false)

        private fun loadInterpreter(context: Context): Interpreter {
            val afd = context.assets.openFd(MODEL_ASSET_PATH)
            val buffer: MappedByteBuffer = FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
            return Interpreter(buffer)
        }
    }
}
