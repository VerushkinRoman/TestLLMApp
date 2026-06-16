package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileWelcomeDialog(
    onSetupProfile: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                "👋 Добро пожаловать в LLM Chat!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Давайте настроим ваш профиль, чтобы ассистент мог давать персонализированные ответы.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "🎯 Что вы получите:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("• Ответы с учетом вашего стека технологий", fontSize = 13.sp)
                    Text("• Персонализированный стиль общения", fontSize = 13.sp)
                    Text("• Учет ограничений вашего проекта", fontSize = 13.sp)
                    Text("• Сохранение профиля между сессиями", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Или выберите готовый пресет:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetQuickButton(
                        icon = "📱",
                        label = "Android",
                        onClick = {
                            onSetupProfile()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetQuickButton(
                        icon = "🌐",
                        label = "Fullstack",
                        onClick = {
                            onSetupProfile()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetQuickButton(
                        icon = "🌱",
                        label = "Junior",
                        onClick = {
                            onSetupProfile()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetQuickButton(
                        icon = "🏗️",
                        label = "Architect",
                        onClick = {
                            onSetupProfile()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSetupProfile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("✏️ Настроить профиль")
                }

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(0.6f)
                ) {
                    Text("Пропустить")
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun PresetQuickButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(icon, fontSize = 18.sp)
            Text(label, fontSize = 11.sp)
        }
    }
}
