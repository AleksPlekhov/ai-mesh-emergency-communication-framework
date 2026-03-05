package com.bitchat.android.ai.classifier

/**
 * Pure-Kotlin utilities shared by all classifier implementations.
 *
 * Extracted as an `internal` object so the tokenizer, softmax, and
 * priority-mapping functions can be exercised from JVM unit tests
 * without requiring the TFLite native runtime (.so / .dylib).
 */
internal object ClassifierUtils {

    /**
     * Punctuation stripped by the Keras Tokenizer at training time.
     * This must match the `filters` field in tokenizer.json exactly.
     */
    val KERAS_FILTERS: Regex = Regex("""[!"#${'$'}%&()*+,\-./:;<=>?@\[\\\]^_`{|}~\t\n]""")

    /**
     * Index reserved for out-of-vocabulary words in a Keras Tokenizer
     * configured with `oov_token="<OOV>"`.  Index 0 is always padding.
     */
    const val OOV_INDEX: Int = 1

    /**
     * Replicates `Tokenizer.texts_to_sequences()` + `pad_sequences(maxlen=seqLen)`:
     *  1. Strip [KERAS_FILTERS] punctuation
     *  2. Lowercase
     *  3. Split on whitespace
     *  4. Map each word to its integer ID (`wordIndex`); unknown → [OOV_INDEX]
     *  5. Right-pad with 0 / truncate to exactly [seqLen] tokens
     */
    fun tokenize(
        text: String,
        wordIndex: Map<String, Int>,
        seqLen: Int
    ): IntArray {
        val cleaned = text.replace(KERAS_FILTERS, "").lowercase()
        val words   = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val ids     = IntArray(seqLen) { 0 }           // 0 = padding
        words.take(seqLen).forEachIndexed { i, word ->
            ids[i] = wordIndex[word] ?: OOV_INDEX
        }
        return ids
    }

    /**
     * Numerically-stable softmax: subtracts the maximum logit before
     * exponentiating to prevent floating-point overflow.
     */
    fun softmax(logits: FloatArray): FloatArray {
        val max  = logits.max()
        val exps = logits.map { Math.exp((it - max).toDouble()).toFloat() }
        val sum  = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    /**
     * Maps the model's 9 emergency categories to our 4-tier [MessagePriority].
     *
     *  CRITICAL — immediate life-safety (medical emergency, structural collapse)
     *  HIGH     — urgent hazard (fire, flood, active security threat)
     *  NORMAL   — serious but manageable (infrastructure, weather, missing person, resources)
     */
    fun mapToPriority(category: String): MessagePriority = when (category) {
        "MEDICAL", "COLLAPSE"                            -> MessagePriority.CRITICAL
        "FIRE", "FLOOD", "SECURITY"                      -> MessagePriority.HIGH
        "INFRASTRUCTURE", "WEATHER",
        "MISSING_PERSON", "RESOURCE_REQUEST"             -> MessagePriority.NORMAL
        else                                             -> MessagePriority.NORMAL
    }
}
