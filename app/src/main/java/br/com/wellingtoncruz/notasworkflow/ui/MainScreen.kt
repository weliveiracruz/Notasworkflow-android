// NotasWorkflow Android - MainScreen.kt
// The main screen: Show (all notes, loose, recent, a folder or a notebook), the notes in the four
// status lanes (closed at first, 200 notes at a time), Create… and the menu. Each note has its
// actions in the TalkBack actions menu, in a long press and in the keys of a hardware keyboard.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.Screen
import br.com.wellingtoncruz.notasworkflow.core.ImportedDocument
import br.com.wellingtoncruz.notasworkflow.core.Markup
import br.com.wellingtoncruz.notasworkflow.core.Model
import br.com.wellingtoncruz.notasworkflow.core.Note
import br.com.wellingtoncruz.notasworkflow.core.NoteFilter
import br.com.wellingtoncruz.notasworkflow.core.Texts

/** Each lane shows this many notes at a time. */
const val PAGE = 200

/** Types a document picker offers (Word, PowerPoint, text, HTML, e-mail). */
val DOCUMENT_TYPES = arrayOf(
	"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
	"application/vnd.openxmlformats-officedocument.presentationml.presentation",
	"text/plain",
	"text/html",
	"message/rfc822",
	"application/octet-stream",
)

private sealed class MainRow(val key: String) {
	class LaneHeader(val laneId: String, val count: Int, val open: Boolean) : MainRow("lane-$laneId")
	class NoteRow(val note: Note) : MainRow(note.id)
	class More(val laneId: String, val count: Int, val remaining: Int) : MainRow("more-$laneId")
	class Empty(val laneId: String) : MainRow("empty-$laneId")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	val store = vm.store
	val revision = vm.revision
	val filter = vm.filter
	val shown = remember { mutableStateMapOf<String, Int>() }

	var menuOpen by remember { mutableStateOf(false) }
	var createOpen by remember { mutableStateOf(false) }
	var choosingFilter by remember { mutableStateOf(false) }
	var newNote by remember { mutableStateOf(false) }
	var newNotebook by remember { mutableStateOf(false) }
	var newFolder by remember { mutableStateOf(false) }
	var document by remember { mutableStateOf<Pair<ImportedDocument, String>?>(null) }
	var exportPicker by remember { mutableStateOf(false) }
	var exportRequest by remember { mutableStateOf<ExportRequest?>(null) }
	var deleting by remember { mutableStateOf<Note?>(null) }
	var moving by remember { mutableStateOf<Note?>(null) }

