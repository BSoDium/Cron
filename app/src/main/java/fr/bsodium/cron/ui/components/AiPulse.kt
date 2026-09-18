package fr.bsodium.cron.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import kotlin.math.roundToInt

enum class AiPulseMode {
    SINGLE_SOURCE,
    RANDOM_WAVE
}

private const val AGSL_SOURCE = """
uniform float2 uResolution;
uniform float  uTime;
uniform float4 uColor0;
uniform float4 uColor1;
uniform float4 uColor2;
uniform float  uGrain;
uniform int    uMode;

// Grain hash
float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// 2D Random vector for Gradient Noise
float2 hash2(float2 p) {
    p = float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

// Classic Perlin/Gradient Noise (smoother and more organic than Value Noise)
float perlinNoise(float2 p) {
    float2 pi = floor(p);
    float2 pf = fract(p);
    
    float2 w = pf * pf * (3.0 - 2.0 * pf);
    
    float a = dot(hash2(pi + float2(0.0, 0.0)), pf - float2(0.0, 0.0));
    float b = dot(hash2(pi + float2(1.0, 0.0)), pf - float2(1.0, 0.0));
    float c = dot(hash2(pi + float2(0.0, 1.0)), pf - float2(0.0, 1.0));
    float d = dot(hash2(pi + float2(1.0, 1.0)), pf - float2(1.0, 1.0));
    
    return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

// Fractional Brownian Motion
float fbm(float2 st) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        value += amplitude * perlinNoise(st);
        st *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

half4 main(float2 coord) {
    float noiseMask = hash(floor(coord / 2.0));
    
    // 1. Aspect Ratio Correction
    // Center the UVs, apply the resolution ratio to the X axis, and un-center.
    // This perfectly prevents the stretching of both the noise and the base sine waves.
    float2 uv = coord / uResolution;
    uv -= 0.5;
    uv.x *= uResolution.x / uResolution.y;
    uv += 0.5;
    
    float t = uTime * 6.2831853;
    float field = 0.0;

    if (uMode == 1) {
        // Mode 1: Domain Warped Random Waves
        float2 p = uv * 3.5; 
        
        // Distort the space in opposing directions over time
        float2 q = float2(
            fbm(p + float2(t * 0.2, t * 0.3)),
            fbm(p + float2(-t * 0.3, t * 0.2))
        );
        
        // Pass the dynamically distorted coordinates into a final noise pass.
        // This gives it that swirling, unpredictable liquid movement.
        float n = fbm(p + q * 2.5);
        
        // Modulate the height map with a sine wave that advances over time,
        // turning the noise into radiating, pulsating rings/waves.
        field = sin(n * 12.0 - t * 2.5) * 0.5 + 0.5; 
    } else {
        // Mode 0: Single Source
        float wave0 = sin((uv.x * 14.0 + uv.y * 5.0) - t) * 0.5 + 0.5;
        float wave1 = sin((uv.x * -8.0 + uv.y * 18.0) - t * 0.7 + 2.1) * 0.5 + 0.5;
        float wave2 = sin((uv.x * 22.0 + uv.y * 11.0) - t * 1.3 + 4.2) * 0.5 + 0.5;
        field = (wave0 + wave1 + wave2) / 3.0;
    }

    float t01 = smoothstep(0.15, 0.7, field);
    float t12 = smoothstep(0.55, 0.95, field);

    float4 color = mix(uColor0, uColor1, t01);
    color = mix(color, uColor2, t12);

    float grainMask = mix(1.0, step(noiseMask, 0.65), uGrain);
    float alpha = (0.35 + field * 0.65) * grainMask;

    return half4(color.rgb * alpha, alpha);
}
"""

private const val TWO_PI_MS = 4000

internal object AiPulseDefaults {
    @Composable
    fun colors(): List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
}

@Composable
internal fun Modifier.aiPulse(
    colors: List<Color> = AiPulseDefaults.colors(),
    mode: AiPulseMode = AiPulseMode.SINGLE_SOURCE,
    center: Alignment = Alignment.Center,
    speed: Float = 1f,
    grain: Float = 0.3f,
    cornerRadius: Dp? = null,
    fadeRadius: Float = 0.8f,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.then(aiPulseModifier(colors, mode, center, speed, grain, cornerRadius, fadeRadius))
}

@Suppress("NewApi")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun aiPulseModifier(
    colors: List<Color>,
    mode: AiPulseMode,
    center: Alignment,
    speed: Float,
    grain: Float,
    cornerRadius: Dp?,
    fadeRadius: Float,
): Modifier {
    val transition = rememberInfiniteTransition(label = "ai-pulse")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1000 * TWO_PI_MS / speed).roundToInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ai-pulse-time",
    )

    val shader = remember { android.graphics.RuntimeShader(AGSL_SOURCE) }
    val brush = remember(shader) { ShaderBrush(shader) }

    val c0 = colors.getOrElse(0) { Color.Magenta }
    val c1 = colors.getOrElse(1) { Color.Cyan }
    val c2 = colors.getOrElse(2) { Color.Yellow }
    val modeInt = if (mode == AiPulseMode.RANDOM_WAVE) 1 else 0

    return Modifier.drawBehind {
        val w = size.width
        val h = size.height

        shader.setFloatUniform("uResolution", w, h)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uColor0", c0.red, c0.green, c0.blue, c0.alpha)
        shader.setFloatUniform("uColor1", c1.red, c1.green, c1.blue, c1.alpha)
        shader.setFloatUniform("uColor2", c2.red, c2.green, c2.blue, c2.alpha)
        shader.setFloatUniform("uGrain", grain)
        shader.setIntUniform("uMode", modeInt)

        drawRect(brush = brush)
    }
}

@Preview(name = "AiPulse — active (Mode 1)")
@Composable
private fun AiPulsePreview() {
    CronTheme {
        Box(
            modifier = Modifier
                .size(200.dp)
                .aiPulse(mode = AiPulseMode.RANDOM_WAVE),
            contentAlignment = Alignment.Center,
        ) {
            Text("AI Active", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(name = "AiPulse — memory card (Wide)")
@Composable
private fun AiPulseMemoryCardPreview() {
    CronTheme {
        Box(
            modifier = Modifier
                .size(width = 360.dp, height = 88.dp)
                .aiPulse(mode = AiPulseMode.RANDOM_WAVE, cornerRadius = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("I prefer a 15-minute buffer before bus rides")
        }
    }
}
