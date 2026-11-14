// NotasWorkflow Android - Dialogs.kt
// The dialogs shared by the screens: a choice in a list, a confirmation, a text to type, the
// Location tree (loose, folders with their notebooks inside, notebooks without folder), New note,
// New notebook and New folder. Every choice is a real radio button for TalkBack.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.core.Folder
import br.com.wellingtoncruz.notasworkflow.core.Model
import br.com.wellingtoncruz.notasworkflow.core.Notebook
import br.com.wellingtoncruz.notasworkflow.core.NotesStore
import br.com.wellingtoncruz.notasworkflow.core.Place
import br.com.wellingtoncruz.notasworkflow.core.Texts

/** A row with a radio button: the whole row is the control. */
@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, indent: Int = 0) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp)
			.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
			.padding(start = (indent * 24).dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		RadioButton(selected = selected, onClick = null)
		Spacer(Modifier.width(8.dp))
		Text(label)
	}
}

/** A list of options; choosing one closes the dialog. */
@Composable
fun ChoiceDialog(
	title: String,
	options: List<String>,
	selected: Int? = null,
	message: String? = null,
	onChoose: (Int) -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState())) {
				if (message != null) Text(message, Modifier.padding(bottom = 8.dp))
				Column(Modifier.selectableGroup()) {
					options.forEachIndexed { i, label -> RadioRow(label, i == selected, { onChoose(i) }) }
				}
			}
		},
		confirmButton = {},
		dismissButton = { TextButton(shape = Tokens.Radius.button, onClick = onDismiss) { Text("Cancelar") } },
	)
}

/** A question with Yes and No; No is the safe answer. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Sim", onConfirm: () -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(text) },
		confirmButton = { TextButton(shape = Tokens.Radius.button, onClick = onConfirm) { Text(confirmLabel) } },
		dismissButton = { TextButton(shape = Tokens.Radius.button, onClick = onDismiss) { Text("Não") } },
	)
}

/** A text to type (name of a folder or notebook, title of a note). */
@Composable
fun TextInputDialog(
	title: String,
	label: String,
	initial: String = "",
	confirmLabel: String = "OK",
	maxLength: Int = Model.MAX_NAME,
	onConfirm: (String) -> Unit,
	onDismiss: () -> Unit,
) {
	var value by remember { mutableStateOf(initial) }
	val focus = remember { FocusRequester() }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			OutlinedTextField(
				value = value,
				onValueChange = { value = it.take(maxLength) },
				label = { Text(label) },
				singleLine = true,
				keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
				modifier = Modifier.fillMaxWidth().focusRequester(focus),
			)
		},
		confirmButton = { TextButton(shape = Tokens.Radius.button, onClick = { onConfirm(value) }) { Text(confirmLabel) } },
		dismissButton = { TextButton(shape = Tokens.Radius.button, onClick = onDismiss) { Text("Cancelar") } },
	)
	LaunchedEffect(Unit) { focus.requestFocus() }
}

/** The Location tree: loose, each folder (closed, with its notebooks inside; choosing the folder
 * itself puts the note directly in it), then the notebooks without folder. */
@Composable
fun LocationTree(store: NotesStore, selected: Place, onSelect: (Place) -> Unit) {
	val currentFolder = store.getNotebook(selected.notebookId)?.folderId
	val open = remember { mutableStateMapOf<String, Boolean>() }
	Column(Modifier.selectableGroup()) {
		RadioRow(Texts.looseNote(), selected.isLoose, { onSelect(Place.LOOSE) })
		for (folder in store.folders) {
			val notebooks = store.notebooksIn(folder.id)
			val isOpen = open[folder.id] ?: (folder.id == currentFolder)
			Row(verticalAlignment = Alignment.CenterVertically) {
				Row(Modifier.weight(1f)) {
					RadioRow(
						"Pasta ${folder.name}, ${Texts.notebooksCount(notebooks.size)}",
						selected.notebookId == null && selected.folderId == folder.id,
						{ onSelect(Place(folderId = folder.id)) },
					)
				}
				if (notebooks.isNotEmpty()) ExpandButton(isOpen, "pasta ${folder.name}") { open[folder.id] = !isOpen }
			}
			if (isOpen) {
				for (nb in notebooks) {
					RadioRow("Caderno ${nb.name}", selected.notebookId == nb.id, { onSelect(Place(notebookId = nb.id)) }, indent = 1)
				}
			}
		}
		for (nb in store.notebooksIn(null)) {
			RadioRow(Texts.notebookPlace(nb), selected.notebookId == nb.id, { onSelect(Place(notebookId = nb.id)) })
		}
	}
}

/** The button that opens or closes a branch, saying its state. */
@Composable
fun ExpandButton(open: Boolean, what: String, onClick: () -> Unit) {
	TextButton(
		shape = Tokens.Radius.button,
		onClick = onClick,
		modifier = Modifier.semantics {
			contentDescription = if (open) "Fechar $what" else "Abrir $what"
			stateDescription = if (open) "aberta" else "fechada"
		},
	) {
		Icon(if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
	}
}

@Composable
fun LocationDialog(title: String, store: NotesStore, current: Place, onChoose: (Place) -> Unit, onDismiss: () -> Unit) {
	var place by remember { mutableStateOf(current) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Column(Modifier.verticalScroll(rememberScrollState())) { LocationTree(store, place) { place = it } } },
		confirmButton = { TextButton(shape = Tokens.Radius.button, onClick = { onChoose(place) }) { Text("OK") } },
		dismissButton = { TextButton(shape = Tokens.Radius.button, onClick = onDismiss) { Text("Cancelar") } },
	)
}

