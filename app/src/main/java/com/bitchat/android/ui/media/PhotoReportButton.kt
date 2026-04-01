package com.bitchat.android.ui.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bitchat.android.features.media.ImageSceneAnalyzer
import com.bitchat.android.features.media.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * Photo analysis button for emergency reporting.
 *
 * Replaces [ImagePickerButton] in the chat input row. Visually distinct (orange
 * [Icons.Filled.AddAPhoto]) to signal that this button does more than plain image send.
 *
 * Gesture → behavior:
 *  • Single click  → gallery picker
 *  • Long click    → camera capture
 *
 * After the image is ready it is fed to [ImageSceneAnalyzer] (ML Kit, bundled, offline).
 * The result drives one of three paths:
 *  1. Emergency category detected  → [onSceneAnalyzed](description, imagePath)
 *  2. Labels found, no category    → [onSceneAnalyzed] with generic "📷 Scene: …" description
 *  3. ML Kit hard failure          → [onImageReady](imagePath)  (plain image send fallback)
 *
 * While analysis is running a small orange [CircularProgressIndicator] replaces the icon.
 *
 * @param onSceneAnalyzed Called when ML Kit returns at least one label.
 *                        [description] is pre-formatted for the message TextField.
 *                        [imagePath]   is the downscaled JPEG (also sent as attachment).
 * @param onImageReady    Fallback: called only when ML Kit returns zero labels or errors.
 *                        Sends the image as a plain attachment (old [ImagePickerButton] behavior).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoReportButton(
    modifier: Modifier = Modifier,
    onSceneAnalyzed: (description: String, imagePath: String, emergencyType: String) -> Unit,
    onImageReady: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capturedImagePath by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    // ── Shared analysis helper ─────────────────────────────────────────────────
    fun analyzeAndDispatch(imagePath: String) {
        scope.launch {
            isAnalyzing = true
            val result = ImageSceneAnalyzer.analyze(context, imagePath)
            isAnalyzing = false
            if (result.description.isNotEmpty()) {
                // ML Kit returned labels (path 1 or 2 — with or without emergency category)
                onSceneAnalyzed(result.description, imagePath, result.emergencyType)
            } else {
                // Hard failure: no labels at all — fall back to plain image send (path 3)
                onImageReady(imagePath)
            }
        }
    }

    // ── Gallery picker ────────────────────────────────────────────────────────
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val outPath = ImageUtils.downscaleAndSaveToAppFiles(context, uri)
            if (!outPath.isNullOrBlank()) analyzeAndDispatch(outPath)
        }
    }

    // ── Camera capture ────────────────────────────────────────────────────────
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = capturedImagePath
        if (success && !path.isNullOrBlank()) {
            val outPath = ImageUtils.downscalePathAndSaveToAppFiles(context, path)
            if (!outPath.isNullOrBlank()) analyzeAndDispatch(outPath)
            runCatching { File(path).delete() }
        } else {
            path?.let { runCatching { File(it).delete() } }
        }
        capturedImagePath = null
    }

    fun startCameraCapture() {
        try {
            val dir = File(context.filesDir, "images/outgoing").apply { mkdirs() }
            val file = File(dir, "photo_report_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            capturedImagePath = file.absolutePath
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            android.util.Log.e("PhotoReportButton", "Camera capture failed", e)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCameraCapture()
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .size(32.dp)
            .combinedClickable(
                onClick = { imagePicker.launch("image/*") },
                onLongClick = {
                    if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startCameraCapture()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFFF9500)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = "Analyze photo and generate emergency report",
                tint = Color(0xFFFF9500), // Orange: visually distinct from the old grey camera icon
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
