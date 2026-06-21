package com.llmapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MemoryStatusChip(
    label: String,
    active: Boolean,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

data class MemorySettings(
    val useShortTerm: Boolean = true,
    val useWorkingMemory: Boolean = true,
    val useLongTerm: Boolean = true,
    val resetWorkingMemory: Boolean = false
)
