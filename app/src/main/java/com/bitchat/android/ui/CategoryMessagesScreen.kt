package com.bitchat.android.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bitchat.android.ai.classifier.ClassificationResult
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage

/**
 * Full-screen overlay that shows only messages belonging to a single emergency category.
 *
 * Slides in from the right when shown, slides out to the right when [onBack] is called.
 * Renders on top of the entire [ChatScreen] content because it is placed after the outer
 * Box in the composable tree and fills the available size.
 */
@Composable
fun CategoryMessagesScreen(
    category: String,
    messages: List<BitchatMessage>,
    classificationCache: SnapshotStateMap<String, ClassificationResult>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val stripeColor = categoryBorderColor(category, colorScheme, isDark)
    val (emoji, label) = categoryEmojiAndLabel(category)

    // Filter messages to only those that belong to this category.
    val filteredMessages = remember(messages, classificationCache.size) {
        messages.filter { msg ->
            classificationCache[msg.id]?.emergencyType == category
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // ── Top bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$emoji $label",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = stripeColor
                )
            }

            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))

            // ── Message list ──────────────────────────────────────────────
            if (filteredMessages.isEmpty()) {
                EmptyMessagesState(
                    connectedPeersCount = 0,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(filteredMessages.size) {
                    if (filteredMessages.isNotEmpty()) {
                        listState.scrollToItem(0)
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                    reverseLayout = true
                ) {
                    items(
                        items = filteredMessages.asReversed(),
                        key = { it.id }
                    ) { message ->
                        MessageItem(
                            message = message,
                            messages = filteredMessages,
                            currentUserNickname = currentUserNickname,
                            meshService = meshService,
                            classification = classificationCache[message.id]
                        )
                    }
                }
            }
        }
    }
}
