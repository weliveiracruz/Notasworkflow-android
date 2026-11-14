// NotasWorkflow Android - NotesStore.kt
// The operations on notes, notebooks and folders and their persistence in a JSON file written
// safely (temporary file and backup copy) by a background thread. Pure Kotlin: the Android
// storage (a file of the app or a document chosen by the person) comes in through DataFile.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** kind: write, corrupt, tooLarge, invalidImport, notFound, emptyName. */
class StoreException(val kind: String, detail: String = "") : Exception(kind) {
	val detail: String = detail.take(200)
}

/** Where the data file is: a file of the app, or a document (Google Drive, OneDrive...) on Android. */
interface DataFile {
	/** Bytes of the file, or null when it does not exist. Raises IOException. */
	fun read(): ByteArray?

	/** Writes the file, keeping the previous one as the backup copy. Raises IOException. */
	fun write(bytes: ByteArray)

	/** Bytes of the backup copy, or null. */
	fun readBackup(): ByteArray?

	/** Keeps a damaged file with another name. Returns that name, or null. */
	fun keepDamaged(): String?

	/** Something that changes when another program changes the file (size and date), or null. */
	fun stamp(): Any?
}

/** The data file in a folder of the device: atomic write, ".bak" backup copy. */
class LocalDataFile(val folder: File, val name: String = NotesStore.FILE_NAME) : DataFile {
	val file: File get() = File(folder, name)
	val backup: File get() = File(folder, "$name.bak")

	override fun read(): ByteArray? {
		val f = file
		if (!f.exists()) return null
		if (f.length() > NotesStore.MAX_FILE_SIZE) throw StoreException("tooLarge")
		return f.readBytes()
	}

	override fun write(bytes: ByteArray) {
		folder.mkdirs()
		val tmp = File(folder, "$name.tmp")
		try {
			tmp.outputStream().use { out ->
				out.write(bytes)
				out.flush()
				out.fd.sync()
			}
			val f = file
			if (f.exists()) {
				backup.delete()
				f.renameTo(backup)
			}
			if (!tmp.renameTo(f)) throw IOException("rename failed")
		} catch (e: IOException) {
			tmp.delete()
			throw e
		}
	}

	override fun readBackup(): ByteArray? {
		val b = backup
		if (!b.exists() || b.length() > NotesStore.MAX_FILE_SIZE) return null
		return b.readBytes()
	}

	override fun keepDamaged(): String? {
		val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
		val target = File(folder, "NotasWorkflow-data.corrupt-$stamp.json")
		return if (file.renameTo(target)) target.name else null
	}

	override fun stamp(): Any? {
		val f = file
		return if (f.exists()) Pair(f.length(), f.lastModified()) else null
	}
}

/** Result of loading: what went wrong, for the interface. */
data class LoadWarning(val kind: String, val savedAs: String, val restored: Boolean)

data class ImportCounts(val notes: Int, val notebooks: Int, val folders: Int) {
	val any: Boolean get() = notes + notebooks + folders > 0
}

data class Parsed(val folders: List<Folder>, val notebooks: List<Notebook>, val notes: List<Note>)

