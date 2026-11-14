// NotasWorkflow Android - BottomTabs.kt
// The five tabs at the bottom of the app: Home (all the notes in their lanes), New (create a note,
// a notebook, a folder or a note from a document), Notebooks and folders, Search and Settings.
// TalkBack reads each tab as a tab, with its position and whether it is selected.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.Screen
import br.com.wellingtoncruz.notasworkflow.TABS
import br.com.wellingtoncruz.notasworkflow.core.ImportedDocument
import br.com.wellingtoncruz.notasworkflow.core.Model
import br.com.wellingtoncruz.notasworkflow.core.NoteFilter

/** (short label shown, full name spoken, icon) of each tab. */
private fun tabInfo(tab: Screen): Triple<String, String, ImageVector> = when (tab) {
	Screen.New -> Triple("Novo", "Novo", Icons.Outlined.Add)
	Screen.Organize -> Triple("Cadernos", "Cadernos e pastas", Icons.AutoMirrored.Outlined.List)
	Screen.SearchNotes -> Triple("Buscar", "Buscar", Icons.Outlined.Search)
	Screen.Settings -> Triple("Configurações", "Configurações", Icons.Outlined.Settings)
	else -> Triple("Início", "Início, todas as folhas", Icons.Outlined.Home)
}

/** The tab bar: white (dark in the dark theme), the selected tab in Trello's selected blue. */
@Composable
fun BottomTabs(vm: AppViewModel) {
	val current = vm.current
	NavigationBar(containerColor = trello.card, contentColor = trello.text, tonalElevation = Tokens.Elevation.card) {
		for (tab in TABS) {
			val (label, spoken, icon) = tabInfo(tab)
			NavigationBarItem(
				selected = current == tab,
				onClick = { vm.selectTab(tab) },
				icon = { Icon(icon, contentDescription = null) },
				label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
				modifier = Modifier.semantics { contentDescription = spoken },
				colors = NavigationBarItemDefaults.colors(
					selectedIconColor = trello.onSelected,
					selectedTextColor = trello.onSelected,
					indicatorColor = trello.selected,
					unselectedIconColor = trello.textSubtle,
					unselectedTextColor = trello.textSubtle,
				),
			)
		}
	}
}

/** The New tab: a note, a notebook, a folder or a note from a document. A new note opens and,
 * when it is closed, the Home tab shows it in its lane. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	var newNote by remember { mutableStateOf(false) }
	var newNotebook by remember { mutableStateOf(false) }
	var newFolder by remember { mutableStateOf(false) }
	var document by remember { mutableStateOf<Pair<ImportedDocument, String>?>(null) }
	val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) vm.readDocument(uri) { doc, name -> document = doc to name }
	}

	BackHandler { vm.back() }

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = { TopAppBar(colors = trelloTopBarColors(), title = { Text("Novo") }) },
		bottomBar = { BottomTabs(vm) },
	) { padding ->
		Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
			NewItem(Icons.Outlined.Create, "Folha…", "Título, local e status; a folha abre para você escrever") { newNote = true }
			NewItem(Icons.AutoMirrored.Outlined.List, "Caderno…", "Um caderno, numa pasta ou sem pasta") { newNotebook = true }
			NewItem(Icons.Outlined.AddCircle, "Pasta…", "Uma pasta para guardar cadernos e folhas") { newFolder = true }
			NewItem(Icons.Outlined.Email, "Folha a partir de documento…", "Word, PowerPoint, texto, HTML ou e-mail") {
				openDocument.launch(DOCUMENT_TYPES)
			}
		}
	}

	if (newNote) {
		NewNoteDialog(vm, initialPlace = vm.defaultPlace(), onCreate = { title, place, lane ->
			newNote = false
			vm.selectTab(Screen.Main)
			vm.createNote(title, place, lane)
		}, onDismiss = { newNote = false })
	}
	document?.let { (doc, name) ->
		NewNoteDialog(
			vm, initialTitle = doc.title, initialPlace = vm.defaultPlace(), caption = "Nova folha a partir de documento",
			onCreate = { title, place, lane ->
				document = null
				vm.selectTab(Screen.Main)
				vm.createNote(title, place, lane, doc.text, name)
			},
			onDismiss = { document = null },
		)
	}
	if (newNotebook) {
		val folder = if (vm.filter.kind == "folder") vm.filter.id else null
		NewNotebookDialog(vm, initialFolder = folder, onCreated = { nb ->
			newNotebook = false
			// Home shows the new notebook, ready for its first note.
			vm.filter = NoteFilter.notebook(nb.id)
			for (lane in Model.LANE_IDS) vm.openLanes[lane] = true
			vm.selectTab(Screen.Main)
		}, onDismiss = { newNotebook = false })
	}
	if (newFolder) NewFolderDialog(vm, onCreated = { newFolder = false }, onDismiss = { newFolder = false })
}

@Composable
private fun NewItem(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.buttonRow(label, onClick)
			.semantics(mergeDescendants = true) { contentDescription = if (detail != null) "$label. $detail" else label }
			.padding(horizontal = Tokens.Space.s200, vertical = Tokens.Space.s150),
	) {
		Icon(icon, contentDescription = null, tint = trello.primary)
		Spacer(Modifier.width(Tokens.Space.s200))
		Column(Modifier.weight(1f)) {
			Text(label, style = MaterialTheme.typography.bodyLarge, color = trello.text)
			if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = trello.textSubtle)
		}
		Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = trello.textSubtlest)
	}
	HorizontalDivider(color = trello.border)
}
