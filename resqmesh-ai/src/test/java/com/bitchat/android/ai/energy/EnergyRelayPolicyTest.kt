package com.bitchat.android.ai.energy

import org.junit.Assert.*
import org.junit.Test

/**
 * JVM unit tests for [EnergyRelayPolicy].
 *
 * Covers:
 *   ✅ networkFactor   — boundary values for all size buckets
 *   ✅ energyMultiplier — every EnergyMode
 *   ✅ relayProbability — combined computation + clamping
 *   ✅ criticalRelayProbability — SOS / high-TTL tier
 *   ✅ passive-mode invariant — ULTRA_LOW_POWER → 0.0 for normal traffic
 *   ✅ full-power invariant  — PERFORMANCE/BALANCED → factor unchanged
 *
 * Run: ./gradlew :resqmesh-ai:test
 */
class EnergyRelayPolicyTest {

    // ─── networkFactor ──────────────────────────────────────────────────────

    @Test
    fun `networkFactor returns 1_0 for network size 1`() {
        assertEquals(1.00f, EnergyRelayPolicy.networkFactor(1), 0.001f)
    }

    @Test
    fun `networkFactor returns 1_0 at boundary 10`() {
        assertEquals(1.00f, EnergyRelayPolicy.networkFactor(10), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_85 just above 10`() {
        assertEquals(0.85f, EnergyRelayPolicy.networkFactor(11), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_85 at boundary 30`() {
        assertEquals(0.85f, EnergyRelayPolicy.networkFactor(30), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_70 just above 30`() {
        assertEquals(0.70f, EnergyRelayPolicy.networkFactor(31), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_70 at boundary 50`() {
        assertEquals(0.70f, EnergyRelayPolicy.networkFactor(50), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_55 just above 50`() {
        assertEquals(0.55f, EnergyRelayPolicy.networkFactor(51), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_55 at boundary 100`() {
        assertEquals(0.55f, EnergyRelayPolicy.networkFactor(100), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_40 above 100`() {
        assertEquals(0.40f, EnergyRelayPolicy.networkFactor(101), 0.001f)
    }

    @Test
    fun `networkFactor returns 0_40 for very large network`() {
        assertEquals(0.40f, EnergyRelayPolicy.networkFactor(10_000), 0.001f)
    }

    // ─── energyMultiplier ───────────────────────────────────────────────────

    @Test
    fun `energyMultiplier is 1_0 for PERFORMANCE`() {
        assertEquals(1.00f, EnergyRelayPolicy.energyMultiplier(EnergyMode.PERFORMANCE), 0.001f)
    }

    @Test
    fun `energyMultiplier is 1_0 for BALANCED`() {
        assertEquals(1.00f, EnergyRelayPolicy.energyMultiplier(EnergyMode.BALANCED), 0.001f)
    }

    @Test
    fun `energyMultiplier is 0_5 for POWER_SAVER`() {
        assertEquals(0.50f, EnergyRelayPolicy.energyMultiplier(EnergyMode.POWER_SAVER), 0.001f)
    }

    @Test
    fun `energyMultiplier is 0_0 for ULTRA_LOW_POWER`() {
        assertEquals(0.00f, EnergyRelayPolicy.energyMultiplier(EnergyMode.ULTRA_LOW_POWER), 0.001f)
    }

    // ─── relayProbability ───────────────────────────────────────────────────

    @Test
    fun `relayProbability equals networkFactor when PERFORMANCE`() {
        val networkSize = 25
        val expected = EnergyRelayPolicy.networkFactor(networkSize)
        assertEquals(expected, EnergyRelayPolicy.relayProbability(networkSize, EnergyMode.PERFORMANCE), 0.001f)
    }

    @Test
    fun `relayProbability equals networkFactor when BALANCED`() {
        val networkSize = 40
        val expected = EnergyRelayPolicy.networkFactor(networkSize)
        assertEquals(expected, EnergyRelayPolicy.relayProbability(networkSize, EnergyMode.BALANCED), 0.001f)
    }

    @Test
    fun `relayProbability is halved for POWER_SAVER`() {
        // networkSize=40 → factor=0.70, multiplier=0.50 → prob=0.35
        assertEquals(0.35f, EnergyRelayPolicy.relayProbability(40, EnergyMode.POWER_SAVER), 0.001f)
    }

    @Test
    fun `relayProbability is 0_0 for ULTRA_LOW_POWER regardless of network size`() {
        for (size in listOf(1, 10, 50, 200)) {
            assertEquals(
                "Expected 0.0 at ULTRA_LOW_POWER for networkSize=$size",
                0.00f,
                EnergyRelayPolicy.relayProbability(size, EnergyMode.ULTRA_LOW_POWER),
                0.001f
            )
        }
    }

    @Test
    fun `relayProbability is clamped to 1_0 maximum`() {
        // Largest possible: factor=1.0, multiplier=1.0 → 1.0 (no overflow)
        val prob = EnergyRelayPolicy.relayProbability(1, EnergyMode.PERFORMANCE)
        assertTrue("Probability must be ≤ 1.0", prob <= 1.0f)
    }

    @Test
    fun `relayProbability is clamped to 0_0 minimum`() {
        val prob = EnergyRelayPolicy.relayProbability(1, EnergyMode.ULTRA_LOW_POWER)
        assertTrue("Probability must be ≥ 0.0", prob >= 0.0f)
    }

    // ─── criticalRelayProbability ───────────────────────────────────────────

    @Test
    fun `criticalRelayProbability is 1_0 for PERFORMANCE`() {
        assertEquals(1.00f, EnergyRelayPolicy.criticalRelayProbability(EnergyMode.PERFORMANCE), 0.001f)
    }

    @Test
    fun `criticalRelayProbability is 1_0 for BALANCED`() {
        assertEquals(1.00f, EnergyRelayPolicy.criticalRelayProbability(EnergyMode.BALANCED), 0.001f)
    }

    @Test
    fun `criticalRelayProbability is 1_0 for POWER_SAVER`() {
        assertEquals(1.00f, EnergyRelayPolicy.criticalRelayProbability(EnergyMode.POWER_SAVER), 0.001f)
    }

    @Test
    fun `criticalRelayProbability is 0_20 for ULTRA_LOW_POWER`() {
        // Near-dead battery: still relay SOS at 20% probability as last-resort forwarder
        assertEquals(0.20f, EnergyRelayPolicy.criticalRelayProbability(EnergyMode.ULTRA_LOW_POWER), 0.001f)
    }

    // ─── invariants / cross-mode relationships ──────────────────────────────

    @Test
    fun `criticalRelayProbability never falls to zero ensuring SOS forwarding`() {
        for (mode in EnergyMode.entries) {
            assertTrue(
                "criticalRelayProbability must be > 0 for $mode",
                EnergyRelayPolicy.criticalRelayProbability(mode) > 0f
            )
        }
    }

    @Test
    fun `normal relay probability is always less than or equal to critical relay probability`() {
        for (mode in EnergyMode.entries) {
            val normal = EnergyRelayPolicy.relayProbability(1, mode)
            val critical = EnergyRelayPolicy.criticalRelayProbability(mode)
            assertTrue(
                "Normal relay ($normal) must be ≤ critical relay ($critical) for $mode",
                normal <= critical
            )
        }
    }

    @Test
    fun `POWER_SAVER relay probability is strictly less than BALANCED for same network size`() {
        val size = 20
        val balanced = EnergyRelayPolicy.relayProbability(size, EnergyMode.BALANCED)
        val saver = EnergyRelayPolicy.relayProbability(size, EnergyMode.POWER_SAVER)
        assertTrue("POWER_SAVER ($saver) must be < BALANCED ($balanced)", saver < balanced)
    }

    @Test
    fun `ULTRA_LOW_POWER normal relay is strictly less than POWER_SAVER`() {
        val size = 20
        val saver = EnergyRelayPolicy.relayProbability(size, EnergyMode.POWER_SAVER)
        val ultra = EnergyRelayPolicy.relayProbability(size, EnergyMode.ULTRA_LOW_POWER)
        assertTrue("ULTRA_LOW_POWER ($ultra) must be < POWER_SAVER ($saver)", ultra < saver)
    }

    @Test
    fun `PERFORMANCE and BALANCED produce identical relay probabilities`() {
        for (size in listOf(1, 15, 40, 75, 150)) {
            assertEquals(
                "PERFORMANCE and BALANCED must be equal for networkSize=$size",
                EnergyRelayPolicy.relayProbability(size, EnergyMode.PERFORMANCE),
                EnergyRelayPolicy.relayProbability(size, EnergyMode.BALANCED),
                0.001f
            )
        }
    }

    @Test
    fun `relay probability decreases as network size grows (BALANCED)`() {
        val small = EnergyRelayPolicy.relayProbability(5, EnergyMode.BALANCED)
        val medium = EnergyRelayPolicy.relayProbability(40, EnergyMode.BALANCED)
        val large = EnergyRelayPolicy.relayProbability(200, EnergyMode.BALANCED)
        assertTrue("small ($small) > medium ($medium)", small > medium)
        assertTrue("medium ($medium) > large ($large)", medium > large)
    }
}
