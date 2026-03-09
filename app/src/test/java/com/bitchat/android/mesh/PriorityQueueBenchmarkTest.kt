package com.bitchat.android.mesh

import org.junit.Assert.*
import org.junit.Test

/**
 * Benchmark comparing FIFO vs PriorityQueue ordering for BLE packet dispatch.
 *
 * Simulates the real-world scenario where a mesh node has queued up many
 * routine packets (e.g. file fragments, gossip sync) when a CRITICAL emergency
 * message arrives.  Demonstrates how quickly the emergency reaches the radio.
 *
 * Priority mapping (matches MessagePriority.ordinal):
 *   0 = CRITICAL  — SOS, cardiac arrest, mayday
 *   1 = HIGH       — fire, flood, injured
 *   2 = NORMAL     — general chat
 *   3 = LOW        — pings, drills, check-ins
 *
 * Note: Tests run entirely in the JVM — no BLE hardware needed.
 */
class PriorityQueueBenchmarkTest {

    // ── Priority constants (mirror MessagePriority.ordinal) ─────────────────
    private val CRITICAL = 0
    private val HIGH     = 1
    private val NORMAL   = 2
    private val LOW      = 3

    /** Minimal simulation of a routed packet with an id and priority. */
    private data class SimPacket(
        val id: String,
        val priority: Int
    ) : Comparable<SimPacket> {
        override fun compareTo(other: SimPacket): Int =
            this.priority.compareTo(other.priority)
    }

    // ── Ordering correctness ─────────────────────────────────────────────────

    @Test
    fun `FIFO - CRITICAL packet waits behind all NORMAL packets`() {
        val queue = ArrayDeque<SimPacket>()
        repeat(100) { i -> queue.addLast(SimPacket("fragment-$i", NORMAL)) }
        queue.addLast(SimPacket("SOS", CRITICAL))  // arrives last

        val order = drainToList(queue)
        val sosPos = order.indexOfFirst { it.id == "SOS" } + 1  // 1-based

        println("\n[FIFO] SOS processed at position $sosPos / ${order.size}")
        assertEquals("FIFO: SOS is the very last packet processed", order.size, sosPos)
    }

    @Test
    fun `PriorityQueue - CRITICAL packet jumps to front regardless of insertion order`() {
        val queue = java.util.PriorityQueue<SimPacket>()
        repeat(100) { i -> queue.add(SimPacket("fragment-$i", NORMAL)) }
        queue.add(SimPacket("SOS", CRITICAL))  // arrives last, should be processed first

        val order = drainToList(queue)
        val sosPos = order.indexOfFirst { it.id == "SOS" } + 1

        println("\n[PriorityQueue] SOS processed at position $sosPos / ${order.size}")
        assertEquals("PriorityQueue: SOS is the very first packet processed", 1, sosPos)
    }

    @Test
    fun `PriorityQueue - correct ordering across all four priority levels`() {
        val queue = java.util.PriorityQueue<SimPacket>()
        // Inserted in deliberately wrong order to prove sorting
        queue.add(SimPacket("normal-1",   NORMAL))
        queue.add(SimPacket("low-1",      LOW))
        queue.add(SimPacket("high-1",     HIGH))
        queue.add(SimPacket("critical-1", CRITICAL))
        queue.add(SimPacket("normal-2",   NORMAL))
        queue.add(SimPacket("critical-2", CRITICAL))
        queue.add(SimPacket("high-2",     HIGH))
        queue.add(SimPacket("low-2",      LOW))

        val order = drainToList(queue)
        println("\n[Mixed] Processing order: ${order.map { it.id }}")

        // First two must be CRITICAL
        assertTrue("pos 1 = CRITICAL", order[0].priority == CRITICAL)
        assertTrue("pos 2 = CRITICAL", order[1].priority == CRITICAL)
        // Next two HIGH
        assertTrue("pos 3 = HIGH",     order[2].priority == HIGH)
        assertTrue("pos 4 = HIGH",     order[3].priority == HIGH)
        // Then NORMAL
        assertTrue("pos 5 = NORMAL",   order[4].priority == NORMAL)
        assertTrue("pos 6 = NORMAL",   order[5].priority == NORMAL)
        // Finally LOW
        assertTrue("pos 7 = LOW",      order[6].priority == LOW)
        assertTrue("pos 8 = LOW",      order[7].priority == LOW)
    }

    // ── Latency benchmark ────────────────────────────────────────────────────