class NotesStore(dataFile: DataFile) {
	companion object {
		const val FILE_NAME = "NotasWorkflowAndroid-data.json"
		const val FORMAT = "notas-workflow"
		const val VERSION = 1
		/** Data and import files above this size are refused without being read. */
		const val MAX_FILE_SIZE = 200L * 1024 * 1024
		/** The background write waits this long after the last change (several changes, one write). */
		const val WRITE_DELAY_MS = 500L
		/** Keys of older exports (browser storage of the web app), still accepted on import. */
		const val WEB_PREFIX = "notas-workflow:"

		/** (folders, notebooks, notes) validated from data of the add-on, of the web app or of this app.
		 * Accepts {"folders", "notebooks", "notes"}, the keys of older exports or just a list of notes. */
		fun parseData(raw: Any?, now: String = Model.nowIso()): Parsed {
			var data: Any? = raw
			if (data is List<*>) data = mapOf("notes" to data)
			if (data !is Map<*, *>) throw IllegalArgumentException("not an object")
			val map = LinkedHashMap<String, Any?>()
			for ((k, v) in data) map[k.toString()] = v
			for (key in listOf("notes", "notebooks", "folders")) {
				if (key !in map && (WEB_PREFIX + key) in map) {
					var value = map[WEB_PREFIX + key]
					if (value is String) value = Json.parse(value)
					map[key] = value
				}
			}
			val notesValue = map["notes"]
			if (notesValue is Map<*, *>) map["notes"] = notesValue.values.toList()
			if (listOf("notes", "notebooks", "folders").none { map[it] is List<*> }) {
				throw IllegalArgumentException("no notes")
			}
			val folders = unique(asList(map["folders"]).map { Model.sanitizeFolder(it, now) }) { it.id }
			val folderIds = folders.map { it.id }.toHashSet()
			val notebooks = unique(asList(map["notebooks"]).map { Model.sanitizeNotebook(it, now, folderIds) }) { it.id }
			val notebookIds = notebooks.map { it.id }.toHashSet()
			val notes = unique(asList(map["notes"]).map { Model.sanitizeNote(it, now, notebookIds, folderIds) }) { it.id }
			return Parsed(folders, notebooks, notes)
		}

		fun parseBytes(bytes: ByteArray): Parsed {
			if (bytes.size > MAX_FILE_SIZE) throw StoreException("tooLarge")
			val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
			return parseData(Json.parse(text))
		}

		private fun asList(value: Any?): List<Any?> = if (value is List<*>) value else emptyList()

		private fun <T> unique(items: List<T?>, id: (T) -> String): List<T> {
			val out = ArrayList<T>()
			val seen = HashSet<String>()
			for (item in items) if (item != null && seen.add(id(item))) out.add(item)
			return out
		}

		fun encode(folders: List<Folder>, notebooks: List<Notebook>, notes: List<Note>): ByteArray {
			val sb = StringBuilder(64 + notes.size * 300)
			sb.append("{\"format\":")
			Json.quote(FORMAT, sb)
			sb.append(",\"version\":").append(VERSION).append(",\"folders\":")
			Json.write(folders.map { folderMap(it) }, sb)
			sb.append(",\"notebooks\":")
			Json.write(notebooks.map { notebookMap(it) }, sb)
			sb.append(",\"notes\":[")
			notes.forEachIndexed { i, n ->
				if (i > 0) sb.append(',')
				Json.write(noteMap(n), sb)
			}
			sb.append("]}")
			return sb.toString().toByteArray(Charsets.UTF_8)
		}

		fun folderMap(f: Folder) = linkedMapOf<String, Any?>(
			"id" to f.id, "name" to f.name, "color" to f.color, "createdAt" to f.createdAt,
		)

		fun notebookMap(nb: Notebook) = linkedMapOf<String, Any?>(
			"id" to nb.id, "name" to nb.name, "color" to nb.color, "folderId" to nb.folderId, "createdAt" to nb.createdAt,
		)

		fun noteMap(n: Note) = linkedMapOf<String, Any?>(
			"id" to n.id, "title" to n.title, "content" to n.content, "tags" to n.tags, "laneId" to n.laneId,
			"notebookId" to n.notebookId, "folderId" to n.folderId, "createdAt" to n.createdAt, "updatedAt" to n.updatedAt,
		)
	}

	@Volatile
	var dataFile: DataFile = dataFile
		private set

	var folders: MutableList<Folder> = ArrayList()
		private set
	var notebooks: MutableList<Notebook> = ArrayList()
		private set
	var notes: MutableList<Note> = ArrayList()
		private set
	private val byId = HashMap<String, Int>()
	private var indexValid = false

	/** Problem found by the last load, for the interface. */
	var warning: LoadWarning? = null
		private set

	/** Called (on the thread that changed the store) after every change. */
	var onChange: (() -> Unit)? = null

	private var lastStamp: Any? = null
	private var writer: Writer? = null

	// -- file --------------------------------------------------------------------------------

