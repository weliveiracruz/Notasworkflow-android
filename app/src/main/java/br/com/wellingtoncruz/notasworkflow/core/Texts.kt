// NotasWorkflow Android - Texts.kt
// Texts built from the data (lane names, counts, places, dates), in Brazilian Portuguese, the
// same words as the NVDA add-on. Pure Kotlin, so they are tested with the rest of the core.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import java.time.ZoneId
import java.time.ZonedDateTime

object Texts {
	const val APP_NAME = "NotasWorkflow"
	const val LANG = "pt-BR"

	fun laneName(laneId: String?): String = when (laneId) {
		"inprogress" -> "Em andamento"
		"blocked" -> "Bloqueada"
		"done" -> "Concluída"
		else -> "A fazer"
	}

	fun untitled() = "Sem título"

	fun noteTitle(note: Note): String = note.title.ifEmpty { untitled() }

	fun notesCount(count: Int) = if (count == 1) "1 folha" else "$count folhas"

	fun notebooksCount(count: Int) = if (count == 1) "1 caderno" else "$count cadernos"

	fun looseNote() = "Folha solta (sem caderno nem pasta)"

	fun notebookPlace(notebook: Notebook, folder: Folder? = null) =
		if (folder != null) "Caderno ${notebook.name} (pasta ${folder.name})" else "Caderno ${notebook.name}"

	fun folderPlace(folder: Folder) = "Pasta ${folder.name}"

	/** Text of the place of a note, or empty for a loose note. */
	fun placeOf(store: NotesStore, note: Note): String {
		store.getNotebook(note.notebookId)?.let { return "caderno ${it.name}" }
		store.getFolder(note.folderId)?.let { return "pasta ${it.name}" }
		return ""
	}

	/** Label of a place, as in the Location field. */
	fun placeLabel(store: NotesStore, place: Place): String {
		store.getNotebook(place.notebookId)?.let { nb -> return notebookPlace(nb, store.getFolder(nb.folderId)) }
		store.getFolder(place.folderId)?.let { return folderPlace(it) }
		return looseNote()
	}

	fun filterName(store: NotesStore, filter: NoteFilter): String = when (filter.kind) {
		"loose" -> "Folhas soltas"
		"recent" -> "Folhas recentes"
		"notebook" -> store.getNotebook(filter.id)?.let { "Caderno: ${it.name}" } ?: "Todas as folhas"
		"folder" -> store.getFolder(filter.id)?.let { "Pasta: ${it.name}" } ?: "Todas as folhas"
		else -> "Todas as folhas"
	}

	private fun local(iso: String?, zone: ZoneId): ZonedDateTime? = Model.parseIso(iso)?.atZone(zone)

	fun formatDate(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
		val dt = local(iso, zone) ?: return ""
		return "%02d/%02d/%d".format(dt.dayOfMonth, dt.monthValue, dt.year)
	}

	fun formatDateTime(dt: ZonedDateTime): String {
		val local = dt.withZoneSameInstant(ZoneId.systemDefault())
		return "%02d/%02d/%d %02d:%02d".format(local.dayOfMonth, local.monthValue, local.year, local.hour, local.minute)
	}

	fun formatTime(dt: ZonedDateTime = ZonedDateTime.now()) = "%02d:%02d".format(dt.hour, dt.minute)

	/** What the lists say for a note: title, status (when asked), place and date. */
	fun noteDescription(note: Note, place: String, withLane: Boolean = false): String {
		val parts = ArrayList<String>()
		parts.add(noteTitle(note))
		if (withLane) parts.add(laneName(note.laneId))
		if (place.isNotEmpty()) parts.add(place)
		val date = formatDate(note.updatedAt)
		if (date.isNotEmpty()) parts.add("atualizada em $date")
		return parts.joinToString(", ")
	}

	fun laneNode(laneId: String, count: Int) = "${laneName(laneId)}, ${notesCount(count)}"

	fun movedTo(laneId: String) = "Status atualizado para ${laneName(laneId)}"

	fun movedToPlace(label: String) = "Agora em: $label"

	fun position(position: Int, total: Int) = "Posição $position de $total"

