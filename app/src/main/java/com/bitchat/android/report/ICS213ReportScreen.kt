package com.bitchat.android.report

import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bitchat.android.ai.report.ICS213Category
import com.bitchat.android.ai.report.ICS213ReportData
import com.bitchat.android.ai.report.ICS213ReportGenerator
import java.io.File

// ── Colour constants (white-background report, theme-independent) ──────────────
private val ReportBg       = Color.White
private val ReportText     = Color.Black
private val ReportLabel    = Color(0xFF333333)
private val ReportBorder   = Color(0xFF000000)
private val ReportLabelBg  = Color(0xFFF0F0F0)
private val ReportSubtle   = Color(0xFF555555)
private val BadgeCritical  = Color(0xFFCC0000)
private val BadgeHigh      = Color(0xFFE65100)
private val BadgeRoutine   = Color(0xFF777777)

/**
 * Full-screen Compose overlay that renders a preview of the ICS-213 report
 * and lets the user share / print it.
 *
 * **WebView-free**: the form is rendered directly from [ICS213ReportData] using
 * a [LazyColumn].  This avoids the Android 11 Trichrome crash where
 * `WebViewFactory.getProvider()` throws `Package not found: com.google.android.webview`
 * when the Android System WebView package is mis-configured.
 *
 * The print/share button tries [ICS213PrintHelper] (WebView + PrintManager) first.
 * If WebView is unavailable it falls back to writing the HTML to the app's cache
 * directory and sharing it via [FileProvider] so any browser can open / print it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ICS213ReportScreen(
    reportData: ICS213ReportData,
    onBack: () -> Unit
) {
    BackHandler(enabled = true, onBack = onBack)

    val context = LocalContext.current
    val html    = remember(reportData) { ICS213ReportGenerator.generateHtml(reportData) }

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
                        onClick = {
                            // Try WebView-based print first; fall back to file share.
                            val webViewOk = runCatching {
                                ICS213PrintHelper.printReport(context, html)
                            }.isSuccess
                            if (!webViewOk) {
                                shareHtmlFile(context, html)
                            }
                        }
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
        ReportContent(
            data = reportData,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

// ── Pure-Compose report body ────────────────────────────────────────────────────

/**
 * Renders [ICS213ReportData] as a scrollable Compose layout with a white background.
 * No WebView required — safe on all Android API levels.
 */
@Composable
private fun ReportContent(data: ICS213ReportData, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.background(ReportBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {

        // ── Form title ─────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, ReportBorder)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ICS-213   GENERAL MESSAGE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    color = ReportText,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = "Generated by DisasterMesh  ·  For official emergency coordination use",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = ReportSubtle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp)
            )
        }

        // ── Header grid ────────────────────────────────────────────────────
        item {
            val msgCount = data.totalMessages
            val catCount = data.categories.size
            val subjectFull = "${data.subject} — $msgCount incident${if (msgCount != 1) "s" else ""}" +
                    " across $catCount categor${if (catCount != 1) "ies" else "y"}"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ReportBorder)
            ) {
                GridRow2Col("TO:", data.to, "MSG NO:", data.messageNumber, bold2 = true)
                GridDivider()
                GridRow2Col("FROM:", data.from, "DATE:", data.date)
                GridDivider()
                GridRow2Col("CHANNEL:", data.channel, "TIME:", data.time)
                GridDivider()
                GridRow1Col("SUBJECT:", subjectFull)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── MESSAGE section title ──────────────────────────────────────────
        item { SectionTitle("MESSAGE") }

        // ── Categories ─────────────────────────────────────────────────────
        if (data.categories.isEmpty()) {
            item {
                Text(
                    text = "No classified emergency messages.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(data.categories) { cat -> CategoryBlock(cat) }
        }

        // ── Operator block ─────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SignatureBlock(
                title      = "OPERATOR (SENDER)",
                nameValue  = data.from,
                dateTime   = data.dateTime,
                blankName  = false
            )
        }

        // ── Supervisor block ───────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SignatureBlock(
                title     = "SUPERVISOR APPROVAL",
                nameValue = "",
                dateTime  = "",
                blankName = true
            )
        }

        // ── Reply section ──────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(14.dp))
            SectionTitle("REPLY")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .border(1.dp, Color(0xFF999999))
            )
        }

        // ── Footer ─────────────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFCCCCCC))
            Text(
                text = "Mesh: ${data.channel}  |  Peers: ${data.peersConnected}" +
                       "  |  Classifier: ${data.classifierVersion}" +
                       "  |  Generated: ${data.dateTime}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF444444),
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Small composable helpers ────────────────────────────────────────────────────

@Composable
private fun SectionTitle(label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = ReportText
        )
        HorizontalDivider(
            thickness = 2.dp,
            color = ReportBorder,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
    }
}

