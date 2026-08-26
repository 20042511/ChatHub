package me.rerere.readstack.ui.detail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.SourceKind
import me.rerere.readstack.ui.Route
import me.rerere.readstack.ui.component.DocumentIcon
import me.rerere.readstack.ui.component.SourceChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    nav: NavHostController,
    args: Route.Detail,
    vm: DetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ref = state.ref

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(ref.title, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DocumentIcon(ref.source, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ref.title, style = MaterialTheme.typography.headlineSmall)
                    if (!ref.subtitle.isNullOrBlank()) {
                        Text(
                            ref.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceChip(ref.source)
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        ref.format.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (!ref.description.isNullOrBlank()) {
                Text(ref.description, style = MaterialTheme.typography.bodyLarge)
            }
            Metadata(ref)

            ActionBlock(
                state = state,
                onDownload = { vm.startDownload() },
                onCancel = { vm.cancelDownload() },
                onOpen = { state.localId?.let { nav.navigate(Route.Reader(it)) } },
            )
        }
    }
}

@Composable
private fun Metadata(ref: DocumentRef) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ref.author?.takeIf { it.isNotBlank() }?.let {
            Row { MetaLabel("Author"); Spacer(Modifier.weight(1f)); Text(it) }
        }
        ref.language.takeIf { it.isNotBlank() }?.let {
            Row { MetaLabel("Language"); Spacer(Modifier.weight(1f)); Text(it) }
        }
        Row {
            MetaLabel("Source")
            Spacer(Modifier.weight(1f))
            Text(
                ref.sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun MetaLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActionBlock(
    state: DetailUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
) {
    when {
        state.localId != null -> {
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Outlined.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("Open in reader")
            }
        }
        state.downloadState is DownloadState.Running ||
            state.downloadState is DownloadState.Enqueued -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { (state.progress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                state.stage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Cancel download") }
            }
        }
        state.downloadState is DownloadState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (state.downloadState as DownloadState.Failed).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("Retry") }
            }
        }
        else -> {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Outlined.CloudDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Download for offline")
            }
        }
    }
}
