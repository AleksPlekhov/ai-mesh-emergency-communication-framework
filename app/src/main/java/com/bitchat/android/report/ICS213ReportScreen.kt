package com.bitchat.android.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import com.bitchat.android.ai.report.ICS213ReportData
import com.bitchat.android.ai.report.ICS213ReportGenerator

/**
 * Full-screen Compose overlay that renders a preview of the ICS-213 report
 * inside a [WebView] and lets the user trigger the Android print dialog
 * (Save as PDF / send to printer) via the Share icon.
 *
 * The white WebView background makes the form legible regardless of the app
 * theme (dark or light).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ICS213ReportScreen(
    reportData: ICS213ReportData,
    onBack: () -> Unit
) {
    BackHandler(enabled = true, onBack = onBack)

    val context = LocalContext.current
    val html = remember(reportData) { ICS213ReportGenerator.generateHtml(reportData) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ICS-213 Report",
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { ICS213PrintHelper.printReport(context, html) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Print / Save as PDF"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color(0xFF00FF41),
                    navigationIconContentColor = Color(0xFF00FF41),
                    actionIconContentColor = Color(0xFF00FF41)
                )
            )
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    setBackgroundColor(android.graphics.Color.WHITE)
                    loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
                }
            },
            update = { webView ->
                // Re-render if reportData changes (e.g. orientation change rebuilding state).
                webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
            }
        )
    }
}
