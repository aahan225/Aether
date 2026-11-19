package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.LavenderMist
import com.example.ui.theme.SolarAmber

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = com.example.ui.theme.LocalAetherGlass.current.defaultCornerRadius,
    contentPadding: Dp = 18.dp,
    highlightTint: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = com.example.ui.theme.LocalAetherGlass.current
    val shape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            .clip(shape)
            .background(glass.cardFillBrush(highlightTint))
            .border(BorderStroke(glass.borderWidth, glass.cardBorderBrush()), shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GlassSectionHeader(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun WeatherConditionIcon(
    weatherCode: Int,
    isDay: Boolean,
    size: Dp = 28.dp,
    contentDescription: String? = null
) {
    val (icon, tint) = when (weatherCode) {
        0, 1 -> if (isDay) Icons.Rounded.WbSunny to SolarAmber else Icons.Rounded.NightsStay to LavenderMist
        2 -> if (isDay) Icons.Rounded.WbCloudy to Color(0xFFFFD68A) else Icons.Rounded.Cloud to Color(0xFFC5D5E8)
        3 -> Icons.Rounded.Cloud to Color(0xFFB5C7DC)
        45, 48 -> Icons.Rounded.Dehaze to Color(0xFFCBD5E1)
        51, 53, 55, 56, 57 -> Icons.Rounded.Grain to AuroraCyan
        61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.WaterDrop to AuroraCyan
        71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit to Color(0xFFE0F2FE)
        95, 96, 99 -> Icons.Rounded.Thunderstorm to Color(0xFFFBBF24)
        else -> Icons.Rounded.WbCloudy to Color(0xFFB5C7DC)
    }
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WeatherSkeletonLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmerColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(78.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(shimmerColor)
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(shimmerColor)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
