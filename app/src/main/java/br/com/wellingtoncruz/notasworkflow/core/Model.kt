// NotasWorkflow Android - Model.kt
// Data model: notes ("folhas"), notebooks, folders and the four status lanes.
// The same data format as the NotasWorkflowNVDA add-on ("notas-workflow", version 1), so the
// files of one can be imported by the other. Pure Kotlin: no Android classes here.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.LocalDateTime
import java.util.UUID

data class Lane(val id: String, val color: String, val order: Int)

data class Folder(
	val id: String,
	val name: String,
	val color: String,
	val createdAt: String,
)

data class Notebook(
	val id: String,
	val name: String,
	val color: String,
	val folderId: String?,
	val createdAt: String,
)

data class Note(
	val id: String,
	val title: String,
	/** Stored HTML (what the add-on and the web app write). */
	val content: String,
	val tags: List<String>,
	val laneId: String,
	val notebookId: String?,
	val folderId: String?,
	val createdAt: String,
	val updatedAt: String,
)

/** Where a note is: a notebook, a folder directly, or neither (loose note). */
data class Place(val notebookId: String? = null, val folderId: String? = null) {
	val isLoose: Boolean get() = notebookId == null && folderId == null

	companion object {
		val LOOSE = Place()
	}
}

/** Filter of the main screen. kind: all, loose, recent, notebook or folder. */
data class NoteFilter(val kind: String, val id: String? = null) {
	companion object {
		val ALL = NoteFilter("all")
		val LOOSE = NoteFilter("loose")
		val RECENT = NoteFilter("recent")
		fun notebook(id: String) = NoteFilter("notebook", id)
		fun folder(id: String) = NoteFilter("folder", id)
	}
}

object Model {
	/** The four status lanes, in order. */
	val LANES = listOf(
		Lane("todo", "#007AFF", 0),
		Lane("inprogress", "#FF9500", 1),
		Lane("blocked", "#FF2D55", 2),
		Lane("done", "#34C759", 3),
	)
	val LANE_IDS: List<String> = LANES.map { it.id }
	const val DEFAULT_LANE = "todo"

	val NOTEBOOK_COLORS = listOf("#007AFF", "#34C759", "#FF9500", "#FF2D55", "#AF52DE", "#5856D6")
	val FOLDER_COLORS = listOf("#FF9500", "#5856D6", "#34C759", "#FF2D55", "#007AFF", "#AF52DE")

	const val MAX_TITLE = 200
	const val MAX_NAME = 100
	const val MAX_TAG = 50
	const val MAX_TAGS = 30
	const val MAX_CONTENT = 1024 * 1024
	const val MAX_ID = 64
	/** Number of notes of "Recent notes". */
	const val RECENT_COUNT = 10

