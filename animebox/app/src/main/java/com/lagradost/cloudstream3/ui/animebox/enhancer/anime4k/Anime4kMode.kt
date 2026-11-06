package com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k

enum class Anime4kMode(
    val title: String,
    val subtitle: String,
    val restoreShader: String?,
    val upscaleShader: String?
) {
    MODE_A(
        title = "Anime4K Mode A",
        subtitle = "Restore CNN (M) + Upscale CNN x2",
        restoreShader = "Restore_CNN_M",
        upscaleShader = "Upscale_CNN_x2_M"
    ),
    MODE_B(
        title = "Anime4K Mode B",
        subtitle = "Restore CNN Soft (M) + Upscale CNN x2",
        restoreShader = "Restore_CNN_Soft_M",
        upscaleShader = "Upscale_CNN_x2_M"
    ),
    MODE_C(
        title = "Anime4K Mode C",
        subtitle = "Upscale Denoise CNN x2",
        restoreShader = null,
        upscaleShader = "Upscale_Denoise_CNN_x2_M"
    )
}
