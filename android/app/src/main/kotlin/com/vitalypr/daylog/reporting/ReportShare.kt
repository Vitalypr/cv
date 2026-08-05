package com.vitalypr.daylog.reporting

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * WhatsApp handoff per spec §6.4: prefer com.whatsapp, then WhatsApp Business,
 * else the system chooser. The group cannot be pre-selected — platform constraint.
 */
object ReportShare {

    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /** Plain-text share (period summaries). */
    fun intentFor(context: Context, reportText: String): Intent =
        targeted(context, Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, reportText)
        })

    /** Styled-PDF share with the text report as caption (daily report, spec §2.4 v0.5). */
    fun pdfIntent(context: Context, pdf: File, caption: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption) // WhatsApp uses it as the document caption
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return targeted(context, send)
    }

    private fun targeted(context: Context, send: Intent): Intent {
        val target = listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull { pkg ->
            send.setPackage(pkg)
            context.packageManager.resolveActivity(send, 0) != null
        }
        return if (target != null) {
            send.setPackage(target)
            send
        } else {
            send.setPackage(null)
            Intent.createChooser(send, null).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
