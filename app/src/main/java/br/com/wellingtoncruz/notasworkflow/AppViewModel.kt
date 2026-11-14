// NotasWorkflow Android - AppViewModel.kt
// The state of the app: the notes store, the settings, the screen being shown and the messages
// spoken by TalkBack (shown as snackbars). Reading, writing, import and export run in background.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.wellingtoncruz.notasworkflow.android.Saf
import br.com.wellingtoncruz.notasworkflow.android.SafDataFile
import br.com.wellingtoncruz.notasworkflow.android.SafExportFolder
import br.com.wellingtoncruz.notasworkflow.core.DataFile
import br.com.wellingtoncruz.notasworkflow.core.DocumentException
import br.com.wellingtoncruz.notasworkflow.core.Exporter
import br.com.wellingtoncruz.notasworkflow.core.ImportedDocument
import br.com.wellingtoncruz.notasworkflow.core.Importer
import br.com.wellingtoncruz.notasworkflow.core.LocalDataFile
import br.com.wellingtoncruz.notasworkflow.core.Model
import br.com.wellingtoncruz.notasworkflow.core.Note
import br.com.wellingtoncruz.notasworkflow.core.NoteFilter
import br.com.wellingtoncruz.notasworkflow.core.NotesStore
import br.com.wellingtoncruz.notasworkflow.core.Search
import br.com.wellingtoncruz.notasworkflow.core.StoreException
import br.com.wellingtoncruz.notasworkflow.core.Texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** The screens of the app (the dialogs are shown over them). */
sealed class Screen {
	data object Main : Screen()

	/** laneOrder: ids of the notes of the note's lane when it was opened, to go to the next one. */
	data class NoteEditor(val noteId: String, val laneOrder: List<String>, val focusContent: Boolean = false) : Screen()

	data object Organize : Screen()

	data object SearchNotes : Screen()

	data object Settings : Screen()

	data object Manual : Screen()

	/** The "More" tab: Export, the count of each lane, Settings and the manual. */
	data object More : Screen()
}

/** The tabs at the bottom of the app, in order. */
val TABS: List<Screen> = listOf(Screen.Main, Screen.SearchNotes, Screen.Organize, Screen.More)

