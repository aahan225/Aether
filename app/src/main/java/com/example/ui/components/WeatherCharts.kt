package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MeteorologyCalculator
import com.example.domain.model.AqiStandard
import com.example.domain.model.AstronomyInfo
import com.example.domain.model.HourlyPoint
import com.example.domain.model.ModelVerificationMetrics
import com.example.domain.model.UserPreferences
import com.example.ui.theme.AqiGood
import com.example.ui.theme.AqiModerate
import com.example.ui.theme.AqiPoor
import com.example.ui.theme.AqiSatisfactory
import com.example.ui.theme.AqiSevere
import com.example.ui.theme.AqiVeryPoor
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.LavenderMist
import com.example.ui.theme.SolarAmber
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive 24-hour temperature, apparent-temperature & precipitation probability chart.
 * Supports touch/drag inspection with dynamic tooltip, day/night shading, and current-hour marker.
 */
@Composable
fun InteractiveHourlyChart(
    hourly: List<HourlyPoint>,
    preferences: UserPreferences,
    modifier: Modifier = Modifier
) {
    val points = remember(hourly) { hourly.take(24) }
    if (points.size < 2) return

    var selectedIdx by remember(points) { mutableIntStateOf(0) }
    val selected = points[selectedIdx.coerceIn(0, points.lastIndex)]

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        // Interactive tooltip readout
        Surface(
            color = onSurface.copy(alpha = 0.08f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedIdx == 0) "Now · ${MeteorologyCalculator.formatHourLabel(selected.timeIso, preferences.use24HourTime)}"
                        else MeteorologyCalculator.formatHourLabel(selected.timeIso, preferences.use24HourTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryColor
                    )
                    Text(
                        text = MeteorologyCalculator.wmoDescription(selected.weatherCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVar
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = MeteorologyCalculator.formatTemp(selected.temperatureC, preferences.tempUnit, true),
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Feels ${MeteorologyCalculator.formatTemp(selected.apparentTemperatureC, preferences.tempUnit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVar
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${selected.precipitationProbability ?: 0}% rain",
                            style = MaterialTheme.typography.labelMedium,
                            color = AuroraCyan
                        )
                        Text(
                            text = MeteorologyCalculator.formatPrecipitation(selected.precipitationMm, preferences.precipUnit),
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVar
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        selectedIdx = (frac * (points.lastIndex)).roundToInt().coerceIn(0, points.lastIndex)
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        selectedIdx = (frac * (points.lastIndex)).roundToInt().coerceIn(0, points.lastIndex)
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val topPad = 14f
                val bottomPad = 24f
                val chartH = h - topPad - bottomPad
                val stepX = w / (points.size - 1).toFloat()

                val minT = (points.minOf { minOf(it.temperatureC, it.apparentTemperatureC) } - 1.5)
                val maxT = (points.maxOf { maxOf(it.temperatureC, it.apparentTemperatureC) } + 1.5)
                val spanT = max(2.0, maxT - minT)

                fun yForTemp(temp: Double): Float {
                    val norm = ((temp - minT) / spanT).toFloat().coerceIn(0f, 1f)
                    return topPad + chartH * (1f - norm)
                }

                // Day/Night shading bands
                points.forEachIndexed { idx, pt ->
                    if (!pt.isDay) {
                        val xStart = (idx * stepX - stepX * 0.5f).coerceAtLeast(0f)
                        drawRect(
                            color = Color(0xFF060B18).copy(alpha = 0.24f),
                            topLeft = Offset(xStart, topPad),
                            size = Size(stepX, chartH)
                        )
                    }
                }

                // Precipitation probability columns
                points.forEachIndexed { idx, pt ->
                    val prob = (pt.precipitationProbability ?: 0).coerceIn(0, 100)
                    if (prob > 0) {
                        val barH = chartH * 0.42f * (prob / 100f)
                        val x = idx * stepX
                        drawRoundRect(
                            color = AuroraCyan.copy(alpha = 0.28f),
                            topLeft = Offset(x - 4f, topPad + chartH - barH),
                            size = Size(8f, barH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }

                // Apparent temperature dashed curve
                val feelsPath = Path()
                points.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = yForTemp(pt.apparentTemperatureC)
                    if (idx == 0) feelsPath.moveTo(x, y) else feelsPath.lineTo(x, y)
                }
                drawPath(
                    path = feelsPath,
                    color = SolarAmber.copy(alpha = 0.50f),
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                )

                // Primary smooth temperature curve & fill
                val tempPath = Path()
                val fillPath = Path()
                points.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = yForTemp(pt.temperatureC)
                    if (idx == 0) {
                        tempPath.moveTo(x, y)
                        fillPath.moveTo(x, topPad + chartH)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (idx - 1) * stepX
                        val prevY = yForTemp(points[idx - 1].temperatureC)
                        val cx = (prevX + x) / 2f
                        tempPath.cubicTo(cx, prevY, cx, y, x, y)
                        fillPath.cubicTo(cx, prevY, cx, y, x, y)
                    }
                }
                fillPath.lineTo((points.lastIndex) * stepX, topPad + chartH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(SolarAmber.copy(alpha = 0.28f), Color.Transparent),
                        startY = topPad,
                        endY = topPad + chartH
                    )
                )

                drawPath(
                    path = tempPath,
                    brush = Brush.horizontalGradient(listOf(SolarAmber, AuroraCyan)),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )

                // Selected / active scrubber vertical guide & halo
                val selX = selectedIdx * stepX
                val selY = yForTemp(selected.temperatureC)
                drawLine(
                    color = onSurface.copy(alpha = 0.35f),
                    start = Offset(selX, topPad),
                    end = Offset(selX, topPad + chartH),
                    strokeWidth = 2f
                )
                drawCircle(
                    color = SolarAmber.copy(alpha = 0.28f),
                    radius = 16f,
                    center = Offset(selX, selY)
                )
                drawCircle(
                    color = SolarAmber,
                    radius = 7f,
                    center = Offset(selX, selY)
                )
            }
        }

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Solid: Temp · Dashed: Feels Like · Bars: Rain %",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVar
            )
            Text(
                text = "Drag graph to inspect",
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor
            )
        }
    }
}