	private val ID_RE = Regex("[A-Za-z0-9_-]{1,$MAX_ID}")
	private val COLOR_RE = Regex("#[0-9A-Fa-f]{6}")
	private val CONTROL_RE = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]")
	private val ISO_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

	fun newId(): String = UUID.randomUUID().toString()

	fun isValidId(value: Any?): Boolean = value is String && ID_RE.matches(value)

	fun isValidColor(value: Any?): Boolean = value is String && COLOR_RE.matches(value)

	/** The current time as JavaScript's Date.toISOString() writes it. */
	fun nowIso(now: Instant = Instant.now()): String =
		ISO_OUT.format(now.atOffset(ZoneOffset.UTC))

	/** The instant of an ISO date, or null. */
	fun parseIso(value: Any?): Instant? {
		if (value !is String || value.length > 40) return null
		val text = value.trim()
		return try {
			OffsetDateTime.parse(text).toInstant()
		} catch (e: DateTimeParseException) {
			try {
				LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
			} catch (e2: DateTimeParseException) {
				null
			}
		}
	}

	private fun isoOr(value: Any?, fallback: String): String =
		if (parseIso(value) != null) value as String else fallback

	/** A single-line text: no control characters, trimmed and limited. */
	fun cleanText(value: Any?, limit: Int): String {
		if (value !is String) return ""
		val text = CONTROL_RE.replace(value, "")
			.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
			.trim()
		return if (text.length > limit) text.substring(0, limit) else text
	}

	/** List of tags: text, no repetition (ignoring case), no empty one, limited. */
	fun cleanTags(tags: Any?): List<String> {
		val list: List<Any?> = when (tags) {
			is String -> tags.split(",")
			is List<*> -> tags
			else -> return emptyList()
		}
		val out = ArrayList<String>()
		val seen = HashSet<String>()
		for (raw in list) {
			val tag = cleanText(raw, MAX_TAG)
			if (tag.isNotEmpty() && seen.add(tag.lowercase())) out.add(tag)
			if (out.size >= MAX_TAGS) break
		}
		return out
	}

	fun laneIndex(laneId: String?): Int = LANE_IDS.indexOf(laneId).coerceAtLeast(0)

	/** Lane before (step -1) or after (step +1), or null at the ends. */
	fun neighbourLane(laneId: String, step: Int): String? {
		val i = laneIndex(laneId) + step
		return LANE_IDS.getOrNull(i)
	}

	fun makeNote(
		title: String,
		laneId: String = DEFAULT_LANE,
		notebookId: String? = null,
		folderId: String? = null,
		content: String = "",
		tags: List<String> = emptyList(),
		now: Instant = Instant.now(),
	): Note {
		val stamp = nowIso(now)
		return Note(
			id = newId(),
			title = cleanText(title, MAX_TITLE),
			content = content,
			tags = cleanTags(tags),
			laneId = if (laneId in LANE_IDS) laneId else DEFAULT_LANE,
			notebookId = notebookId,
			folderId = folderId,
			createdAt = stamp,
			updatedAt = stamp,
		)
	}

	fun sanitizeFolder(raw: Any?, now: String, defaultColor: String = FOLDER_COLORS[0]): Folder? {
		if (raw !is Map<*, *> || !isValidId(raw["id"])) return null
		val name = cleanText(raw["name"], MAX_NAME)
		if (name.isEmpty()) return null
		val color = raw["color"]
		return Folder(
			id = raw["id"] as String,
			name = name,
			color = if (isValidColor(color)) color as String else defaultColor,
			createdAt = isoOr(raw["createdAt"], now),
		)
	}

	fun sanitizeNotebook(raw: Any?, now: String, folderIds: Set<String>): Notebook? {
		val base = sanitizeFolder(raw, now, NOTEBOOK_COLORS[0]) ?: return null
		val folderId = (raw as Map<*, *>)["folderId"]
		return Notebook(
			id = base.id,
			name = base.name,
			color = base.color,
			folderId = if (folderId is String && folderId in folderIds) folderId else null,
			createdAt = base.createdAt,
		)
	}

	/** A valid note built from untrusted data (file or import), or null. */
	fun sanitizeNote(raw: Any?, now: String, notebookIds: Set<String>, folderIds: Set<String>): Note? {
		if (raw !is Map<*, *> || !isValidId(raw["id"])) return null
		var content = raw["content"]
		if (content !is String || content.length > MAX_CONTENT) content = ""
		val lane = raw["laneId"]
		val notebookId = raw["notebookId"]
		val folderId = raw["folderId"]
		val created = isoOr(raw["createdAt"], now)
		return Note(
			id = raw["id"] as String,
			title = cleanText(raw["title"], MAX_TITLE),
			content = content,
			tags = cleanTags(raw["tags"]),
			laneId = if (lane is String && lane in LANE_IDS) lane else DEFAULT_LANE,
			notebookId = if (notebookId is String && notebookId in notebookIds) notebookId else null,
			folderId = if (folderId is String && folderId in folderIds) folderId else null,
			createdAt = created,
			updatedAt = isoOr(raw["updatedAt"], created),
		)
	}

	fun updatedKey(note: Note): Long = parseIso(note.updatedAt)?.toEpochMilli() ?: 0L

	/** Notes shown by a filter, in the order of the list (recent: last changed first). */
	fun filterNotes(notes: List<Note>, notebooks: List<Notebook>, filter: NoteFilter): List<Note> =
		when (filter.kind) {
			"loose" -> notes.filter { it.notebookId == null && it.folderId == null }
			"recent" -> notes.sortedByDescending { updatedKey(it) }.take(RECENT_COUNT)
			"notebook" -> notes.filter { it.notebookId == filter.id }
			"folder" -> {
				val inFolder = notebooks.filter { it.folderId == filter.id }.map { it.id }.toHashSet()
				notes.filter { it.folderId == filter.id || it.notebookId in inFolder }
			}
			else -> notes.toList()
		}

	/** laneId -> notes, for the four lanes, keeping the order. */
	fun byLane(notes: List<Note>): Map<String, List<Note>> {
		val out = LinkedHashMap<String, MutableList<Note>>()
		for (lane in LANE_IDS) out[lane] = ArrayList()
		for (note in notes) (out[note.laneId] ?: out[DEFAULT_LANE]!!).add(note)
		return out
	}
}
