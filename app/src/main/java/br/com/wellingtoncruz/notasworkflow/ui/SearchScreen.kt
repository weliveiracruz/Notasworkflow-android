// NotasWorkflow Android - SearchScreen.kt
// Search: the title and the content, ignoring accents and case, while typing. Each result shows
// the title, an excerpt, the relevance and the date; choosing it opens the note.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.core.Search
import br.com.wellingtoncruz.notasworkflow.core.SearchResult
import br.com.wellingtoncruz.notasworkflow.core.Texts
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	var query by rememberSaveable { mutableStateOf("") }
	var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
	var status by remember { mutableStateOf("") }
	val focus = remember { FocusRequester() }

	BackHandler { vm.back() }

	LaunchedEffect(query, vm.revision) {
		if (Search.terms(query).isEmpty()) {
			results = emptyList()
			status = ""
			return@LaunchedEffect
		}
		// Waits a moment: the search runs when the typing pauses.
		delay(250)
		val notes = vm.store.notes.toList()
		val ids = notes.mapTo(HashSet()) { it.id }
		val found = withContext(vm.searchDispatcher) {
			vm.searchIndex.forget(ids)
			Search.search(notes, query, index = vm.searchIndex)
		}
		results = found
		status = if (found.isEmpty()) "Nenhuma folha encontrada" else "Encontrado: ${Texts.notesCount(found.size)}"
	}
	LaunchedEffect(Unit) { focus.requestFocus() }

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = {
			TopAppBar(
				title = { Text("Buscar folhas") },
				navigationIcon = {
					IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar") }
				},
			)
		},
	) { padding ->
		Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
			OutlinedTextField(
				value = query,
				onValueChange = { query = it.take(Search.MAX_QUERY) },
				label = { Text("Buscar") },
				singleLine = true,
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
				modifier = Modifier.fillMaxWidth().focusRequester(focus),
			)
			Text(
				status,
				style = MaterialTheme.typography.labelLarge,
				modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
			)
			if (results.isNotEmpty()) {
				Text(
					"Resultados (${results.size}):",
					style = MaterialTheme.typography.titleSmall,
					modifier = Modifier.semantics { heading() },
				)
			}
			LazyColumn(Modifier.fillMaxSize()) {
				items(results, key = { it.note.id }) { r ->
					val relevance = "${(r.score * 100).toInt()}%"
					val date = Texts.formatDate(r.note.updatedAt)
					Column(
						Modifier
							.buttonRow("abrir") { vm.openNote(r.note.id) }
							.semantics(mergeDescendants = true) {
								contentDescription = "${Texts.noteTitle(r.note)}. ${r.excerpt}. Relevância $relevance. Atualizada em $date"
							}
							.padding(vertical = 8.dp),
					) {
						Text(Texts.noteTitle(r.note), fontWeight = FontWeight.SemiBold)
						Text(r.excerpt, style = MaterialTheme.typography.bodySmall)
						Text("Relevância $relevance · Atualizada em $date", style = MaterialTheme.typography.labelSmall)
					}
					HorizontalDivider()
				}
			}
		}
	}
}