	val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) vm.readDocument(uri) { doc, name -> document = doc to name }
	}

	val rows: List<MainRow> = remember(revision, filter, vm.openLanes.toMap(), shown.toMap(), vm.loaded) {
		if (!vm.loaded) return@remember emptyList()
		val byLane = Model.byLane(store.filtered(filter))
		val out = ArrayList<MainRow>()
		for (lane in Model.LANE_IDS) {
			val notes = byLane.getValue(lane)
			val open = vm.openLanes[lane] == true
			out.add(MainRow.LaneHeader(lane, notes.size, open))
			if (!open) continue
			if (notes.isEmpty()) out.add(MainRow.Empty(lane))
			val limit = shown[lane] ?: PAGE
			notes.take(limit).forEach { out.add(MainRow.NoteRow(it)) }
			if (notes.size > limit) out.add(MainRow.More(lane, minOf(PAGE, notes.size - limit), notes.size - limit))
		}
		out
	}
	val listState = rememberLazyListState()

	// After a change of status or position the focus goes to the right note.
	LaunchedEffect(vm.focusNoteId, rows) {
		val id = vm.focusNoteId ?: return@LaunchedEffect
		val note = store.getNote(id) ?: run { vm.focusNoteId = null; return@LaunchedEffect }
		if (vm.openLanes[note.laneId] != true) {
			vm.openLanes[note.laneId] = true
			return@LaunchedEffect
		}
		val index = rows.indexOfFirst { it is MainRow.NoteRow && it.note.id == id }
		if (index >= 0) listState.scrollToItem(index)
	}

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = {
			TopAppBar(
				title = { Text(Texts.APP_NAME) },
				actions = {
					IconButton(onClick = { vm.open(Screen.SearchNotes) }) { Icon(Icons.Filled.Search, contentDescription = "Buscar") }
					Box {
						IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções") }
						DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
							DropdownMenuItem(text = { Text("Cadernos e pastas…") }, onClick = { menuOpen = false; vm.open(Screen.Organize) })
							DropdownMenuItem(text = { Text("Exportar…") }, onClick = { menuOpen = false; exportPicker = true })
							DropdownMenuItem(text = { Text("Quantas folhas em cada raia") }, onClick = { menuOpen = false; vm.say(vm.laneCounts()) })
							DropdownMenuItem(text = { Text("Configurações…") }, onClick = { menuOpen = false; vm.open(Screen.Settings) })
							DropdownMenuItem(text = { Text("Manual de uso…") }, onClick = { menuOpen = false; vm.open(Screen.Manual) })
						}
					}
				},
			)
		},
		floatingActionButton = {
			Box {
				ExtendedFloatingActionButton(
					onClick = { createOpen = true },
					icon = { Icon(Icons.Filled.Add, contentDescription = null) },
					text = { Text("Criar…") },
				)
				DropdownMenu(expanded = createOpen, onDismissRequest = { createOpen = false }) {
					DropdownMenuItem(text = { Text("Folha…") }, onClick = { createOpen = false; newNote = true })
					DropdownMenuItem(text = { Text("Caderno…") }, onClick = { createOpen = false; newNotebook = true })
					DropdownMenuItem(text = { Text("Pasta…") }, onClick = { createOpen = false; newFolder = true })
					DropdownMenuItem(
						text = { Text("Folha a partir de documento…") },
						onClick = { createOpen = false; openDocument.launch(DOCUMENT_TYPES) },
					)
				}
			}
		},
	) { padding ->
		Column(Modifier.fillMaxSize().padding(padding)) {
			if (!vm.loaded) {
				Text(
					"Carregando as folhas…",
					modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
				)
				return@Column
			}
			val total = remember(revision, filter) { store.filtered(filter).size }
			OutlinedButton(
				onClick = { choosingFilter = true },
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
					.semantics { contentDescription = "Mostrar: ${Texts.filterName(store, filter)}, ${Texts.notesCount(total)}. Mudar" },
			) {
				Text("Mostrar: ${Texts.filterName(store, filter)} (${Texts.notesCount(total)})", maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(bottom = 96.dp),
			) {
				items(rows, key = { it.key }) { row ->
					when (row) {
						is MainRow.LaneHeader -> LaneHeader(row) { vm.openLanes[row.laneId] = !row.open }
						is MainRow.NoteRow -> NoteItem(
							vm, row.note,
							onDelete = { deleting = row.note },
							onMove = { moving = row.note },
							onExport = { exportRequest = ExportRequest("note", row.note.id) },
						)
						is MainRow.More -> Text(
							Texts.showMore(row.count, row.remaining),
							color = MaterialTheme.colorScheme.primary,
							modifier = Modifier.buttonRow("mostrar") { shown[row.laneId] = (shown[row.laneId] ?: PAGE) + PAGE }
								.padding(horizontal = 32.dp, vertical = 12.dp),
						)
						is MainRow.Empty -> Text(
							"Nenhuma folha nesta raia",
							style = MaterialTheme.typography.bodyMedium,
							modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
						)
					}
				}
			}
		}
	}

	if (choosingFilter) FilterDialog(vm, onDismiss = { choosingFilter = false })
	if (newNote) {
		NewNoteDialog(vm, initialPlace = vm.defaultPlace(), onCreate = { title, place, lane ->
			newNote = false
			vm.createNote(title, place, lane)
		}, onDismiss = { newNote = false })
	}
	document?.let { (doc, name) ->
		NewNoteDialog(
			vm, initialTitle = doc.title, initialPlace = vm.defaultPlace(), caption = "Nova folha a partir de documento",
			onCreate = { title, place, lane ->
				document = null
				vm.createNote(title, place, lane, doc.text, name)
			},
			onDismiss = { document = null },
		)
	}
	if (newNotebook) {
		NewNotebookDialog(vm, initialFolder = if (filter.kind == "folder") filter.id else null, onCreated = { nb ->
			newNotebook = false
			vm.filter = NoteFilter.notebook(nb.id)
		}, onDismiss = { newNotebook = false })
	}
	if (newFolder) NewFolderDialog(vm, onCreated = { newFolder = false }, onDismiss = { newFolder = false })
	if (exportPicker) {
		ExportPicker(vm, onChosen = { exportPicker = false; exportRequest = it }, onDismiss = { exportPicker = false })
	}
	exportRequest?.let { ExportFlow(vm, it) { exportRequest = null } }
	deleting?.let { note ->
		ConfirmDialog(
			title = "Excluir folha",
			text = "Excluir a folha ${Texts.noteTitle(note)}? Isso não pode ser desfeito.",
			confirmLabel = "Excluir",
			onConfirm = {
				deleting = null
				val lane = vm.laneOf(note)
				val i = lane.indexOfFirst { it.id == note.id }
				vm.focusNoteId = (lane.getOrNull(i + 1) ?: lane.getOrNull(i - 1))?.id
				vm.deleteNote(note)
			},
			onDismiss = { deleting = null },
		)
	}
	moving?.let { note ->
		LocationDialog("Mover ${Texts.noteTitle(note)} para", store, store.placeOf(note), { place ->
			moving = null
			vm.moveNoteTo(note, place)
		}, { moving = null })
	}
}

