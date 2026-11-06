package com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Surfaces the real state of the GPU pipeline to the user (one toast per new configuration),
 * so it is obvious whether the CNN chain is actually running or why it is not.
 */
object Anime4kStatus {
    @Volatile var lastMessage: String = ""
        private set
    @Volatile var isRunning: Boolean = false
        private set

    private val main = Handler(Looper.getMainLooper())

    fun report(context: Context, running: Boolean, message: String) {
        val changed = message != lastMessage
        isRunning = running
        lastMessage = message
        if (!changed) return
        val app = context.applicationContext
        main.post {
            try {
                Toast.makeText(app, message, if (running) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {
            }
        }
    }

    fun reset() {
        lastMessage = ""
        isRunning = false
    }
}
