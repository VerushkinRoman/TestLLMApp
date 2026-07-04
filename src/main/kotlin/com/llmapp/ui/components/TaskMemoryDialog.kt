package com.llmapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.memory.TaskMemory

private const val TRUNCATE_LENGTH = 120

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskMemoryDialog(
    memory: TaskMemory?,
    onDismiss: () -> Unit,
) {
    if (memory == null || memory.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\uD83E\uDDE0 Memory", fontSize = 18.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Text(
                    "Task memory is empty. Start a conversation and the system will automatically extract goals, constraints, progress, decisions and context.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {},
        )
        return
    }

    val p = memory.progress

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83E\uDDE0", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Memory", fontSize = 18.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (memory.goal.isNotEmpty()) {
                    DialogSectionLabel("Goal")
                    ExpandableGoalText(text = memory.goal)
                }

                if (memory.constraintsAndPrefs.isNotEmpty()) {
                    val shown = minOf(memory.constraintsAndPrefs.size, 10)
                    DialogSectionLabel("Constraints & Preferences ($shown of ${memory.constraintsAndPrefs.size})")
                    memory.constraintsAndPrefs.takeLast(shown).forEach { item ->
                        ExpandableBulletText(text = item)
                    }
                }

                if (p.done.isNotEmpty()) {
                    val shown = minOf(p.done.size, 10)
                    DialogSectionLabel("Done ($shown of ${p.done.size})")
                    p.done.takeLast(shown).forEach { item ->
                        ExpandableBulletText(text = item)
                    }
                }

                if (p.inProgress.isNotEmpty()) {
                    val shown = minOf(p.inProgress.size, 10)
                    DialogSectionLabel("In Progress ($shown of ${p.inProgress.size})")
                    p.inProgress.takeLast(shown).forEach { item ->
                        ExpandableBulletText(text = item)
                    }
                }

                if (p.blocked.isNotEmpty()) {
                    val shown = minOf(p.blocked.size, 10)
                    DialogSectionLabel("Blocked ($shown of ${p.blocked.size})")
                    p.blocked.takeLast(shown).forEach { item ->
                        ExpandableBulletText(text = item)
                    }
                }

                if (memory.decisions.isNotEmpty()) {
                    DialogSectionLabel("Key Decisions (${memory.decisions.size})")
                    memory.decisions.forEach { decision ->
                        ExpandableBulletText(text = decision)
                    }
                }

                if (memory.criticalContext.isNotEmpty()) {
                    val shown = minOf(memory.criticalContext.size, 20)
                    DialogSectionLabel("Critical Context ($shown of ${memory.criticalContext.size})")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        memory.criticalContext.takeLast(shown).forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ExpandableBulletText(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val truncated = text.length > TRUNCATE_LENGTH

    Text(
        text = "\u2022 " + if (truncated && !expanded) text.take(TRUNCATE_LENGTH) + " \u2026" else text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        lineHeight = 15.sp,
    )
}

@Composable
private fun ExpandableGoalText(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val truncated = text.length > TRUNCATE_LENGTH

    Text(
        text = if (truncated && !expanded) text.take(TRUNCATE_LENGTH) + " \u2026" else text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        lineHeight = 15.sp,
    )
}

@Composable
fun DialogSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 1.dp)
    )
}
