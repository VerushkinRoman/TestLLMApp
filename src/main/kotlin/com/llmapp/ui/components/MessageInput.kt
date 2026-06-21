package com.llmapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessageInput(
    inputText: String,
    cursorPosition: Int,
    onInputChange: (String, Int) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: (() -> Unit)? = null,
    isGenerating: Boolean = false,
    isDemoRunning: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDemoRunning) {
                OutlinedTextField(
                    value = "🔬 Демонстрация запущена... Ожидайте завершения",
                    onValueChange = {},
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                    placeholder = { Text("Демонстрация запущена...") },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    readOnly = true,
                    enabled = false
                )

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                var textFieldValue by remember(inputText) {
                    mutableStateOf(
                        TextFieldValue(
                            text = inputText,
                            selection = TextRange(cursorPosition)
                        )
                    )
                }

                LaunchedEffect(inputText) {
                    if (textFieldValue.text != inputText) {
                        textFieldValue = TextFieldValue(
                            text = inputText,
                            selection = TextRange(cursorPosition)
                        )
                    }
                }

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        if (!isGenerating) {
                            textFieldValue = newValue
                            onInputChange(newValue.text, newValue.selection.start)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        )
                        .onKeyEvent { keyEvent ->
                            when (keyEvent.key) {
                                Key.Enter if !keyEvent.isCtrlPressed && !isGenerating -> {
                                    onSendMessage()
                                    true
                                }

                                Key.Enter if keyEvent.isCtrlPressed -> {
                                    if (!isGenerating) {
                                        val newValue = textFieldValue.copy(
                                            text = textFieldValue.text + "\n",
                                            selection = TextRange(textFieldValue.text.length + 1)
                                        )
                                        textFieldValue = newValue
                                        onInputChange(newValue.text, newValue.selection.start)
                                    }
                                    true
                                }

                                else -> false
                            }
                        },
                    placeholder = { Text("Введите сообщение...") },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (!isGenerating)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = if (!isGenerating)
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedTextColor = if (!isGenerating)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedTextColor = if (!isGenerating)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (isGenerating) ImeAction.None else ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating) {
                                onSendMessage()
                            }
                        }
                    ),
                    singleLine = false,
                    maxLines = 5,
                    readOnly = isGenerating
                )

                if (isGenerating && onStopGeneration != null) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(onClick = onStopGeneration),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = Color.White,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(enabled = !isGenerating) {
                                if (!isGenerating) {
                                    onSendMessage()
                                }
                            },
                        shape = CircleShape,
                        color = if (!isGenerating)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shadowElevation = if (!isGenerating) 6.dp else 0.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send",
                                modifier = Modifier.size(24.dp),
                                tint = if (!isGenerating)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
