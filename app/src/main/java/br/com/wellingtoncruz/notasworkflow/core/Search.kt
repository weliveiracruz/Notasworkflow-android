// NotasWorkflow Android - Search.kt
// Text search of the notes, ignoring accents and case. The text of each note is prepared once
// and kept in an index until the note changes, so a search in 100,000 notes takes a fraction
// of a second.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import java.text.Normalizer
import java.util.PriorityQueue

data class SearchResult(val note: Note, val score: Double, val excerpt: String)

object Search {
	const val MAX_RESULTS = 100
	const val EXCERPT_LENGTH = 120
	const val MAX_QUERY = 200

	// Combining marks (accents) left by NFKD.
	private val MARKS = Regex("[\\u0300-\\u036f\\u1ab0-\\u1aff\\u1dc0-\\u1dff\\u20d0-\\u20ff\\ufe20-\\ufe2f]")
	private val SKIP = Regex(
		"<(script|style|template|noscript|iframe|object|head|title)\\b[^>]*>.*?</\\1\\s*>",
		setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
	)
	private val TAG = Regex("<[^>]*>")
	private val SPACES = Regex("\\s+")

	/** Lower case without accents: "Ação" -> "acao". */
	fun fold(text: String): String = MARKS.replace(Normalizer.normalize(text.lowercase().replace("ß", "ss"), Normalizer.Form.NFKD), "")

	private fun foldChar(ch: String): String = fold(ch)

	/** (folded text, index in the original text of each folded character). */
	fun foldWithMap(text: String): Pair<String, IntArray> {
		val folded = fold(text)
		if (folded.length == text.length) return Pair(folded, IntArray(text.length) { it })
		val out = StringBuilder()
		val positions = ArrayList<Int>()
		var i = 0
		while (i < text.length) {
			val count = Character.charCount(text.codePointAt(i))
			val f = foldChar(text.substring(i, i + count))
			out.append(f)
			repeat(f.length) { positions.add(i) }
			i += count
		}
		return Pair(out.toString(), positions.toIntArray())
	}

	/** Words of the stored HTML for the search: tags, scripts and styles removed. */
	fun plainText(content: String?): String {
		if (content.isNullOrEmpty()) return ""
		var text = content
		if ('<' in text) text = TAG.replace(SKIP.replace(text, " "), " ")
		return SPACES.replace(HtmlTokenizer.unescape(text), " ").trim()
	}

	fun terms(query: String?): List<String> =
		fold((query ?: "").take(MAX_QUERY)).split(SPACES).filter { it.isNotEmpty() }

	private fun cut(plain: String, maxLen: Int) = plain.take(maxLen) + if (plain.length > maxLen) "…" else ""

	/** Part of the text around the first term found. */
	fun excerpt(text: String, query: String, maxLen: Int = EXCERPT_LENGTH): String {
		val plain = text.split(SPACES).filter { it.isNotEmpty() }.joinToString(" ")
		val found = terms(query)
		if (found.isEmpty()) return cut(plain, maxLen)
		val (folded, positions) = foldWithMap(plain)
		val idx = found.map { folded.indexOf(it) }.firstOrNull { it != -1 } ?: return cut(plain, maxLen)
		val at = positions[idx]
		val start = maxOf(0, at - 40)
		val end = minOf(plain.length, at + maxLen - 40)
		return (if (start > 0) "…" else "") + plain.substring(start, end) + (if (end < plain.length) "…" else "")
	}

	internal class Entry(val content: String, val title: String, val plain: String, val foldedTitle: String, val haystack: String)

	/** Prepared text of each note. An entry is rebuilt only when the title or the content change. */
	class Index {
		private val entries = HashMap<String, Entry>()
		private val dates = HashMap<String, Pair<String, Long>>()

		internal fun entry(note: Note): Entry {
			val cached = entries[note.id]
			// Identity checks: an edited or imported note always gets new strings, so this is O(1).
			if (cached != null && cached.content === note.content && cached.title === note.title) return cached
			val plain = plainText(note.content)
			val foldedTitle = fold(note.title)
			val entry = Entry(note.content, note.title, plain, foldedTitle, foldedTitle + " " + fold(plain))
			entries[note.id] = entry
			return entry
		}

		internal fun updated(note: Note): Long {
			val cached = dates[note.id]
			if (cached != null && cached.first === note.updatedAt) return cached.second
			val value = Model.parseIso(note.updatedAt)?.toEpochMilli() ?: 0L
			dates[note.id] = Pair(note.updatedAt, value)
			return value
		}

		/** Drops the entries of notes that no longer exist. */
		fun forget(keep: Set<String>) {
			entries.keys.retainAll(keep)
			dates.keys.retainAll(keep)
		}

		val size: Int get() = entries.size
	}

	private class Ranked(val score: Double, val titleHits: Int, val updated: Long, val note: Note, val plain: String)

	private val ORDER = compareBy<Ranked>({ it.score }, { it.titleHits }, { it.updated })

	/** Results best first. score = terms found / terms; ties: more terms in the title, then the
	 * note changed last. */
	fun search(notes: List<Note>, query: String, limit: Int = MAX_RESULTS, index: Index = Index()): List<SearchResult> {
		val wanted = terms(query)
		if (wanted.isEmpty()) return emptyList()
		// Only the best ones are kept (a common word can match tens of thousands of notes).
		val best = PriorityQueue(limit + 1, ORDER)
		for (note in notes) {
			val entry = index.entry(note)
			var hits = 0
			for (t in wanted) if (entry.haystack.contains(t)) hits++
			if (hits == 0) continue
			var titleHits = 0
			for (t in wanted) if (entry.foldedTitle.contains(t)) titleHits++
			best.add(Ranked(hits.toDouble() / wanted.size, titleHits, index.updated(note), note, entry.plain))
			if (best.size > limit) best.poll()
		}
		val sorted = ArrayList(best)
		sorted.sortWith(ORDER.reversed())
		return sorted.map { SearchResult(it.note, it.score, excerpt(it.plain, query)) }
	}
}