/** What an export writes: (heading or null, notes, subfolder name or null) for each section. */
data class ExportPlan(
	val name: String,
	val title: String?,
	val sections: List<Triple<String?, List<Note>, String?>>,
	val count: Int,
	val place: String,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
	private val context: Context get() = getApplication()
	private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

	val store: NotesStore = NotesStore(initialDataFile())

	/** Changes on every change of the notes: the screens read the store again. */
	var revision by mutableIntStateOf(0)
		private set
	var loaded by mutableStateOf(false)
		private set

	var autoSaveSeconds by mutableIntStateOf(prefs.getInt("autoSaveSeconds", 2).coerceIn(1, 30))
		private set
	var announceAutoSave by mutableStateOf(prefs.getBoolean("announceAutoSave", false))
		private set
	var dataFileName by mutableStateOf(prefs.getString("syncName", null))
		private set

	val screens = mutableStateListOf<Screen>(Screen.Main)
	var filter by mutableStateOf(NoteFilter.ALL)

	/** Lanes open in the main list (they start closed, as in the add-on). */
	val openLanes = mutableStateMapOf<String, Boolean>()

	/** Note that gets the focus when the main list is shown again. */
	var focusNoteId by mutableStateOf<String?>(null)

	private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
	val messages: SharedFlow<String> = _messages

	/** The search index is used by one thread only. */
	val searchDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
	val searchIndex = Search.Index()

	init {
		store.onChange = { revision++ }
		store.enableBackgroundSaving { e -> say(Texts.storeError(e)) }
		load()
	}

	private fun initialDataFile(): DataFile {
		val uri = prefs.getString("syncUri", null)
		return if (uri != null) SafDataFile(context, Uri.parse(uri)) else LocalDataFile(context.filesDir)
	}

	fun say(text: String) {
		_messages.tryEmit(text)
	}

	private fun load() {
		viewModelScope.launch(Dispatchers.IO) {
			store.load()
			withContext(Dispatchers.Main) {
				loaded = true
				revision++
				store.warning?.let { say(Texts.loadWarning(it)) }
			}
		}
	}

	/** Runs a change of the store; a problem is spoken. Returns the result, or null. */
	fun <T> safe(block: () -> T): T? = try {
		block()
	} catch (e: StoreException) {
		say(Texts.storeError(e))
		null
	}

	/** When the app comes back: reads the file again if another device or program changed it. */
	fun onResume() {
		if (!loaded) return
		viewModelScope.launch(Dispatchers.IO) {
			if (store.changedOnDisk()) {
				store.load()
				withContext(Dispatchers.Main) {
					revision++
					say("Folhas atualizadas com as mudanças do arquivo")
				}
			}
		}
	}

	/** When the app leaves the screen: everything pending is written. */
	fun onStop() {
		viewModelScope.launch(Dispatchers.IO) { store.flush() }
	}

	override fun onCleared() {
		store.flush()
		searchDispatcher.close()
		super.onCleared()
	}

	// -- navigation ------------------------------------------------------------------------------

	val current: Screen get() = screens.last()

	fun open(screen: Screen) {
		screens.add(screen)
	}

	/** Shows a tab: the notes tab is the base; every other tab goes on top of it, so Back returns to the notes. */
	fun selectTab(tab: Screen) {
		if (current == tab) return
		screens.clear()
		screens.add(Screen.Main)
		if (tab != Screen.Main) screens.add(tab)
	}

	fun back() {
		if (screens.size > 1) screens.removeAt(screens.size - 1)
	}

	/** Opens a note; laneOrder is the order of its lane in the main list. */
	fun openNote(noteId: String, focusContent: Boolean = false) {
		val note = store.getNote(noteId) ?: return
		val lane = store.filtered(filter).filter { it.laneId == note.laneId }.map { it.id }
		open(Screen.NoteEditor(noteId, lane, focusContent))
	}

	// -- settings --------------------------------------------------------------------------------

	fun setAutoSave(seconds: Int) {
		autoSaveSeconds = seconds.coerceIn(1, 30)
		prefs.edit().putInt("autoSaveSeconds", autoSaveSeconds).apply()
	}

	fun setAnnounce(value: Boolean) {
		announceAutoSave = value
		prefs.edit().putBoolean("announceAutoSave", value).apply()
	}

	/** Uses a document (for example the add-on's file in Google Drive) as the data file. */
	fun useDocument(uri: Uri) {
		Saf.keepPermission(context, uri)
		val name = Saf.displayName(context, uri)
		relocate(SafDataFile(context, uri)) {
			prefs.edit().putString("syncUri", uri.toString()).putString("syncName", name).apply()
			dataFileName = name
		}
	}

	/** Goes back to the file kept by the app on the device. */
	fun useDeviceStorage() {
		relocate(LocalDataFile(context.filesDir)) {
			prefs.edit().remove("syncUri").remove("syncName").apply()
			dataFileName = null
		}
	}

	private fun relocate(target: DataFile, done: () -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			val result = try {
				store.relocate(target)
			} catch (e: StoreException) {
				withContext(Dispatchers.Main) { say(Texts.storeError(e)) }
				return@launch
			}
			withContext(Dispatchers.Main) {
				done()
				revision++
				say(
					if (result == "loaded") "Usando as folhas do arquivo: ${Texts.notesCount(store.notes.size)}"
					else "Folhas salvas no novo arquivo",
				)
			}
		}
	}

	fun importNotes(uri: Uri) {
		viewModelScope.launch(Dispatchers.IO) {
			val bytes = try {
				Saf.readBytes(context.contentResolver, uri, NotesStore.MAX_FILE_SIZE)
			} catch (e: Saf.TooBigException) {
				withContext(Dispatchers.Main) { say(Texts.storeError(StoreException("tooLarge"))) }
				return@launch
			} catch (e: Exception) {
				withContext(Dispatchers.Main) { say(Texts.storeError(StoreException("invalidImport"))) }
				return@launch
			}
			withContext(Dispatchers.Main) {
				safe { store.importBytes(bytes) }?.let { say(Texts.imported(it)) }
			}
		}
	}

	fun exportNotes(uri: Uri) {
		val bytes = store.exportBytes()
		val count = store.notes.size
		viewModelScope.launch(Dispatchers.IO) {
			val ok = try {
				Saf.openOutput(context.contentResolver, uri).use { it.write(bytes) }
				true
			} catch (e: Exception) {
				false
			}
			withContext(Dispatchers.Main) {
				say(if (ok) "Exportado: ${Texts.notesCount(count)}" else Texts.exportFailed())
			}
		}
	}

	// -- documents -------------------------------------------------------------------------------

	/** Reads a document in background; done gets the document and the file name. */
	fun readDocument(uri: Uri, done: (ImportedDocument, String) -> Unit) {
		val name = Saf.displayName(context, uri)
		val mime = context.contentResolver.getType(uri)
		say("Lendo o documento…")
		viewModelScope.launch(Dispatchers.IO) {
			val result: Any = try {
				val bytes = Saf.readBytes(context.contentResolver, uri, Importer.MAX_FILE.toLong())
				Importer.readDocument(name, bytes, Texts.DOCUMENT_LABELS, { Texts.formatDateTime(it) }, mime)
			} catch (e: Saf.TooBigException) {
				DocumentException("tooBig")
			} catch (e: DocumentException) {
				e
			} catch (e: Exception) {
				DocumentException("unreadable")
			}
			withContext(Dispatchers.Main) {
				if (result is ImportedDocument) done(result, name)
				else say(Texts.documentError((result as DocumentException).kind, name))
			}
		}
	}

	// -- export as a document --------------------------------------------------------------------

	/** What exporting a note, notebook or folder writes, or null when it no longer exists. */
	fun exportPlan(kind: String, id: String): ExportPlan? {
		fun lanes(notes: List<Note>): List<Note> {
			val grouped = Model.byLane(notes)
			return Model.LANE_IDS.flatMap { grouped.getValue(it) }
		}
		return when (kind) {
			"note" -> {
				val note = store.getNote(id) ?: return null
				ExportPlan(Texts.noteTitle(note), null, listOf(Triple(null, listOf(note), null)), 1, Texts.placeOf(store, note))
			}
			"notebook" -> {
				val nb = store.getNotebook(id) ?: return null
				val notes = lanes(store.notesIn(id))
				ExportPlan(nb.name, Texts.notebookPlace(nb), listOf(Triple(null, notes, null)), notes.size, "")
			}
			else -> {
				val folder = store.getFolder(id) ?: return null
				val sections = ArrayList<Triple<String?, List<Note>, String?>>()
				sections.add(Triple(Texts.directInFolder(), lanes(store.notesInFolder(id)), null))
				for (nb in store.notebooksIn(id)) {
					sections.add(Triple(Texts.notebookPlace(nb), lanes(store.notesIn(nb.id)), nb.name))
				}
				ExportPlan(folder.name, Texts.folderPlace(folder), sections, sections.sumOf { it.second.size }, "")
			}
		}
	}

	private fun describe(plan: ExportPlan): (Note) -> List<String> = { note ->
		Texts.noteDetails(note, if (plan.place.isNotEmpty()) plan.place else Texts.placeOf(store, note))
	}

	/** Writes the whole plan in one document chosen by the person. */
	fun exportToFile(plan: ExportPlan, fmt: String, uri: Uri) {
		// The descriptions are made now (they read the store, which belongs to the main thread).
		val details = plan.sections.flatMap { it.second }.associate { it.id to describe(plan)(it) }
		viewModelScope.launch(Dispatchers.IO) {
			val ok = try {
				val intro = if (plan.title != null) Texts.exportIntro(plan.count) else null
				val blocks = Exporter.buildDocument(
					plan.title, intro, plan.sections.map { it.first to it.second },
					{ details[it.id] ?: emptyList() }, { Texts.noteTitle(it) },
				)
				Exporter.writeDocument(Saf.openOutput(context.contentResolver, uri), fmt, blocks, plan.title ?: plan.name, Texts.LANG)
				true
			} catch (e: Exception) {
				false
			}
			val file = Saf.displayName(context, uri)
			withContext(Dispatchers.Main) { say(if (ok) Texts.exported(plan.count, file) else Texts.exportFailed()) }
		}
	}

	/** Writes one file for each note in a new folder inside the folder chosen by the person. */
	fun exportSeparate(plan: ExportPlan, fmt: String, treeUri: Uri) {
		val details = plan.sections.flatMap { it.second }.associate { it.id to describe(plan)(it) }
		viewModelScope.launch(Dispatchers.IO) {
			val result = try {
				Exporter.writeSeparate(
					SafExportFolder(context, treeUri), plan.name, fmt, plan.sections.map { it.third to it.second },
					{ note ->
						Exporter.buildDocument(null, null, listOf(null to listOf(note)), { details[it.id] ?: emptyList() }, { Texts.noteTitle(it) })
					},
					{ Texts.noteTitle(it) }, Texts.LANG, Texts.untitled(),
				)
			} catch (e: Exception) {
				null
			}
			withContext(Dispatchers.Main) {
				say(if (result != null) Texts.exportedSeparate(result.second, result.first.displayName) else Texts.exportFailed())
			}
		}
	}
}
