package me.rerere.readstack.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.SearchResult
import me.rerere.readstack.domain.model.SourceKind
import me.rerere.readstack.ui.Route
import me.rerere.readstack.ui.component.DocumentCard
import me.rerere.readstack.ui.component.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    nav: NavHostController,
    sourceOverride: String? = null,
    vm: BrowseViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Browse",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.isLoading && state.trendingBySource.isEmpty()) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.items.forEach { cat ->
                        SourceTile(cat = cat, onClick = {
                            nav.navigate(Route.SourceDetail(cat.source.name))
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
            state.items.forEach { cat ->
                val items = state.trendingBySource[cat.source]?.items.orEmpty()
                if (items.isNotEmpty()) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Trending on ${cat.title}",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "See all",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(items.size) { i ->
                                    val it = items[i]
                                    DocumentCard(
                                        result = it,
                                        onClick = { nav.openDetail(it.ref) },
                                        modifier = Modifier.width(320.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceTile(
    cat: SourceCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(cat.accent)
    val icon = when (cat.source) {
        SourceKind.GITHUB -> Icons.Outlined.Star
        SourceKind.GUTENBERG -> Icons.Outlined.AutoStories
        SourceKind.WEB_SEARCH -> Icons.Outlined.Public
        else -> Icons.Outlined.MenuBook
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent)
            }
            Spacer(Modifier.height(12.dp))
            Text(cat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                cat.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Open",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ArrowForward, null, tint = accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/* --------- Navigation helpers ---------------------------------- */

private fun NavHostController.openDetail(ref: DocumentRef) {
    navigate(
        Route.Detail(
            source = ref.source.name,
            externalId = ref.externalId,
            title = ref.title,
            subtitle = ref.subtitle,
            author = ref.author,
            coverUrl = ref.coverUrl,
            description = ref.description,
            sourceUrl = ref.sourceUrl,
            downloadUrl = ref.downloadUrl,
            format = ref.format.name,
            language = ref.language,
            tags = ref.tags.joinToString(","),
        )
    )
}
