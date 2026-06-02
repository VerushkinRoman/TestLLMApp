package com.llmapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.llmapp.ui.components.ChatTopBar
import com.llmapp.ui.components.MessageInput
import com.llmapp.ui.components.MessageList
import com.llmapp.ui.models.ChatMessageUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessageUI>,
    isTyping: Boolean,
    currentModel: String,
    controlEnabled: Boolean,
    onSendMessage: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
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

        MessageList(
            messages = messages,
            isTyping = isTyping,
            listState = listState,
            modifier = Modifier.weight(1f)
        )

        MessageInput(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSendMessage = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                    focusRequester.requestFocus()
                }
            },
            modifier = Modifier.imePadding(),
            focusRequester = focusRequester
        )
    }
}
