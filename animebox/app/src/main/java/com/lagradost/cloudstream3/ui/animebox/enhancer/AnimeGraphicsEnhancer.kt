package com.lagradost.cloudstream3.ui.animebox.enhancer

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect

enum class EnhancerPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val defaultSharpness: Float,
    val defaultContrast: Float,
    val defaultSaturation: Float,
    val defaultLineClarity: Float
) {
    ULTRA_CLARITY(
        id = "ultra_clarity",
        title = "Ultra Anime HD",
        subtitle = "Natural Studio Tone & Soft Edge Crispness",
        description = "Authentic studio look with gentle micro-contrast and true-to-source natural anime grading.",
        defaultSharpness = 0.20f,
        defaultContrast = 1.06f,
        defaultSaturation = 1.06f,
        defaultLineClarity = 0.40f
    ),
    INK_MASTER_HDR(
        id = "ink_master_hdr",
        title = "Dynamic Ink HDR",
        subtitle = "Deep Inked Outlines & Rich Dynamic Contrast",
        description = "Deep inked character contours, radiant cel highlights, and high-impact HDR depth.",
        defaultSharpness = 0.26f,
        defaultContrast = 1.29f,
        defaultSaturation = 1.22f,
        defaultLineClarity = 1.23f
    ),
    CAS_RAZOR_SHARP(
        id = "cas_razor_sharp",
        title = "Razor Sharp CAS",
        subtitle = "AMD FidelityFX-inspired Adaptive Sharpening",
        description = "Maximum edge crispness and high-frequency line definition with zero ringing or artifacting.",
        defaultSharpness = 0.25f,
        defaultContrast = 1.33f,
        defaultSaturation = 1.26f,
        defaultLineClarity = 0.90f
    ),
    OLED_VIVID(
        id = "oled_vivid",
        title = "OLED Vivid Pop",
        subtitle = "Deep Inky Blacks & Radiant Palette",
        description = "Deep saturated anime colors, inky black outlines, and bright specular highlights.",
        defaultSharpness = 0.24f,
        defaultContrast = 1.24f,
        defaultSaturation = 1.27f,
        defaultLineClarity = 0.32f
    ),
    CUSTOM(
        id = "custom",
        title = "Custom Tuning",
        subtitle = "Manual GPU Controls",
        description = "Customized balance tailored precisely to your device screen.",
        defaultSharpness = 0.25f,
        defaultContrast = 1.20f,
        defaultSaturation = 1.20f,
        defaultLineClarity = 0.80f
    );

    companion object {
        fun fromId(id: String): EnhancerPreset {
            return entries.firstOrNull { it.id == id } ?: ULTRA_CLARITY
        }
    }
}

/**
 * Real-time Hardware GPU Graphics Enhancer & Edge Sharpening Engine.
 * Utilizes AGSL RuntimeShaders on Android 13+ (API 33+) with 9-tap Contrast Adaptive Sharpening (CAS),
 * and Android 12+ RenderEffect with Calibrated S-Curve & Vibrance matrices.
 * Applied exclusively to the video surface so subtitles remain 100% clean and untouched.
 */
object AnimeGraphicsEnhancer {

