// NotasWorkflow Android - NoteScreen.kt
// The note window: title, location, status and the content in the light markup. It saves by itself
// some seconds after the typing stops; Save and closing always save. Formatting… applies a format
// to the selection or to the current line; Insert document… puts the text of a document at the
// cursor. If the status or the location changed, closing goes to the next note of the same lane.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.Screen
import br.com.wellingtoncruz.notasworkflow.core.Importer
import br.com.wellingtoncruz.notasworkflow.core.Markup
import br.com.wellingtoncruz.notasworkflow.core.Model
import br.com.wellingtoncruz.notasworkflow.core.Texts
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteScreen(vm: AppViewModel, screen: Screen.NoteEditor, snackbar: SnackbarHostState) {
	val store = vm.store
	val original = remember(screen.noteId) { store.getNote(screen.noteId) }
	if (original == null) {
		LaunchedEffect(screen.noteId) {
			vm.say("Esta folha foi excluída.")
			vm.back()
		}
		return
	}
	val originalText = remember(screen.noteId) { Markup.htmlToText(original.content) }

	var title by remember { mutableStateOf(original.title) }
	var content by remember { mutableStateOf(TextFieldValue(originalText)) }
	var lane by remember { mutableStateOf(original.laneId) }
	var place by remember { mutableStateOf(store.placeOf(original)) }

	var savedTitle by remember { mutableStateOf(original.title) }
	var savedText by remember { mutableStateOf(originalText) }
	var savedLane by remember { mutableStateOf(original.laneId) }
	var savedPlace by remember { mutableStateOf(store.placeOf(original)) }
	var saveState by remember { mutableStateOf("Salva") }
	var edits by remember { mutableIntStateOf(0) }

	var choosingLane by remember { mutableStateOf(false) }
	var choosingPlace by remember { mutableStateOf(false) }
	var formatting by remember { mutableStateOf(false) }
	var closed by remember { mutableStateOf(false) }

	val titleFocus = remember { FocusRequester() }
	val contentFocus = remember { FocusRequester() }

	fun dirty() = title != savedTitle || content.text != savedText || lane != savedLane || place != savedPlace

	/** Saves what changed. auto: the automatic saving (spoken only when the setting asks). */
	fun save(auto: Boolean): Boolean {
		if (store.getNote(screen.noteId) == null) {
			vm.say("Esta folha foi excluída.")
			return false
		}
		if (!dirty()) return true
		var html: String? = null
		if (content.text != savedText) {
			html = Markup.textToHtml(content.text)
			if (html.length > Model.MAX_CONTENT) {
				vm.say("A folha passou de 1 MB: o conteúdo não foi salvo. Apague parte do texto.")
				html = null
			}
		}
		val result = vm.safe {
			store.updateNote(screen.noteId, title = title, content = html, laneId = lane, place = place)
		} ?: return false
		savedTitle = title
		if (html != null) savedText = content.text
		savedLane = lane
		savedPlace = place
		saveState = "Salva às ${Texts.formatTime()}"
		if (result && (!auto || vm.announceAutoSave)) vm.say("Folha salva")
		return true
	}

	/** Save and close: the focus goes back to the note, or to the next note of its old lane. */
	fun close() {
		if (closed) return
		save(auto = false)
		closed = true
		val now = store.getNote(screen.noteId)
		if (now != null && (now.laneId != original.laneId || store.placeOf(now) != store.placeOf(original))) {
			val order = screen.laneOrder
			val i = order.indexOf(screen.noteId)
			val next = order.drop(i + 1).firstOrNull { id -> store.getNote(id)?.laneId == original.laneId }
				?: order.take(maxOf(0, i)).lastOrNull { id -> store.getNote(id)?.laneId == original.laneId }
			vm.openLanes[original.laneId] = true
			vm.focusNoteId = next
		} else {
			vm.focusNoteId = screen.noteId
		}
		vm.back()
	}

	fun applyFormat(kind: String) {
		val sel = content.selection
		val r = Markup.applyFormat(content.text, sel.start, sel.end, kind)
		content = TextFieldValue(r.text, TextRange(r.start, r.end))
		edits++
		vm.say(if (r.applied) Texts.formatName(kind) else Texts.formatRemoved(kind))
	}

	val insertDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		vm.readDocument(uri) { doc, name ->
			val text = content.text
			val at = content.selection.min
			val before = if (at > 0 && text[at - 1] != '\n') "\n" else ""
			val after = if (at < text.length && text[at] != '\n') "\n" else ""
			val room = Model.MAX_CONTENT - Markup.textToHtml(text).length - 16
			val (fitted, cut) = Importer.fitContent(doc.text, room)
			if (fitted.isEmpty()) {
				vm.say(Texts.noteFull())
				return@readDocument
			}
			val insert = before + fitted + after
			content = TextFieldValue(text.substring(0, at) + insert + text.substring(at), TextRange(at + insert.length))
			edits++
			vm.say("Texto de $name inserido")
			if (cut) vm.say(Texts.contentCut())
		}
	}

	// Automatic saving, some seconds after the typing stops.
	LaunchedEffect(edits) {
		if (edits == 0 || !dirty()) return@LaunchedEffect
		saveState = "Alterações não salvas…"
		delay(vm.autoSaveSeconds * 1000L)
		save(auto = true)
	}

	// Leaving the app saves too (nothing is lost).
	val saveNow by rememberUpdatedState({ if (!closed) save(auto = true) })
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	DisposableEffect(lifecycle) {
		val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) saveNow() }
		lifecycle.addObserver(observer)
		onDispose { lifecycle.removeObserver(observer) }
	}

	BackHandler { close() }

	LaunchedEffect(screen.noteId) {
		if (screen.focusContent) contentFocus.requestFocus() else titleFocus.requestFocus()
	}

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = {
			TopAppBar(
				title = { Text("Folha") },
				navigationIcon = {
					IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salvar e fechar") }
				},
				actions = {
					IconButton(onClick = { save(auto = false) }) { Icon(Icons.Filled.Check, contentDescription = "Salvar") }
				},
			)
		},
	) { padding ->
		Column(
			Modifier
				.fillMaxSize()
				.padding(padding)
				.imePadding()
				.padding(horizontal = 16.dp)
				.onPreviewKeyEvent { e ->
					if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
					when (e.key) {
						Key.S -> { save(auto = false); true }
						Key.B -> { applyFormat("bold"); true }
						Key.I -> { applyFormat("italic"); true }
						else -> false
					}
				},
		) {
			OutlinedTextField(
				value = title,
				onValueChange = {
					title = it.take(Model.MAX_TITLE)
					edits++
				},
				label = { Text("Título") },
				singleLine = true,
				keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
				modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
			)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
				OutlinedButton(
					onClick = { choosingPlace = true },
					modifier = Modifier.semantics { contentDescription = "Local: ${Texts.placeLabel(store, place)}. Mudar" },
				) { Text("Local: ${Texts.placeLabel(store, place)}") }
				OutlinedButton(
					onClick = { choosingLane = true },
					modifier = Modifier.semantics { contentDescription = "Status: ${Texts.laneName(lane)}. Mudar" },
				) { Text("Status: ${Texts.laneName(lane)}") }
				TextButton(onClick = { formatting = true }) { Text("Formatação…") }
				TextButton(onClick = { insertDocument.launch(DOCUMENT_TYPES) }) { Text("Inserir documento…") }
			}
			Text(
				saveState,
				style = MaterialTheme.typography.labelMedium,
				modifier = Modifier
					.padding(vertical = 4.dp)
					.semantics {
						contentDescription = "Situação do salvamento: $saveState"
						if (vm.announceAutoSave) liveRegion = LiveRegionMode.Polite
					},
			)
			OutlinedTextField(
				value = content,
				onValueChange = {
					if (it.text != content.text) edits++
					content = it
				},
				label = { Text("Conteúdo") },
				keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
				modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp).focusRequester(contentFocus),
			)
		}
	}

	if (choosingLane) {
		ChoiceDialog(
			title = "Status",
			options = Model.LANE_IDS.map { Texts.laneName(it) },
			selected = Model.laneIndex(lane),
			onChoose = { i ->
				lane = Model.LANE_IDS[i]
				choosingLane = false
				edits++
			},
			onDismiss = { choosingLane = false },
		)
	}
	if (choosingPlace) {
		LocationDialog("Local", store, place, { p ->
			place = store.location(p)
			choosingPlace = false
			edits++
		}, { choosingPlace = false })
	}
	if (formatting) {
		ChoiceDialog(
			title = "Formatação",
			options = Markup.FORMATS.map { Texts.formatName(it) },
			onChoose = { i ->
				formatting = false
				applyFormat(Markup.FORMATS[i])
				contentFocus.requestFocus()
			},
			onDismiss = { formatting = false },
		)
	}
}

