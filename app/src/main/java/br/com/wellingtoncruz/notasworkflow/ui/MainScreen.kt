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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
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
	/** "+ Adicionar uma folha" at the end of an open lane, as in a Trello list. */
	class Add(val laneId: String) : MainRow("add-$laneId")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	val store = vm.store
	val revision = vm.revision
	val filter = vm.filter
	val shown = remember { mutableStateMapOf<String, Int>() }

	var createOpen by remember { mutableStateOf(false) }
	var choosingFilter by remember { mutableStateOf(false) }
	/** The lane of the new note being created, or null. */
	var newNote by remember { mutableStateOf<String?>(null) }
	var newNotebook by remember { mutableStateOf(false) }
	var newFolder by remember { mutableStateOf(false) }
	var document by remember { mutableStateOf<Pair<ImportedDocument, String>?>(null) }
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
			out.add(MainRow.Add(lane))
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
		containerColor = trello.board,
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = {
			TopAppBar(
				colors = trelloTopBarColors(),
				title = { Text(Texts.APP_NAME) },
			)
		},
		bottomBar = { BottomTabs(vm) },
		floatingActionButton = {
			Box {
				ExtendedFloatingActionButton(
					onClick = { createOpen = true },
					containerColor = trello.primary,
					contentColor = trello.onPrimary,
					shape = Tokens.Radius.card,
					icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
					text = { Text("Criar…") },
				)
				DropdownMenu(expanded = createOpen, onDismissRequest = { createOpen = false }) {
					DropdownMenuItem(text = { Text("Folha…") }, onClick = { createOpen = false; newNote = Model.DEFAULT_LANE })
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
					color = trello.onBoard,
					modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
				)
				return@Column
			}
			val total = remember(revision, filter) { store.filtered(filter).size }
			Row(
				horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s100),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s100, vertical = Tokens.Space.s100),
			) {
				// Show…: a translucent button over the board, as the buttons of a Trello board header.
				Button(
					onClick = { choosingFilter = true },
					shape = Tokens.Radius.button,
					colors = ButtonDefaults.buttonColors(containerColor = trello.boardButton, contentColor = trello.onBoard),
					modifier = Modifier.weight(1f).heightIn(min = Tokens.Size.touch)
						.semantics { contentDescription = "Mostrar: ${Texts.filterName(store, filter)}, ${Texts.notesCount(total)}. Mudar" },
				) {
					Text("Mostrar: ${Texts.filterName(store, filter)} (${Texts.notesCount(total)})", maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
				// The button to create a new note, always at hand on the home screen.
				Button(
					onClick = { newNote = Model.DEFAULT_LANE },
					shape = Tokens.Radius.button,
					colors = ButtonDefaults.buttonColors(containerColor = trello.card, contentColor = trello.text),
					modifier = Modifier.heightIn(min = Tokens.Size.touch),
				) {
					Icon(Icons.Outlined.Add, contentDescription = null)
					Spacer(Modifier.width(Tokens.Space.s050))
					Text("Nova folha")
				}
			}
			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(bottom = 96.dp),
			) {
				items(rows, key = { it.key }) { row ->
					when (row) {
						is MainRow.LaneHeader -> LaneHeader(row) { vm.openLanes[row.laneId] = !row.open }
						is MainRow.NoteRow -> ListBody {
							NoteItem(
								vm, row.note,
								onDelete = { deleting = row.note },
								onMove = { moving = row.note },
								onExport = { exportRequest = ExportRequest("note", row.note.id) },
							)
						}
						is MainRow.More -> ListBody {
							Text(
								Texts.showMore(row.count, row.remaining),
								color = trello.primary,
								style = MaterialTheme.typography.labelLarge,
								modifier = Modifier.buttonRow("mostrar") { shown[row.laneId] = (shown[row.laneId] ?: PAGE) + PAGE }
									.padding(horizontal = Tokens.Space.s100, vertical = Tokens.Space.s150),
							)
						}
						is MainRow.Empty -> ListBody {
							Text(
								"Nenhuma folha nesta raia",
								style = MaterialTheme.typography.bodyMedium,
								color = trello.textSubtle,
								modifier = Modifier.padding(horizontal = Tokens.Space.s100, vertical = Tokens.Space.s150),
							)
						}
						is MainRow.Add -> AddNoteRow(row.laneId) { newNote = row.laneId }
					}
				}
			}
		}
	}

	if (choosingFilter) FilterDialog(vm, onDismiss = { choosingFilter = false })
	newNote?.let { startLane ->
		NewNoteDialog(vm, initialPlace = vm.defaultPlace(), initialLane = startLane, onCreate = { title, place, lane ->
			newNote = null
			vm.createNote(title, place, lane)
		}, onDismiss = { newNote = null })
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

/** The grey body of a lane (Trello list), around a card or a line. */
@Composable
private fun ListBody(content: @Composable () -> Unit) {
	Box(
		Modifier
			.fillMaxWidth()
			.padding(horizontal = Tokens.Space.s100)
			.background(trello.list)
			.padding(horizontal = Tokens.Space.s100, vertical = Tokens.Space.s050),
	) { content() }
}

/** The title of a lane: the top of a Trello list (the whole list when closed). */
@Composable
private fun LaneHeader(row: MainRow.LaneHeader, onToggle: () -> Unit) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = Tokens.Space.s100, end = Tokens.Space.s100, top = Tokens.Space.s150)
			.clip(if (row.open) Tokens.Radius.listTop else Tokens.Radius.list)
			.background(trello.list)
			.heightIn(min = 56.dp)
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
				contentDescription = Texts.laneNode(row.laneId, row.count)
				stateDescription = if (row.open) "aberta" else "fechada"
			}
			.padding(horizontal = Tokens.Space.s150),
	) {
		Box(
			Modifier
				.width(Tokens.Size.labelWidth)
				.height(Tokens.Size.labelHeight)
				.clip(Tokens.Radius.label)
				.background(laneColor(row.laneId)),
		)
		Spacer(Modifier.width(Tokens.Space.s150))
		Text(
			Texts.laneName(row.laneId),
			style = MaterialTheme.typography.titleSmall,
			color = trello.text,
			modifier = Modifier.weight(1f),
		)
		Text(
			"${row.count}",
			style = MaterialTheme.typography.labelMedium,
			color = trello.textSubtle,
			modifier = Modifier.padding(horizontal = Tokens.Space.s100),
		)
		Icon(
			if (row.open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
			contentDescription = null,
			tint = trello.textSubtle,
		)
	}
}