	fun showMore(count: Int, remaining: Int) = "Mostrar mais $count folhas ($remaining ainda não mostradas)"

	fun formatName(kind: String): String = when (kind) {
		"bold" -> "Negrito"
		"italic" -> "Itálico"
		"strike" -> "Tachado"
		"code" -> "Código"
		"heading2" -> "Título 2"
		"heading3" -> "Título 3"
		"bullet" -> "Lista com marcadores"
		"ordered" -> "Lista numerada"
		"quote" -> "Citação"
		else -> "Divisor"
	}

	fun formatRemoved(kind: String) = "${formatName(kind)} removido"

	fun storeError(e: StoreException): String = when (e.kind) {
		"write" -> "Não foi possível salvar as folhas. Confira se há espaço e se o arquivo das folhas pode ser gravado."
		"tooLarge" -> "O arquivo tem mais de 200 MB e não foi aberto."
		"invalidImport" -> "Este arquivo não tem folhas do NotasWorkflow."
		"emptyName" -> "O nome não pode ficar vazio."
		else -> "Este item não existe mais."
	}

	fun loadWarning(w: LoadWarning): String =
		if (w.restored) "As folhas salvas estavam danificadas. O arquivo foi guardado como ${w.savedAs} e a cópia de segurança foi usada."
		else "As folhas salvas estavam danificadas. O arquivo foi guardado como ${w.savedAs} e não havia cópia de segurança que pudesse ser usada."

	fun imported(c: ImportCounts) =
		"Importado: ${notesCount(c.notes)}, ${notebooksCount(c.notebooks)}, ${c.folders} pastas"

	// -- export ---------------------------------------------------------------------------------

	val EXPORT_FORMATS = listOf("Documento do Word (.docx)", "Markdown (.md)", "Página HTML (.html)")
	val EXPORT_MODES = listOf("Um arquivo com todas as folhas", "Um arquivo para cada folha")

	/** Lines written under the title of an exported note: status, place and dates. */
	fun noteDetails(note: Note, place: String): List<String> {
		val lines = ArrayList<String>()
		lines.add("Status: ${laneName(note.laneId)}")
		if (place.isNotEmpty()) lines.add("Local: $place")
		val created = formatDate(note.createdAt)
		val updated = formatDate(note.updatedAt)
		if (created.isNotEmpty() && updated.isNotEmpty()) lines.add("Criada em $created, atualizada em $updated")
		return lines
	}

	fun exportIntro(count: Int) = "Exportado do NotasWorkflow em ${formatDate(Model.nowIso())}: ${notesCount(count)}."

	fun directInFolder() = "Folhas direto na pasta"

	fun nothingToExport(name: String) = "Nada para exportar: $name não tem folhas."

	fun exported(count: Int, file: String) = "Exportado: ${notesCount(count)}, em $file"

	fun exportedSeparate(count: Int, folder: String) = "Exportado: ${notesCount(count)}, um arquivo por folha, na pasta $folder"

	fun exportFailed() = "Não foi possível exportar. Confira se o local escolhido pode ser gravado."

	// -- documents ------------------------------------------------------------------------------

	val DOCUMENT_LABELS = mapOf(
		"slide" to "Slide {number}",
		"slideTitled" to "Slide {number}: {title}",
		"notes" to "Anotações do orador:",
		"from" to "De:",
		"to" to "Para:",
		"cc" to "Cc:",
		"date" to "Data:",
		"attachments" to "Anexos:",
	)

	fun documentError(kind: String, file: String): String = when (kind) {
		"unsupported" -> "$file não pode ser lido. Escolha um documento do Word (.docx), do PowerPoint (.pptx), de texto (.txt), HTML ou de e-mail (.eml)."
		"tooBig" -> "$file tem mais de 50 MB e não foi lido."
		"damaged" -> "Não foi possível ler $file: o arquivo está danificado ou protegido por senha."
		"empty" -> "$file não tem texto."
		else -> "Não foi possível abrir $file."
	}

	fun contentCut() = "O texto era longo demais: só a primeira parte foi usada (uma folha guarda até 1 MB)."

	fun noteFull() = "A folha está cheia (até 1 MB): nada foi inserido."
}
