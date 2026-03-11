package com.bitchat.android.ai.energy

/**
 * Local energy mode that mirrors [PowerManager.PowerMode] in :app without
 * creating a cross-module dependency.  Mapped in :app via a simple extension.
 *
 *  PERFORMANCE     — charging in foreground; relay freely
 *  BALANCED        — normal battery; default behaviour
 *  POWER_SAVER     — low battery (11–20 %); reduce relay load
 *  ULTRA_LOW_POWER — critical battery (≤ 10 %); passive receive-only
 */
enum class EnergyMode {
    PERFORMANCE,
    BALANCED,
    POWER_SAVER,
    ULTRA_LOW_POWER
}

/**
 * Rule-based relay probability engine for the BLE mesh transport layer.
 *
 * Combines two independent signals to decide how likely this node should
 * forward a received packet to its BLE neighbours:
 *
 *   1. **Network congestion factor** — scales down in dense networks to
 *      reduce broadcast storms while still providing redundant coverage.
 *
 *   2. **Local energy multiplier** — further attenuates relay probability
 *      when the device's battery is low so the last few percent of charge
 *      are reserved for sending *own* messages, not relaying others'.
 *
 * Design principles
 * ─────────────────
 * • Pure Kotlin — no Android framework dependencies, fully unit-testable.
 * • Rule-based for v1 — sufficient without training data; the multiplier
 *   function is the natural seam for a future ML replacement.
 * • Two relay tiers:
 *     - [relayProbability]         — normal packets
 *     - [criticalRelayProbability] — high-TTL (CRITICAL / SOS) packets that
 *       bypass the network factor but are still attenuated at ULTRA_LOW_POWER
 *
 * Module placement: :resqmesh-ai (no Android deps) per CLAUDE.md §9.
 */
object EnergyRelayPolicy {

    // ── Network congestion factor ──────────────────────────────────────────
    //
    // Small networks relay everything to guarantee connectivity.
    // Dense networks reduce probability to avoid flooding the channel.
    //
    // These thresholds match the legacy inline values in PacketRelayManager
    // so existing behaviour is preserved when EnergyMode == BALANCED.
    //
    fun networkFactor(networkSize: Int): Float = when {
        networkSize <= 10  -> 1.00f
        networkSize <= 30  -> 0.85f
        networkSize <= 50  -> 0.70f
        networkSize <= 100 -> 0.55f
        else               -> 0.40f
    }

    // ── Energy multiplier ─────────────────────────────────────────────────
    //
    //  PERFORMANCE / BALANCED  → 1.0  — full relay, no change
    //  POWER_SAVER             → 0.5  — halve probability (save power, stay useful)
    //  ULTRA_LOW_POWER         → 0.0  — passive mode (never relay normal packets)
    //
    fun energyMultiplier(mode: EnergyMode): Float = when (mode) {
        EnergyMode.PERFORMANCE    -> 1.00f
        EnergyMode.BALANCED       -> 1.00f
        EnergyMode.POWER_SAVER    -> 0.50f
        EnergyMode.ULTRA_LOW_POWER -> 0.00f
    }

    /**
     * Combined relay probability [0.0, 1.0] for a **normal** packet.
     *
     * = networkFactor(networkSize) × energyMultiplier(mode)
     *
     * Example: networkSize = 40 (factor = 0.70), POWER_SAVER (mult = 0.50)
     *   → probability = 0.35 (vs 0.70 on full battery)
     */
    fun relayProbability(networkSize: Int, mode: EnergyMode): Float =
        (networkFactor(networkSize) * energyMultiplier(mode)).coerceIn(0f, 1f)

    /**
     * Relay probability for a **high-TTL (critical / SOS)** packet.
     *
     * Critical packets bypass the network congestion factor — a node always
     * tries to forward an SOS regardless of how many peers are nearby.
     *
     * At ULTRA_LOW_POWER we allow 20 % rather than 0 % so that a near-dead
     * device can still act as a last-resort forwarder for a life-safety message.
     */
    fun criticalRelayProbability(mode: EnergyMode): Float = when (mode) {
        EnergyMode.ULTRA_LOW_POWER -> 0.20f   // near-dead — minimal but non-zero
        else                       -> 1.00f   // all other modes: always relay
    }
}