    private const val AGSL_CAS_SHARPEN_SHADER = """
        uniform shader image;
        uniform float uSharpness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uLineClarity;

        vec4 main(vec2 fragCoord) {
            // Physical pixel step offset calibrated for high-density mobile displays
            float step = 1.5;
            vec4 c = image.eval(fragCoord);
            
            // 5-Tap Cross Kernel
            vec4 n = image.eval(fragCoord + vec2( 0.0, -step));
            vec4 s = image.eval(fragCoord + vec2( 0.0,  step));
            vec4 w = image.eval(fragCoord + vec2(-step,  0.0));
            vec4 e = image.eval(fragCoord + vec2( step,  0.0));
            
            // 4 Diagonal Samples for 9-Tap High-Precision Kernel
            float dStep = step * 0.7071;
            vec4 nw = image.eval(fragCoord + vec2(-dStep, -dStep));
            vec4 ne = image.eval(fragCoord + vec2( dStep, -dStep));
            vec4 sw = image.eval(fragCoord + vec2(-dStep,  dStep));
            vec4 se = image.eval(fragCoord + vec2( dStep,  dStep));
            
            // ITU-R BT.709 Luminance Conversion
            vec3 lumaCoeff = vec3(0.2126, 0.7152, 0.0722);
            float lumaC = dot(c.rgb, lumaCoeff);
            float lumaN = dot(n.rgb, lumaCoeff);
            float lumaS = dot(s.rgb, lumaCoeff);
            float lumaW = dot(w.rgb, lumaCoeff);
            float lumaE = dot(e.rgb, lumaCoeff);
            
            // Local Contrast & Peak Luminance Bounds (CAS core logic)
            float minLuma = min(lumaC, min(min(lumaN, lumaS), min(lumaW, lumaE)));
            float maxLuma = max(lumaC, max(max(lumaN, lumaS), max(lumaE, lumaE)));
            
            // Contrast Adaptive Weighting (limits over-sharpening & noise in flat cel regions)
            float contrastRange = maxLuma - minLuma + 0.0001;
            float peakDistance = min(minLuma, 1.0 - maxLuma);
            float amp = clamp(peakDistance / contrastRange, 0.0, 1.0);
            
            // Scaled adaptive weight
            float casWeight = -sqrt(amp) * 0.85 * uSharpness;
            
            // Cross + Diagonal weighted convolution
            vec3 crossSum = n.rgb + s.rgb + w.rgb + e.rgb;
            vec3 diagSum = nw.rgb + ne.rgb + sw.rgb + se.rgb;
            vec3 sharpRgb = (c.rgb + crossSum * (casWeight * 0.75) + diagSum * (casWeight * 0.25)) / (1.0 + 3.0 * casWeight + 1.0 * casWeight);
            sharpRgb = clamp(sharpRgb, 0.0, 1.0);
            
            // Dynamic Anime Inked Line Clarity:
            // Deepens dark contour edges with strong gradients to make anime characters pop
            if (uLineClarity > 0.0) {
                float edge = clamp((maxLuma - minLuma) * 3.5, 0.0, 1.0);
                if (lumaC < 0.55 && edge > 0.08) {
                    float darkFactor = 1.0 - (uLineClarity * 0.30 * edge * (1.0 - lumaC));
                    sharpRgb = sharpRgb * darkFactor;
                }
            }
            
            // Dynamic Contrast S-Curve
            if (uContrast != 1.0) {
                vec3 cMid = sharpRgb - 0.5;
                sharpRgb = clamp(0.5 + cMid * uContrast, 0.0, 1.0);
            }
            
            // Calibrated Anime Vibrance & Color Saturation pop
            if (uSaturation != 1.0) {
                float l = dot(sharpRgb, lumaCoeff);
                float maxC = max(sharpRgb.r, max(sharpRgb.g, sharpRgb.b));
                float minC = min(sharpRgb.r, min(sharpRgb.g, sharpRgb.b));
                float satAmt = maxC - minC;
                float vibranceWeight = (1.0 - satAmt * 0.5) * (uSaturation - 1.0);
                sharpRgb = clamp(mix(vec3(l), sharpRgb, 1.0 + vibranceWeight * 1.5), 0.0, 1.0);
            }
            
            return vec4(clamp(sharpRgb, 0.0, 1.0), c.a);
        }
    """

    private var cachedRuntimeShader: Any? = null

    /**
     * Creates native Android RenderEffect directly applicable to video surface (TextureView).
     */
    fun createNativeRenderEffect(
        sharpness: Float,
        contrast: Float,
        saturation: Float,
        lineClarity: Float
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val shader = (cachedRuntimeShader as? RuntimeShader)
                    ?: RuntimeShader(AGSL_CAS_SHARPEN_SHADER).also {
                        cachedRuntimeShader = it
                    }
                shader.setFloatUniform("uSharpness", sharpness)
                shader.setFloatUniform("uContrast", contrast)
                shader.setFloatUniform("uSaturation", saturation)
                shader.setFloatUniform("uLineClarity", lineClarity)

                return RenderEffect.createRuntimeShaderEffect(shader, "image")
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val matrix = createCalibratedColorMatrix(contrast, saturation)
                val colorFilter = ColorMatrixColorFilter(matrix)
                return RenderEffect.createColorFilterEffect(colorFilter)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Creates a high-performance Compose RenderEffect for real-time video post-processing.
     */
    fun createComposeRenderEffect(
        sharpness: Float,
        contrast: Float,
        saturation: Float,
        lineClarity: Float
    ): androidx.compose.ui.graphics.RenderEffect? {
        val nativeEffect = createNativeRenderEffect(sharpness, contrast, saturation, lineClarity)
        return nativeEffect?.asComposeRenderEffect()
    }

    /**
     * Builds a hardware color matrix that enhances anime contrast, black depth, and color vibrancy.
     */
    fun createCalibratedColorMatrix(contrast: Float, saturation: Float): ColorMatrix {
        val satMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }

        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        return ColorMatrix().apply {
            setConcat(contrastMatrix, satMatrix)
        }
    }
}
