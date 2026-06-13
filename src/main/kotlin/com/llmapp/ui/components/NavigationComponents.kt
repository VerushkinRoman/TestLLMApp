package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.ui.models.Screen

@Composable
fun AppNavigationRail(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.width(80.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            NavigationRailItem(
                selected = currentScreen == Screen.Chat,
                onClick = { onScreenSelected(Screen.Chat) },
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, "Chat") },
                label = { Text("Chat", fontSize = 10.sp) }
            )

            NavigationRailItem(
                selected = currentScreen == Screen.Agents,
                onClick = { onScreenSelected(Screen.Agents) },
                icon = { Icon(Icons.Default.Folder, "Chats") },
                label = { Text("Chats", fontSize = 10.sp) }
            )

            NavigationRailItem(
                selected = currentScreen == Screen.Models,
                onClick = { onScreenSelected(Screen.Models) },
                icon = { Icon(Icons.Default.ModelTraining, "Models") },
                label = { Text("Models", fontSize = 10.sp) }
            )

            NavigationRailItem(
                selected = currentScreen == Screen.Demo,
                onClick = { onScreenSelected(Screen.Demo) },
                icon = { Icon(Icons.Default.Science, "Demo") },
                label = { Text("Demo", fontSize = 10.sp) }
            )

            NavigationRailItem(
                selected = currentScreen == Screen.Settings,
                onClick = { onScreenSelected(Screen.Settings) },
                icon = { Icon(Icons.Default.Settings, "Settings") },
                label = { Text("Settings", fontSize = 10.sp) }
            )

            Spacer(modifier = Modifier.weight(1f))

            NavigationRailItem(
                selected = false,
                onClick = onClearHistory,
                icon = { Icon(Icons.Default.Delete, "Clear") },
                label = { Text("Clear", fontSize = 10.sp) }
            )
        }
    }
}