/** "+ Adicionar uma folha": the bottom of an open lane; creates a note already in this lane. */
@Composable
private fun AddNoteRow(laneId: String, onClick: () -> Unit) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Tokens.Space.s100)
			.clip(Tokens.Radius.listBottom)
			.background(trello.list)
			.padding(horizontal = Tokens.Space.s100, vertical = Tokens.Space.s050)
			.clip(Tokens.Radius.button)
			.heightIn(min = Tokens.Size.touch)
			.clickable(role = Role.Button, onClick = onClick)
			.semantics(mergeDescendants = true) { contentDescription = "Adicionar uma folha em ${Texts.laneName(laneId)}" }
			.padding(horizontal = Tokens.Space.s100),
	) {
		Icon(Icons.Outlined.Add, contentDescription = null, tint = trello.textSubtle)
		Spacer(Modifier.width(Tokens.Space.s100))
		Text("Adicionar uma folha", style = MaterialTheme.typography.labelLarge, color = trello.textSubtle)
	}
}

/** A note: a white Trello card with the label of its lane, the title, a preview and the details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteItem(vm: AppViewModel, note: Note, onDelete: () -> Unit, onMove: () -> Unit, onExport: () -> Unit) {
	val store = vm.store
	val place = Texts.placeOf(store, note)
	val description = Texts.noteDescription(note, place)
	val focus = remember { FocusRequester() }
	var focused by remember { mutableStateOf(false) }
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

	Box(Modifier.padding(vertical = Tokens.Space.s025)) {
		Surface(
			shape = Tokens.Radius.card,
			color = trello.card,
			contentColor = trello.text,
			shadowElevation = Tokens.Elevation.card,
			// The focus ring of Trello, for the keyboard.
			border = if (focused) BorderStroke(2.dp, trello.focus) else null,
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.focusRequester(focus)
					.onFocusChanged { focused = it.isFocused }
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
					.padding(start = Tokens.Space.s150, end = Tokens.Space.s050, top = Tokens.Space.s100, bottom = Tokens.Space.s100),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Column(Modifier.weight(1f)) {
					Box(
						Modifier
							.padding(bottom = Tokens.Space.s075)
							.width(Tokens.Size.labelWidth)
							.height(Tokens.Size.labelHeight)
							.clip(Tokens.Radius.label)
							.background(laneColor(note.laneId)),
					)
					Text(
						Texts.noteTitle(note),
						style = MaterialTheme.typography.bodyMedium,
						fontWeight = FontWeight.Medium,
						color = trello.text,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					val preview = remember(note.content) { Markup.preview(note.content) }
					if (preview.isNotEmpty()) {
						Text(
							preview,
							style = MaterialTheme.typography.bodySmall,
							color = trello.textSubtle,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					val meta = listOf(place, Texts.formatDate(note.updatedAt)).filter { it.isNotEmpty() }.joinToString(" · ")
					Text(
						meta,
						style = MaterialTheme.typography.labelSmall,
						color = trello.textSubtlest,
						modifier = Modifier.padding(top = Tokens.Space.s050),
					)
				}
				// The actions are already in the TalkBack actions menu: the button is only for sight.
				IconButton(onClick = { menu = true }, modifier = Modifier.clearAndSetSemantics { }) {
					Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = trello.textSubtle)
				}
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
		dismissButton = { TextButton(shape = Tokens.Radius.button, onClick = onDismiss) { Text("Fechar") } },
	)
}

