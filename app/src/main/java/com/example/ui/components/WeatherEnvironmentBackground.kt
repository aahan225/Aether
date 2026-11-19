package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.example.domain.model.ThemeMode
import com.example.domain.model.WeatherScene
import com.example.ui.theme.resolveAtmosphericPalette
import kotlin.math.PI
import kotlin.math.sin

private data class ParticleSeed(
    val xFrac: Float,
    val yFrac: Float,
    val speed: Float,
    val size: Float,
    val phase: Float
)

@Composable
fun WeatherEnvironmentBackground(
    scene: WeatherScene,
    themeMode: ThemeMode,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val target = resolveAtmosphericPalette(scene, themeMode)
    val topColor by animateColorAsState(target.skyTop, tween(900), label = "skyTop")
    val midColor by animateColorAsState(target.skyMid, tween(900), label = "skyMid")
    val bottomColor by animateColorAsState(target.skyBottom, tween(900), label = "skyBottom")
    val glowColor by animateColorAsState(target.ambientGlow, tween(900), label = "skyGlow")

    val seeds = remember {
        List(48) { idx ->
            val h1 = ((idx * 73 + 19) % 100) / 100f
            val h2 = ((idx * 41 + 37) % 100) / 100f
            val h3 = 0.55f + ((idx * 29) % 100) / 110f
            val h4 = 1.4f + ((idx * 17) % 10) * 0.35f
            val h5 = ((idx * 53) % 360) * (PI.toFloat() / 180f)
            ParticleSeed(h1, h2, h3, h4, h5)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "atmosphereMotion")
    val phase by if (reducedMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.25f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    }

    val fastPhase by if (reducedMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fastPhase"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topColor, midColor, bottomColor)
                )
            )
    ) {
        if (themeMode == ThemeMode.DYNAMIC_WEATHER) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Atmospheric upper ambient glow
                val glowCenter = when (scene) {
                    WeatherScene.GOLDEN_HOUR -> Offset(w * 0.78f, h * 0.22f)
                    WeatherScene.CLEAR_DAY, WeatherScene.PARTLY_CLOUDY -> Offset(w * 0.82f, h * 0.14f)
                    WeatherScene.CLEAR_NIGHT -> Offset(w * 0.80f, h * 0.16f)
                    else -> Offset(w * 0.5f, h * 0.18f)
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = glowCenter,
                        radius = w * 0.72f
                    ),
                    radius = w * 0.72f,
                    center = glowCenter
                )

                when (scene) {
                    WeatherScene.CLEAR_DAY, WeatherScene.GOLDEN_HOUR -> {
                        val pulse = 1f + 0.05f * sin(phase * 2f * PI.toFloat())
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x99FFF1C1), Color(0x22FFB84D), Color.Transparent),
                                center = glowCenter,
                                radius = w * 0.34f * pulse
                            ),
                            radius = w * 0.34f * pulse,
                            center = glowCenter
                        )
                    }

                    WeatherScene.CLEAR_NIGHT -> {
                        // Subtle twinkling stars
                        seeds.take(36).forEach { s ->
                            val y = s.yFrac * h * 0.55f
                            val twinkle = 0.28f + 0.55f * ((sin(phase * 4f * PI.toFloat() + s.phase) + 1f) / 2f)
                            drawCircle(
                                color = Color.White.copy(alpha = twinkle),
                                radius = s.size * 0.75f,
                                center = Offset(s.xFrac * w, y)
                            )
                        }
                        // Crescent moon glow
                        drawCircle(
                            color = Color(0xFFE8F1FF).copy(alpha = 0.88f),
                            radius = w * 0.048f,
                            center = glowCenter
                        )
                        drawCircle(
                            color = topColor.copy(alpha = 0.94f),
                            radius = w * 0.042f,
                            center = Offset(glowCenter.x - w * 0.02f, glowCenter.y - w * 0.01f)
                        )
                    }

                    WeatherScene.PARTLY_CLOUDY, WeatherScene.CLOUDY, WeatherScene.FOG -> {
                        val drift = phase * w * 0.3f
                        val cloudAlpha = if (scene == WeatherScene.FOG) 0.16f else 0.11f
                        for (i in 0..4) {
                            val cx = ((i * 0.27f * w + drift) % (w * 1.4f)) - w * 0.2f
                            val cy = h * (0.12f + i * 0.09f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = cloudAlpha), Color.Transparent),
                                    center = Offset(cx, cy),
                                    radius = w * 0.42f
                                ),
                                radius = w * 0.42f,
                                center = Offset(cx, cy)
                            )
                        }
                    }

                    WeatherScene.RAIN, WeatherScene.THUNDERSTORM -> {
                        // Subtle sheet lightning flash during thunderstorms
                        if (scene == WeatherScene.THUNDERSTORM && !reducedMotion) {
                            val flash = if (phase in 0.72f..0.74f || phase in 0.755f..0.765f) 0.20f else 0f
                            if (flash > 0f) {
                                drawRect(color = Color(0xFFDCEBFF).copy(alpha = flash))
                            }
                        }
                        // Realistic slanted rainfall streaks
                        val dropCount = if (scene == WeatherScene.THUNDERSTORM) 44 else 32
                        seeds.take(dropCount).forEach { s ->
                            val y = ((s.yFrac + fastPhase * s.speed * 1.6f) % 1.0f) * h
                            val x = ((s.xFrac * w) - (fastPhase * 38f * s.speed) + w) % w
                            val len = 18f + s.size * 8f
                            drawLine(
                                color = Color(0xFFBCE6FF).copy(alpha = 0.24f),
                                start = Offset(x, y),
                                end = Offset(x - 4f, y + len),
                                strokeWidth = 1.6f,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    WeatherScene.SNOW -> {
                        seeds.take(40).forEach { s ->
                            val y = ((s.yFrac + phase * s.speed * 2.1f) % 1.0f) * h
                            val sway = sin(phase * 2f * PI.toFloat() + s.phase) * 24f
                            val x = ((s.xFrac * w) + sway + w) % w
                            drawCircle(
                                color = Color.White.copy(alpha = 0.52f),
                                radius = s.size * 1.35f,
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }
    }
}