	/** Reads the file. A damaged file is kept with another name and the backup is used. */
	fun load() {
		flush()
		warning = null
		setAll(Parsed(emptyList(), emptyList(), emptyList()))
		val bytes = try {
			dataFile.read()
		} catch (e: Exception) {
			loadDamaged()
			return
		}
		if (bytes == null || bytes.all { it == ' '.code.toByte() || it == '\n'.code.toByte() || it == '\r'.code.toByte() }) {
			// No file yet, or an empty document just created: no notes.
			lastStamp = dataFile.stamp()
			return
		}
		try {
			setAll(parseBytes(bytes))
		} catch (e: Exception) {
			loadDamaged()
			return
		}
		lastStamp = dataFile.stamp()
	}

	private fun loadDamaged() {
		val saved = try {
			dataFile.keepDamaged()
		} catch (e: Exception) {
			null
		}
		var restored = false
		try {
			val backup = dataFile.readBackup()
			if (backup != null) {
				setAll(parseBytes(backup))
				restored = true
			}
		} catch (e: Exception) {
			setAll(Parsed(emptyList(), emptyList(), emptyList()))
		}
		warning = LoadWarning("corrupt", saved ?: "", restored)
		if (restored) {
			try {
				writeNow()
			} catch (e: StoreException) {
				// The warning already tells the person.
			}
		}
	}

	private fun setAll(parsed: Parsed) {
		folders = parsed.folders.toMutableList()
		notebooks = parsed.notebooks.toMutableList()
		notes = parsed.notes.toMutableList()
		indexValid = false
	}

	fun toBytes(): ByteArray = encode(folders, notebooks, notes)

	/** From now on save() returns at once and a thread writes the file. flush() waits for it. */
	fun enableBackgroundSaving(onError: ((StoreException) -> Unit)? = null) {
		if (writer == null) writer = Writer(onError)
	}

	/** Waits for the background write, if any. Returns the error of a failed write, or null. */
	fun flush(): StoreException? = writer?.flush()

	fun save() {
		val w = writer
		if (w != null) {
			// Copies of the lists: the notes are immutable, so the thread sees a fixed state.
			w.request(Parsed(folders.toList(), notebooks.toList(), notes.toList()))
		} else {
			writeNow()
		}
	}

	private fun writeNow() = write(Parsed(folders.toList(), notebooks.toList(), notes.toList()))

	private fun write(data: Parsed) {
		try {
			dataFile.write(encode(data.folders, data.notebooks, data.notes))
		} catch (e: Exception) {
			throw StoreException("write", e.javaClass.simpleName)
		}
		lastStamp = dataFile.stamp()
	}

	fun changedOnDisk(): Boolean {
		if (writer?.busy() == true) return false
		return try {
			dataFile.stamp() != lastStamp
		} catch (e: Exception) {
			false
		}
	}

	/** Reads the file again when another program (a synced folder) changed it. */
	fun reloadIfChanged(): Boolean {
		if (changedOnDisk()) {
			load()
			changed(save = false)
			return true
		}
		return false
	}

	/** Uses another data file: its notes when it has them ("loaded"), otherwise the current
	 * notes are written there ("copied"). */
	fun relocate(target: DataFile): String {
		flush()
		val old = dataFile
		dataFile = target
		val existing = try {
			target.read()
		} catch (e: Exception) {
			dataFile = old
			throw StoreException("write", e.javaClass.simpleName)
		}
		if (existing != null && existing.any { it != ' '.code.toByte() && it != '\n'.code.toByte() && it != '\r'.code.toByte() }) {
			load()
			changed(save = false)
			return "loaded"
		}
		try {
			writeNow()
		} catch (e: StoreException) {
			dataFile = old
			throw e
		}
		return "copied"
	}

	private fun changed(save: Boolean = true) {
		if (save) save()
		onChange?.invoke()
	}

	// -- import and export -----------------------------------------------------------------

