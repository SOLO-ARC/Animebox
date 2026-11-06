package com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k

data class Anime4kPass(
    val desc: String,
    val save: String,
    val widthExpr: String?,
    val heightExpr: String?,
    val shaderBody: String,
    val binds: List<String>
)

data class Anime4kTexture(
    val textureId: Int,
    val width: Int,
    val height: Int
)

data class UniformBindings(
    val textureName: String,
    val rawLoc: Int,
    val sizeLoc: Int,
    val ptLoc: Int
)

data class CompiledPass(
    val pass: Anime4kPass,
    val programId: Int,
    val outWidth: Int,
    val outHeight: Int,
    val isIntermediate: Boolean,
    var textureId: Int = 0,
    var fboId: Int = 0,
    val uniforms: List<UniformBindings>
)
