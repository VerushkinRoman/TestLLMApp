package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var experience by remember { mutableStateOf(profile.experience) }
    var style by remember { mutableStateOf(profile.preferredStyle) }
    var techString by remember { mutableStateOf(profile.preferredTech.joinToString(", ")) }
    var goalsString by remember { mutableStateOf(profile.commonGoals.joinToString(", ")) }
    var notes by remember { mutableStateOf(profile.customNotes) }

    var styleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Редактирование профиля") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = experience,
                    onValueChange = { experience = it },
                    label = { Text("Опыт (junior/middle/senior)") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = styleExpanded,
                    onExpandedChange = { styleExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (style) {
                            ResponseStyle.CONCISE -> "Краткий"
                            ResponseStyle.DETAILED -> "Детальный"
                            ResponseStyle.BALANCED -> "Сбалансированный"
                            ResponseStyle.TECHNICAL -> "Технический"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Стиль ответов") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = styleExpanded,
                        onDismissRequest = { styleExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Краткий (concise)") },
                            onClick = { style = ResponseStyle.CONCISE; styleExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Детальный (detailed)") },
                            onClick = { style = ResponseStyle.DETAILED; styleExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Сбалансированный (balanced)") },
                            onClick = { style = ResponseStyle.BALANCED; styleExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Технический (technical)") },
                            onClick = { style = ResponseStyle.TECHNICAL; styleExpanded = false }
                        )
                    }
                }

                OutlinedTextField(
                    value = techString,
                    onValueChange = { techString = it },
                    label = { Text("Технологии (через запятую)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Kotlin, Compose, KMP") }
                )

                OutlinedTextField(
                    value = goalsString,
                    onValueChange = { goalsString = it },
                    label = { Text("Цели (через запятую)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Изучить KMP, Улучшить архитектуру") }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedProfile = UserProfile(
                        name = name,
                        experience = experience,
                        preferredStyle = style,
                        preferredTech = techString.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() },
                        commonGoals = goalsString.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() },
                        customNotes = notes
                    )
                    onSave(updatedProfile)
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ConstraintsEditDialog(
    constraints: ProjectConstraints,
    onDismiss: () -> Unit,
    onSave: (ProjectConstraints) -> Unit
) {
    var techStackString by remember { mutableStateOf(constraints.techStack.joinToString(", ")) }
    var forbiddenTechString by remember { mutableStateOf(constraints.forbiddenTech.joinToString(", ")) }
    var architecture by remember { mutableStateOf(constraints.architecture) }
    var standards by remember { mutableStateOf(constraints.codingStandards) }
    var rules by remember { mutableStateOf(constraints.specialRules) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔧 Редактирование ограничений") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = techStackString,
                    onValueChange = { techStackString = it },
                    label = { Text("Технологический стек (через запятую)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Kotlin, KMP, Compose") }
                )

                OutlinedTextField(
                    value = forbiddenTechString,
                    onValueChange = { forbiddenTechString = it },
                    label = { Text("Запрещенные технологии (через запятую)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Java, RxJava, XML") }
                )

                OutlinedTextField(
                    value = architecture,
                    onValueChange = { architecture = it },
                    label = { Text("Архитектура") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("MVI с Clean Architecture") }
                )

                OutlinedTextField(
                    value = standards,
                    onValueChange = { standards = it },
                    label = { Text("Стандарты кодирования") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("Kotlin Coding Conventions, Detekt") }
                )

                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    label = { Text("Особые правила") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedConstraints = ProjectConstraints(
                        techStack = techStackString.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() },
                        forbiddenTech = forbiddenTechString.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() },
                        architecture = architecture,
                        codingStandards = standards,
                        specialRules = rules
                    )
                    onSave(updatedConstraints)
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