	/** Joins the notes of an exported file (of this app, of the add-on or of the web app).
	 * Same id: the one changed last wins. */
	fun importBytes(bytes: ByteArray): ImportCounts {
		val parsed = try {
			parseBytes(bytes)
		} catch (e: StoreException) {
			throw e
		} catch (e: Exception) {
			throw StoreException("invalidImport", e.javaClass.simpleName)
		}
		var nFolders = 0
		var nNotebooks = 0
		var nNotes = 0
		val knownFolders = folders.map { it.id }.toHashSet()
		for (f in parsed.folders) if (knownFolders.add(f.id)) {
			folders.add(f)
			nFolders++
		}
		val knownNotebooks = notebooks.map { it.id }.toHashSet()
		for (nb in parsed.notebooks) if (knownNotebooks.add(nb.id)) {
			notebooks.add(nb)
			nNotebooks++
		}
		val index = HashMap<String, Int>()
		notes.forEachIndexed { i, n -> index[n.id] = i }
		for (note in parsed.notes) {
			val i = index[note.id]
			if (i == null) {
				index[note.id] = notes.size
				notes.add(note)
				nNotes++
			} else if (Model.updatedKey(note) > Model.updatedKey(notes[i])) {
				notes[i] = note
				nNotes++
			}
		}
		indexValid = false
		val counts = ImportCounts(nNotes, nNotebooks, nFolders)
		if (counts.any) changed()
		return counts
	}

	fun exportBytes(): ByteArray = toBytes()

	// -- reading ---------------------------------------------------------------------------

	private fun indexOf(noteId: String): Int {
		if (!indexValid) {
			byId.clear()
			notes.forEachIndexed { i, n -> byId[n.id] = i }
			indexValid = true
		}
		val i = byId[noteId] ?: return -1
		if (i < notes.size && notes[i].id == noteId) return i
		indexValid = false
		return indexOf(noteId)
	}

	fun getNote(noteId: String?): Note? {
		if (noteId == null) return null
		val i = indexOf(noteId)
		return if (i >= 0) notes[i] else null
	}

	fun getNotebook(notebookId: String?): Notebook? =
		if (notebookId == null) null else notebooks.firstOrNull { it.id == notebookId }

	fun getFolder(folderId: String?): Folder? =
		if (folderId == null) null else folders.firstOrNull { it.id == folderId }

	fun filtered(filter: NoteFilter): List<Note> = Model.filterNotes(notes, notebooks, filter)

	fun notebooksIn(folderId: String?): List<Notebook> = notebooks.filter { it.folderId == folderId }

	fun notesIn(notebookId: String): List<Note> = notes.filter { it.notebookId == notebookId }

	/** Notes put directly in a folder (not in one of its notebooks). */
	fun notesInFolder(folderId: String): List<Note> = notes.filter { it.folderId == folderId && it.notebookId == null }

	fun looseNotes(): List<Note> = notes.filter { it.notebookId == null && it.folderId == null }

	data class PlaceCounts(val byNotebook: Map<String, Int>, val byFolder: Map<String, Int>, val loose: Int)

	/** Numbers of notes by place in one pass. */
	fun placeCounts(): PlaceCounts {
		val byNotebook = HashMap<String, Int>()
		val byFolder = HashMap<String, Int>()
		var loose = 0
		for (n in notes) {
			when {
				n.notebookId != null -> byNotebook[n.notebookId] = (byNotebook[n.notebookId] ?: 0) + 1
				n.folderId != null -> byFolder[n.folderId] = (byFolder[n.folderId] ?: 0) + 1
				else -> loose++
			}
		}
		return PlaceCounts(byNotebook, byFolder, loose)
	}

	private fun <T> need(item: T?, kind: String = "note"): T = item ?: throw StoreException("notFound", kind)

	// -- notes -----------------------------------------------------------------------------

	/** Valid place of a note: a notebook, a folder directly, or neither (loose note). */
	fun location(place: Place): Place {
		if (place.notebookId != null && getNotebook(place.notebookId) != null) return Place(place.notebookId, null)
		if (place.folderId != null && getFolder(place.folderId) != null) return Place(null, place.folderId)
		return Place.LOOSE
	}

	fun placeOf(note: Note): Place = Place(note.notebookId, note.folderId)

	fun addNote(title: String, laneId: String = Model.DEFAULT_LANE, place: Place = Place.LOOSE, content: String = ""): Note {
		val where = location(place)
		val note = Model.makeNote(title, laneId, where.notebookId, where.folderId, content.take(Model.MAX_CONTENT))
		notes.add(note)
		byId[note.id] = notes.size - 1
		changed()
		return note
	}

