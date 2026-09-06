package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context

/**
 * Standard flavor stub for Sherpa-ONNX speech recognition.
 * No native C++ libraries or ONNX dependencies are included in this flavor.
 */
object SherpaOnnxSpeechEngine {
    val isSupported: Boolean = false

    fun recognize(context: Context?, audioWav: ByteArray, speechModelCode: String): String? {
        return null
    }

    fun release() {
        // No-op in standard flavor
    }
}