@Composable
fun WindCompassDial(
    windDirectionDeg: Int,
    windSpeedLabel: String,
    gustsLabel: String,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.size(116.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f - 8f

            // Outer ticks
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = deg % 90 == 0
                val inner = r - if (isMajor) 12f else 6f
                drawLine(
                    color = onSurface.copy(alpha = if (isMajor) 0.55f else 0.20f),
                    start = Offset(cx + inner * cos(rad).toFloat(), cy + inner * sin(rad).toFloat()),
                    end = Offset(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat()),
                    strokeWidth = if (isMajor) 3f else 1.5f,
                    cap = StrokeCap.Round
                )
            }

            // Direction arrow (meteorological wind direction is where wind comes FROM; point arrow toward flow)
            val rad = Math.toRadians((windDirectionDeg - 90).toDouble())
            val tip = Offset(cx + (r - 14f) * cos(rad).toFloat(), cy + (r - 14f) * sin(rad).toFloat())
            val tail = Offset(cx - (r - 22f) * cos(rad).toFloat(), cy - (r - 22f) * sin(rad).toFloat())
            drawLine(
                color = AuroraCyan,
                start = tail,
                end = tip,
                strokeWidth = 4.5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = AuroraCyan, radius = 6f, center = tip)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = MeteorologyCalculator.windCardinal(windDirectionDeg),
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = windSpeedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = AuroraCyan
            )
            Text(
                text = "G $gustsLabel",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVar
            )
        }
    }
}

