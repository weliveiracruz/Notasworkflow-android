// NotasWorkflow Android - NoteOps.kt
// The changes made to a note from the lists: next or previous status, up or down inside its
// lane, with the same spoken answers as the add-on.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.core.Markup
import br.com.wellingtoncruz.notasworkflow.core.Model
import br.com.wellingtoncruz.notasworkflow.core.Note
import br.com.wellingtoncruz.notasworkflow.core.Place
import br.com.wellingtoncruz.notasworkflow.core.Texts

/** The notes of the lane of a note, as the main list shows them. */
fun AppViewModel.laneOf(note: Note): List<Note> = store.filtered(filter).filter { it.laneId == note.laneId }

/** Next (step 1) or previous (step -1) status. The focus goes to the next note of the old lane. */
fun AppViewModel.stepStatus(noteId: String, step: Int) {
	val note = store.getNote(noteId) ?: return
	val target = Model.neighbourLane(note.laneId, step)
	if (target == null) {
		say(if (step < 0) "Já está na primeira raia" else "Já está na última raia")
		return
	}
	val lane = laneOf(note)
	val i = lane.indexOfFirst { it.id == noteId }
	val next = lane.getOrNull(i + 1) ?: lane.getOrNull(i - 1)
	if (safe { store.moveNote(noteId, target) } == true) {
		focusNoteId = next?.id
		say(Texts.movedTo(target))
	}
}

/** Up (step -1) or down (step 1) inside the lane. */
fun AppViewModel.moveInLane(noteId: String, step: Int) {
	if (filter.kind == "recent") {
		say("Folhas recentes ficam na ordem da última alteração; aqui a ordem não pode ser mudada")
		return
	}
	val note = store.getNote(noteId) ?: return
	val lane = laneOf(note)
	val i = lane.indexOfFirst { it.id == noteId }
	val other = lane.getOrNull(i + step)
	if (other == null) {
		say(if (step < 0) "Já está no topo da raia" else "Já está no fim da raia")
		return
	}
	if (safe { store.placeNoteBeside(noteId, other.id, after = step > 0) } == true) {
		focusNoteId = noteId
		say(Texts.position(i + step + 1, lane.size))
	}
}

fun AppViewModel.deleteNote(note: Note) {
	if (safe { store.deleteNote(note.id) } != null) say("Folha excluída: ${Texts.noteTitle(note)}")
}

fun AppViewModel.moveNoteTo(note: Note, place: Place) {
	if (safe { store.moveNoteTo(note.id, place) } == true) say(Texts.movedToPlace(Texts.placeLabel(store, store.location(place))))
}

/** Where a new note goes by default: the notebook or folder being shown. */
fun AppViewModel.defaultPlace(): Place = when (filter.kind) {
	"notebook" -> Place(notebookId = filter.id)
	"folder" -> Place(folderId = filter.id)
	else -> Place.LOOSE
}

/** Creates a note (with the text of a document, when there is one) and opens it. */
fun AppViewModel.createNote(title: String, place: Place, laneId: String, documentText: String? = null, fileName: String? = null) {
	var content = ""
	var cut = false
	if (documentText != null) {
		val fitted = br.com.wellingtoncruz.notasworkflow.core.Importer.fitContent(documentText)
		content = Markup.textToHtml(fitted.first)
		cut = fitted.second
	}
	val note = safe { store.addNote(title, laneId, place, content) } ?: return
	openLanes[note.laneId] = true
	say(
		if (fileName != null) "Folha criada em ${Texts.laneName(note.laneId)} com o texto de $fileName"
		else "Folha criada em ${Texts.laneName(note.laneId)}",
	)
	if (cut) say(Texts.contentCut())
	openNote(note.id, focusContent = true)
}

/** "A fazer, 3 folhas; Em andamento, 1 folha..." of the notes shown. */
fun AppViewModel.laneCounts(): String {
	val byLane = Model.byLane(store.filtered(filter))
	return Model.LANE_IDS.joinToString("; ") { Texts.laneNode(it, byLane.getValue(it).size) }
}