	/** Changes the given fields; updatedAt only changes when something really changed.
	 * Returns true when the note changed. */
	fun updateNote(
		noteId: String,
		title: String? = null,
		content: String? = null,
		tags: List<String>? = null,
		laneId: String? = null,
		place: Place? = null,
	): Boolean {
		val i = indexOf(noteId)
		val note = need(if (i >= 0) notes[i] else null)
		var updated = note
		if (title != null) updated = updated.copy(title = Model.cleanText(title, Model.MAX_TITLE))
		if (content != null) updated = updated.copy(content = content.take(Model.MAX_CONTENT))
		if (tags != null) updated = updated.copy(tags = Model.cleanTags(tags))
		if (laneId != null && laneId in Model.LANE_IDS) updated = updated.copy(laneId = laneId)
		if (place != null) {
			val where = location(place)
			updated = updated.copy(notebookId = where.notebookId, folderId = where.folderId)
		}
		if (updated == note) return false
		notes[i] = updated.copy(updatedAt = Model.nowIso())
		changed()
		return true
	}

	fun deleteNote(noteId: String) {
		val i = indexOf(noteId)
		need(if (i >= 0) notes[i] else null)
		notes.removeAt(i)
		indexValid = false
		changed()
	}

	fun moveNote(noteId: String, laneId: String): Boolean = updateNote(noteId, laneId = laneId)

	fun moveNoteTo(noteId: String, place: Place): Boolean = updateNote(noteId, place = place)

	/** Puts the note just before (after=false) or just after (after=true) another one: the order of
	 * a lane is the order of the list, as in the add-on. Nothing else of the note changes, not even
	 * updatedAt. True when the order changed. */
	fun placeNoteBeside(noteId: String, otherId: String, after: Boolean): Boolean {
		val i = indexOf(noteId)
		val note = need(if (i >= 0) notes[i] else null)
		var j = indexOf(otherId)
		need(if (j >= 0) notes[j] else null)
		if (i == j) return false
		if (i == (if (after) j + 1 else j - 1)) return false
		if ((j > i) == after && kotlin.math.abs(j - i) == 1) {
			notes[i] = notes[j]
			notes[j] = note
		} else {
			notes.removeAt(i)
			if (j > i) j -= 1
			notes.add(if (after) j + 1 else j, note)
		}
		indexValid = false
		changed()
		return true
	}

	// -- folders -----------------------------------------------------------------------------

	fun addFolder(name: String): Folder {
		val clean = Model.cleanText(name, Model.MAX_NAME)
		if (clean.isEmpty()) throw StoreException("emptyName")
		val folder = Folder(Model.newId(), clean, Model.FOLDER_COLORS[folders.size % Model.FOLDER_COLORS.size], Model.nowIso())
		folders.add(folder)
		changed()
		return folder
	}

	fun renameFolder(folderId: String, name: String) {
		val i = folders.indexOfFirst { it.id == folderId }
		need(folders.getOrNull(i), "folder")
		val clean = Model.cleanText(name, Model.MAX_NAME)
		if (clean.isEmpty()) throw StoreException("emptyName")
		folders[i] = folders[i].copy(name = clean)
		changed()
	}

	/** (notebooks, notes) that deleting the folder deletes. */
	fun folderContents(folderId: String): Pair<Int, Int> {
		val notebookIds = notebooks.filter { it.folderId == folderId }.map { it.id }.toHashSet()
		val count = notes.count { it.folderId == folderId || it.notebookId in notebookIds }
		return Pair(notebookIds.size, count)
	}

	/** Deletes the folder and everything in it: its notebooks, the notes put directly in it and
	 * the notes of its notebooks. Returns (notebooks, notes) deleted. */
	fun deleteFolder(folderId: String): Pair<Int, Int> {
		val folder = need(getFolder(folderId), "folder")
		val notebookIds = notebooks.filter { it.folderId == folderId }.map { it.id }.toHashSet()
		folders.remove(folder)
		notebooks = notebooks.filter { it.id !in notebookIds }.toMutableList()
		val before = notes.size
		notes = notes.filter { it.folderId != folderId && it.notebookId !in notebookIds }.toMutableList()
		indexValid = false
		changed()
		return Pair(notebookIds.size, before - notes.size)
	}

