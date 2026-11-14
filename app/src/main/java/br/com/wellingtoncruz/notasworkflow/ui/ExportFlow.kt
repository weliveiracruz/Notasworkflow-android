// NotasWorkflow Android - ExportFlow.kt
// Export… as a document: the format (Word, Markdown, HTML), for a notebook or a folder one file
// with all the notes or one file for each note, then where (the system's file picker).
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.core.Exporter
import br.com.wellingtoncruz.notasworkflow.core.StoreException
import br.com.wellingtoncruz.notasworkflow.core.Texts

/** What is exported: kind is note, notebook or folder. */
data class ExportRequest(val kind: String, val id: String)

@Composable
fun ExportFlow(vm: AppViewModel, request: ExportRequest, onFinish: () -> Unit) {
	val plan = remember(request) { vm.exportPlan(request.kind, request.id) }
	var fmt by remember { mutableStateOf<String?>(null) }
	var waiting by remember { mutableStateOf(false) }

	val docx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_TYPES.getValue("docx"))) { uri ->
		if (uri != null && plan != null) vm.exportToFile(plan, "docx", uri)
		onFinish()
	}
	val md = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_TYPES.getValue("md"))) { uri ->
		if (uri != null && plan != null) vm.exportToFile(plan, "md", uri)
		onFinish()
	}
	val html = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME_TYPES.getValue("html"))) { uri ->
		if (uri != null && plan != null) vm.exportToFile(plan, "html", uri)
		onFinish()
	}
	val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		val chosen = fmt
		if (uri != null && plan != null && chosen != null) vm.exportSeparate(plan, chosen, uri)
		onFinish()
	}

	if (plan == null) {
		LaunchedEffect(request) {
			vm.say(Texts.storeError(StoreException("notFound")))
			onFinish()
		}
		return
	}
	if (plan.count == 0) {
		LaunchedEffect(request) {
			vm.say(Texts.nothingToExport(plan.title ?: plan.name))
			onFinish()
		}
		return
	}

	fun saveOneFile(format: String) {
		waiting = true
		val name = Exporter.safeName(plan.name, Texts.untitled()) + Exporter.EXTENSIONS.getValue(format)
		when (format) {
			"docx" -> docx.launch(name)
			"md" -> md.launch(name)
			else -> html.launch(name)
		}
	}

	if (waiting) return
	val chosen = fmt
	if (chosen == null) {
		ChoiceDialog(
			title = "Exportar",
			message = "Exportar ${plan.title ?: plan.name} como:",
			options = Texts.EXPORT_FORMATS,
			onChoose = { i ->
				val format = Exporter.FORMATS[i]
				fmt = format
				if (request.kind == "note") saveOneFile(format)
			},
			onDismiss = onFinish,
		)
	} else if (request.kind != "note") {
		ChoiceDialog(
			title = "Exportar",
			message = "Como exportar ${Texts.notesCount(plan.count)}:",
			options = Texts.EXPORT_MODES,
			onChoose = { i ->
				if (i == 0) {
					saveOneFile(chosen)
				} else {
					waiting = true
					vm.say("Escolha onde criar a pasta ${plan.name}")
					tree.launch(null)
				}
			},
			onDismiss = onFinish,
		)
	}
}

/** Export… of the main screen: a notebook or a folder, chosen in a list. */
@Composable
fun ExportPicker(vm: AppViewModel, onChosen: (ExportRequest) -> Unit, onDismiss: () -> Unit) {
	var kind by remember { mutableStateOf<String?>(null) }
	val store = vm.store
	when (kind) {
		null -> ChoiceDialog(
			title = "Exportar",
			options = listOf("Caderno…", "Pasta…"),
			onChoose = { i ->
				val k = if (i == 0) "notebook" else "folder"
				if (k == "notebook" && store.notebooks.isEmpty()) {
					vm.say("Não há cadernos.")
					onDismiss()
				} else if (k == "folder" && store.folders.isEmpty()) {
					vm.say("Não há pastas.")
					onDismiss()
				} else {
					kind = k
				}
			},
			onDismiss = onDismiss,
		)
		"notebook" -> ChoiceDialog(
			title = "Exportar qual caderno?",
			options = store.notebooks.map { Texts.notebookPlace(it, store.getFolder(it.folderId)) },
			onChoose = { i -> onChosen(ExportRequest("notebook", store.notebooks[i].id)) },
			onDismiss = onDismiss,
		)
		else -> ChoiceDialog(
			title = "Exportar qual pasta?",
			options = store.folders.map { it.name },
			onChoose = { i -> onChosen(ExportRequest("folder", store.folders[i].id)) },
			onDismiss = onDismiss,
		)
	}
}
