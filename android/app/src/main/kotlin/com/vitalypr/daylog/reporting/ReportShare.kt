package com.vitalypr.daylog.reporting

import android.content.Context
import android.content.Intent

/**
 * WhatsApp handoff per spec §6.4: prefer com.whatsapp, then WhatsApp Business,
 * else the system chooser. The group cannot be pre-selected — platform constraint.
 */
object ReportShare {

    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun intentFor(context: Context, reportText: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        val target = listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull { pkg ->
            send.setPackage(pkg)
            context.packageManager.resolveActivity(send, 0) != null
        }
        return if (target != null) {
            send.setPackage(target)
            send
        } else {
            send.setPackage(null)
            Intent.createChooser(send, null)
        }
    }
}
