package com.lagradost.cloudstream3.ui.animebox.enhancer.anime4k

/**
 * Parser for mpv user-shader (.glsl) files as shipped by bloc97/Anime4K, mirroring the
 * reference player's pipeline: split on `//!` directive blocks, evaluate the RPN size
 * expressions and emit a GLES 3.0 fragment shader for every pass.
 */
object Anime4kPassParser {

    fun parseGlsl(glslContent: String): List<Anime4kPass> {
        val passes = mutableListOf<Anime4kPass>()
        val headers = mutableMapOf<String, MutableList<String>>()
        val body = StringBuilder()
        var inHeaderBlock = false

        fun flush() {
            // Only blocks that really are passes (HOOK directive + hook() entry point).
            // Text before the first directive (e.g. the MIT licence comment) is discarded –
            // previously it became a bogus pass without hook() that broke the whole chain.
            val hasHook = headers.containsKey("HOOK")
            val src = body.toString()
            if (hasHook && src.contains("hook()")) {
                passes.add(
                    Anime4kPass(
                        desc = headers["DESC"]?.firstOrNull() ?: "pass ${passes.size}",
                        save = headers["SAVE"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "MAIN",
                        widthExpr = headers["WIDTH"]?.firstOrNull(),
                        heightExpr = headers["HEIGHT"]?.firstOrNull(),
                        shaderBody = src,
                        binds = (headers["BIND"] ?: emptyList()).filter { it.isNotBlank() }
                    )
                )
            }
            headers.clear()
            body.setLength(0)
        }

        for (rawLine in glslContent.lines()) {
            val line = rawLine.trimEnd('\r')
            val trimmed = line.trim()
            if (trimmed.startsWith("//!")) {
                if (!inHeaderBlock) {
                    // A new directive block starts → close the previous pass.
                    flush()
                    inHeaderBlock = true
                }
                val directive = trimmed.substring(3).trim()
                val spaceIdx = directive.indexOf(' ')
                val key = if (spaceIdx == -1) directive else directive.substring(0, spaceIdx)
                val value = if (spaceIdx == -1) "" else directive.substring(spaceIdx + 1).trim()
                headers.getOrPut(key.uppercase()) { mutableListOf() }.add(value)
            } else {
                inHeaderBlock = false
                if (headers.isNotEmpty()) {
                    body.append(line).append('\n')
                }
            }
        }
        flush()
        return passes
    }

    /**
     * Evaluates mpv's RPN size expressions, e.g. `conv2d_last_tf.w 2 *`.
     * [fallback] is used when the expression is missing (the hooked texture size).
     */
    fun evaluateRpn(expr: String?, sizes: Map<String, Anime4kTexture>, fallback: Int, hooked: Anime4kTexture): Int {
        if (expr.isNullOrBlank()) return fallback
        val stack = ArrayDeque<Float>()
        for (token in expr.trim().split(Regex("\\s+"))) {
            when (token) {
                "+", "-", "*", "/", ">", "<" -> {
                    val b = stack.removeLastOrNull() ?: 0f
                    val a = stack.removeLastOrNull() ?: 0f
                    stack.addLast(
                        when (token) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> if (b != 0f) a / b else 0f
                            ">" -> if (a > b) 1f else 0f
                            else -> if (a < b) 1f else 0f
                        }
                    )
                }
                else -> {
                    val dot = token.lastIndexOf('.')
                    if (dot > 0 && (token.endsWith(".w") || token.endsWith(".width") || token.endsWith(".h") || token.endsWith(".height"))) {
                        val name = token.substring(0, dot)
                        val isW = token.substring(dot + 1).startsWith("w")
                        val tex = if (name == "HOOKED" || name == "OUTPUT" || name == "NATIVE") hooked else (sizes[name] ?: hooked)
                        stack.addLast((if (isW) tex.width else tex.height).toFloat())
                    } else {
                        stack.addLast(token.toFloatOrNull() ?: 0f)
                    }
                }
            }
        }
        val v = stack.lastOrNull() ?: return fallback
        return v.toInt().coerceAtLeast(1)
    }

    /** Same layout the reference player generates (Lsp7.b): GLES 3.0, mpv-style macros. */
    fun generateFragmentShader(pass: Anime4kPass, halfPrecision: Boolean): String {
        val prec = if (halfPrecision) "mediump" else "highp"
        val sb = StringBuilder()
        sb.append("#version 300 es\n")
        sb.append("precision ").append(prec).append(" float;\n")
        sb.append("precision ").append(prec).append(" int;\n")
        sb.append("in highp vec2 vPos;\n")
        sb.append("out vec4 fragColor;\n")
        val names = LinkedHashSet<String>().apply { addAll(pass.binds) }
        for (b in names) {
            sb.append("uniform sampler2D ").append(b).append("_raw;\n")
            sb.append("uniform vec2 ").append(b).append("_size;\n")
            sb.append("uniform vec2 ").append(b).append("_pt;\n")
            sb.append("#define ").append(b).append("_pos vPos\n")
            sb.append("#define ").append(b).append("_tex(pos) texture(").append(b).append("_raw, pos)\n")
            sb.append("#define ").append(b).append("_texOff(off) ").append(b).append("_tex(")
                .append(b).append("_pos + ").append(b).append("_pt * vec2(off))\n")
        }
        sb.append(pass.shaderBody)
        sb.append("\nvoid main() { fragColor = hook(); }\n")
        return sb.toString()
    }
}
