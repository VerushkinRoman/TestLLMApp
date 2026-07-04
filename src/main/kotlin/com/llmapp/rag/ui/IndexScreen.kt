package com.llmapp.rag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.rag.RagMode
import com.llmapp.rag.domain.RerankerType

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AddArticleSection(
            onAdd = { title, content -> viewModel.obtainEvent(IndexEvent.AddUserArticle(title, content)) },
            userArticles = state.userArticles,
            onRemove = { index -> viewModel.obtainEvent(IndexEvent.RemoveUserArticle(index)) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Построение RAG-индекса",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Индексация документов ЧМ-2026 через HuggingFace с двумя стратегиями разбивки",
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
                Text(if (state.isBuilding) "Построение..." else "Построить индекс")
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
                Text("Fixed-size")
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
                Text("Structural")
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
                    title = "Fixed-Size (по символам)",
                    stats = stats,
                    modifier = Modifier.weight(1f),
                )
            }
            state.structuralStats?.let { stats ->
                StatsCard(
                    title = "Structural (по секциям)",
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

        RerankerConfigSection(
            ragMode = state.ragMode,
            rerankerConfig = state.rerankerConfig,
            onModeChange = { viewModel.obtainEvent(IndexEvent.SetRagMode(it)) },
            onRerankerTypeChange = { viewModel.obtainEvent(IndexEvent.SetRerankerType(it)) },
            onThresholdChange = { viewModel.obtainEvent(IndexEvent.SetThreshold(it)) },
            onTopKBeforeChange = { viewModel.obtainEvent(IndexEvent.SetTopKBefore(it)) },
            onTopKAfterChange = { viewModel.obtainEvent(IndexEvent.SetTopKAfter(it)) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        SearchSection(
            searchQuery = state.searchQuery,
            isSearching = state.isSearching,
            searchResults = state.searchResults,
            isIndexed = state.isIndexed,
            isComparing = state.isComparing,
            comparisonResult = state.comparisonResult,
            ragMode = state.ragMode,
            onSearchQueryChange = { viewModel.obtainEvent(IndexEvent.Search(it)) },
            onSearch = { viewModel.obtainEvent(IndexEvent.Search(state.searchQuery)) },
            onCompare = { viewModel.obtainEvent(IndexEvent.CompareModes) },
            onClearComparison = { viewModel.obtainEvent(IndexEvent.ClearComparison) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        LogSection(log = state.log)
    }
}

@Composable
private fun AddArticleSection(
    onAdd: (title: String, content: String) -> Unit,
    userArticles: List<UserArticle>,
    onRemove: (Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Добавить свою статью",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Название статьи", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("Текст статьи (plain text)...", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                maxLines = 8,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Добавлено: ${userArticles.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FilledTonalButton(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            onAdd(title.trim(), content.trim())
                            title = ""
                            content = ""
                        }
                    },
                    enabled = title.isNotBlank() && content.isNotBlank(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить", fontSize = 12.sp)
                }
            }

            if (userArticles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                userArticles.forEachIndexed { index, article ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}. ${article.title}",
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${article.content.length} симв.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(
                                onClick = { onRemove(index) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Удалить",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RerankerConfigSection(
    ragMode: RagMode,
    rerankerConfig: com.llmapp.rag.domain.RerankerConfig,
    onModeChange: (RagMode) -> Unit,
    onRerankerTypeChange: (RerankerType) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onTopKBeforeChange: (Int) -> Unit,
    onTopKAfterChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Режим поиска и реранкинг",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RagMode.entries.forEach { mode ->
                    FilterChip(
                        selected = ragMode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(mode.label, fontSize = 11.sp) },
                    )
                }
            }

            if (ragMode != RagMode.BASIC) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RerankerType.entries.forEach { type ->
                        FilterChip(
                            selected = rerankerConfig.type == type,
                            onClick = { onRerankerTypeChange(type) },
                            label = {
                                Text(
                                    when (type) {
                                        RerankerType.NONE -> "Без реранкера"
                                        RerankerType.SIMILARITY_THRESHOLD -> "По порогу"
                                        RerankerType.HEURISTIC -> "Эвристический"
                                    },
                                    fontSize = 10.sp,
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                var sliderValue by remember(rerankerConfig.similarityThreshold) {
                    mutableFloatStateOf(rerankerConfig.similarityThreshold)
                }
                Text(
                    text = "Порог отсечения: ${"%.2f".format(sliderValue)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onThresholdChange(sliderValue) },
                    valueRange = 0f..0.9f,
                    steps = 17,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Top-K до фильтрации: ${rerankerConfig.topKBefore}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Top-K после фильтрации: ${rerankerConfig.topKAfter}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                var topKBefore by remember(rerankerConfig.topKBefore) {
                    androidx.compose.runtime.mutableIntStateOf(rerankerConfig.topKBefore)
                }
                Slider(
                    value = topKBefore.toFloat(),
                    onValueChange = { topKBefore = it.toInt(); onTopKBeforeChange(it.toInt()) },
                    valueRange = 5f..50f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                var topKAfter by remember(rerankerConfig.topKAfter) {
                    androidx.compose.runtime.mutableIntStateOf(rerankerConfig.topKAfter)
                }
                Slider(
                    value = topKAfter.toFloat(),
                    onValueChange = { topKAfter = it.toInt(); onTopKAfterChange(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    isComparing: Boolean,
    comparisonResult: RagComparisonResult?,
    ragMode: RagMode,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCompare: () -> Unit,
    onClearComparison: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Поиск по индексу (режим: ${ragMode.label})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Поисковый запрос...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = isIndexed,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                FilledTonalButton(
                    onClick = onSearch,
                    enabled = isIndexed && searchQuery.isNotBlank() && !isSearching && !isComparing,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Найти", fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = onCompare,
                    enabled = isIndexed && searchQuery.isNotBlank() && !isSearching && !isComparing,
                ) {
                    Icon(
                        Icons.Default.Compare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Сравнить режимы", fontSize = 11.sp)
                }
            }

            if (isSearching || isComparing) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isComparing) "Сравниваю 3 режима..." else "Поиск...",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            comparisonResult?.let { ComparisonResultsSection(it, onClearComparison) }

            if (searchResults.isNotEmpty() && comparisonResult == null) {
                Spacer(modifier = Modifier.height(8.dp))
                searchResults.forEach { result ->
                    SearchResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun ComparisonResultsSection(
    result: RagComparisonResult,
    onClear: () -> Unit,
) {
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Сравнение режимов",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                FilledTonalButton(onClick = onClear) {
                    Text("Закрыть", fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Запрос: \"${result.originalQuery}\"",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.rewrittenQuery != result.originalQuery) {
                Text(
                    text = "Расширенный: \"${result.rewrittenQuery}\"",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            ModeResultRow(
                modeName = "Базовый",
                modeIcon = "🔍",
                chunksCount = result.basicChunksCount,
                timeMs = result.basicTimeMs,
                results = result.basicResults,
                color = Color(0xFF757575),
            )

            ModeResultRow(
                modeName = "Фильтр (порог ${"%.2f".format(result.filteredThreshold)})",
                modeIcon = "🔎",
                chunksCount = result.filteredChunksCount,
                timeMs = result.filteredTimeMs,
                removedCount = result.filteredRemoved,
                results = result.filteredResults,
                color = Color(0xFF1976D2),
            )

            ModeResultRow(
                modeName = "Rewrite + Фильтр",
                modeIcon = "✨",
                chunksCount = result.rewriteFilterChunksCount,
                timeMs = result.rewriteFilterTimeMs,
                removedCount = result.rewriteFilterRemoved,
                results = result.rewriteFilterResults,
                color = Color(0xFF388E3C),
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Общее время сравнения: ${result.totalComparisonTimeMs}мс",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeResultRow(
    modeName: String,
    modeIcon: String,
    chunksCount: Int,
    timeMs: Long,
    removedCount: Int? = null,
    results: List<com.llmapp.rag.domain.SearchResult>,
    color: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = modeIcon, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = modeName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "чанков: $chunksCount",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${timeMs}мс",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (removedCount != null && removedCount > 0) {
                Text(
                    text = "Отсеяно нерелевантных: $removedCount",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (results.isNotEmpty()) {
                results.take(3).forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "#${r.rank} ${r.chunk.title}",
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "%.3f".format(r.score),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color,
                        )
                    }
                }
                if (results.size > 3) {
                    Text(
                        text = "... и ещё ${results.size - 3}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: com.llmapp.rag.domain.SearchResult) {
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
                    text = "Место #${result.rank}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Релевантность: ${"%.4f".format(result.score)}",
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
                text = "Раздел: ${result.chunk.section}",
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
            StatRow("Документов", "${stats.totalDocuments}")
            StatRow("Чанков", "${stats.totalChunks}")
            StatRow("Средний чанк", "${stats.avgChunkChars} симв.")
            StatRow("Мин. чанк", "${stats.minChunkChars} симв.")
            StatRow("Макс. чанк", "${stats.maxChunkChars} симв.")
            StatRow("Всего символов", "${stats.totalChars}")
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
                text = "Сравнение: Fixed-Size vs Structural",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))

            val diffChunks = fixed.totalChunks - structural.totalChunks
            val chunksText = if (diffChunks > 0) "fixed создаёт на $diffChunks чанков больше"
            else if (diffChunks < 0) "structural создаёт на ${-diffChunks} чанков больше"
            else "одинаковое количество чанков"

            val diffAvg = fixed.avgChunkChars - structural.avgChunkChars
            val avgText = if (diffAvg > 0) "fixed чанки в среднем на $diffAvg символов длиннее"
            else if (diffAvg < 0) "structural чанки в среднем на ${-diffAvg} символов длиннее"
            else "одинаковый средний размер"

            val totalFixed = fixed.totalChars
            val totalStruct = structural.totalChars
            val coverageText = if (totalFixed > totalStruct) {
                "fixed покрывает на ${totalFixed - totalStruct} символов больше (меньше потерь при наложении)"
            } else {
                "structural покрывает на ${totalStruct - totalFixed} символов больше (лучшее покрытие)"
            }

            Text(
                text = "• Чанки: $chunksText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "• Размер: $avgText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "• Покрытие: $coverageText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            val recommendation = when {
                fixed.totalChunks < structural.totalChunks * 0.7 ->
                    "Fixed-size даёт меньше, но крупнее чанков — хорошо для общего поиска"

                structural.totalChunks < fixed.totalChunks * 0.7 ->
                    "Structural даёт меньше семантически связных чанков — хорошо для Q&A"

                structural.totalChunks > fixed.totalChunks * 1.5 ->
                    "Fixed-size компактнее. Попробуй объединить мелкие секции."

                else ->
                    "Обе стратегии дают похожую гранулярность. Выбирай по задаче."
            }
            Text(
                text = "Рекомендация: $recommendation",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
            )
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
                text = "Лог построения",
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
