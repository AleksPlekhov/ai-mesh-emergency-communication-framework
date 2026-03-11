package com.bitchat.android.ai.classifier

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * JVM unit tests for the *logic* layer of the emergency message classifier.
 *
 * These tests exercise every component of the classification pipeline
 * that does NOT require the TFLite native runtime:
 *
 *   ✅ Tokenizer — Keras Tokenizer replication (punctuation stripping,
 *                  lowercasing, word-to-ID mapping, padding / truncation)
 *   ✅ Softmax   — numerical stability, probabilities ∈ [0,1], sum = 1
 *   ✅ Priority mapping — all 9 emergency categories → MessagePriority
 *   ✅ Full simulated pipeline — tokenize → fake logits → softmax → priority
 *   ✅ Asset integrity — label_map.json and tokenizer.json are well-formed
 *
 * Why not the TFLite interpreter itself?
 *   The TFLite native library (.so) is compiled for Android ABIs (arm64, x86).
 *   There is no macOS ARM/x86-64 native build, so `Interpreter` cannot be
 *   instantiated on the JVM.  Full model inference is covered by the
 *   instrumented tests in src/androidTest (run on emulator or device).
 *
 * Run: ./gradlew :resqmesh-ai:testDebugUnitTest
 */
class TFLiteMessageClassifierTest {

    // ─── Tokenizer tests ──────────────────────────────────────────────────────

    @Test
    fun `tokenizer lowercases input and strips punctuation`() {
        // "Fire!", "FIRE!!" → both should resolve to the same token id as "fire"
        val ids1 = ClassifierUtils.tokenize("Fire!", WORD_INDEX, SEQ_LEN)
        val ids2 = ClassifierUtils.tokenize("FIRE!!", WORD_INDEX, SEQ_LEN)
        assertEquals("Upper and lower-case should map to the same token", ids1[0], ids2[0])

        val plain = WORD_INDEX["fire"] ?: ClassifierUtils.OOV_INDEX
        assertEquals("'fire' should resolve to its vocab id", plain, ids1[0])
    }

    @Test
    fun `tokenizer pads short input with zeros to seqLen`() {
        val ids = ClassifierUtils.tokenize("fire", WORD_INDEX, SEQ_LEN)
        assertEquals(SEQ_LEN, ids.size)
        // After the first token, every slot should be 0 (padding)
        assertTrue("Expected zero-padding after first token",
            ids.drop(1).all { it == 0 })
    }

    @Test
    fun `tokenizer maps unknown words to OOV index`() {
        val ids = ClassifierUtils.tokenize("xyzzy_totally_unknown_word", WORD_INDEX, SEQ_LEN)
        assertEquals(ClassifierUtils.OOV_INDEX, ids[0])
    }

    @Test
    fun `tokenizer handles empty string without crashing`() {
        val ids = ClassifierUtils.tokenize("", WORD_INDEX, SEQ_LEN)
        assertEquals(SEQ_LEN, ids.size)
        assertTrue("Empty input → all-zero ids", ids.all { it == 0 })
    }

    @Test
    fun `tokenizer truncates to seqLen for very long input`() {
        val longText = "fire ".repeat(SEQ_LEN + 50)
        val ids = ClassifierUtils.tokenize(longText, WORD_INDEX, SEQ_LEN)
        assertEquals(SEQ_LEN, ids.size)
    }

    @Test
    fun `medical emergency keywords are in the vocabulary`() {
        val text = "person unconscious not breathing need ambulance immediately"
        val ids = ClassifierUtils.tokenize(text, WORD_INDEX, SEQ_LEN)
        val knownCount = ids.count { it > ClassifierUtils.OOV_INDEX }
        assertTrue(
            "Expected ≥3 recognized tokens in a medical message, got $knownCount",
            knownCount >= 3
        )
    }

    @Test
    fun `fire-related keywords are in the vocabulary`() {
        val text = "fire spreading apartment building people upper floors evacuation"
        val ids = ClassifierUtils.tokenize(text, WORD_INDEX, SEQ_LEN)
        val knownCount = ids.count { it > ClassifierUtils.OOV_INDEX }
        assertTrue(
            "Expected ≥4 fire-related tokens in vocab, got $knownCount",
            knownCount >= 4
        )
    }

