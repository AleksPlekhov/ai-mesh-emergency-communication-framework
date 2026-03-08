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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.ai.classifier.ClassificationResult
import com.bitchat.android.model.BitchatMessage

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
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Derive the active categories from the cache: only entries that crossed the badge threshold.
    val countByCategory: Map<String, Int> = remember(classificationCache.size) {
        classificationCache.values
            .filter { shouldShowEmergencyBadge(it) && it.emergencyType.isNotEmpty() }
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
        containerColor = colorScheme.surfaceVariant,
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

            // Bottom spacer so content isn't hidden behind navigation bar
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
