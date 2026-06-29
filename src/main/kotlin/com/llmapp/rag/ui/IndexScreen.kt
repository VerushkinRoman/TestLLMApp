package com.llmapp.rag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IndexScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { IndexViewModel() }
    val state by viewModel.state.collectAsState()
    val action by viewModel.actions.collectAsState(initial = null)

    LaunchedEffect(action) {
        action?.let {
            viewModel.obtainEvent(IndexEvent.ClearAction)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "RAG Index Pipeline",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Индексация документов ЧМ-2026 с эмбеддингами и сравнение стратегий chunking",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { viewModel.obtainEvent(IndexEvent.BuildIndex) },
                enabled = !state.isBuilding,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.isBuilding) "Building..." else "Build Index")
            }

            OutlinedButton(
                onClick = { viewModel.obtainEvent(IndexEvent.ShowFixedIndex) },
                enabled = !state.isBuilding,
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Fixed")
            }

            OutlinedButton(
                onClick = { viewModel.obtainEvent(IndexEvent.ShowStructuralIndex) },
                enabled = !state.isBuilding,
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Struct")
            }
        }

        if (state.isBuilding) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.fixedSizeStats?.let { stats ->
                StatsCard(
                    title = "Fixed-Size Chunking",
                    stats = stats,
                    modifier = Modifier.weight(1f),
                )
            }
            state.structuralStats?.let { stats ->
                StatsCard(
                    title = "Structural Chunking",
                    stats = stats,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.fixedSizeStats != null && state.structuralStats != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ComparisonCard(
                fixed = state.fixedSizeStats!!,
                structural = state.structuralStats!!,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SearchSection(
            searchQuery = state.searchQuery,
            isSearching = state.isSearching,
            searchResults = state.searchResults,
            isIndexed = state.isIndexed,
            onSearchQueryChange = { viewModel.obtainEvent(IndexEvent.Search(it)) },
            onSearch = { viewModel.obtainEvent(IndexEvent.Search(state.searchQuery)) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        LogSection(log = state.log)
    }
}

@Composable
private fun StatsCard(
    title: String,
    stats: IndexStats,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatRow("Documents", "${stats.totalDocuments}")
            StatRow("Chunks", "${stats.totalChunks}")
            StatRow("Avg chunk", "${stats.avgChunkChars} chars")
            StatRow("Min chunk", "${stats.minChunkChars} chars")
            StatRow("Max chunk", "${stats.maxChunkChars} chars")
            StatRow("Total chars", "${stats.totalChars}")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ComparisonCard(
    fixed: IndexStats,
    structural: IndexStats,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Comparison: Fixed-Size vs Structural Chunking",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))

            val diffChunks = fixed.totalChunks - structural.totalChunks
            val chunksText = if (diffChunks > 0) "fixed creates $diffChunks more chunks"
            else if (diffChunks < 0) "structural creates ${-diffChunks} more chunks"
            else "same number of chunks"

            val diffAvg = fixed.avgChunkChars - structural.avgChunkChars
            val avgText = if (diffAvg > 0) "fixed chunks are $diffAvg chars larger on average"
            else if (diffAvg < 0) "structural chunks are ${-diffAvg} chars larger on average"
            else "same average size"

            val totalFixed = fixed.totalChars
            val totalStruct = structural.totalChars
            val coverageText = if (totalFixed > totalStruct) {
                "fixed covers ${totalFixed - totalStruct} more chars (less overlap loss)"
            } else {
                "structural covers ${totalStruct - totalFixed} more chars (better coverage)"
            }

            Text(
                text = "• Chunks: $chunksText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "• Size: $avgText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "• Coverage: $coverageText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            val recommendation = when {
                fixed.totalChunks < structural.totalChunks * 0.7 ->
                    "Fixed-size creates fewer, larger chunks — good for general retrieval"

                structural.totalChunks < fixed.totalChunks * 0.7 ->
                    "Structural creates fewer, semantically coherent chunks — good for Q&A"

                structural.totalChunks > fixed.totalChunks * 1.5 ->
                    "Fixed-size is more compact. Consider merging small sections."

                else ->
                    "Both strategies produce similar granularity. Choose based on use case."
            }
            Text(
                text = "Recommendation: $recommendation",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSection(
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<com.llmapp.rag.domain.SearchResult>,
    isIndexed: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Search Index",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search query...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = isIndexed,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                FilledTonalButton(
                    onClick = onSearch,
                    enabled = isIndexed && searchQuery.isNotBlank() && !isSearching,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search", fontSize = 12.sp)
                }
            }

            if (isSearching) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                searchResults.forEach { result ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Rank #${result.rank}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Score: ${"%.4f".format(result.score)}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "[${result.chunk.source}] ${result.chunk.title}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Section: ${result.chunk.section}",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = result.chunk.content.take(200) + "...",
                                fontSize = 10.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogSection(log: List<String>) {
    if (log.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Build Log",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .verticalScroll(scrollState),
            ) {
                log.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}