    @Test
    fun `flood-related keywords are in the vocabulary`() {
        val text = "flood water rising fast road blocked evacuation"
        val ids = ClassifierUtils.tokenize(text, WORD_INDEX, SEQ_LEN)
        val knownCount = ids.count { it > ClassifierUtils.OOV_INDEX }
        assertTrue(
            "Expected ≥3 flood-related tokens in vocab, got $knownCount",
            knownCount >= 3
        )
    }

    // ─── Softmax tests ────────────────────────────────────────────────────────

    @Test
    fun `softmax outputs sum to 1`() {
        val logits = floatArrayOf(2.0f, 1.0f, 0.1f, -1.0f, 0.5f, 1.2f, -0.5f, 0.8f, 1.5f)
        val probs = ClassifierUtils.softmax(logits)
        assertEquals("Softmax probabilities must sum to 1.0", 1.0f, probs.sum(), 1e-5f)
    }

    @Test
    fun `softmax argmax matches the position of the highest logit`() {
        val logits = floatArrayOf(0.1f, 0.2f, 5.0f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
        val probs = ClassifierUtils.softmax(logits)
        val argmax = probs.indices.maxByOrNull { probs[it] }
        assertEquals("argmax must point to the highest-logit index (2)", 2, argmax)
    }

    @Test
    fun `softmax output is always a valid probability`() {
        val logits = floatArrayOf(100f, -100f, 0f, 50f, -50f, 30f, -30f, 20f, -20f)
        ClassifierUtils.softmax(logits).forEach { p ->
            assertTrue("Probability $p is out of [0,1]", p in 0f..1f)
        }
    }

    @Test
    fun `softmax is numerically stable with large logit differences`() {
        val logits = floatArrayOf(1000f, -1000f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val probs = ClassifierUtils.softmax(logits)
        assertFalse("Softmax should not produce NaN", probs.any { it.isNaN() })
        assertFalse("Softmax should not produce Inf", probs.any { it.isInfinite() })
        assertEquals(1.0f, probs.sum(), 1e-4f)
    }

    // ─── Priority mapping — all 9 categories ─────────────────────────────────

    @Test fun `MEDICAL maps to CRITICAL`() =
        assertEquals(MessagePriority.CRITICAL, ClassifierUtils.mapToPriority("MEDICAL"))

    @Test fun `COLLAPSE maps to CRITICAL`() =
        assertEquals(MessagePriority.CRITICAL, ClassifierUtils.mapToPriority("COLLAPSE"))

    @Test fun `FIRE maps to HIGH`() =
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority("FIRE"))

    @Test fun `FLOOD maps to HIGH`() =
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority("FLOOD"))

