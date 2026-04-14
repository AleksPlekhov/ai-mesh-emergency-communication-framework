package com.bitchat.android.ai.benchmarks
// ⚠️ IMPORTANT: Replace package name with your actual module package
// Open any existing file in :resqmesh-ai and copy the package line

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.util.PriorityQueue

@RunWith(AndroidJUnit4::class)
class PriorityQueueBenchmarkTest {

    // Simulated packet with priority ordering (higher priority = dequeued first)
    data class Packet(val priority: Int, val id: Int) : Comparable<Packet> {
        override fun compareTo(other: Packet) = other.priority - this.priority
    }

    companion object {
        const val CRITICAL      = 3
        const val NORMAL        = 1
        const val PACKET_TX_US  = 100L          // 100μs per packet = BLE inter-packet interval
        val QUEUE_SIZES         = listOf(100, 500, 1000)
        const val WARMUP_RUNS   = 3             // JIT warmup, not measured
        const val MEASURED_RUNS = 10            // take median of these
    }

    // Busy-wait simulates BLE packet transmission time.
    // More accurate than Thread.sleep for sub-millisecond intervals.
    @Suppress("ControlFlowWithEmptyBody")
    private fun simulateTx() {
        val deadline = System.nanoTime() + PACKET_TX_US * 1_000L
        while (System.nanoTime() < deadline);
    }

    // Single PQ measurement: fill queue with n NORMAL, add CRITICAL, measure wait time
    private fun measurePQ(n: Int): Double {
        val pq = PriorityQueue<Packet>()
        repeat(n) { i -> pq.add(Packet(NORMAL, i)) }

        val t0 = System.nanoTime()
        pq.add(Packet(CRITICAL, -1))
        while (true) {
            val p = pq.poll() ?: break
            simulateTx()
            if (p.priority == CRITICAL) break
        }
        return (System.nanoTime() - t0) / 1_000.0   // return microseconds
    }

    // Single FIFO measurement: fill queue with n NORMAL, add CRITICAL at end, measure wait
    private fun measureFIFO(n: Int): Double {
        val fifo = ArrayDeque<Packet>()
        repeat(n) { i -> fifo.addLast(Packet(NORMAL, i)) }

        val t0 = System.nanoTime()
        fifo.addLast(Packet(CRITICAL, -1))
        while (true) {
            val p = fifo.removeFirst()
            simulateTx()
            if (p.priority == CRITICAL) break
        }
        return (System.nanoTime() - t0) / 1_000.0   // return microseconds
    }

    @Test
    fun benchmarkPriorityQueue_vs_FIFO() {
        val device = android.os.Build.MODEL
        val sdk    = android.os.Build.VERSION.SDK_INT

        // JIT warmup — not measured
        println("Warming up JIT...")
        repeat(WARMUP_RUNS) { measurePQ(100); measureFIFO(100) }

        println("\n=== BLE Priority Queue Benchmark ===")
        println("Device : $device (API $sdk)")
        println("TX_delay: ${PACKET_TX_US}μs/packet (BLE inter-packet interval)")
        println("Runs   : $MEASURED_RUNS (reporting median)")
        println()
        println("%-6s  %-12s  %-12s  %-8s".format("N", "PQ_median_μs", "FIFO_median_μs", "Ratio"))
        println("-".repeat(50))

        for (n in QUEUE_SIZES) {
            val pqSamples   = (1..MEASURED_RUNS).map { measurePQ(n) }.sorted()
            val fifoSamples = (1..MEASURED_RUNS).map { measureFIFO(n) }.sorted()

            val pqMed   = pqSamples[MEASURED_RUNS / 2]
            val fifoMed = fifoSamples[MEASURED_RUNS / 2]
            val ratio   = fifoMed / pqMed.coerceAtLeast(1.0)

            println("%-6d  %-12.1f  %-12.1f  %-8.1f".format(
                n, pqMed, fifoMed, ratio))
        }

        println("-".repeat(50))
        println("Expected: PQ≈${PACKET_TX_US}μs (1 packet), FIFO≈N×${PACKET_TX_US}μs")
        println("=== END: $device ===\n")
    }
}