// NotasWorkflow Android - BottomTabs.kt
// The tabs at the bottom of the app (Notes, Search, Notebooks and folders, More) and the More tab,
// with what was in the menu of the top bar: Export…, How many notes in each lane, Settings…, User manual….
// TalkBack reads each tab as a tab, with its position and whether it is selected.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.Screen
import br.com.wellingtoncruz.notasworkflow.TABS

private fun tabLabel(tab: Screen): String = when (tab) {
	Screen.SearchNotes -> "Buscar"
	Screen.Organize -> "Cadernos e pastas"
	Screen.More -> "Mais"
	else -> "Folhas"
}

private fun tabIcon(tab: Screen): ImageVector = when (tab) {
	Screen.SearchNotes -> Icons.Outlined.Search
	Screen.Organize -> Icons.AutoMirrored.Outlined.List
	Screen.More -> Icons.Outlined.Menu
	else -> Icons.Outlined.Home
}

/** The tab bar: white (dark in the dark theme), the selected tab in Trello's selected blue. */
@Composable
fun BottomTabs(vm: AppViewModel) {
	val current = vm.current
	NavigationBar(containerColor = trello.card, contentColor = trello.text, tonalElevation = Tokens.Elevation.card) {
		for (tab in TABS) {
			NavigationBarItem(
				selected = current == tab,
				onClick = { vm.selectTab(tab) },
				icon = { Icon(tabIcon(tab), contentDescription = null) },
				label = { Text(tabLabel(tab), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
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

/** The More tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	var exportPicker by remember { mutableStateOf(false) }
	var exportRequest by remember { mutableStateOf<ExportRequest?>(null) }

	BackHandler { vm.back() }

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = { TopAppBar(colors = trelloTopBarColors(), title = { Text("Mais") }) },
		bottomBar = { BottomTabs(vm) },
	) { padding ->
		Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
			MoreItem(Icons.Outlined.Share, "Exportar…", "Um caderno ou uma pasta como Word, Markdown ou HTML") { exportPicker = true }
			MoreItem(Icons.Outlined.CheckCircle, "Quantas folhas em cada raia", "Do que está sendo mostrado na aba Folhas") {
				vm.say(vm.laneCounts())
			}
			MoreItem(Icons.Outlined.Settings, "Configurações…", "Salvamento, arquivo das folhas, importar e exportar") {
				vm.open(Screen.Settings)
			}
			MoreItem(Icons.Outlined.Info, "Manual de uso…", null) { vm.open(Screen.Manual) }
		}
	}

	if (exportPicker) {
		ExportPicker(vm, onChosen = { exportPicker = false; exportRequest = it }, onDismiss = { exportPicker = false })
	}
	exportRequest?.let { ExportFlow(vm, it) { exportRequest = null } }
}

@Composable
private fun MoreItem(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.buttonRow(label, onClick)
			.semantics(mergeDescendants = true) { contentDescription = if (detail != null) "$label. $detail" else label }
			.padding(horizontal = Tokens.Space.s200, vertical = Tokens.Space.s150),
	) {
		Icon(icon, contentDescription = null, tint = trello.textSubtle)
		Spacer(Modifier.width(Tokens.Space.s200))
		Column(Modifier.weight(1f)) {
			Text(label, style = MaterialTheme.typography.bodyLarge, color = trello.text)
			if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = trello.textSubtle)
		}
		Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = trello.textSubtlest)
	}
	HorizontalDivider(color = trello.border)
}
