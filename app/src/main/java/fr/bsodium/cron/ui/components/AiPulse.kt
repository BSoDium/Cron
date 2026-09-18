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
uniform float  uScale;
uniform float  uIntensity;
uniform float  uSoftness;
uniform int    uMode;

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float2 hash2(float2 p) {
    p = float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

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

float sdRoundedBox(float2 p, float2 b, float4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    float2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

half4 main(float2 coord) {
    float pixelNoise = hash(coord);
    float2 uv = (coord - 0.5 * uResolution) / min(uResolution.x, uResolution.y);
    float t = uTime * 6.2831853;
    float field = 0.0;

    // 1. Onde de base avec la fréquence exacte d'origine
    if (uMode == 1) {
        float2 p = uv * (3.5 * uScale); 
        float2 q = float2(
            fbm(p + float2(t * 0.2, t * 0.3)),
            fbm(p + float2(-t * 0.3, t * 0.2))
        );
        float n = fbm(p + q * (2.5 * uIntensity));
        field = sin(n * 12.0 - t * 1.5) * 0.5 + 0.5; 
    } else {
        float2 aspect = uResolution / min(uResolution.x, uResolution.y);
        float2 cardSize = (aspect * 0.5) - 0.1;
        float d = sdRoundedBox(uv, cardSize, float4(0.15));
        float distortion = fbm(uv * 4.0 + t * 0.5) * 0.05 * uIntensity;
        // La fréquence d'origine est conservée intacte
        field = sin((d + distortion) * (15.0 * uScale) - t * 1.5) * 0.5 + 0.5;
    }

    // 2. Élimination des "lignes" nettes :
    // On combine un dégradé continu doux (smoothColor) et un tramage de grain (ditherColor)
    // uSoftness contrôle le dosage entre le grain pur et le dégradé continu
    
    float4 smoothColor = mix(uColor0, uColor1, field);
    smoothColor = mix(smoothColor, uColor2, smoothstep(0.4, 0.8, field));

    // Seuil de bruit stochastique adouci
    float ditherStep = step(pixelNoise, field);
    float4 ditherColor = mix(uColor0, uColor1, ditherStep);

    // uSoftness = 0.0 -> Grain pur (peut faire des lignes)
    // uSoftness = 1.0 -> Fondu continu sans lignes tranchées, avec texture de bruit diffuse
    float4 finalColor = mix(ditherColor, smoothColor, clamp(uSoftness, 0.0, 1.0));

    return half4(finalColor.rgb * finalColor.a, finalColor.a);
}
"""

private const val TWO_PI_MS = 6000

internal object AiPulseDefaults {
    @Composable
    fun colors(): List<Color> = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
    )
}

@Composable
internal fun Modifier.aiPulse(
    colors: List<Color> = AiPulseDefaults.colors(),
    mode: AiPulseMode = AiPulseMode.SINGLE_SOURCE,
    speed: Float = 1f,
    scale: Float = 1f,
    intensity: Float = 0.8f,
    softness: Float = 0.7f, // Valeur idéale entre 0.4f et 0.8f
    enabled: Boolean = true,
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.then(aiPulseModifier(colors, mode, speed, scale, intensity, softness))
}

@Suppress("NewApi")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun aiPulseModifier(
    colors: List<Color>,
    mode: AiPulseMode,
    speed: Float,
    scale: Float,
    intensity: Float,
    softness: Float,
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

    val c0 = colors.getOrElse(0) { Color.Transparent }
    val c1 = colors.getOrElse(1) { Color.Transparent }
    val c2 = colors.getOrElse(2) { Color.Transparent }
    val modeInt = if (mode == AiPulseMode.RANDOM_WAVE) 1 else 0

    return Modifier.drawBehind {
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uColor0", c0.red, c0.green, c0.blue, c0.alpha)
        shader.setFloatUniform("uColor1", c1.red, c1.green, c1.blue, c1.alpha)
        shader.setFloatUniform("uColor2", c2.red, c2.green, c2.blue, c2.alpha)
        shader.setFloatUniform("uScale", scale)
        shader.setFloatUniform("uIntensity", intensity)
        shader.setFloatUniform("uSoftness", softness)
        shader.setIntUniform("uMode", modeInt)

        drawRect(brush = brush)
    }
}

@Preview(name = "Loading State — Smooth Frequency")
@Composable
private fun AiPulseCardPreview() {
    CronTheme {
        Box(
            modifier = Modifier
                .size(width = 320.dp, height = 120.dp)
                .aiPulse(
                    mode = AiPulseMode.SINGLE_SOURCE,
                    scale = 0.8f,
                    softness = 0.7f,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Loading...", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(name = "AiPulse — active (Mode 1)")
@Composable
private fun AiPulsePreview() {
    CronTheme {
        Box(
            modifier = Modifier
                .size(200.dp)
                .aiPulse(
                    mode = AiPulseMode.RANDOM_WAVE,
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("AI Active", style = MaterialTheme.typography.labelLarge)
        }
    }
}
