package com.nuvio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.nuvio.app.features.magnet.MagnetLink
import com.nuvio.app.features.settings.AppIconPlatform

class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingLink(intent)?.let { link ->
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = AppIconPlatform.currentLauncherComponent(this@LinkActivity)
                    putExtra(EXTRA_LINK, link)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }
        finish()
    }

    private fun incomingLink(intent: Intent?): String? {
        val data = intent?.dataString?.trim().orEmpty()
        if (data.isNotEmpty()) return data
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        return MagnetLink.extractFromText(sharedText)
    }

    companion object {
        const val EXTRA_LINK = "link"
    }
}
