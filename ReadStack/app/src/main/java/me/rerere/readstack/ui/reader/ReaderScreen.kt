package me.rerere.readstack.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.distinctUntilChanged
import me.rerere.readstack.domain.model.Annotation
import me.rerere.readstack.domain.model.AnnotationKind
import me.rerere.readstack.ui.Route
import me.rerere.readstack.ui.component.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    nav: NavHostController,
    @Suppress("UNUSED_PARAMETER") args: Route.Reader,
    vm: ReaderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    var showNoteDialog by remember { mutableStateOf(false) }

    // Persist read progress on every distinct scroll position
    LaunchedEffect(scroll, state.document?.id) {
        snapshotFlow { scroll.value to scroll.maxValue }
            .distinctUntilChanged()
            .collect { (value, max) ->
                val progress = if (max > 0) value.toFloat() / max else 0f
                vm.onScroll(value, progress)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.document?.ref?.title ?: "Reader", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showNoteDialog = true }) {
                        Icon(Icons.Outlined.NoteAdd, null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.document == null -> Text(
                "Document not found.",
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    val annotated = remember(state.content, state.annotations) {
                        buildAnnotatedString {
                            val text = state.content
                            append(text)
                            state.annotations
                                .filter { it.kind == AnnotationKind.HIGHLIGHT }
                                .forEach { a ->
                                    val start = a.location.coerceIn(0, text.length)
                                    val end = (a.location + a.length).coerceIn(0, text.length)
                                    if (end > start) {
                                        addStyle(
                                            style = SpanStyle(background = Color(a.color)),
                                            start = start,
                                            end = end,
                                        )
                                    }
                                }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (state.annotations.isNotEmpty()) {
                        AnnotationList(
                            annotations = state.annotations,
                            onClick = {},
                            onDelete = vm::deleteAnnotation,
                        )
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Add note") },
            text = {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("What's on your mind?") },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (body.isNotBlank()) {
                        vm.addNote(body)
                        body = ""
                    }
                    showNoteDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AnnotationList(
    annotations: List<Annotation>,
    onClick: (Annotation) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Annotations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height((annotations.size * 80).dp.coerceAtMost(400.dp)),
            ) {
                items(annotations, key = { it.id }) { a ->
                    AnnotationRow(a, onClick = { onClick(a) }, onDelete = { onDelete(a.id) })
                }
            }
        }
    }
}

@Composable
private fun AnnotationRow(a: Annotation, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (a.kind == AnnotationKind.NOTE) Icons.Outlined.Bookmark else Icons.Outlined.HighlightAlt,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(a.text, style = MaterialTheme.typography.bodyMedium)
            a.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}
