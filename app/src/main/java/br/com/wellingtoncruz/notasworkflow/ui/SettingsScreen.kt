// NotasWorkflow Android - SettingsScreen.kt
// Settings: the automatic saving time, announcing the automatic saving, the notes file (the app's
// own, or a document such as the NotasWorkflowNVDA-data.json of the NVDA add-on kept in Google Drive
// or OneDrive, to have the same notes on the phone and on the computer), Import and Export notes.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	BackHandler { vm.back() }

	val openData = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.useDocument(uri) }
	val createData = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		if (uri != null) vm.useDocument(uri)
	}
	val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importNotes(uri) }
	val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		if (uri != null) vm.exportNotes(uri)
	}
	val jsonTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = {
			TopAppBar(
				colors = trelloTopBarColors(),
				title = { Text("Configurações") },
				navigationIcon = {
					IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Fechar") }
				},
			)
		},
	) { padding ->
		Column(
			Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
		) {
			Heading("Salvamento")
			val seconds = vm.autoSaveSeconds
			Text("Salvar automaticamente depois de $seconds segundos")
			Slider(
				value = seconds.toFloat(),
				onValueChange = { vm.setAutoSave(it.roundToInt()) },
				valueRange = 1f..30f,
				steps = 28,
				modifier = Modifier.semantics {
					contentDescription = "Salvar automaticamente depois de (segundos)"
					stateDescription = "$seconds segundos"
				},
			)
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 48.dp)
					.toggleable(value = vm.announceAutoSave, role = Role.Switch, onValueChange = { vm.setAnnounce(it) }),
			) {
				Text("Anunciar o salvamento automático", Modifier.weight(1f))
				Switch(checked = vm.announceAutoSave, onCheckedChange = null)
			}

			Heading("Arquivo das folhas")
			Text(
				vm.dataFileName?.let { "As folhas estão no documento $it." }
					?: "As folhas estão no armazenamento do aparelho.",
			)
			Text(
				"Para ter as mesmas folhas no celular e no computador, abra aqui o arquivo NotasWorkflowNVDA-data.json " +
					"que o complemento do NVDA grava numa pasta do Google Drive ou do OneDrive.",
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.padding(vertical = 8.dp),
			)
			OutlinedButton(shape = Tokens.Radius.button, onClick = { openData.launch(jsonTypes) }, modifier = Modifier.fillMaxWidth()) {
				Text("Abrir arquivo de folhas…")
			}
			OutlinedButton(shape = Tokens.Radius.button, onClick = { createData.launch("NotasWorkflowNVDA-data.json") }, modifier = Modifier.fillMaxWidth()) {
				Text("Criar arquivo de folhas…")
			}
			if (vm.dataFileName != null) {
				OutlinedButton(shape = Tokens.Radius.button, onClick = { vm.useDeviceStorage() }, modifier = Modifier.fillMaxWidth()) {
					Text("Usar o armazenamento do aparelho")
				}
			}

			Heading("Importar e exportar")
			OutlinedButton(shape = Tokens.Radius.button, onClick = { importFile.launch(jsonTypes) }, modifier = Modifier.fillMaxWidth()) {
				Text("Importar folhas…")
			}
			OutlinedButton(shape = Tokens.Radius.button, onClick = { exportFile.launch("NotasWorkflow-folhas.json") }, modifier = Modifier.fillMaxWidth()) {
				Text("Exportar folhas…")
			}
			Text(
				"Importar junta as folhas de um arquivo exportado pelo NotasWorkflow, pelo complemento do NVDA ou pelo aplicativo web: " +
					"uma folha que já existe fica com a versão alterada por último.",
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.padding(vertical = 8.dp),
			)
		}
	}
}

@Composable
private fun Heading(text: String) {
	Text(
		text,
		style = MaterialTheme.typography.titleMedium,
		modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
	)
}
