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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import kotlin.math.roundToInt

// See docs/expressive.md — continuous shader clock exception.
private const val AGSL_SOURCE = """
uniform float2 uResolution;
uniform float  uTime;
uniform float2 uCenter;
uniform float4 uColor0;
uniform float4 uColor1;
uniform float4 uColor2;
uniform float  uGrain;
uniform float  uCornerK;
uniform float  uFadeRadius;

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 coord) {
    float noise = hash(floor(coord / 2.0));

    float2 uv = coord / uResolution;
    float aspect = uResolution.x / uResolution.y;
    float2 delta = uv - uCenter;

    float2 cd = float2(delta.x * aspect, delta.y);
    float circDist = length(cd);

    float nx = abs(delta.x) * 2.0;
    float ny = abs(delta.y) * 2.0 * aspect;
    float rectDist = pow(pow(nx, uCornerK) + pow(ny, uCornerK), 1.0 / uCornerK) * 0.5;

    float useRect = step(2.01, uCornerK);
    float dist = mix(circDist, rectDist, useRect);

    float t = uTime * 6.2831853;

    float wave0 = sin(dist * 8.0 - t) * 0.5 + 0.5;
    float wave1 = sin(dist * 12.0 - t * 0.7 + 2.1) * 0.5 + 0.5;
    float wave2 = sin(dist * 6.0 - t * 1.3 + 4.2) * 0.5 + 0.5;
    float field = (wave0 + wave1 + wave2) / 3.0;

    float colorBlend = dist * (2.5 / uFadeRadius);
    float t01 = clamp(colorBlend, 0.0, 1.0);
    float t12 = clamp(colorBlend - 1.0, 0.0, 1.0);

    float4 color = mix(uColor0, uColor1, t01);
    color = mix(color, uColor2, t12);

    float fade = 1.0 - smoothstep(0.0, uFadeRadius, dist);
    float grainMask = mix(1.0, step(noise, 0.65), uGrain);
    float alpha = field * fade * grainMask;

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
    center: Alignment = Alignment.Center,
    speed: Float = 1f,
    grain: Float = 0.3f,
    cornerRadius: Dp? = null,
    fadeRadius: Float = 0.8f,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.then(aiPulseModifier(colors, center, speed, grain, cornerRadius, fadeRadius))
}

@Suppress("NewApi")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun aiPulseModifier(
    colors: List<Color>,
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

    return Modifier.drawBehind {
        val w = size.width
        val h = size.height

        val offset = center.align(
            IntSize(1, 1),
            IntSize(w.roundToInt(), h.roundToInt()),
            layoutDirection,
        )
        val cx = if (w > 0f) offset.x / w else 0.5f
        val cy = if (h > 0f) offset.y / h else 0.5f

        shader.setFloatUniform("uResolution", w, h)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uCenter", cx, cy)
        shader.setFloatUniform("uColor0", c0.red, c0.green, c0.blue, c0.alpha)
        shader.setFloatUniform("uColor1", c1.red, c1.green, c1.blue, c1.alpha)
        shader.setFloatUniform("uColor2", c2.red, c2.green, c2.blue, c2.alpha)
        shader.setFloatUniform("uGrain", grain)

        val cornerK = if (cornerRadius != null) {
            val fraction = (cornerRadius.toPx() / (h / 2f)).coerceIn(0.05f, 1f)
            (2f / fraction).coerceIn(2f, 40f)
        } else 2f
        shader.setFloatUniform("uCornerK", cornerK)
        shader.setFloatUniform("uFadeRadius", fadeRadius)

        drawRect(brush = brush)
    }
}

@Preview(name = "AiPulse — active")
@Composable
private fun AiPulsePreview() {
    CronTheme {
        Box(
            modifier = Modifier
                .size(200.dp)
                .aiPulse(),
            contentAlignment = Alignment.Center,
        ) {
            Text("AI Active", style = MaterialTheme.typography.labelLarge)
        }
    }
}