    /**
     * Key benchmark.
     *
     * Simulates the broadcaster queue just before an emergency:
     *   - N routine packets (file transfer, gossip sync) already queued.
     *   - 1 CRITICAL "SOS / cardiac arrest" message arrives.
     *
     * At 100 µs per BLE notification (realistic for BLE 2M PHY):
     *   FIFO     → CRITICAL is radio-transmitted after  N × 100 µs
     *   Priority → CRITICAL is radio-transmitted after  1 × 100 µs  (next slot)
     */
    @Test
    fun `benchmark - CRITICAL latency FIFO vs PriorityQueue at 1000 queued packets`() {
        val packetCount      = 1000
        val usPerPacket      = 100L   // µs — realistic BLE notification RTT
        val msPerPacket      = usPerPacket / 1_000.0

        // ── FIFO ────────────────────────────────────────────────────
        val fifo = ArrayDeque<SimPacket>()
        repeat(packetCount) { i -> fifo.addLast(SimPacket("frag-$i", NORMAL)) }
        fifo.addLast(SimPacket("CRITICAL-SOS", CRITICAL))   // arrives last

        var fifoPosition = 0
        val fifoOrder = drainToList(fifo)
        fifoPosition = fifoOrder.indexOfFirst { it.id == "CRITICAL-SOS" } + 1
        val fifoLatencyMs = fifoPosition * msPerPacket

        // ── PriorityQueue ────────────────────────────────────────────
        val pq = java.util.PriorityQueue<SimPacket>()
        repeat(packetCount) { i -> pq.add(SimPacket("frag-$i", NORMAL)) }
        pq.add(SimPacket("CRITICAL-SOS", CRITICAL))         // arrives last

        var pqPosition = 0
        val pqOrder = drainToList(pq)
        pqPosition = pqOrder.indexOfFirst { it.id == "CRITICAL-SOS" } + 1
        val pqLatencyMs = pqPosition * msPerPacket

        val improvementX = fifoPosition.toDouble() / pqPosition.toDouble()

        println("""

            ╔══════════════════════════════════════════════════════════════╗
            ║              PRIORITY QUEUE BROADCAST BENCHMARK             ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  Setup: $packetCount NORMAL packets in queue, then 1 CRITICAL arrives
            ║  Simulated BLE notification latency: $usPerPacket µs / packet
            ╠══════════════════════════════════════════════════════════════╣
            ║  FIFO (old)                                                  ║
            ║    SOS processed at position : $fifoPosition / ${fifoOrder.size}
            ║    Simulated radio delay     : ${fifoLatencyMs.toLong()} ms
            ╠══════════════════════════════════════════════════════════════╣
            ║  PriorityQueue (new)                                         ║
            ║    SOS processed at position : $pqPosition / ${pqOrder.size}
            ║    Simulated radio delay     : ${pqLatencyMs.toLong()} ms
            ╠══════════════════════════════════════════════════════════════╣
            ║  Improvement: ${improvementX.toInt()}x faster for CRITICAL messages       ║
            ╚══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        // Assertions
        assertEquals("FIFO: SOS is last in queue",      packetCount + 1, fifoPosition)
        assertEquals("PriorityQueue: SOS is first",     1,               pqPosition)
        assertTrue(  "Improvement >= ${packetCount}x",  improvementX >= packetCount.toDouble())
    }

    @Test
    fun `benchmark - mixed load with HIGH and CRITICAL arriving mid-queue`() {
        val normalCount  = 500
        val fifo = ArrayDeque<SimPacket>()
        val pq   = java.util.PriorityQueue<SimPacket>()

        // 200 normal packets
        repeat(200) { i ->
            val p = SimPacket("normal-$i", NORMAL)
            fifo.addLast(p); pq.add(p)
        }
        // HIGH "building fire" arrives
        val highMsg = SimPacket("HIGH-fire", HIGH)
        fifo.addLast(highMsg); pq.add(highMsg)

        // 300 more normal packets
        repeat(300) { i ->
            val p = SimPacket("normal-${200+i}", NORMAL)
            fifo.addLast(p); pq.add(p)
        }
        // CRITICAL "cardiac arrest" arrives very late
        val critMsg = SimPacket("CRITICAL-cardiac", CRITICAL)
        fifo.addLast(critMsg); pq.add(critMsg)

        val fifoOrder = drainToList(fifo)
        val pqOrder   = drainToList(pq)

        val fifoHighPos = fifoOrder.indexOfFirst { it.id == "HIGH-fire"       } + 1
        val fifoCritPos = fifoOrder.indexOfFirst { it.id == "CRITICAL-cardiac" } + 1
        val pqHighPos   = pqOrder.indexOfFirst   { it.id == "HIGH-fire"       } + 1
        val pqCritPos   = pqOrder.indexOfFirst   { it.id == "CRITICAL-cardiac" } + 1

        println("""

            [Mixed Load Benchmark]
            FIFO:  HIGH at pos $fifoHighPos, CRITICAL at pos $fifoCritPos
            PQ:    HIGH at pos $pqHighPos,   CRITICAL at pos $pqCritPos
            HIGH improvement  : ${fifoHighPos / pqHighPos}x
            CRITICAL improvement: ${fifoCritPos / pqCritPos}x
        """.trimIndent())

        // PQ must always process CRITICAL before HIGH before NORMAL
        assertTrue("PQ: CRITICAL before HIGH", pqCritPos < pqHighPos)
        assertTrue("PQ: HIGH before all NORMAL traffic", pqHighPos < pqOrder.indexOfFirst { it.priority == NORMAL } + 2)
        assertTrue("FIFO: CRITICAL is last", fifoCritPos == normalCount + 2)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun drainToList(queue: ArrayDeque<SimPacket>): List<SimPacket> {
        val result = mutableListOf<SimPacket>()
        while (queue.isNotEmpty()) result.add(queue.removeFirst())
        return result
    }

    private fun drainToList(queue: java.util.PriorityQueue<SimPacket>): List<SimPacket> {
        val result = mutableListOf<SimPacket>()
        while (queue.isNotEmpty()) result.add(queue.poll()!!)
        return result
    }
}