@Composable
private fun LaneHeader(row: MainRow.LaneHeader, onToggle: () -> Unit) {
	val lane = Model.LANES.first { it.id == row.laneId }
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 56.dp)
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.clickable(onClickLabel = if (row.open) "fechar" else "abrir", role = Role.Button, onClick = onToggle)
			.onPreviewKeyEvent { e ->
				if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				when {
					e.key == Key.DirectionRight && !row.open -> { onToggle(); true }
					e.key == Key.DirectionLeft && row.open -> { onToggle(); true }
					else -> false
				}
			}
			.semantics(mergeDescendants = true) {
				heading()
				stateDescription = if (row.open) "aberta" else "fechada"
			}
			.padding(horizontal = 12.dp),
	) {
		Icon(
			if (row.open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = null,
		)
		Box(Modifier.padding(horizontal = 8.dp).width(6.dp).height(24.dp).background(laneColor(lane.color)))
		Text(Texts.laneNode(row.laneId, row.count), style = MaterialTheme.typography.titleMedium)
	}
	HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteItem(vm: AppViewModel, note: Note, onDelete: () -> Unit, onMove: () -> Unit, onExport: () -> Unit) {
	val store = vm.store
	val place = Texts.placeOf(store, note)
	val description = Texts.noteDescription(note, place)
	val focus = remember { FocusRequester() }
	var menu by remember { mutableStateOf(false) }
	val next = Model.neighbourLane(note.laneId, 1)
	val previous = Model.neighbourLane(note.laneId, -1)

	LaunchedEffect(vm.focusNoteId) {
		if (vm.focusNoteId == note.id) {
			try {
				focus.requestFocus()
			} catch (e: IllegalStateException) {
				// Not attached yet: the list scrolls to it and this runs again.
				return@LaunchedEffect
			}
			vm.focusNoteId = null
		}
	}

	val actions = buildList {
		add(CustomAccessibilityAction("Abrir") { vm.openNote(note.id); true })
		if (next != null) add(CustomAccessibilityAction("Próximo status: ${Texts.laneName(next)}") { vm.stepStatus(note.id, 1); true })
		if (previous != null) add(CustomAccessibilityAction("Status anterior: ${Texts.laneName(previous)}") { vm.stepStatus(note.id, -1); true })
		add(CustomAccessibilityAction("Subir na raia") { vm.moveInLane(note.id, -1); true })
		add(CustomAccessibilityAction("Descer na raia") { vm.moveInLane(note.id, 1); true })
		add(CustomAccessibilityAction("Mover para…") { onMove(); true })
		add(CustomAccessibilityAction("Exportar…") { onExport(); true })
		add(CustomAccessibilityAction("Excluir") { onDelete(); true })
	}

	Box {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.focusRequester(focus)
				.onPreviewKeyEvent { e ->
					if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
					when {
						e.isAltPressed && e.isShiftPressed && e.key == Key.DirectionRight -> { vm.stepStatus(note.id, 1); true }
						e.isAltPressed && e.isShiftPressed && e.key == Key.DirectionLeft -> { vm.stepStatus(note.id, -1); true }
						e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionUp -> { vm.moveInLane(note.id, -1); true }
						e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionDown -> { vm.moveInLane(note.id, 1); true }
						e.key == Key.Delete -> { onDelete(); true }
						else -> false
					}
				}
				.combinedClickable(
					onClickLabel = "abrir",
					onLongClickLabel = "mais opções",
					onLongClick = { menu = true },
					onClick = { vm.openNote(note.id) },
				)
				.semantics(mergeDescendants = true) {
					contentDescription = description
					customActions = actions
				}
				.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(Modifier.weight(1f)) {
				Text(Texts.noteTitle(note), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
				val preview = remember(note.content) { Markup.preview(note.content) }
				if (preview.isNotEmpty()) {
					Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
				val meta = listOf(place, Texts.formatDate(note.updatedAt)).filter { it.isNotEmpty() }.joinToString(" · ")
				Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			// The actions are already in the TalkBack actions menu: the button is only for sight.
			IconButton(onClick = { menu = true }, modifier = Modifier.clearAndSetSemantics { }) {
				Icon(Icons.Filled.MoreVert, contentDescription = null)
			}
		}
		DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
			DropdownMenuItem(text = { Text("Abrir") }, onClick = { menu = false; vm.openNote(note.id) })
			if (next != null) DropdownMenuItem(text = { Text("Próximo status: ${Texts.laneName(next)}") }, onClick = { menu = false; vm.stepStatus(note.id, 1) })
			if (previous != null) DropdownMenuItem(text = { Text("Status anterior: ${Texts.laneName(previous)}") }, onClick = { menu = false; vm.stepStatus(note.id, -1) })
			DropdownMenuItem(text = { Text("Subir na raia") }, onClick = { menu = false; vm.moveInLane(note.id, -1) })
			DropdownMenuItem(text = { Text("Descer na raia") }, onClick = { menu = false; vm.moveInLane(note.id, 1) })
			DropdownMenuItem(text = { Text("Mover para…") }, onClick = { menu = false; onMove() })
			DropdownMenuItem(text = { Text("Exportar…") }, onClick = { menu = false; onExport() })
			DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; onDelete() })
		}
	}
	HorizontalDivider()
}

