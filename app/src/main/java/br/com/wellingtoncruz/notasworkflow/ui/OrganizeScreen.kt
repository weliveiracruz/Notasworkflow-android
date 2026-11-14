// NotasWorkflow Android - OrganizeScreen.kt
// Notebooks and folders: each folder with its notebooks and its notes, a "No folder" branch with the
// other notebooks and a "Loose notes" branch. Create, Rename, Move to, Export and Delete for each
// item (TalkBack actions menu, long press, F2 and Delete keys). Deleting a notebook or a folder
// deletes what is inside it, and the question says how much.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.core.Folder
import br.com.wellingtoncruz.notasworkflow.core.Model
import br.com.wellingtoncruz.notasworkflow.core.Note
import br.com.wellingtoncruz.notasworkflow.core.Notebook
import br.com.wellingtoncruz.notasworkflow.core.Place
import br.com.wellingtoncruz.notasworkflow.core.Texts

/** An action of an item of the tree: its label and what it does. */
private data class TreeAction(val label: String, val run: () -> Unit)

private class TreeRow(
	val key: String,
	val label: String,
	val depth: Int,
	/** null: not a branch; true or false: open or closed. */
	val open: Boolean?,
	val bold: Boolean,
	val onClick: () -> Unit,
	val actions: List<TreeAction>,
	val onRename: (() -> Unit)? = null,
	val onDelete: (() -> Unit)? = null,
)

/** The dialog being shown by the screen. */
private sealed class Pending {
	class NewNote(val place: Place) : Pending()
	class NewNotebook(val folderId: String?) : Pending()
	data object NewFolder : Pending()
	class RenameFolder(val folder: Folder) : Pending()
	class RenameNotebook(val notebook: Notebook) : Pending()
	class RenameNote(val note: Note) : Pending()
	class MoveNote(val note: Note) : Pending()
	class MoveNotebook(val notebook: Notebook) : Pending()
	class DeleteFolder(val folder: Folder) : Pending()
	class DeleteNotebook(val notebook: Notebook) : Pending()
	class DeleteNote(val note: Note) : Pending()
	class Export(val request: ExportRequest) : Pending()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	val store = vm.store
	val revision = vm.revision
	val open = remember { mutableStateMapOf<String, Boolean>() }
	var pending by remember { mutableStateOf<Pending?>(null) }
	var createMenu by remember { mutableStateOf(false) }

	BackHandler { vm.back() }

	fun noteRow(note: Note, depth: Int) = TreeRow(
		key = "note-${note.id}",
		label = Texts.noteDescription(note, "", withLane = true),
		depth = depth,
		open = null,
		bold = false,
		onClick = { vm.openNote(note.id) },
		actions = listOf(
			TreeAction("Abrir") { vm.openNote(note.id) },
			TreeAction("Renomear…") { pending = Pending.RenameNote(note) },
			TreeAction("Mover para…") { pending = Pending.MoveNote(note) },
			TreeAction("Exportar…") { pending = Pending.Export(ExportRequest("note", note.id)) },
			TreeAction("Excluir") { pending = Pending.DeleteNote(note) },
		),
		onRename = { pending = Pending.RenameNote(note) },
		onDelete = { pending = Pending.DeleteNote(note) },
	)

	fun notebookRows(nb: Notebook, depth: Int, notes: List<Note>, out: MutableList<TreeRow>) {
		val key = "nb-${nb.id}"
		val isOpen = open[key] == true
		out.add(
			TreeRow(
				key = key,
				label = "Caderno ${nb.name}, ${Texts.notesCount(notes.size)}",
				depth = depth,
				open = isOpen,
				bold = true,
				onClick = { open[key] = !isOpen },
				actions = listOf(
					TreeAction("Criar folha aqui…") { pending = Pending.NewNote(Place(notebookId = nb.id)) },
					TreeAction("Renomear…") { pending = Pending.RenameNotebook(nb) },
					TreeAction("Mover para…") { pending = Pending.MoveNotebook(nb) },
					TreeAction("Exportar…") { pending = Pending.Export(ExportRequest("notebook", nb.id)) },
					TreeAction("Excluir") { pending = Pending.DeleteNotebook(nb) },
				),
				onRename = { pending = Pending.RenameNotebook(nb) },
				onDelete = { pending = Pending.DeleteNotebook(nb) },
			),
		)
		if (isOpen) notes.forEach { out.add(noteRow(it, depth + 1)) }
	}