	// -- notebooks ---------------------------------------------------------------------------

	fun addNotebook(name: String, folderId: String? = null): Notebook {
		val clean = Model.cleanText(name, Model.MAX_NAME)
		if (clean.isEmpty()) throw StoreException("emptyName")
		val folder = if (folderId != null && getFolder(folderId) != null) folderId else null
		val notebook = Notebook(
			Model.newId(), clean, Model.NOTEBOOK_COLORS[notebooks.size % Model.NOTEBOOK_COLORS.size], folder, Model.nowIso(),
		)
		notebooks.add(notebook)
		changed()
		return notebook
	}

	fun renameNotebook(notebookId: String, name: String) {
		val i = notebooks.indexOfFirst { it.id == notebookId }
		need(notebooks.getOrNull(i), "notebook")
		val clean = Model.cleanText(name, Model.MAX_NAME)
		if (clean.isEmpty()) throw StoreException("emptyName")
		notebooks[i] = notebooks[i].copy(name = clean)
		changed()
	}

	fun moveNotebookToFolder(notebookId: String, folderId: String?) {
		val i = notebooks.indexOfFirst { it.id == notebookId }
		need(notebooks.getOrNull(i), "notebook")
		if (folderId != null) need(getFolder(folderId), "folder")
		notebooks[i] = notebooks[i].copy(folderId = folderId)
		changed()
	}

	fun notebookContents(notebookId: String): Int = notes.count { it.notebookId == notebookId }

	/** Deletes the notebook and its notes. Returns the number of notes deleted. */
	fun deleteNotebook(notebookId: String): Int {
		val notebook = need(getNotebook(notebookId), "notebook")
		notebooks.remove(notebook)
		val before = notes.size
		notes = notes.filter { it.notebookId != notebookId }.toMutableList()
		indexValid = false
		changed()
		return before - notes.size
	}

	// -- background writing --------------------------------------------------------------------

	/** Writes the file in a thread. Changes made while a write is running become a single next
	 * write (only the latest state is written). */
	private inner class Writer(val onError: ((StoreException) -> Unit)?) {
		private val lock = Object()
		private var pending: Parsed? = null
		private var idle = true
		private var lastError: StoreException? = null
		private var lastRequest = 0L
		private val thread = Thread({ loop() }, "NotasWorkflow-save").apply {
			isDaemon = true
			start()
		}

		fun request(data: Parsed) {
			synchronized(lock) {
				pending = data
				idle = false
				lastRequest = System.nanoTime()
				lock.notifyAll()
			}
		}

		fun busy(): Boolean = synchronized(lock) { !idle }

		fun flush(timeoutMs: Long = 120_000): StoreException? {
			val end = System.currentTimeMillis() + timeoutMs
			synchronized(lock) {
				// Wakes the thread so it writes at once instead of waiting for the pause.
				lastRequest = 0L
				lock.notifyAll()
				while (!idle) {
					val left = end - System.currentTimeMillis()
					if (left <= 0) break
					lock.wait(left)
				}
				val error = lastError
				lastError = null
				return error
			}
		}

		private fun loop() {
			while (true) {
				val data: Parsed
				synchronized(lock) {
					while (pending == null) lock.wait()
					// Waits for the changes to stop for a moment: a series of changes is written once.
					while (true) {
						if (lastRequest == 0L) break
						val waited = (System.nanoTime() - lastRequest) / 1_000_000
						if (waited >= WRITE_DELAY_MS) break
						lock.wait(WRITE_DELAY_MS - waited)
					}
					data = pending!!
					pending = null
				}
				var error: StoreException? = null
				try {
					write(data)
				} catch (e: StoreException) {
					error = e
				}
				synchronized(lock) {
					lastError = error
					if (pending == null) idle = true
					lock.notifyAll()
				}
				if (error != null) onError?.invoke(error)
			}
		}
	}
}
