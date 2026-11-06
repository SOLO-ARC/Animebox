package com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Multi-pass Anime4K CNN executor running inside Media3's video frame processor,
 * mirroring the reference player (Tsuzuku `Ldc`):
 *  - every intermediate pass renders into an RGBA16F / GL_HALF_FLOAT texture (LINEAR, CLAMP_TO_EDGE)
 *    so the signed CNN activations are preserved (8-bit textures would clip them),
 *  - Full / Half precision only switches the shader float precision (highp / mediump),
 *  - the final depth-to-space pass writes the 2x frame straight into Media3's output framebuffer.
 */
@OptIn(UnstableApi::class)
class Anime4kShaderProgram(
    private val context: Context,
    private val mode: Anime4kMode,
    private val halfPrecision: Boolean,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    companion object {
        private const val TAG = "Anime4K"
        private const val ATTRIB_POSITION = 0

        private const val VERTEX_SHADER = """#version 300 es
in vec4 aFramePosition;
out highp vec2 vPos;
void main() {
    gl_Position = aFramePosition;
    vPos = aFramePosition.xy * 0.5 + 0.5;
}
"""

        private const val PASSTHROUGH_FRAG = """#version 300 es
precision mediump float;
uniform sampler2D MAIN_raw;
in highp vec2 vPos;
out vec4 fragColor;
void main() {
    fragColor = texture(MAIN_raw, vPos);
}
"""
    }

    // Same full-screen quad as the reference (x, y, z, w) triangle strip.
    private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 1f,
                     1f, -1f, 0f, 1f,
                    -1f,  1f, 0f, 1f,
                     1f,  1f, 0f, 1f
                )
            )
            position(0)
        }

    private var inputWidth = 0
    private var inputHeight = 0
    private var passthroughProgram = 0
    private var passthroughMainRawLoc = -1
    private val compiledPasses = mutableListOf<CompiledPass>()
    private val currentFbo = IntArray(1)

    private fun loadGlsl(name: String): String =
        context.assets.open("anime4k/Anime4K_$name.glsl").bufferedReader().use { it.readText() }

    private fun compileShader(type: Int, source: String, label: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) throw VideoFrameProcessingException("glCreateShader failed ($label)")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw VideoFrameProcessingException("compile $label: $log")
        }
        return shader
    }

    private fun createProgram(fragSrc: String, label: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER, "$label/vs")
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc, label)
        val program = GLES20.glCreateProgram()
        if (program == 0) throw VideoFrameProcessingException("glCreateProgram failed ($label)")
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glBindAttribLocation(program, ATTRIB_POSITION, "aFramePosition")
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw VideoFrameProcessingException("link $label: $log")
        }
        return program
    }

    /** RGBA16F render target, identical parameters to the reference implementation. */
    private fun allocateTarget(cp: CompiledPass) {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, cp.outWidth, cp.outHeight, 0,
            GLES20.GL_RGBA, GLES30.GL_HALF_FLOAT, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        cp.textureId = tex[0]
        cp.fboId = fbo[0]
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw VideoFrameProcessingException("framebuffer RGBA16F incomplete: 0x${Integer.toHexString(status)}")
        }
    }

    override fun configure(inW: Int, inH: Int): Size {
        inputWidth = inW
        inputHeight = inH

        val savedFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFbo, 0)

        if (passthroughProgram == 0) {
            passthroughProgram = createProgram(PASSTHROUGH_FRAG, "passthrough")
            passthroughMainRawLoc = GLES20.glGetUniformLocation(passthroughProgram, "MAIN_raw")
        }
        releasePasses()

        try {
            val raw = mutableListOf<Anime4kPass>()
            val sourcePixels = inW.toLong() * inH.toLong()
            val bypassCnn = inH >= 1400 || sourcePixels >= 3_000_000L
            val moderateHd = inH in 900..1399
            val keepAtSourceResolution = inH >= 900 || sourcePixels >= 1_400_000L
            val usedSourceResolutionFallback = keepAtSourceResolution && !bypassCnn && !moderateHd

            if (bypassCnn) {
                Anime4kStatus.report(
                    context,
                    true,
                    "${mode.title} · native 4K passthrough to keep playback smooth"
                )
                return Size(inW, inH)
            }

            if (usedSourceResolutionFallback) {
                // The x2 CNN pass creates oversized intermediate work on HD sources. Keep a
                // single source-resolution restoration pass instead, and never allocate 4K
                // intermediates for 1080p input. Mode C uses the soft restoration network here.
                val sourceResolutionShader = mode.restoreShader ?: "Restore_CNN_Soft_M"
                raw += Anime4kPassParser.parseGlsl(loadGlsl(sourceResolutionShader))
            } else if (moderateHd) {
                // At 1080p, run one CNN pass and cap its output to 1440p (the same output
                // dimensions as 2x enhancement of 720p). Avoid both 4K intermediates and the
                // extra restoration pass that made HD playback stall on mobile GPUs.
                mode.upscaleShader?.let { raw += Anime4kPassParser.parseGlsl(loadGlsl(it)) }
            } else {
                mode.restoreShader?.let { raw += Anime4kPassParser.parseGlsl(loadGlsl(it)) }
                mode.upscaleShader?.let { raw += Anime4kPassParser.parseGlsl(loadGlsl(it)) }
            }
            if (moderateHd) {
                // Resample HD input to 720p before the CNN so 1080p runs the same network
                // workload as the already-smooth 720p path. The final frame is still 1440p.
                raw.add(0, Anime4kPass(
                    desc = "HD workload downsample",
                    save = "MAIN",
                    widthExpr = "MAIN.w MAIN.h 720 / /",
                    heightExpr = "720",
                    shaderBody = "vec4 hook() { return HOOKED_tex(HOOKED_pos); }",
                    binds = listOf("HOOKED")
                ))
            }
            if (raw.isEmpty()) throw VideoFrameProcessingException("no Anime4K passes parsed")

            val sizes = mutableMapOf<String, Anime4kTexture>()
            var main = Anime4kTexture(0, inW, inH)
            sizes["MAIN"] = main

            raw.forEachIndexed { i, pass ->
                var outW = Anime4kPassParser.evaluateRpn(pass.widthExpr, sizes, main.width, main)
                var outH = Anime4kPassParser.evaluateRpn(pass.heightExpr, sizes, main.height, main)
                val isFinalOutput = i == raw.lastIndex && pass.save == "MAIN"
                if (moderateHd && isFinalOutput) {
                    val capScale = minOf(2f, 1440f / inH)
                    outW = minOf(outW, (inW * capScale).toInt().coerceAtLeast(inW))
                    outH = minOf(outH, (inH * capScale).toInt().coerceAtLeast(inH))
                }
                val program = createProgram(Anime4kPassParser.generateFragmentShader(pass, halfPrecision), pass.desc)
                val uniforms = LinkedHashSet(pass.binds).map { b ->
                    UniformBindings(
                        textureName = b,
                        rawLoc = GLES20.glGetUniformLocation(program, "${b}_raw"),
                        sizeLoc = GLES20.glGetUniformLocation(program, "${b}_size"),
                        ptLoc = GLES20.glGetUniformLocation(program, "${b}_pt")
                    )
                }
                val isFinal = isFinalOutput
                val cp = CompiledPass(pass, program, outW, outH, !isFinal, uniforms = uniforms)
                if (!isFinal) allocateTarget(cp)
                compiledPasses += cp

                val produced = Anime4kTexture(0, outW, outH)
                sizes[pass.save] = produced
                if (pass.save == "MAIN") main = produced
            }

            val last = compiledPasses.last()
            if (last.isIntermediate) throw VideoFrameProcessingException("last pass does not write MAIN")
            val performanceMode = when {
                moderateHd -> " · 1440p capped mode"
                usedSourceResolutionFallback -> " · source-resolution mode"
                else -> ""
            }
            val msg = "${mode.title}${performanceMode} · ${if (halfPrecision) "Half" else "Full"} Precision: " +
                    "${inW}×${inH} → ${last.outWidth}×${last.outHeight} (${compiledPasses.size} CNN passes)"
            Log.i(TAG, msg)
            Anime4kStatus.report(context, true, msg)
            return Size(last.outWidth, last.outHeight)
        } catch (e: Throwable) {
            Log.e(TAG, "Anime4K pipeline failed, passing frames through", e)
            releasePasses()
            Anime4kStatus.report(context, false, "Anime4K unavailable on this GPU: ${e.message?.take(160)}")
            return Size(inW, inH)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFbo[0])
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        // Media3 has already bound its output framebuffer + viewport for this frame.
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, currentFbo, 0)
        val outputFbo = currentFbo[0]
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        if (compiledPasses.isEmpty()) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexId)
            GLES20.glUseProgram(passthroughProgram)
            if (passthroughMainRawLoc != -1) GLES20.glUniform1i(passthroughMainRawLoc, 0)
            drawQuad()
            return
        }

        val textures = HashMap<String, Anime4kTexture>(16)
        textures["MAIN"] = Anime4kTexture(inputTexId, inputWidth, inputHeight)

        for (cp in compiledPasses) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, if (cp.isIntermediate) cp.fboId else outputFbo)
            GLES20.glViewport(0, 0, cp.outWidth, cp.outHeight)
            GLES20.glUseProgram(cp.programId)

            cp.uniforms.forEachIndexed { slot, u ->
                val t = if (u.textureName == "HOOKED") textures["MAIN"]!! else (textures[u.textureName] ?: textures["MAIN"]!!)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.textureId)
                if (u.rawLoc != -1) GLES20.glUniform1i(u.rawLoc, slot)
                if (u.sizeLoc != -1) GLES20.glUniform2f(u.sizeLoc, t.width.toFloat(), t.height.toFloat())
                if (u.ptLoc != -1) GLES20.glUniform2f(u.ptLoc, 1f / t.width, 1f / t.height)
            }
            drawQuad()

            if (cp.isIntermediate) {
                textures[cp.pass.save] = Anime4kTexture(cp.textureId, cp.outWidth, cp.outHeight)
            }
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun drawQuad() {
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(ATTRIB_POSITION)
        GLES20.glVertexAttribPointer(ATTRIB_POSITION, 4, GLES20.GL_FLOAT, false, 0, quadBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(ATTRIB_POSITION)
    }

    private fun releasePasses() {
        for (cp in compiledPasses) {
            if (cp.programId != 0) GLES20.glDeleteProgram(cp.programId)
            if (cp.textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(cp.textureId), 0)
            if (cp.fboId != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(cp.fboId), 0)
        }
        compiledPasses.clear()
    }

    override fun release() {
        super.release()
        releasePasses()
        if (passthroughProgram != 0) {
            GLES20.glDeleteProgram(passthroughProgram)
            passthroughProgram = 0
        }
    }
}
