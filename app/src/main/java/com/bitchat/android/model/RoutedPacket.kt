package com.bitchat.android.model

import com.bitchat.android.protocol.BitchatPacket

/**
 * Represents a routed packet with additional metadata
 * Used for processing and routing packets in the mesh network
 *
 * [priority] maps to [com.bitchat.android.ai.classifier.MessagePriority.ordinal]:
 *   0 = CRITICAL, 1 = HIGH, 2 = NORMAL (default), 3 = LOW
 * Lower value = higher urgency, processed first by the priority broadcaster queue.
 */
data class RoutedPacket(
    val packet: BitchatPacket,
    val peerID: String? = null,           // Who sent it (parsed from packet.senderID)
    val relayAddress: String? = null,     // Address it came from (for avoiding loopback)
    val transferId: String? = null,       // Optional stable transfer ID for progress tracking
    val priority: Int = 2                 // 0=CRITICAL · 1=HIGH · 2=NORMAL · 3=LOW
)