/** Show: all notes, loose, recent, each folder (closed, with its notebooks inside), then the
 * notebooks without folder. Choosing an item shows its notes. */
@Composable
private fun FilterDialog(vm: AppViewModel, onDismiss: () -> Unit) {
	val store = vm.store
	val counts = remember(vm.revision) { store.placeCounts() }
	val current = vm.filter
	val currentFolder = if (current.kind == "notebook") store.getNotebook(current.id)?.folderId else null
	val open = remember { mutableStateMapOf<String, Boolean>() }
	fun choose(f: NoteFilter) {
		vm.filter = f
		onDismiss()
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Mostrar") },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
				RadioRow("Todas as folhas, ${Texts.notesCount(store.notes.size)}", current.kind == "all", { choose(NoteFilter.ALL) })
				RadioRow("Folhas soltas, ${Texts.notesCount(counts.loose)}", current.kind == "loose", { choose(NoteFilter.LOOSE) })
				RadioRow("Folhas recentes", current.kind == "recent", { choose(NoteFilter.RECENT) })
				for (folder in store.folders) {
					val notebooks = store.notebooksIn(folder.id)
					val isOpen = open[folder.id] ?: (folder.id == currentFolder)
					val inFolder = (counts.byFolder[folder.id] ?: 0) + notebooks.sumOf { counts.byNotebook[it.id] ?: 0 }
					Row(verticalAlignment = Alignment.CenterVertically) {
						Box(Modifier.weight(1f)) {
							RadioRow(
								"Pasta ${folder.name}, ${Texts.notebooksCount(notebooks.size)}, ${Texts.notesCount(inFolder)}",
								current.kind == "folder" && current.id == folder.id,
								{ choose(NoteFilter.folder(folder.id)) },
							)
						}
						if (notebooks.isNotEmpty()) ExpandButton(isOpen, "pasta ${folder.name}") { open[folder.id] = !isOpen }
					}
					if (isOpen) {
						for (nb in notebooks) {
							RadioRow(
								"Caderno ${nb.name}, ${Texts.notesCount(counts.byNotebook[nb.id] ?: 0)}",
								current.kind == "notebook" && current.id == nb.id,
								{ choose(NoteFilter.notebook(nb.id)) },
								indent = 1,
							)
						}
					}
				}
				for (nb in store.notebooksIn(null)) {
					RadioRow(
						"Caderno ${nb.name}, ${Texts.notesCount(counts.byNotebook[nb.id] ?: 0)}",
						current.kind == "notebook" && current.id == nb.id,
						{ choose(NoteFilter.notebook(nb.id)) },
					)
				}
			}
		},
		confirmButton = {},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
	)
}