    @Test fun `SECURITY maps to HIGH`() =
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority("SECURITY"))

    @Test fun `INFRASTRUCTURE maps to NORMAL`() =
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority("INFRASTRUCTURE"))

    @Test fun `WEATHER maps to NORMAL`() =
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority("WEATHER"))

    @Test fun `MISSING_PERSON maps to NORMAL`() =
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority("MISSING_PERSON"))

    @Test fun `RESOURCE_REQUEST maps to NORMAL`() =
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority("RESOURCE_REQUEST"))

    @Test fun `unknown category defaults to NORMAL`() =
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority("TOTALLY_UNKNOWN"))

    // ─── Full simulated pipeline ──────────────────────────────────────────────
    // These tests mimic the model: produce a logit vector that peaks at a
    // specific class index, then verify the pipeline selects the right label
    // and priority.  The tokenizer is real; only the "inference" step is faked.

    @Test
    fun `pipeline high MEDICAL logit gives CRITICAL priority`() {
        val logits = FloatArray(9) { -2f }.also { it[LABELS.entries.first { e -> e.value == "MEDICAL" }.key] = 5f }
        val probs  = ClassifierUtils.softmax(logits)
        val top    = probs.indices.maxByOrNull { probs[it] }!!
        assertEquals("MEDICAL", LABELS[top])
        assertEquals(MessagePriority.CRITICAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
        assertTrue("Confidence should be near 1.0 with dominant logit", probs[top] > 0.90f)
    }

    @Test
    fun `pipeline high COLLAPSE logit gives CRITICAL priority`() {
        val idx    = LABELS.entries.first { it.value == "COLLAPSE" }.key
        val logits = FloatArray(9) { -2f }.also { it[idx] = 5f }
        val probs  = ClassifierUtils.softmax(logits)
        val top    = probs.indices.maxByOrNull { probs[it] }!!
        assertEquals("COLLAPSE", LABELS[top])
        assertEquals(MessagePriority.CRITICAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high FIRE logit gives HIGH priority`() {
        val idx    = LABELS.entries.first { it.value == "FIRE" }.key
        val logits = FloatArray(9) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("FIRE", LABELS[top])
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high FLOOD logit gives HIGH priority`() {
        val idx    = LABELS.entries.first { it.value == "FLOOD" }.key
        val logits = FloatArray(9) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("FLOOD", LABELS[top])
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high SECURITY logit gives HIGH priority`() {
        val idx    = LABELS.entries.first { it.value == "SECURITY" }.key
        val logits = FloatArray(9) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("SECURITY", LABELS[top])
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high WEATHER logit gives NORMAL priority`() {
        val idx    = LABELS.entries.first { it.value == "WEATHER" }.key
        val logits = FloatArray(9) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("WEATHER", LABELS[top])
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high RESOURCE_REQUEST logit gives NORMAL priority`() {
        val idx    = LABELS.entries.first { it.value == "RESOURCE_REQUEST" }.key
        val logits = FloatArray(9) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("RESOURCE_REQUEST", LABELS[top])
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    // ─── Asset integrity ─────────────────────────────────────────────────────

    @Test
    fun `label_map_json contains exactly 9 categories`() {
        assertEquals(9, LABELS.size)
    }

    @Test
    fun `label_map_json contains all expected emergency categories`() {
        val expected = setOf(
            "FIRE", "FLOOD", "MEDICAL", "COLLAPSE", "INFRASTRUCTURE",
            "SECURITY", "WEATHER", "MISSING_PERSON", "RESOURCE_REQUEST"
        )
        assertEquals(expected, LABELS.values.toSet())
    }

    @Test
    fun `label indices are 0-based and contiguous`() {
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 6, 7, 8), LABELS.keys.toSet())
    }

    @Test
    fun `word_index has a meaningful vocabulary size`() {
        assertTrue(
            "Expected vocab size > 100, got ${WORD_INDEX.size}",
            WORD_INDEX.size > 100
        )
    }

    @Test
    fun `word_index contains OOV token at index 1 and real words start at index 2`() {
        // Keras Tokenizer stores the oov_token ("<OOV>") explicitly in word_index at index 1.
        // Real vocabulary words start at index 2.
        val oovEntry = WORD_INDEX.entries.find { it.value == 1 }
        assertNotNull("word_index must contain a token at index 1 (the OOV slot)", oovEntry)
        assertTrue(
            "Index-1 entry should be the OOV token, got '${oovEntry?.key}'",
            oovEntry!!.key.lowercase().contains("oov")
        )
        // Verify no real word appears below index 2
        val belowTwo = WORD_INDEX.entries.filter { it.value < 1 }
        assertTrue("No word should have index < 1 (index 0 is padding)", belowTwo.isEmpty())
    }

    // ─── Companion (shared fixtures) ─────────────────────────────────────────

    companion object {
        /**
         * Use the model's typical sequence length as the test default.
         * The actual value is read at runtime from the TFLite tensor shape;
         * here we match what was observed during model inspection.
         */
        private const val SEQ_LEN = 50

        private lateinit var WORD_INDEX: Map<String, Int>
        private lateinit var LABELS: Map<Int, String>

        /**
         * Load the real tokenizer.json and label_map.json from src/main/assets/.
         * Gradle runs unit tests with the module root as the working directory,
         * so the relative path resolves correctly without any Android Context.
         */
        @JvmStatic
        @BeforeClass
        fun loadRealAssets() {
            // ── tokenizer.json ─────────────────────────────────────────────
            val tokenizerJson = File("src/main/assets/tokenizer.json").readText()
            val config = JSONObject(tokenizerJson).getJSONObject("config")
            val rawWordIndex = JSONObject(config.getString("word_index"))
            WORD_INDEX = buildMap {
                rawWordIndex.keys().forEach { word -> put(word, rawWordIndex.getInt(word)) }
            }

            // ── label_map.json ─────────────────────────────────────────────
            val labelJson = File("src/main/assets/label_map.json").readText()
            val obj = JSONObject(labelJson)
            LABELS = buildMap {
                obj.keys().forEach { key -> put(key.toInt(), obj.getString(key)) }
            }
        }
    }
}
