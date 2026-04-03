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

    @Test fun `NONE maps to NONE`() =
        assertEquals(MessagePriority.NONE, ClassifierUtils.mapToPriority("NONE"))

    @Test fun `unknown category defaults to NONE`() =
        assertEquals(MessagePriority.NONE, ClassifierUtils.mapToPriority("TOTALLY_UNKNOWN"))

    // ─── Full simulated pipeline ──────────────────────────────────────────────
    // These tests mimic the model: produce a logit vector that peaks at a
    // specific class index, then verify the pipeline selects the right label
    // and priority.  The tokenizer is real; only the "inference" step is faked.

    @Test
    fun `pipeline high MEDICAL logit gives CRITICAL priority`() {
        val logits = FloatArray(LABELS.size) { -2f }.also { it[LABELS.entries.first { e -> e.value == "MEDICAL" }.key] = 5f }
        val probs  = ClassifierUtils.softmax(logits)
        val top    = probs.indices.maxByOrNull { probs[it] }!!
        assertEquals("MEDICAL", LABELS[top])
        assertEquals(MessagePriority.CRITICAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
        assertTrue("Confidence should be near 1.0 with dominant logit", probs[top] > 0.90f)
    }

    @Test
    fun `pipeline high COLLAPSE logit gives CRITICAL priority`() {
        val idx    = LABELS.entries.first { it.value == "COLLAPSE" }.key
        val logits = FloatArray(LABELS.size) { -2f }.also { it[idx] = 5f }
        val probs  = ClassifierUtils.softmax(logits)
        val top    = probs.indices.maxByOrNull { probs[it] }!!
        assertEquals("COLLAPSE", LABELS[top])
        assertEquals(MessagePriority.CRITICAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high FIRE logit gives HIGH priority`() {
        val idx    = LABELS.entries.first { it.value == "FIRE" }.key
        val logits = FloatArray(LABELS.size) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("FIRE", LABELS[top])
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high FLOOD logit gives HIGH priority`() {
        val idx    = LABELS.entries.first { it.value == "FLOOD" }.key
        val logits = FloatArray(LABELS.size) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("FLOOD", LABELS[top])
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high SECURITY logit gives HIGH priority`() {
        val idx    = LABELS.entries.first { it.value == "SECURITY" }.key
        val logits = FloatArray(LABELS.size) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("SECURITY", LABELS[top])
        assertEquals(MessagePriority.HIGH, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high WEATHER logit gives NORMAL priority`() {
        val idx    = LABELS.entries.first { it.value == "WEATHER" }.key
        val logits = FloatArray(LABELS.size) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("WEATHER", LABELS[top])
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    @Test
    fun `pipeline high RESOURCE_REQUEST logit gives NORMAL priority`() {
        val idx    = LABELS.entries.first { it.value == "RESOURCE_REQUEST" }.key
        val logits = FloatArray(LABELS.size) { -2f }.also { it[idx] = 5f }
        val top    = ClassifierUtils.softmax(logits).let { p -> p.indices.maxByOrNull { p[it] }!! }
        assertEquals("RESOURCE_REQUEST", LABELS[top])
        assertEquals(MessagePriority.NORMAL, ClassifierUtils.mapToPriority(LABELS[top]!!))
    }

    // ─── Asset integrity ─────────────────────────────────────────────────────

    @Test
    fun `label_map_json contains exactly 10 categories`() {
        assertEquals(10, LABELS.size)
    }

    @Test
    fun `label_map_json contains all expected emergency categories`() {
        val expected = setOf(
            "FIRE", "FLOOD", "MEDICAL", "COLLAPSE", "INFRASTRUCTURE",
            "SECURITY", "WEATHER", "MISSING_PERSON", "RESOURCE_REQUEST", "NONE"
        )
        assertEquals(expected, LABELS.values.toSet())
    }

    @Test
    fun `label indices are 0-based and contiguous`() {
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), LABELS.keys.toSet())
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

    // ─── Vocabulary coverage — 10 examples per emergency category ─────────────
    // These verify the tokenizer recognises enough domain-specific words for
    // each category.  Since TFLite native cannot run on JVM, we assert that
    // at least 40 % of the non-trivial words are in the vocabulary (not OOV).

    private fun assertVocabCoverage(message: String, category: String, minRatio: Float = 0.40f) {
        val ids = ClassifierUtils.tokenize(message, WORD_INDEX, SEQ_LEN)
        val wordCount = message.replace(ClassifierUtils.KERAS_FILTERS, "")
            .lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val knownCount = ids.take(wordCount).count { it > ClassifierUtils.OOV_INDEX }
        assertTrue(
            "$category: expected ≥${(minRatio * 100).toInt()}% vocab coverage for " +
                "\"${message.take(60)}…\" but got $knownCount/$wordCount",
            knownCount >= (wordCount * minRatio).toInt()
        )
    }

    // ── FIRE ──────────────────────────────────────────────────────────────────

    @Test fun `vocab FIRE 01`() = assertVocabCoverage(
        "The curtains caught fire from a candle and it's spreading to the ceiling.", "FIRE")

    @Test fun `vocab FIRE 02`() = assertVocabCoverage(
        "I see thick black smoke coming out of the warehouse windows on Industrial Way.", "FIRE")

    @Test fun `vocab FIRE 03`() = assertVocabCoverage(
        "There is a forest fire moving quickly toward the residential area.", "FIRE")

    @Test fun `vocab FIRE 04`() = assertVocabCoverage(
        "My oven exploded and now the kitchen is in flames.", "FIRE")

    @Test fun `vocab FIRE 05`() = assertVocabCoverage(
        "A power line fell and the grass underneath it is starting to burn.", "FIRE")

    @Test fun `vocab FIRE 06`() = assertVocabCoverage(
        "I smell something burning in the apartment hallway, it's getting hard to breathe.", "FIRE")

    @Test fun `vocab FIRE 07`() = assertVocabCoverage(
        "The trash compactor at the mall is on fire.", "FIRE")

    @Test fun `vocab FIRE 08`() = assertVocabCoverage(
        "A car crashed and the engine bay is engulfed in flames.", "FIRE")

    @Test fun `vocab FIRE 09`() = assertVocabCoverage(
        "Help, my grill flared up and the back deck is catching fire!", "FIRE")

    @Test fun `vocab FIRE 10`() = assertVocabCoverage(
        "Lightning struck the barn and the hayloft is burning.", "FIRE")

    // ── FLOOD ─────────────────────────────────────────────────────────────────

    @Test fun `vocab FLOOD 01`() = assertVocabCoverage(
        "The creek is overflowing and the water is up to my front porch.", "FLOOD")

    @Test fun `vocab FLOOD 02`() = assertVocabCoverage(
        "Heavy rain has caused the basement to fill with three feet of water.", "FLOOD")

    @Test fun `vocab FLOOD 03`() = assertVocabCoverage(
        "The main road is completely submerged and cars are floating away.", "FLOOD")

    @Test fun `vocab FLOOD 04`() = assertVocabCoverage(
        "The levee looks like it's about to break under the pressure.", "FLOOD")

    @Test fun `vocab FLOOD 05`() = assertVocabCoverage(
        "We are trapped on our roof because the street has turned into a river.", "FLOOD")

    @Test fun `vocab FLOOD 06`() = assertVocabCoverage(
        "Flash flooding has washed out the bridge on Oak Street.", "FLOOD")

    @Test fun `vocab FLOOD 07`() = assertVocabCoverage(
        "Water is gushing out of the storm drains and flooding the parking lot.", "FLOOD")

    @Test fun `vocab FLOOD 08`() = assertVocabCoverage(
        "My yard is underwater and it's starting to seep through the door frames.", "FLOOD")

    @Test fun `vocab FLOOD 09`() = assertVocabCoverage(
        "The dam upstream has been breached, we need to evacuate.", "FLOOD")

    @Test fun `vocab FLOOD 10`() = assertVocabCoverage(
        "Tidal surge is flooding the coastal highway.", "FLOOD")

    // ── MEDICAL ───────────────────────────────────────────────────────────────

    @Test fun `vocab MEDICAL 01`() = assertVocabCoverage(
        "My wife is in labor and the contractions are only a minute apart.", "MEDICAL")

    @Test fun `vocab MEDICAL 02`() = assertVocabCoverage(
        "I think I'm having an allergic reaction; my throat is closing up.", "MEDICAL")

    @Test fun `vocab MEDICAL 03`() = assertVocabCoverage(
        "A man just collapsed on the sidewalk and isn't waking up.", "MEDICAL")

    @Test fun `vocab MEDICAL 04`() = assertVocabCoverage(
        "There is a lot of blood; he cut his arm deeply with a circular saw.", "MEDICAL")

    @Test fun `vocab MEDICAL 05`() = assertVocabCoverage(
        "My child drank some cleaning fluid and is vomiting.", "MEDICAL")

    @Test fun `vocab MEDICAL 06`() = assertVocabCoverage(
        "I'm feeling a sharp pain in my chest and my left arm is numb.", "MEDICAL")

    @Test fun `vocab MEDICAL 07`() = assertVocabCoverage(
        "Someone has been pulled from the pool and they aren't breathing.", "MEDICAL")

    @Test fun `vocab MEDICAL 08`() = assertVocabCoverage(
        "He fell off a ladder and his leg looks broken at a weird angle.", "MEDICAL")

    @Test fun `vocab MEDICAL 09`() = assertVocabCoverage(
        "I found my roommate unconscious on the floor with a bottle of pills.", "MEDICAL")

    @Test fun `vocab MEDICAL 10`() = assertVocabCoverage(
        "A pedestrian was hit by a car and has a serious head injury.", "MEDICAL")

    // ── COLLAPSE ──────────────────────────────────────────────────────────────

    @Test fun `vocab COLLAPSE 01`() = assertVocabCoverage(
        "The balcony on the second floor just fell onto the sidewalk.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 02`() = assertVocabCoverage(
        "Part of the ceiling in the grocery store has caved in.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 03`() = assertVocabCoverage(
        "The construction site scaffolding just collapsed with workers on it.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 04`() = assertVocabCoverage(
        "A sinkhole opened up in the middle of the intersection.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 05`() = assertVocabCoverage(
        "The old bridge just crumbled into the river.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 06`() = assertVocabCoverage(
        "An interior wall collapsed during the renovation.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 07`() = assertVocabCoverage(
        "The roof of the stadium gave way under the weight of the snow.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 08`() = assertVocabCoverage(
        "A retaining wall failed and mud is sliding toward the house.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 09`() = assertVocabCoverage(
        "The stairs broke while we were walking down them.", "COLLAPSE")

    @Test fun `vocab COLLAPSE 10`() = assertVocabCoverage(
        "The mine shaft has caved in and we can't get out.", "COLLAPSE")

    // ── INFRASTRUCTURE ────────────────────────────────────────────────────────

    @Test fun `vocab INFRASTRUCTURE 01`() = assertVocabCoverage(
        "A massive tree fell and took down all the power lines on this block.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 02`() = assertVocabCoverage(
        "The traffic lights are out at the busiest intersection in town.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 03`() = assertVocabCoverage(
        "A water main burst and there is a huge geyser in the street.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 04`() = assertVocabCoverage(
        "There is a strong smell of natural gas coming from the street vent.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 05`() = assertVocabCoverage(
        "The sewer line backed up and is overflowing into the park.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 06`() = assertVocabCoverage(
        "A high-voltage transformer just blew up and the sparks are flying.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 07`() = assertVocabCoverage(
        "The highway has a giant crack in it that's making it impassable.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 08`() = assertVocabCoverage(
        "The city's communication lines seem to be completely down.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 09`() = assertVocabCoverage(
        "There is a chemical leak from a pipe at the industrial plant.", "INFRASTRUCTURE")

    @Test fun `vocab INFRASTRUCTURE 10`() = assertVocabCoverage(
        "The railway tracks are warped and look dangerous for the next train.", "INFRASTRUCTURE")

    // ── SECURITY ──────────────────────────────────────────────────────────────

    @Test fun `vocab SECURITY 01`() = assertVocabCoverage(
        "Someone is trying to kick in my front door right now.", "SECURITY")

    @Test fun `vocab SECURITY 02`() = assertVocabCoverage(
        "I just saw a man with a gun running into the shopping center.", "SECURITY")

    @Test fun `vocab SECURITY 03`() = assertVocabCoverage(
        "A group of people are breaking windows and looting the store.", "SECURITY")

    @Test fun `vocab SECURITY 04`() = assertVocabCoverage(
        "I'm being followed by a suspicious vehicle for the last five miles.", "SECURITY")

    @Test fun `vocab SECURITY 05`() = assertVocabCoverage(
        "Someone just snatched my purse and ran toward the subway station.", "SECURITY")

    @Test fun `vocab SECURITY 06`() = assertVocabCoverage(
        "There is a fight breaking out in the bar and someone has a knife.", "SECURITY")

    @Test fun `vocab SECURITY 07`() = assertVocabCoverage(
        "I hear someone moving around downstairs but I live alone.", "SECURITY")

    @Test fun `vocab SECURITY 08`() = assertVocabCoverage(
        "Two men are trying to hotwire a car in the driveway across the street.", "SECURITY")

    @Test fun `vocab SECURITY 09`() = assertVocabCoverage(
        "I've been robbed at gunpoint at the ATM.", "SECURITY")

    @Test fun `vocab SECURITY 10`() = assertVocabCoverage(
        "There is an intruder in my backyard looking through the windows.", "SECURITY")

    // ── WEATHER ───────────────────────────────────────────────────────────────

    @Test fun `vocab WEATHER 01`() = assertVocabCoverage(
        "A massive hailstorm is smashing car windshields and windows.", "WEATHER")

    @Test fun `vocab WEATHER 02`() = assertVocabCoverage(
        "The wind is so strong it's ripping shingles off the roofs.", "WEATHER")

    @Test fun `vocab WEATHER 03`() = assertVocabCoverage(
        "Visibility is zero due to a heavy blizzard; I'm stranded in my car.", "WEATHER")

    @Test fun `vocab WEATHER 04`() = assertVocabCoverage(
        "A tornado funnel cloud has been spotted just south of the city.", "WEATHER")

    @Test fun `vocab WEATHER 05`() = assertVocabCoverage(
        "The heatwave is intense and the elderly residents have no AC.", "WEATHER")

    @Test fun `vocab WEATHER 06`() = assertVocabCoverage(
        "A severe thunderstorm is knocking down trees everywhere.", "WEATHER")

    @Test fun `vocab WEATHER 07`() = assertVocabCoverage(
        "There is an ice storm and the roads are like a skating rink.", "WEATHER")

    @Test fun `vocab WEATHER 08`() = assertVocabCoverage(
        "A dust storm is rolling in and we can't see the road.", "WEATHER")

    @Test fun `vocab WEATHER 09`() = assertVocabCoverage(
        "The hurricane sirens are going off, where is the nearest shelter?", "WEATHER")

    @Test fun `vocab WEATHER 10`() = assertVocabCoverage(
        "Extreme fog has caused a multi-car pileup on the freeway.", "WEATHER")

    // ── MISSING_PERSON ────────────────────────────────────────────────────────

    @Test fun `vocab MISSING_PERSON 01`() = assertVocabCoverage(
        "I can't find my daughter; she was right behind me at the zoo.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 02`() = assertVocabCoverage(
        "My elderly father with dementia wandered off an hour ago.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 03`() = assertVocabCoverage(
        "A hiker has failed to return to the base camp by sunset.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 04`() = assertVocabCoverage(
        "My toddler climbed out of the window and I can't find him anywhere.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 05`() = assertVocabCoverage(
        "My friend went for a swim in the ocean and hasn't come back to shore.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 06`() = assertVocabCoverage(
        "We lost contact with the scout group in the mountains.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 07`() = assertVocabCoverage(
        "A teenager has been missing since leaving school yesterday afternoon.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 08`() = assertVocabCoverage(
        "I found a small child crying alone in the parking lot.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 09`() = assertVocabCoverage(
        "My husband didn't come home from work and his phone is dead.", "MISSING_PERSON")

    @Test fun `vocab MISSING_PERSON 10`() = assertVocabCoverage(
        "A skier went off-trail and hasn't been seen for four hours.", "MISSING_PERSON")

    // ── RESOURCE_REQUEST ──────────────────────────────────────────────────────

    @Test fun `vocab RESOURCE_REQUEST 01`() = assertVocabCoverage(
        "We need emergency blankets and food for the displaced families.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 02`() = assertVocabCoverage(
        "Does anyone have a generator? Our medical equipment needs power.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 03`() = assertVocabCoverage(
        "The shelter is running out of clean water and baby formula.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 04`() = assertVocabCoverage(
        "We need sandbags to protect our homes from the rising river.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 05`() = assertVocabCoverage(
        "Is there a place where we can get dry clothes and shoes?", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 06`() = assertVocabCoverage(
        "We require oxygen tanks for the clinic since the delivery didn't arrive.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 07`() = assertVocabCoverage(
        "Our neighborhood needs a truck to clear the fallen debris.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 08`() = assertVocabCoverage(
        "Requesting a helicopter for a remote supply drop of medicine.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 09`() = assertVocabCoverage(
        "We need more volunteers to help search the debris field.", "RESOURCE_REQUEST")

    @Test fun `vocab RESOURCE_REQUEST 10`() = assertVocabCoverage(
        "Is there a mobile kitchen available for the emergency workers?", "RESOURCE_REQUEST")

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
