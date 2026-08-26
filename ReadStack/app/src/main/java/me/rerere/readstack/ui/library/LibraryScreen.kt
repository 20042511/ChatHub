package me.rerere.readstack.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.readstack.R
import me.rerere.readstack.domain.model.LocalDocument
import me.rerere.readstack.ui.Route
import me.rerere.readstack.ui.component.EmptyState
import me.rerere.readstack.ui.component.LibraryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    nav: NavHostController,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.documents.isEmpty() && !state.isLoading) {
            EmptyState(
                icon = Icons.Outlined.AutoStories,
                title = "Your library is empty",
                description = "Search or browse to find something worth reading.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LibraryGrid(
                documents = state.documents,
                contentPadding = padding,
                onClick = { doc -> nav.navigate(Route.Reader(doc.id)) },
                onLongClick = { doc -> vm.delete(doc.id) },
            )
        }
    }
}

@Composable
private fun LibraryGrid(
    documents: List<LocalDocument>,
    contentPadding: PaddingValues,
    onClick: (LocalDocument) -> Unit,
    onLongClick: (LocalDocument) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        items(documents, key = { it.id }) { doc ->
            LibraryCard(
                doc = doc,
                onClick = { onClick(doc) },
                onLongClick = { onLongClick(doc) },
            )
        }
    }
}
