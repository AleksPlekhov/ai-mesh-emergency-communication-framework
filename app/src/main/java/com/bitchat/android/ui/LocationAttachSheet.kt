package com.bitchat.android.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices

/**
 * Bottom sheet shown when the user sends a message that the keyword classifier
 * flags as CRITICAL or HIGH priority.
 *
 * Allows the user to optionally attach their current location (GPS or manual text)
 * before the message is dispatched. Dismissing or tapping "Skip" sends the message
 * without a location so the original message is never lost.
 *
 * @param onSend Called with the location string to append (null = no location).
 *               The caller is responsible for sending the final message.
 * @param onSkip Called when the user taps "Skip" — the message should NOT be sent.
 * @param onDismiss Called when the sheet is dismissed without a choice being made.
 *                  Treat as "send without location".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationAttachSheet(
    onSend: (location: String?) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var locationText by remember { mutableStateOf("") }
    var isLoadingGps by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚠️", fontSize = 20.sp)
                Text(
                    text = "EMERGENCY MESSAGE DETECTED",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "Add your location so rescuers can find you.\n" +
                       "Send an update with each new message as you move.",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            // ── GPS button ───────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    isLoadingGps = true
                    fetchLastLocation(context) { lat, lon ->
                        locationText = if (lat != null && lon != null) {
                            "%.5f,%.5f".format(lat, lon)
                        } else {
                            ""
                        }
                        isLoadingGps = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                enabled = !isLoadingGps
            ) {
                if (isLoadingGps) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Locating…",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Use GPS coordinates",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }

            // ── Manual address field ─────────────────────────────────────────
            OutlinedTextField(
                value = locationText,
                onValueChange = { locationText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "or type address / landmark",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                shape = RoundedCornerShape(4.dp)
            )

            // ── Action buttons ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Refuse — discard message, do not send
                OutlinedButton(
                    onClick = { onSkip() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Skip",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }

                // Submit — send with location (trimmed, or null if empty)
                Button(
                    onClick = { onSend(locationText.trim().takeIf { it.isNotEmpty() }) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF41),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        "Send ➜",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Fetches the last known device location via FusedLocationProviderClient.
 * The callback is invoked on the main thread with (lat, lon) or (null, null) on failure.
 */
@SuppressLint("MissingPermission")
private fun fetchLastLocation(
    context: Context,
    callback: (lat: Double?, lon: Double?) -> Unit
) {
    try {
        LocationServices
            .getFusedLocationProviderClient(context)
            .lastLocation
            .addOnSuccessListener { location ->
                callback(location?.latitude, location?.longitude)
            }
            .addOnFailureListener {
                callback(null, null)
            }
    } catch (e: SecurityException) {
        callback(null, null)
    }
}
