package com.llmapp.ui.screens

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.llmapp.ui.components.ChatTopBar
import com.llmapp.ui.components.MessageBubble
import com.llmapp.ui.components.MessageInput
import com.llmapp.ui.components.TypingIndicator
import com.llmapp.ui.models.ChatMessageUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessageUI>,
    isTyping: Boolean,
    currentModel: String,
    controlEnabled: Boolean,
    inputText: String,
    cursorPosition: Int,
    onInputTextChange: (String, Int) -> Unit,
    onSendMessage: (String) -> Unit,
    onRegenerateMessage: ((String) -> Unit)? = null,
    onEditMessage: ((String, String) -> Unit)? = null,
    onStopGeneration: (() -> Unit)? = null,
    isGenerating: Boolean = false
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            controlEnabled = controlEnabled,
            currentModel = currentModel,
        )

        Row(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().padding(end = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            onRegenerate = if (message.role == "assistant" && onRegenerateMessage != null) {
                                { onRegenerateMessage(message.id) }
                            } else null,
                            onEdit = if (message.role == "user" && onEditMessage != null) {
                                { newText -> onEditMessage(message.id, newText) }
                            } else null,
                            isRegenerating = false
                        )
                    }

                    if (isTyping) {
                        item(key = "typing_indicator") {
                            TypingIndicator()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            VerticalScrollbar(
                modifier = Modifier.width(12.dp),
                adapter = rememberScrollbarAdapter(listState),
                style = ScrollbarStyle(
                    minimalHeight = 30.dp,
                    thickness = 12.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            )
        }

        MessageInput(
            inputText = inputText,
            cursorPosition = cursorPosition,
            onInputChange = onInputTextChange,
            onSendMessage = {
                if (inputText.isNotBlank() && !isGenerating) {
                    onSendMessage(inputText)
                    focusRequester.requestFocus()
                }
            },
            onStopGeneration = onStopGeneration,
            isGenerating = isGenerating,
            modifier = Modifier.imePadding(),
            focusRequester = focusRequester
        )
    }
}