	val rows: List<TreeRow> = remember(revision, open.toMap()) {
		val byNotebook = HashMap<String, MutableList<Note>>()
		val byFolder = HashMap<String, MutableList<Note>>()
		val loose = ArrayList<Note>()
		for (n in store.notes) {
			when {
				n.notebookId != null -> byNotebook.getOrPut(n.notebookId) { ArrayList() }.add(n)
				n.folderId != null -> byFolder.getOrPut(n.folderId) { ArrayList() }.add(n)
				else -> loose.add(n)
			}
		}
		val out = ArrayList<TreeRow>()
		for (folder in store.folders) {
			val key = "folder-${folder.id}"
			val isOpen = open[key] == true
			val notebooks = store.notebooksIn(folder.id)
			val direct = byFolder[folder.id] ?: emptyList<Note>()
			val count = direct.size + notebooks.sumOf { byNotebook[it.id]?.size ?: 0 }
			out.add(
				TreeRow(
					key = key,
					label = "Pasta ${folder.name}, ${Texts.notebooksCount(notebooks.size)}, ${Texts.notesCount(count)}",
					depth = 0,
					open = isOpen,
					bold = true,
					onClick = { open[key] = !isOpen },
					actions = listOf(
						TreeAction("Criar folha aqui…") { pending = Pending.NewNote(Place(folderId = folder.id)) },
						TreeAction("Criar caderno aqui…") { pending = Pending.NewNotebook(folder.id) },
						TreeAction("Renomear…") { pending = Pending.RenameFolder(folder) },
						TreeAction("Exportar…") { pending = Pending.Export(ExportRequest("folder", folder.id)) },
						TreeAction("Excluir") { pending = Pending.DeleteFolder(folder) },
					),
					onRename = { pending = Pending.RenameFolder(folder) },
					onDelete = { pending = Pending.DeleteFolder(folder) },
				),
			)
			if (isOpen) {
				notebooks.forEach { notebookRows(it, 1, byNotebook[it.id] ?: emptyList(), out) }
				direct.forEach { out.add(noteRow(it, 1)) }
			}
		}
		val withoutFolder = store.notebooksIn(null)
		val noFolderOpen = open["nofolder"] == true
		out.add(
			TreeRow(
				"nofolder", "Sem pasta, ${Texts.notebooksCount(withoutFolder.size)}", 0, noFolderOpen, true,
				{ open["nofolder"] = !noFolderOpen },
				listOf(TreeAction("Criar caderno aqui…") { pending = Pending.NewNotebook(null) }),
			),
		)
		if (noFolderOpen) withoutFolder.forEach { notebookRows(it, 1, byNotebook[it.id] ?: emptyList(), out) }
		val looseOpen = open["loose"] == true
		out.add(
			TreeRow(
				"loose", "Folhas soltas, ${Texts.notesCount(loose.size)}", 0, looseOpen, true,
				{ open["loose"] = !looseOpen },
				listOf(TreeAction("Criar folha aqui…") { pending = Pending.NewNote(Place.LOOSE) }),
			),
		)
		if (looseOpen) loose.forEach { out.add(noteRow(it, 1)) }
		out
	}

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = {
			TopAppBar(
				title = { Text("Cadernos e pastas") },
				navigationIcon = {
					IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar") }
				},
				actions = {
					Box {
						IconButton(onClick = { createMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "Criar…") }
						DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
							DropdownMenuItem(text = { Text("Folha…") }, onClick = { createMenu = false; pending = Pending.NewNote(Place.LOOSE) })
							DropdownMenuItem(text = { Text("Caderno…") }, onClick = { createMenu = false; pending = Pending.NewNotebook(null) })
							DropdownMenuItem(text = { Text("Pasta…") }, onClick = { createMenu = false; pending = Pending.NewFolder })
						}
					}
				},
			)
		},
	) { padding ->
		LazyColumn(Modifier.fillMaxSize().padding(padding)) {
			items(rows, key = { it.key }) { row -> TreeItem(row) }
		}
	}

	when (val p = pending) {
		null -> {}
		is Pending.NewNote -> NewNoteDialog(vm, initialPlace = p.place, onCreate = { title, place, lane ->
			pending = null
			vm.createNote(title, place, lane)
		}, onDismiss = { pending = null })
		is Pending.NewNotebook -> NewNotebookDialog(vm, initialFolder = p.folderId, onCreated = { pending = null }, onDismiss = { pending = null })
		is Pending.NewFolder -> NewFolderDialog(vm, onCreated = { pending = null }, onDismiss = { pending = null })
		is Pending.RenameFolder -> TextInputDialog("Renomear", "Novo nome", p.folder.name, onConfirm = { name ->
			if (vm.safe { store.renameFolder(p.folder.id, name) } != null) {
				pending = null
				vm.say("Renomeado para ${Model.cleanText(name, Model.MAX_NAME)}")
			}
		}, onDismiss = { pending = null })
		is Pending.RenameNotebook -> TextInputDialog("Renomear", "Novo nome", p.notebook.name, onConfirm = { name ->
			if (vm.safe { store.renameNotebook(p.notebook.id, name) } != null) {
				pending = null
				vm.say("Renomeado para ${Model.cleanText(name, Model.MAX_NAME)}")
			}
		}, onDismiss = { pending = null })
		is Pending.RenameNote -> TextInputDialog("Renomear", "Novo título", p.note.title, maxLength = Model.MAX_TITLE, onConfirm = { name ->
			pending = null
			if (vm.safe { store.updateNote(p.note.id, title = name) } != null) {
				vm.say("Renomeado para ${Model.cleanText(name, Model.MAX_TITLE).ifEmpty { Texts.untitled() }}")
			}
		}, onDismiss = { pending = null })
		is Pending.MoveNote -> LocationDialog("Mover ${Texts.noteTitle(p.note)} para", store, store.placeOf(p.note), { place ->
			pending = null
			vm.moveNoteTo(p.note, place)
		}, { pending = null })
		is Pending.MoveNotebook -> {
			val folders = store.folders.toList()
			ChoiceDialog(
				title = "Mover para pasta",
				message = "Pasta do caderno ${p.notebook.name}:",
				options = listOf("Sem pasta") + folders.map { it.name },
				selected = if (p.notebook.folderId == null) 0 else folders.indexOfFirst { it.id == p.notebook.folderId } + 1,
				onChoose = { i ->
					pending = null
					val target = if (i == 0) null else folders[i - 1]
					if (vm.safe { store.moveNotebookToFolder(p.notebook.id, target?.id) } != null) {
						vm.say(Texts.movedToPlace(if (target == null) "Sem pasta" else Texts.folderPlace(target)))
					}
				},
				onDismiss = { pending = null },
			)
		}
		is Pending.DeleteFolder -> {
			val (notebooks, notes) = store.folderContents(p.folder.id)
			ConfirmDialog(
				title = "Excluir",
				text = if (notebooks == 0 && notes == 0) "Excluir a pasta ${p.folder.name}? Ela está vazia."
				else "Excluir a pasta ${p.folder.name} e tudo o que está nela: ${Texts.notebooksCount(notebooks)} e ${Texts.notesCount(notes)}? Isso não pode ser desfeito.",
				confirmLabel = "Excluir",
				onConfirm = {
					pending = null
					if (vm.safe { store.deleteFolder(p.folder.id) } != null) {
						if (vm.filter.kind == "folder" && vm.filter.id == p.folder.id) vm.filter = br.com.wellingtoncruz.notasworkflow.core.NoteFilter.ALL
						vm.say("${p.folder.name} excluído")
					}
				},
				onDismiss = { pending = null },
			)
		}
		is Pending.DeleteNotebook -> {
			val notes = store.notebookContents(p.notebook.id)
			ConfirmDialog(
				title = "Excluir",
				text = if (notes == 0) "Excluir o caderno ${p.notebook.name}? Ele está vazio."
				else "Excluir o caderno ${p.notebook.name} e tudo o que está nele: ${Texts.notesCount(notes)}? Isso não pode ser desfeito.",
				confirmLabel = "Excluir",
				onConfirm = {
					pending = null
					if (vm.safe { store.deleteNotebook(p.notebook.id) } != null) {
						if (vm.filter.kind == "notebook" && vm.filter.id == p.notebook.id) vm.filter = br.com.wellingtoncruz.notasworkflow.core.NoteFilter.ALL
						vm.say("${p.notebook.name} excluído")
					}
				},
				onDismiss = { pending = null },
			)
		}
		is Pending.DeleteNote -> ConfirmDialog(
			title = "Excluir folha",
			text = "Excluir a folha ${Texts.noteTitle(p.note)}? Isso não pode ser desfeito.",
			confirmLabel = "Excluir",
			onConfirm = {
				pending = null
				vm.deleteNote(p.note)
			},
			onDismiss = { pending = null },
		)
		is Pending.Export -> ExportFlow(vm, p.request) { pending = null }
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeItem(row: TreeRow) {
	var menu by remember { mutableStateOf(false) }
	Box {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 48.dp)
				.onPreviewKeyEvent { e ->
					if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
					when {
						e.key == Key.F2 && row.onRename != null -> { row.onRename.invoke(); true }
						(e.key == Key.Delete || e.key == Key.ForwardDel) && row.onDelete != null -> { row.onDelete.invoke(); true }
						e.key == Key.DirectionRight && row.open == false -> { row.onClick(); true }
						e.key == Key.DirectionLeft && row.open == true -> { row.onClick(); true }
						else -> false
					}
				}
				.combinedClickable(
					onClickLabel = when (row.open) {
						null -> "abrir"
						true -> "fechar"
						false -> "abrir"
					},
					onLongClickLabel = if (row.actions.isNotEmpty()) "mais opções" else null,
					onLongClick = if (row.actions.isNotEmpty()) ({ menu = true }) else null,
					onClick = row.onClick,
				)
				.semantics(mergeDescendants = true) {
					contentDescription = row.label
					if (row.open != null) stateDescription = if (row.open) "aberto" else "fechado"
					customActions = row.actions.map { a -> CustomAccessibilityAction(a.label) { a.run(); true } }
				}
				.padding(start = (16 + row.depth * 24).dp, end = 16.dp),
		) {
			if (row.open != null) {
				Icon(if (row.open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
			}
			Text(
				row.label,
				fontWeight = if (row.bold) FontWeight.SemiBold else FontWeight.Normal,
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.padding(start = 4.dp),
			)
		}
		DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
			row.actions.forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; a.run() }) }
		}
	}
	HorizontalDivider()
}

