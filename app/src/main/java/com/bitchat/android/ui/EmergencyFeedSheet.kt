package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.ai.classifier.ClassificationResult
import com.bitchat.android.ai.classifier.MessagePriority
import com.bitchat.android.ai.emergency.categoryEmojiAndLabel
import com.bitchat.android.ai.emergency.shouldShowEmergencyBadge
import com.bitchat.android.ai.report.ICS213Category
import com.bitchat.android.ai.report.ICS213Message
import com.bitchat.android.ai.report.ICS213ReportData
import com.bitchat.android.model.BitchatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Priority sort order for the Emergency Feed sheet. */
private val CATEGORY_ORDER = listOf(
    "MEDICAL", "COLLAPSE",                                        // CRITICAL
    "FIRE", "FLOOD", "SECURITY",                                  // HIGH
    "INFRASTRUCTURE", "WEATHER", "MISSING_PERSON", "RESOURCE_REQUEST" // NORMAL
)

/**
 * Bottom sheet showing all active emergency categories sorted by priority.
 *
 * Each row has:
 *  • A coloured left stripe matching the message badge colour.
 *  • Emoji + category label.
 *  • Message count (right-aligned).
 *
 * A close (✕) button sits in the top-right corner.
 * Tapping a row calls [onCategorySelected] and dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyFeedSheet(
    classificationCache: SnapshotStateMap<String, ClassificationResult>,
    messages: List<BitchatMessage>,
    currentUserNickname: String,
    peersConnected: Int,
    currentChannel: String,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onGenerateReport: (ICS213ReportData) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Derive the active categories from the cache: only entries that crossed the badge threshold.
    // Unclassified "NONE" results are excluded — they are not real emergencies.
    val countByCategory: Map<String, Int> = remember(classificationCache.size) {
        classificationCache.values
            .filter {
                shouldShowEmergencyBadge(it) &&
                    it.emergencyType.isNotEmpty() &&
                    it.emergencyType != "NONE"
            }
            .groupBy { it.emergencyType }
            .mapValues { it.value.size }
    }

    val sortedCategories: List<String> = remember(countByCategory) {
        countByCategory.keys
            .sortedBy { type ->
                val idx = CATEGORY_ORDER.indexOf(type)
                if (idx == -1) Int.MAX_VALUE else idx
            }
    }

    // Use ModalBottomSheet directly so we can set a surface-elevated container color
    // that is visually distinct from the chat background.
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colorScheme.surfaceDim,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Title bar with close button ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🚨 Emergency Feed",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))

            if (sortedCategories.isEmpty()) {
                // ── Empty state ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No emergencies detected.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // ── Category list ─────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(sortedCategories, key = { it }) { category ->
                        val stripeColor = categoryBorderColor(category, colorScheme, isDark)
                        val (emoji, label) = categoryEmojiAndLabel(category)
                        val count = countByCategory[category] ?: 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCategorySelected(category) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Coloured left stripe
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(stripeColor)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Emoji + label
                            Text(
                                text = "$emoji $label",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = stripeColor,
                                modifier = Modifier.weight(1f)
                            )

                            // Message count badge
                            Text(
                                text = "$count",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.padding(end = 24.dp)
                            )
                        }

                        HorizontalDivider(
                            color = colorScheme.outline.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 20.dp)
                        )
                    }
                }
            }

            // ── Generate ICS-213 Report button (only when there are incidents) ──
            if (sortedCategories.isNotEmpty()) {
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
                Button(
                    onClick = {
                        val reportData = buildReportData(
                            sortedCategories = sortedCategories,
                            messages = messages,
                            classificationCache = classificationCache,
                            currentUserNickname = currentUserNickname,
                            peersConnected = peersConnected,
                            currentChannel = currentChannel
                        )
                        onGenerateReport(reportData)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF41),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "GENERATE ICS-213 REPORT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Bottom spacer so content isn't hidden behind navigation bar
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────────

/**
 * Builds an [ICS213ReportData] from the Feed's current state.
 * Called on the button tap — data is snapshotted at that moment.
 *
 * Messages from the same sender within a 5-minute window in the same category are
 * consolidated into a single entry so the report stays concise.
 */
