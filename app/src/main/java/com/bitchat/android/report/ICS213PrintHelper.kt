package com.bitchat.android.report

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Triggers the Android system print dialog for an ICS-213 HTML report.
 *
 * The [WebView] is kept off the layout tree (never attached to a visible
 * Window).  The [WebViewClient.onPageFinished] callback fires once the HTML
 * is fully rendered, at which point [WebView.createPrintDocumentAdapter] is
 * safe to call.
 *
 * The lambda closure captures [webView], preventing GC until after the print
 * job adapter is handed to [PrintManager].
 */
object ICS213PrintHelper {

    fun printReport(
        context: Context,
        html: String,
        jobName: String = "ICS-213 Emergency Report"
    ) {
        // Note: WebView(context) may throw AndroidRuntimeException on Android 11 devices
        // where com.google.android.webview is mis-configured (Trichrome issue).
        // The caller (ICS213ReportScreen) wraps this call in runCatching and falls back
        // to shareHtmlFile() when an exception is thrown — do NOT catch internally.
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager =
                    context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = view.createPrintDocumentAdapter(jobName)
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                    .setResolution(
                        PrintAttributes.Resolution("ics213_res", "300dpi", 300, 300)
                    )
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, printAdapter, printAttributes)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }
}
