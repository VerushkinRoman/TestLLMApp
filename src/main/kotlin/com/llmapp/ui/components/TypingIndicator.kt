package com.llmapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val dots = listOf(0, 1, 2)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 140.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Думает",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                dots.forEach { index ->
                    val delay = index * 200

                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(delay)
                        ),
                        label = "scale_$index"
                    )

                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(delay)
                        ),
                        label = "alpha_$index"
                    )

                    Box(
                        modifier = Modifier
                            .size(6.dp * scale)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                    )
                }
            }
        }
    }
}