/** Two-column header row: [label1 | value1 | label2 | value2] */
@Composable
private fun GridRow2Col(
    label1: String, value1: String,
    label2: String, value2: String,
    bold2: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    ) {
        Text(
            text = label1,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = ReportLabel,
            modifier = Modifier
                .background(ReportLabelBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .width(80.dp)
        )
        Text(
            text = value1,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = ReportText,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ReportBorder)
        )
        Text(
            text = label2,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = ReportLabel,
            modifier = Modifier
                .background(ReportLabelBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .width(72.dp)
        )
        Text(
            text = value2,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold2) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            color = ReportText,
            modifier = Modifier
                .width(130.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

/** Single full-width row: [label | value spanning full width] */
@Composable
private fun GridRow1Col(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = ReportLabel,
            modifier = Modifier
                .background(ReportLabelBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .width(80.dp)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = ReportText,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GridDivider() =
    HorizontalDivider(thickness = 1.dp, color = ReportBorder)

@Composable
private fun CategoryBlock(cat: ICS213Category) {
    val badgeColor = when (cat.priority) {
        "CRITICAL" -> BadgeCritical
        "HIGH"     -> BadgeHigh
        else       -> BadgeRoutine
    }
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        // Category header: emoji + name + priority badge + count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${cat.emoji} ${cat.name}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ReportText
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(badgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = cat.priority,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${cat.messages.size} report${if (cat.messages.size != 1) "s" else ""})",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = ReportSubtle
            )
        }

        // Message rows
        cat.messages.forEach { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 3.dp)
            ) {
                Text(
                    text = "•",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = ReportText,
                    modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = msg.sender,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ReportText
                        )
                        Text(
                            text = "  [${msg.timestamp}]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ReportSubtle
                        )
                        Text(
                            text = "  · ${msg.confidencePct}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF777777)
                        )
                    }
                    Text(
                        text = "\u201C${msg.text}\u201D",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ReportText,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureBlock(
    title: String,
    nameValue: String,
    dateTime: String,
    blankName: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ReportBorder)
    ) {
        // Section header
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = ReportLabel,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(ReportLabelBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        HorizontalDivider(thickness = 1.dp, color = ReportBorder)
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Name
            Column(
                modifier = Modifier
                    .weight(2f)
                    .padding(8.dp)
            ) {
                Text("Name", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ReportSubtle)
                if (blankName) {
                    SignatureLine()
                } else {
                    Text(nameValue, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ReportText)
                }
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFCCCCCC)))
            // Position
            Column(modifier = Modifier.weight(2f).padding(8.dp)) {
                Text("Position / Title", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ReportSubtle)
                SignatureLine()
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFCCCCCC)))
            // Signature
            Column(modifier = Modifier.weight(3f).padding(8.dp)) {
                Text("Signature", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ReportSubtle)
                SignatureLine()
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFCCCCCC)))
            // Date/Time
            Column(modifier = Modifier.weight(2f).padding(8.dp)) {
                Text("Date / Time", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ReportSubtle)
                if (blankName || dateTime.isBlank()) {
                    SignatureLine()
                } else {
                    Text(dateTime, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ReportText)
                }
            }
        }
    }
}

@Composable
private fun SignatureLine() =
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(1.dp)
            .background(ReportBorder)
    )

// ── HTML file-share fallback (when WebView is unavailable) ─────────────────────

/**
 * Writes [html] to the app cache directory and shares it via [FileProvider].
 *
 * The user can open the file in any browser, then print from there.
 * Used as the fallback when [ICS213PrintHelper.printReport] fails because
 * Android System WebView is unavailable on the device.
 */
private fun shareHtmlFile(context: android.content.Context, html: String) {
    try {
        val reportsDir = File(context.cacheDir, "reports").also { it.mkdirs() }
        val file = File(reportsDir, "ics213_report.html")
        file.writeText(html, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Open report in browser").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        Log.e("ICS213Screen", "Failed to share HTML file", e)
    }
}