@Composable
fun MiniSparklineChart(
    values: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) return
    Canvas(modifier = modifier.fillMaxWidth().height(54.dp)) {
        val minV = values.min()
        val maxV = values.max()
        val span = max(0.5, maxV - minV)
        val stepX = size.width / (values.size - 1).toFloat()
        val padY = 6f
        val availH = size.height - padY * 2

        val path = Path()
        val fill = Path()
        values.forEachIndexed { idx, v ->
            val x = idx * stepX
            val norm = ((v - minV) / span).toFloat().coerceIn(0f, 1f)
            val y = padY + availH * (1f - norm)
            if (idx == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), Color.Transparent))
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SunAndMoonArcView(
    astronomy: AstronomyInfo,
    use24Hour: Boolean,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        ) {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.78f
            val leftX = w * 0.08f
            val rightX = w * 0.92f
            val spanX = rightX - leftX
            val arcHeight = h * 0.62f

            // Horizon line
            drawLine(
                color = onSurface.copy(alpha = 0.22f),
                start = Offset(0f, horizonY),
                end = Offset(w, horizonY),
                strokeWidth = 2f
            )

            // Solar arc trajectory
            val arcPath = Path()
            val steps = 40
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = leftX + spanX * t
                val y = horizonY - sin(t * PI.toFloat()) * arcHeight
                if (i == 0) arcPath.moveTo(x, y) else arcPath.lineTo(x, y)
            }
            drawPath(
                path = arcPath,
                color = SolarAmber.copy(alpha = 0.45f),
                style = Stroke(
                    width = 3.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            )

            // Current sun position dot
            val p = astronomy.solarProgress.coerceIn(0f, 1f)
            val sunX = leftX + spanX * p
            val sunY = horizonY - sin(p * PI.toFloat()) * arcHeight
            drawCircle(
                color = SolarAmber.copy(alpha = 0.30f),
                radius = 18f,
                center = Offset(sunX, sunY)
            )
            drawCircle(
                color = SolarAmber,
                radius = 8.5f,
                center = Offset(sunX, sunY)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("SUNRISE", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
                Text(
                    MeteorologyCalculator.formatLocalTime(astronomy.sunriseIso, use24Hour),
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurface
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DAYLIGHT", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
                Text(astronomy.daylightFormatted, style = MaterialTheme.typography.titleSmall, color = SolarAmber)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("SUNSET", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
                Text(
                    MeteorologyCalculator.formatLocalTime(astronomy.sunsetIso, use24Hour),
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurface
                )
            }
        }
    }
}

@Composable
fun AqiSpectrumBar(
    aqiValue: Int?,
    standard: AqiStandard,
    modifier: Modifier = Modifier
) {
    val maxScale = 500f
    val fraction = ((aqiValue ?: 0) / maxScale).coerceIn(0f, 1f)
    val colors = if (standard == AqiStandard.INDIA_CPCB) {
        listOf(AqiGood, AqiSatisfactory, AqiModerate, AqiPoor, AqiVeryPoor, AqiSevere)
    } else {
        listOf(AqiGood, AqiModerate, AqiPoor, AqiVeryPoor, Color(0xFF9333EA), AqiSevere)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val barH = 8.dp.toPx()
        val yTop = (size.height - barH) / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(colors),
            topLeft = Offset(0f, yTop),
            size = Size(size.width, barH),
            cornerRadius = CornerRadius(barH / 2f, barH / 2f)
        )
        if (aqiValue != null) {
            val cx = (size.width * fraction).coerceIn(8f, size.width - 8f)
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(cx, size.height / 2f))
            drawCircle(color = Color(0xFF0A1424), radius = 4.dp.toPx(), center = Offset(cx, size.height / 2f))
        }
    }
}

@Composable
fun VerificationBarChart(
    models: List<ModelVerificationMetrics>,
    modifier: Modifier = Modifier
) {
    if (models.isEmpty()) return
    val maxErr = (models.maxOf { maxOf(it.tempMaeC, it.tempRmseC) } * 1.15).coerceAtLeast(1.0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        models.forEach { m ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(m.modelName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        String.format(Locale.US, "MAE %.2f°C · RMSE %.2f°C", m.tempMaeC, m.tempRmseC),
                        style = MaterialTheme.typography.labelMedium,
                        color = AuroraCyan
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                    val maeW = (size.width * (m.tempMaeC / maxErr).toFloat()).coerceIn(6f, size.width)
                    val rmseW = (size.width * (m.tempRmseC / maxErr).toFloat()).coerceIn(6f, size.width)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
                        size = Size(size.width, 6.dp.toPx()),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = AuroraCyan,
                        size = Size(maeW, 6.dp.toPx()),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(0f, 9.dp.toPx()),
                        size = Size(size.width, 6.dp.toPx()),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = LavenderMist,
                        topLeft = Offset(0f, 9.dp.toPx()),
                        size = Size(rmseW, 6.dp.toPx()),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }
    }
}
