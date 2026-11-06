package com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

@OptIn(UnstableApi::class)
class Anime4kEffect(
    val mode: Anime4kMode,
    val halfPrecision: Boolean
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return Anime4kShaderProgram(context, mode, halfPrecision, useHdr)
    }
}
