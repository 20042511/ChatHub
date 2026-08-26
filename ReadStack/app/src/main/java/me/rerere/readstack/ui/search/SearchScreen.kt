package me.rerere.readstack.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.readstack.R
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.ui.Route
import me.rerere.readstack.ui.component.DocumentCard
import me.rerere.readstack.ui.component.LibraryCard
import me.rerere.readstack.ui.component.SourceChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    nav: NavHostController,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tabs = remember { listOf(SearchScope.WEB, SearchScope.LOCAL) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SearchBar(
                    query = state.query,
                    onQueryChange = { vm.setQuery(it) },
                    onSearch = { /* nothing — results stream in */ },
                    active = false,
                    onActiveChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                ) { /* suggestions slot, unused */ }
            }
            item {
                if (state.isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
            item {
                SecondaryTabRow(
                    selectedTabIndex = tabs.indexOf(state.scope),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    tabs.forEach { scope ->
                        Tab(
                            selected = state.scope == scope,
                            onClick = { vm.setScope(scope) },
                            text = {
                                Text(
                                    if (scope == SearchScope.WEB) stringResource(R.string.search_web)
                                    else stringResource(R.string.search_local)
                                )
                            },
                        )
                    }
                }
            }

            if (state.scope == SearchScope.LOCAL) {
                if (state.local.isEmpty()) {
                    item {
                        Text(
                            "Nothing in your library yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    items(state.local, key = { it.id }) { doc ->
                        LibraryCard(
                            doc = doc,
                            onClick = { nav.navigate(Route.Reader(doc.id)) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            } else {
                state.web.forEach { (source, paged) ->
                    if (paged.items.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SourceChip(source)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${paged.items.size} results",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(paged.items.size) { i ->
                            DocumentCard(
                                result = paged.items[i],
                                onClick = { nav.openDetail(paged.items[i].ref) },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
                if (state.web.isEmpty() && state.query.isNotBlank() && !state.isSearching) {
                    item {
                        Text(
                            "No results yet. Try a different query.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

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
