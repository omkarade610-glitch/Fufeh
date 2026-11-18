package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.services.VoiceStatus
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.NeonViolet
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun JarvisAiOrb(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    voiceStatus: VoiceStatus = VoiceStatus.IDLE,
    amplitude: Float = 0f
) {
    val infinite = rememberInfiniteTransition(label = "jarvis_orb")
    val outerAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7200, easing = LinearEasing)),
        label = "outer_angle"
    )
    val innerAngle by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(4600, easing = LinearEasing)),
        label = "inner_angle"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (voiceStatus) {
                    VoiceStatus.LISTENING -> 650
                    VoiceStatus.SPEAKING -> 750
                    VoiceStatus.THINKING -> 500
                    VoiceStatus.IDLE -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryGlow = when (voiceStatus) {
        VoiceStatus.LISTENING -> EmeraldSignal
        VoiceStatus.THINKING -> NeonViolet
        VoiceStatus.SPEAKING -> ElectricCyan
        VoiceStatus.IDLE -> ElectricCyan
    }
    val secondaryGlow = when (voiceStatus) {
        VoiceStatus.LISTENING -> ElectricCyan
        VoiceStatus.THINKING -> CobaltBlue
        VoiceStatus.SPEAKING -> NeonViolet
        VoiceStatus.IDLE -> CobaltBlue
    }

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val dynamicBoost = 1f + (amplitude.coerceIn(0f, 1f) * 0.16f)

        // Ambient halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.42f),
                    secondaryGlow.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * dynamicBoost
            ),
            radius = radius * dynamicBoost,
            center = center
        )

        // Outer HUD arc
        val outerStroke = radius * 0.06f
        val outerInset = radius * 0.12f
        drawArc(
            color = primaryGlow.copy(alpha = 0.85f),
            startAngle = outerAngle,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(outerInset, outerInset),
            size = Size(this.size.width - outerInset * 2, this.size.height - outerInset * 2),
            style = Stroke(width = outerStroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = NeonViolet.copy(alpha = 0.65f),
            startAngle = outerAngle + 180f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(outerInset, outerInset),
            size = Size(this.size.width - outerInset * 2, this.size.height - outerInset * 2),
            style = Stroke(width = outerStroke, cap = StrokeCap.Round)
        )

        // Counter-rotating inner ring
        val innerInset = radius * 0.27f
        drawArc(
            color = secondaryGlow.copy(alpha = 0.9f),
            startAngle = innerAngle,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(innerInset, innerInset),
            size = Size(this.size.width - innerInset * 2, this.size.height - innerInset * 2),
            style = Stroke(width = outerStroke * 0.85f, cap = StrokeCap.Round)
        )

        // Glowing core sphere
        val coreRadius = radius * 0.40f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, primaryGlow, secondaryGlow.copy(alpha = 0.8f)),
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )
    }
}

@Composable
fun VoiceWaveformBar(
    voiceStatus: VoiceStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "waveform")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "wave_phase"
    )
    val barColor = when (voiceStatus) {
        VoiceStatus.LISTENING -> EmeraldSignal
        VoiceStatus.SPEAKING -> ElectricCyan
        VoiceStatus.THINKING -> NeonViolet
        VoiceStatus.IDLE -> MaterialTheme.colorScheme.outline
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val barCount = 24
        val spacing = size.width / barCount
        val barWidth = spacing * 0.48f
        val activeScale = when (voiceStatus) {
            VoiceStatus.LISTENING -> 0.45f + amplitude.coerceIn(0f, 1f) * 0.55f
            VoiceStatus.SPEAKING -> 0.72f
            VoiceStatus.THINKING -> 0.42f
            VoiceStatus.IDLE -> 0.14f
        }
        for (i in 0 until barCount) {
            val wave = ((sin(phase + i * 0.55f) + 1f) / 2f)
            val barHeight = (size.height * (0.18f + wave * 0.82f * activeScale)).coerceAtLeast(4.dp.toPx())
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (size.height - barHeight) / 2f
            drawLine(
                color = barColor,
                start = Offset(x + barWidth / 2f, y),
                end = Offset(x + barWidth / 2f, y + barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            NeonViolet.copy(alpha = 0.30f)
        )
    )
    Surface(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = surfaceColor,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun StatusPillBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StudioSelectorStrip(
    items: List<Pair<String, ImageVector>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    testTagPrefix: String
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        items(items.indices.toList()) { index ->
            val (label, icon) = items[index]
            val selected = index == selectedIndex
            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .testTag("${testTagPrefix}_$index")
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(index) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SetupRequiredBanner(
    serviceName: String,
    reason: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    GlassPanel(
        accentColor = AmberAlert,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = "Setup required",
                tint = AmberAlert,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "SETUP REQUIRED – CONNECT EXTERNAL SERVICE",
                style = MaterialTheme.typography.labelLarge,
                color = AmberAlert,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$serviceName: $reason",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = AmberAlert, contentColor = Color.Black),
                modifier = Modifier.height(48.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorBannerCard(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    GlassPanel(
        accentColor = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