/** A section title inside a dialog (TalkBack moves by headings). */
@Composable
fun DialogHeading(text: String) {
	Text(
		text,
		style = MaterialTheme.typography.titleSmall,
		modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
	)
}

/** New note: title, location (with New notebook… and New folder…) and status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewNoteDialog(
	vm: AppViewModel,
	initialTitle: String = "",
	initialPlace: Place = Place.LOOSE,
	initialLane: String = Model.DEFAULT_LANE,
	caption: String = "Nova folha",
	onCreate: (title: String, place: Place, laneId: String) -> Unit,
	onDismiss: () -> Unit,
) {
	val store = vm.store
	var title by remember { mutableStateOf(initialTitle) }
	var place by remember { mutableStateOf(store.location(initialPlace)) }
	var lane by remember { mutableStateOf(if (initialLane in Model.LANE_IDS) initialLane else Model.DEFAULT_LANE) }
	var choosingPlace by remember { mutableStateOf(false) }
	var newNotebook by remember { mutableStateOf(false) }
	var newFolder by remember { mutableStateOf(false) }
	val focus = remember { FocusRequester() }
	// Reads the revision so the location label follows notebooks and folders created here.
	@Suppress("UNUSED_VARIABLE") val revision = vm.revision

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(caption) },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState())) {
				OutlinedTextField(
					value = title,
					onValueChange = { title = it.take(Model.MAX_TITLE) },
					label = { Text("Título") },
					singleLine = true,
					keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
					modifier = Modifier.fillMaxWidth().focusRequester(focus),
				)
				DialogHeading("Local")
				OutlinedButton(
					shape = Tokens.Radius.button,
					onClick = { choosingPlace = true },
					modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Local: ${Texts.placeLabel(store, place)}. Mudar" },
				) { Text(Texts.placeLabel(store, place)) }
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(shape = Tokens.Radius.button, onClick = { newNotebook = true }) { Text("Novo caderno…") }
					TextButton(shape = Tokens.Radius.button, onClick = { newFolder = true }) { Text("Nova pasta…") }
				}
				DialogHeading("Status")
				Column(Modifier.selectableGroup()) {
					for (id in Model.LANE_IDS) RadioRow(Texts.laneName(id), lane == id, { lane = id })
				}
			}
		},
		confirmButton = { TextButton(shape = Tokens.Radius.button, onClick = { onCreate(title, place, lane) }) { Text("Criar") } },
		dismissButton = { TextButton(shape = Tokens.Radius.button, onClick = onDismiss) { Text("Cancelar") } },
	)
	LaunchedEffect(Unit) { focus.requestFocus() }

	if (choosingPlace) {
		LocationDialog("Local", store, place, { place = it; choosingPlace = false }, { choosingPlace = false })
	}
	if (newNotebook) {
		NewNotebookDialog(vm, initialFolder = store.getNotebook(place.notebookId)?.folderId ?: place.folderId, onCreated = { nb ->
			place = Place(notebookId = nb.id)
			newNotebook = false
		}, onDismiss = { newNotebook = false })
	}
	if (newFolder) {
		NewFolderDialog(vm, onCreated = { f ->
			place = Place(folderId = f.id)
			newFolder = false
		}, onDismiss = { newFolder = false })
	}
}

@Composable
fun NewFolderDialog(vm: AppViewModel, onCreated: (Folder) -> Unit, onDismiss: () -> Unit) {
	TextInputDialog(
		title = "Nova pasta",
		label = "Nome da nova pasta",
		confirmLabel = "Criar",
		onConfirm = { name ->
			vm.safe { vm.store.addFolder(name) }?.let {
				vm.say("Pasta ${it.name} criada")
				onCreated(it)
			}
		},
		onDismiss = onDismiss,
	)
}

/** New notebook: the name and the folder (No folder or one of the folders), with New folder…. */
@Composable
fun NewNotebookDialog(vm: AppViewModel, initialFolder: String? = null, onCreated: (Notebook) -> Unit, onDismiss: () -> Unit) {
	val store = vm.store
	var name by remember { mutableStateOf("") }
	var folderId by remember { mutableStateOf(initialFolder?.takeIf { store.getFolder(it) != null }) }
	var newFolder by remember { mutableStateOf(false) }
	val focus = remember { FocusRequester() }
	@Suppress("UNUSED_VARIABLE") val revision = vm.revision

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Novo caderno") },
		text = {
			Column(Modifier.verticalScroll(rememberScrollState())) {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it.take(Model.MAX_NAME) },
					label = { Text("Nome") },
					singleLine = true,
					keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
					modifier = Modifier.fillMaxWidth().focusRequester(focus),
				)
				DialogHeading("Pasta")
				Column(Modifier.selectableGroup()) {
					RadioRow("Nenhuma pasta", folderId == null, { folderId = null })
					for (f in store.folders) RadioRow(f.name, folderId == f.id, { folderId = f.id })
				}
				TextButton(shape = Tokens.Radius.button, onClick = { newFolder = true }) { Text("Nova pasta…") }
			}
		},
		confirmButton = {
			TextButton(shape = Tokens.Radius.button, onClick = {
				vm.safe { store.addNotebook(name, folderId) }?.let {
					vm.say("Caderno ${it.name} criado")
					onCreated(it)
				}
			}) { Text("Criar") }
		},
		dismissButton = { TextButton(shape = Tokens.Radius.button, onClick = onDismiss) { Text("Cancelar") } },
	)
	LaunchedEffect(Unit) { focus.requestFocus() }
	if (newFolder) {
		NewFolderDialog(vm, onCreated = { folderId = it.id; newFolder = false }, onDismiss = { newFolder = false })
	}
}

/** A clickable line that TalkBack reads as a button. */
fun Modifier.buttonRow(label: String, onClick: () -> Unit): Modifier =
	this.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
