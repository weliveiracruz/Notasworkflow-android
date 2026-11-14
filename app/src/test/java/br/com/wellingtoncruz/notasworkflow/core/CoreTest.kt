// NotasWorkflow Android - CoreTest.kt
// Tests of the core: data, persistence, markup, search, import and export.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoreTest {
	private fun tempDir(): File = Files.createTempDirectory("nw").toFile()

	private fun store(dir: File = tempDir()): NotesStore = NotesStore(LocalDataFile(dir)).also { it.load() }

	// -- model ----------------------------------------------------------------------------------

	@Test
	fun cleanTextRemovesControlsAndLimits() {
		assertEquals("a b c", Model.cleanText("  a\nb\tc\u0001 ", 100))
		assertEquals("abc", Model.cleanText("abcdef", 3))
		assertEquals("", Model.cleanText(42, 10))
	}

	@Test
	fun cleanTagsUniqueIgnoringCase() {
		assertEquals(listOf("A", "b"), Model.cleanTags("A, a, b, ,"))
		assertEquals(30, Model.cleanTags((1..50).map { "t$it" }).size)
	}

	@Test
	fun lanesAndNeighbours() {
		assertEquals(listOf("todo", "inprogress", "blocked", "done"), Model.LANE_IDS)
		assertEquals("inprogress", Model.neighbourLane("todo", 1))
		assertNull(Model.neighbourLane("todo", -1))
		assertNull(Model.neighbourLane("done", 1))
	}

	@Test
	fun isoDates() {
		val iso = Model.nowIso()
		assertTrue(Regex("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z").matches(iso))
		assertNotNull(Model.parseIso("2026-01-02T03:04:05.678Z"))
		assertNotNull(Model.parseIso("2026-01-02T03:04:05+00:00"))
		assertNull(Model.parseIso("ontem"))
	}

	@Test
	fun sanitizeRefusesBadIdsAndFixesReferences() {
		val now = Model.nowIso()
		assertNull(Model.sanitizeNote(mapOf("id" to "../x"), now, emptySet(), emptySet()))
		val n = Model.sanitizeNote(
			mapOf("id" to "a1", "title" to "T", "laneId" to "nope", "notebookId" to "gone", "content" to 5),
			now, emptySet(), emptySet(),
		)!!
		assertEquals("todo", n.laneId)
		assertNull(n.notebookId)
		assertEquals("", n.content)
	}

	// -- JSON -----------------------------------------------------------------------------------

	@Test
	fun jsonRoundTrip() {
		val value = mapOf("a" to listOf(1L, 2.5, true, null, "x\"\n\u0001é"), "b" to mapOf<String, Any?>())
		assertEquals(value, Json.parse(Json.stringify(value)))
	}

	@Test
	fun jsonRefusesDeepNesting() {
		try {
			Json.parse("[".repeat(5000) + "]".repeat(5000))
			fail()
		} catch (e: JsonException) {
			// Expected.
		}
	}

	// -- store ----------------------------------------------------------------------------------

	@Test
	fun notesPersistAndReload() {
		val dir = tempDir()
		val s = store(dir)
		val folder = s.addFolder("Projetos")
		val nb = s.addNotebook("Casa", folder.id)
		val note = s.addNote("Comprar tinta", "inprogress", Place(notebookId = nb.id), "<p>azul</p>")
		val s2 = store(dir)
		assertEquals(1, s2.notes.size)
		assertEquals(note, s2.getNote(note.id))
		assertEquals(nb, s2.getNotebook(nb.id))
		assertEquals(folder, s2.getFolder(folder.id))
		assertTrue(File(dir, NotesStore.FILE_NAME).readText().startsWith("{\"format\":\"notas-workflow\",\"version\":1,"))
	}

	@Test
	fun updateOnlyChangesUpdatedAtWhenSomethingChanged() {
		val s = store()
		val n = s.addNote("A")
		assertFalse(s.updateNote(n.id, title = "A"))
		assertTrue(s.updateNote(n.id, title = "B", laneId = "done"))
		assertEquals("B", s.getNote(n.id)!!.title)
		assertEquals("done", s.getNote(n.id)!!.laneId)
		assertFalse(s.updateNote(n.id, laneId = "invalid"))
	}

	@Test
	fun locationPrefersNotebookAndValidates() {
		val s = store()
		val f = s.addFolder("F")
		val nb = s.addNotebook("N", f.id)
		assertEquals(Place(nb.id, null), s.location(Place(nb.id, f.id)))
		assertEquals(Place(null, f.id), s.location(Place(null, f.id)))
		assertEquals(Place.LOOSE, s.location(Place("gone", "gone")))
	}

	@Test
	fun placeNoteBesideMovesInsideTheList() {
		val s = store()
		val a = s.addNote("a")
		val b = s.addNote("b")
		val c = s.addNote("c")
		assertTrue(s.placeNoteBeside(c.id, a.id, after = false))
		assertEquals(listOf("c", "a", "b"), s.notes.map { it.title })
		assertTrue(s.placeNoteBeside(c.id, a.id, after = true))
		assertEquals(listOf("a", "c", "b"), s.notes.map { it.title })
		assertFalse(s.placeNoteBeside(c.id, a.id, after = true))
		assertTrue(s.placeNoteBeside(a.id, b.id, after = true))
		assertEquals(listOf("c", "b", "a"), s.notes.map { it.title })
	}

	@Test
	fun deletingAFolderDeletesEverythingInIt() {
		val s = store()
		val f = s.addFolder("F")
		val nb = s.addNotebook("N", f.id)
		s.addNote("in notebook", place = Place(nb.id))
		s.addNote("in folder", place = Place(folderId = f.id))
		s.addNote("loose")
		assertEquals(Pair(1, 2), s.folderContents(f.id))
		assertEquals(Pair(1, 2), s.deleteFolder(f.id))
		assertEquals(listOf("loose"), s.notes.map { it.title })
		assertTrue(s.notebooks.isEmpty())
	}

	@Test
	fun deletingANotebookDeletesItsNotes() {
		val s = store()
		val nb = s.addNotebook("N")
		s.addNote("x", place = Place(nb.id))
		s.addNote("y")
		assertEquals(1, s.notebookContents(nb.id))
		assertEquals(1, s.deleteNotebook(nb.id))
		assertEquals(listOf("y"), s.notes.map { it.title })
	}

	@Test
	fun emptyNamesAreRefused() {
		val s = store()
		try {
			s.addFolder("   ")
			fail()
		} catch (e: StoreException) {
			assertEquals("emptyName", e.kind)
		}
	}

	@Test
	fun filters() {
		val s = store()
		val f = s.addFolder("F")
		val nb = s.addNotebook("N", f.id)
		s.addNote("nb", place = Place(nb.id))
		s.addNote("f", place = Place(folderId = f.id))
		s.addNote("loose")
		assertEquals(3, s.filtered(NoteFilter.ALL).size)
		assertEquals(listOf("loose"), s.filtered(NoteFilter.LOOSE).map { it.title })
		assertEquals(listOf("nb"), s.filtered(NoteFilter.notebook(nb.id)).map { it.title })
		assertEquals(listOf("nb", "f"), s.filtered(NoteFilter.folder(f.id)).map { it.title })
		assertEquals(3, s.filtered(NoteFilter.RECENT).size)
	}

	@Test
	fun damagedFileUsesTheBackup() {
		val dir = tempDir()
		val s = store(dir)
		s.addNote("first")
		s.addNote("second")
		File(dir, NotesStore.FILE_NAME).writeText("{broken")
		val s2 = store(dir)
		assertNotNull(s2.warning)
		assertTrue(s2.warning!!.restored)
		assertEquals(listOf("first"), s2.notes.map { it.title })
		assertTrue(dir.list()!!.any { it.startsWith("NotasWorkflow-data.corrupt-") })
	}

	@Test
	fun importMergesAndTheLastChangedWins() {
		val s = store()
		val n = s.addNote("local")
		val other = NotesStore.parseData(
			mapOf(
				"notes" to listOf(
					mapOf("id" to n.id, "title" to "newer", "updatedAt" to "2999-01-01T00:00:00.000Z"),
					mapOf("id" to "new1", "title" to "new"),
				),
				"folders" to listOf(mapOf("id" to "f1", "name" to "Pasta")),
			),
		)
		val counts = s.importBytes(NotesStore.encode(other.folders, other.notebooks, other.notes))
		assertEquals(ImportCounts(2, 0, 1), counts)
		assertEquals("newer", s.getNote(n.id)!!.title)
		assertEquals(2, s.notes.size)
	}

	@Test
	fun importAcceptsTheWebAppKeys() {
		val s = store()
		val web = "{\"notas-workflow:notes\":\"[{\\\"id\\\":\\\"w1\\\",\\\"title\\\":\\\"web\\\"}]\"}"
		assertEquals(1, s.importBytes(web.toByteArray()).notes)
		try {
			s.importBytes("{\"x\":1}".toByteArray())
			fail()
		} catch (e: StoreException) {
			assertEquals("invalidImport", e.kind)
		}
	}

	@Test
	fun backgroundSavingWritesTheLatestState() {
		val dir = tempDir()
		val s = store(dir)
		s.enableBackgroundSaving()
		repeat(50) { s.addNote("n$it") }
		assertNull(s.flush())
		assertEquals(50, store(dir).notes.size)
	}

	@Test
	fun relocateCopiesOrLoads() {
		val s = store()
		s.addNote("mine")
		val empty = tempDir()
		assertEquals("copied", s.relocate(LocalDataFile(empty)))
		val full = tempDir()
		store(full).addNote("theirs")
		assertEquals("loaded", s.relocate(LocalDataFile(full)))
		assertEquals(listOf("theirs"), s.notes.map { it.title })
	}

	@Test
	fun filesOfTheNvdaAddOnAreRead() {
		val addOn = """{"format":"notas-workflow","version":1,"folders":[{"id":"f","name":"Trabalho","color":"#FF9500","createdAt":"2026-01-01T00:00:00.000Z"}],
			"notebooks":[{"id":"b","name":"Reuniões","color":"#007AFF","folderId":"f","createdAt":"2026-01-01T00:00:00.000Z"}],
			"notes":[{"id":"n","title":"Ata","content":"<p>texto</p>","tags":[],"laneId":"blocked","notebookId":"b","folderId":null,"createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-02T00:00:00.000Z"}]}"""
		val p = NotesStore.parseBytes(addOn.toByteArray())
		assertEquals("blocked", p.notes[0].laneId)
		assertEquals("b", p.notes[0].notebookId)
		assertEquals("f", p.notebooks[0].folderId)
	}

	// -- markup ---------------------------------------------------------------------------------

	@Test
	fun markupRoundTrip() {
		val text = "## Título\nTexto **negrito** *itálico* ~~x~~ `c`\n- a\n- b\n3. c\n> q\n---\n```\nx < y\n```"
		assertEquals(text, Markup.htmlToText(Markup.textToHtml(text)))
		assertEquals("<p>2*3*4</p>", Markup.textToHtml("2*3*4"))
	}

	@Test
	fun applyFormatTogglesBold() {
		val on = Markup.applyFormat("abc", 0, 3, "bold")
		assertEquals(Markup.Formatted("**abc**", 2, 5, true), on)
		val off = Markup.applyFormat(on.text, on.start, on.end, "bold")
		assertEquals(Markup.Formatted("abc", 0, 3, false), off)
	}

	@Test
	fun applyFormatNumbersLines() {
		assertEquals("1. a\n2. b", Markup.applyFormat("a\nb", 0, 3, "ordered").text)
		assertEquals("a\nb", Markup.applyFormat("1. a\n2. b", 0, 9, "ordered").text)
	}

	@Test
	fun scriptsAreNeverKept() {
		val text = Markup.htmlToText("<p>a</p><script>alert('<p>x</p>')</script><style>p{}</style><p>b</p>")
		assertEquals("a\nb", text)
	}

	// -- search ---------------------------------------------------------------------------------

	@Test
	fun searchIgnoresAccentsAndCase() {
		val notes = listOf(
			Model.makeNote("Ação urgente", content = "<p>Reunião</p>"),
			Model.makeNote("Outra", content = "<p>acao no texto</p>"),
		)
		val r = Search.search(notes, "ACAO reuniao")
		assertEquals(2, r.size)
		assertEquals("Ação urgente", r[0].note.title)
		assertEquals(1.0, r[0].score, 0.0)
		assertEquals(0.5, r[1].score, 0.0)
		assertTrue(Search.search(notes, "   ").isEmpty())
	}

	@Test
	fun searchIsFastWithManyNotes() {
		val notes = (0 until 100_000).map {
			Model.makeNote("Folha $it", content = "<p>conteúdo número $it com palavras comuns</p>")
		}
		val index = Search.Index()
		Search.search(notes, "aquecimento", index = index)
		val start = System.nanoTime()
		val r = Search.search(notes, "palavras 99999", index = index)
		val ms = (System.nanoTime() - start) / 1_000_000
		assertEquals(100, r.size)
		assertEquals("Folha 99999", r[0].note.title)
		assertTrue("search took $ms ms", ms < 2000)
	}

	// -- import ---------------------------------------------------------------------------------

	private fun zip(vararg parts: Pair<String, String>): ByteArray {
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use { z ->
			for ((name, text) in parts) {
				z.putNextEntry(ZipEntry(name))
				z.write(text.toByteArray())
				z.closeEntry()
			}
		}
		return out.toByteArray()
	}

	@Test
	fun docxHeadingsListsAndBold() {
		val w = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
		val doc = "<w:document $w><w:body>" +
			"<w:p><w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr><w:r><w:t>Título</w:t></w:r></w:p>" +
			"<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>forte</w:t></w:r><w:r><w:t xml:space=\"preserve\"> fraco</w:t></w:r></w:p>" +
			"<w:tbl><w:tr><w:tc><w:p><w:r><w:t>a</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>b</w:t></w:r></w:p></w:tc></w:tr></w:tbl>" +
			"</w:body></w:document>"
		val d = Importer.readDocument("rel.docx", zip("word/document.xml" to doc))
		assertEquals("rel", d.title)
		assertEquals("# Título\n**forte** fraco\na | b", d.text)
	}

	@Test
	fun docxWithADtdIsRefused() {
		val bomb = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"aaaa\">]><x>&a;</x>"
		try {
			Importer.readDocument("x.docx", zip("word/document.xml" to bomb))
			fail()
		} catch (e: DocumentException) {
			assertEquals("damaged", e.kind)
		}
	}

	@Test
	fun unsupportedAndEmptyDocuments() {
		try {
			Importer.readDocument("x.pdf", ByteArray(3))
			fail()
		} catch (e: DocumentException) {
			assertEquals("unsupported", e.kind)
		}
		try {
			Importer.readDocument("x.txt", "  \n ".toByteArray())
			fail()
		} catch (e: DocumentException) {
			assertEquals("empty", e.kind)
		}
		try {
			Importer.readDocument("x.docx", "not a zip".toByteArray())
			fail()
		} catch (e: DocumentException) {
			assertEquals("damaged", e.kind)
		}
	}

	@Test
	fun emailHeadersAndBody() {
		val eml = "From: =?utf-8?B?QW7DoQ==?= <ana@example.com>\r\nTo: bruno@example.com\r\nSubject: =?iso-8859-1?Q?Reuni=E3o?=\r\n" +
			"Date: Mon, 12 Oct 2026 14:30:00 -0300\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=\"XX\"\r\n\r\n" +
			"--XX\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nOl=C3=A1 mundo\r\n" +
			"--XX\r\nContent-Type: application/pdf; name=\"pauta.pdf\"\r\nContent-Disposition: attachment; filename=\"pauta.pdf\"\r\nContent-Transfer-Encoding: base64\r\n\r\nJVBERg==\r\n--XX--\r\n"
		val d = Importer.readDocument("m.eml", eml.toByteArray(), Texts.DOCUMENT_LABELS, { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) })
		assertEquals("Reunião", d.title)
		assertEquals(
			"**De:** Aná <ana@example.com>\n**Para:** bruno@example.com\n**Data:** 12/10/2026\n**Anexos:** pauta.pdf\n---\nOlá mundo",
			d.text,
		)
	}

	@Test
	fun fitContentCutsLongTexts() {
		val text = (1..1000).joinToString("\n") { "linha $it" }
		val (fitted, cut) = Importer.fitContent(text, 500)
		assertTrue(cut)
		assertTrue(Markup.textToHtml(fitted).length <= 500)
	}

	// -- export ---------------------------------------------------------------------------------

	@Test
	fun exportMarkdownOfANote() {
		val note = Model.makeNote("Ata", content = Markup.textToHtml("## Pauta\n- um\n- dois\n**fim**"))
		val blocks = Exporter.buildDocument(null, null, listOf(null to listOf(note)), { listOf("Status: A fazer") }, { it.title })
		assertEquals("# Ata\n\nStatus: A fazer\n\n## Pauta\n\n- um\n- dois\n\n**fim**\n", Exporter.toMarkdown(blocks))
	}

	@Test
	fun exportSeparateFilesNeverOverwrite() {
		val dir = tempDir()
		File(dir, "Caderno").mkdirs()
		val notes = listOf(Model.makeNote("Igual"), Model.makeNote("Igual"), Model.makeNote("a/b"))
		val (root, count) = Exporter.writeSeparate(
			FileExportFolder(dir), "Caderno", "md", listOf(null to notes),
			{ Exporter.buildDocument(null, null, listOf(null to listOf(it)), { emptyList() }, { n -> n.title }) },
			{ it.title }, "pt-BR", "Sem título",
		)
		assertEquals(3, count)
		assertEquals("Caderno (2)", root.displayName)
		assertEquals(setOf("igual.md", "igual (2).md", "ab.md"), root.existingNames())
	}

	@Test
	fun safeNames() {
		assertEquals("_CON", Exporter.safeName("CON", "x"))
		assertEquals("x", Exporter.safeName(" ... ", "x"))
		assertEquals("abc", Exporter.safeName("a<b>c", "x"))
	}
}
