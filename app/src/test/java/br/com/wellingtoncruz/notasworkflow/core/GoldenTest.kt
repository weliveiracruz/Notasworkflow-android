// NotasWorkflow Android - GoldenTest.kt
// Parity with the NotasWorkflowNVDA add-on 1.12.0: the expected results in src/test/resources/golden
// were produced by the add-on's own Python code (markup, search, exporter and importer) and the
// Kotlin core must give exactly the same.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class GoldenTest {
	private fun resource(name: String): ByteArray =
		GoldenTest::class.java.classLoader!!.getResourceAsStream("golden/$name")!!.use { it.readBytes() }

	private val vectors: Map<String, Any?> by lazy { Json.parse(String(resource("vectors.json"), Charsets.UTF_8)) as Map<String, Any?> }

	@Test
	fun textToHtml() = (vectors["t2h"] as Map<String, String>).forEach { (k, w) -> assertEquals(w, Markup.textToHtml(k)) }

	@Test
	fun htmlToText() = (vectors["h2t"] as Map<String, String>).forEach { (k, w) -> assertEquals(w, Markup.htmlToText(k)) }

	@Test
	fun plainTextAndPreview() {
		(vectors["plain"] as Map<String, String>).forEach { (k, w) -> assertEquals(w, Markup.plainText(k)) }
		(vectors["preview"] as Map<String, String>).forEach { (k, w) -> assertEquals(w, Markup.preview(k, 20)) }
	}

	@Test
	fun applyFormat() {
		for (f in vectors["fmt"] as List<Map<String, Any?>>) {
			val r = Markup.applyFormat(f["text"] as String, (f["start"] as Long).toInt(), (f["end"] as Long).toInt(), f["kind"] as String)
			val o = f["out"] as List<Any?>
			assertEquals(o, listOf(r.text, r.start.toLong(), r.end.toLong(), r.applied))
		}
	}

	@Test
	fun search() {
		val notes = NotesStore.parseData(mapOf("notes" to vectors["notes"])).notes
		(vectors["search"] as Map<String, List<List<Any?>>>).forEach { (q, w) ->
			val got = Search.search(notes, q).map { listOf(it.note.id, it.score, it.excerpt) }
			assertEquals(w.map { listOf(it[0], (it[1] as Number).toDouble(), it[2]) }, got)
		}
		(vectors["fold"] as Map<String, String>).forEach { (k, w) -> assertEquals(w, Search.fold(k)) }
	}

	@Test
	fun export() {
		val notes = NotesStore.parseData(mapOf("notes" to vectors["notes"])).notes
		val n2 = notes[2].copy(content = vectors["content2"] as String)
		val blocks = Exporter.buildDocument(
			"Caderno X", "Intro.", listOf(null to notes.take(2), "Seção" to listOf(n2)),
			{ listOf("Status: A fazer", "Local: x") }, { it.title.ifEmpty { "Sem título" } },
		)
		assertEquals(vectors["md"], Exporter.toMarkdown(blocks))
		// The only difference from the add-on: Roboto (the Android font) in the page style.
		assertEquals(vectors["html"], Exporter.toHtml(blocks, "Caderno X", "pt-BR").replace("Segoe UI,Roboto,", "Segoe UI,"))
		val t = (vectors["t2h"] as Map<String, String>).keys.toList()
		val cb = Exporter.contentBlocks(Markup.textToHtml(t[0] + "\n" + t[1] + "\n" + t[2]))
		assertEquals(vectors["md2"], Exporter.toMarkdown(cb))
		(vectors["safe"] as Map<String, String>).forEach { (k, w) -> assertEquals(w, Exporter.safeName(k, "Sem título")) }
		assertEquals(vectors["tidy"], Importer.tidy("  a   b  \n\n• item\n```\n  keep   spaces\n```\n\t\tfim  "))
	}

	@Test
	fun documents() {
		val want = Json.parse(String(resource("docs.json"), Charsets.UTF_8)) as Map<String, List<String>>
		for ((file, w) in want) {
			val d = Importer.readDocument(file, resource("docs/$file"), Texts.DOCUMENT_LABELS, {
				"%02d/%02d/%d %02d:%02d".format(it.dayOfMonth, it.monthValue, it.year, it.hour, it.minute)
			})
			assertEquals(w, listOf(d.title, d.text))
		}
	}
}