private fun buildReportData(
    sortedCategories: List<String>,
    messages: List<BitchatMessage>,
    classificationCache: Map<String, ClassificationResult>,
    currentUserNickname: String,
    peersConnected: Int,
    currentChannel: String
): ICS213ReportData {
    val now = Date()
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val timeFmt = SimpleDateFormat("HHmm", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val timeDisplayFmt = SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val tsFmt = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    // Unique message ID: DM-YYYYMMDD-HHMM
    val messageNumber = "DM-${dateFmt.format(now).replace("-", "")}-${timeFmt.format(now)}"

    val categories = sortedCategories.filter { it != "NONE" }.map { cat ->
        val (emoji, label) = categoryEmojiAndLabel(cat)
        val catMessages = messages.filter { msg ->
            val result = classificationCache[msg.id] ?: return@filter false
            result.emergencyType == cat && shouldShowEmergencyBadge(result)
        }

        // Derive category-level priority from the highest-priority message.
        val catPriority = catMessages
            .mapNotNull { classificationCache[it.id]?.priority }
            .minByOrNull { it.ordinal }   // lower ordinal = higher severity
            ?.let { p ->
                when (p) {
                    MessagePriority.CRITICAL -> "CRITICAL"
                    MessagePriority.HIGH     -> "HIGH"
                    else                     -> "ROUTINE"
                }
            } ?: "ROUTINE"

        ICS213Category(
            name = label,
            emoji = emoji,
            priority = catPriority,
            messages = consolidateMessages(catMessages, classificationCache, tsFmt)
        )
    }

    return ICS213ReportData(
        messageNumber = messageNumber,
        from = currentUserNickname,
        date = dateFmt.format(now),
        time = timeDisplayFmt.format(now) + " UTC",
        peersConnected = peersConnected,
        channel = currentChannel,
        classifierVersion = "CompositeClassifier v1.0",
        categories = categories
    )
}

/**
 * Maps each emergency message to its own [ICS213Message] row, sorted by time.
 *
 * Each incident is kept separate so the report accurately reflects individual
 * reports. The main message text is capped at 120 chars; any appended 📍 location
 * is preserved in full and never truncated.
 */
private fun consolidateMessages(
    catMessages: List<BitchatMessage>,
    classificationCache: Map<String, ClassificationResult>,
    tsFmt: SimpleDateFormat
): List<ICS213Message> {
    if (catMessages.isEmpty()) return emptyList()

    return catMessages
        .sortedBy { it.timestamp }
        .map { msg ->
            val conf = classificationCache[msg.id]?.confidence ?: 0f

            // Split text at the appended location tag (if present) so we never
            // truncate GPS coordinates mid-number.
            val content = stripSourceMarkers(msg.content)
            val locIdx = content.indexOf("\n📍")
            val (mainText, locPart) = if (locIdx >= 0)
                content.substring(0, locIdx) to content.substring(locIdx)
            else
                content to ""
            val truncatedMain = if (mainText.length > 120) mainText.take(120) + "…" else mainText

            ICS213Message(
                sender    = msg.sender,
                timestamp = tsFmt.format(msg.timestamp),
                text      = truncatedMain + locPart,
                confidencePct = (conf * 100).toInt()
            )
        }
}

/**
 * Removes emoji decorations from the message so the ICS-213 report renders clean plain text:
 *  • Inline category emoji: 🔥 🌊 🏥 🏚 🚨 ⛈ 🔍 🔧 📦 ⚠️
 *    (emitted by VisionTFLiteClassifier/SceneToEmergencyMapper in photo-generated descriptions)
 *
 * The 📍 location marker is intentionally preserved — it stays in the report body.
 */
private val CATEGORY_EMOJI_REGEX = Regex("""[🔥🌊🏥🏚🚨⛈🔍🔧📦⚠️]""")
private fun stripSourceMarkers(text: String): String =
    text.replace(CATEGORY_EMOJI_REGEX, "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
