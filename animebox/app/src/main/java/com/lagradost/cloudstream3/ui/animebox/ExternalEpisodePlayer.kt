package com.lagradost.cloudstream3.ui.animebox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Opens the Android system chooser for a resolved online or downloaded episode. */
internal fun Activity.openEpisodeInExternalPlayer(
    source: String,
    title: String,
    referer: String = "",
    subtitle: String = ""
): Boolean {
    if (source.isBlank()) return false
    return runCatching {
        val uri = if (source.startsWith("/")) {
            FileProvider.getUriForFile(this, "$packageName.provider", File(source))
        } else {
            Uri.parse(source)
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (referer.isNotBlank()) {
                putExtra("referer", referer)
                putExtra("http-referer", referer)
            }
            if (subtitle.isNotBlank()) {
                putExtra("subtitleUrl", subtitle)
                putExtra("subs", subtitle)
            }
        }
        startActivity(Intent.createChooser(viewIntent, "Play $title with"))
        true
    }.getOrElse {
        Toast.makeText(this, "No external video player could open this episode", Toast.LENGTH_LONG).show()
        false
    }
}
