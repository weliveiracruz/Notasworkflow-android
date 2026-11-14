// NotasWorkflow Android - Importer.kt
// Reads the text of a document (Word .docx, PowerPoint .pptx, text .txt, HTML page or e-mail
// .eml) as the light markup of the editor: headings, lists, bold, italic and strikethrough are
// kept; images are not; a table row becomes one line "a | b | c". Used to create a note from a
// document and to insert a document in a note. The labels come already translated.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** kind: unsupported, tooBig, unreadable, damaged or empty. */
class DocumentException(val kind: String) : Exception(kind)

data class ImportedDocument(val title: String, val text: String)

object Importer {
	/** Extensions that can be read (the old binary .doc, .ppt and .rtf cannot, nor .msg and .pdf). */
	val EXTENSIONS = listOf(".docx", ".pptx", ".txt", ".html", ".htm", ".eml")
	/** Largest file read. */
	const val MAX_FILE = 50 * 1024 * 1024
	/** Largest part of a .docx or .pptx once uncompressed, and all the parts together. */
	const val MAX_PART = 100 * 1024 * 1024
	const val MAX_UNZIPPED = 300L * 1024 * 1024

	private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
	private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
	private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
	private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
	private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"
	private const val DC = "http://purl.org/dc/elements/1.1/"
	private const val NOTES_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide"

	private val CONTROL_RE = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f\\ud800-\\udfff]")
	private val SPACES_RE = Regex("[ \\t\\u00a0\\u2000-\\u200b\\u202f\\u205f\\u3000]+")
	private val META_CHARSET_RE = Regex("""<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9_.:-]+)""", RegexOption.IGNORE_CASE)
	private val HTML_TITLE_RE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
	private val HEADING_STYLE_RE = Regex("^heading\\s*([1-6])$")
	/** Bullet characters typed or written by Word in a page or a text: the line becomes a list item. */
	private val BULLET_CHAR_RE = Regex("^[\\u00b7\\u2022\\u25aa\\u25e6\\u25cf\\u25a0\\u00a7\\uf0b7] ")
	private val FALSE = setOf("0", "false", "off")

	val DEFAULT_LABELS = mapOf(
		"slide" to "Slide {number}",
		"slideTitled" to "Slide {number}: {title}",
		"notes" to "Speaker notes:",
		"from" to "From:",
		"to" to "To:",
		"cc" to "Cc:",
		"date" to "Date:",
		"attachments" to "Attachments:",
	)

	fun extension(name: String): String {
		val dot = name.lastIndexOf('.')
		return if (dot < 0) "" else name.substring(dot).lowercase()
	}

	fun canRead(name: String): Boolean = extension(name) in EXTENSIONS

	/** (title, text) of a document: the title of the document (or the subject of the e-mail),
	 * otherwise the file name; text in the light markup of the editor. Raises DocumentException. */
	fun readDocument(
		fileName: String,
		data: ByteArray,
		labels: Map<String, String> = DEFAULT_LABELS,
		formatDate: ((ZonedDateTime) -> String)? = null,
		mimeType: String? = null,
	): ImportedDocument {
		val allLabels = DEFAULT_LABELS + labels
		var ext = extension(fileName)
		if (ext !in EXTENSIONS) ext = extensionOfMime(mimeType) ?: throw DocumentException("unsupported")
		if (data.size > MAX_FILE) throw DocumentException("tooBig")
		val (rawTitle, rawText) = try {
			when (ext) {
				".docx" -> docx(data)
				".pptx" -> pptx(data, allLabels)
				".eml" -> Mime.readEmail(data, allLabels, formatDate)
				".txt" -> Pair("", decode(data, null))
				else -> html(data)
			}
		} catch (e: DocumentException) {
			throw e
		} catch (e: Exception) {
			// A damaged file, a file protected by a password (an encrypted .docx is not a zip)...
			throw DocumentException("damaged")
		}
		val text = tidy(rawText)
		if (text.isBlank()) throw DocumentException("empty")
		var title = Model.cleanText(CONTROL_RE.replace(rawTitle, ""), Model.MAX_TITLE)
		if (title.isEmpty()) {
			val base = fileName.substringAfterLast('/')
			title = Model.cleanText(if ('.' in base) base.substringBeforeLast('.') else base, Model.MAX_TITLE)
		}
		return ImportedDocument(title, text)
	}

	private fun extensionOfMime(mime: String?): String? = when (mime) {
		"application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx"
		"application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx"
		"message/rfc822" -> ".eml"
		"text/plain" -> ".txt"
		"text/html" -> ".html"
		else -> null
	}

	/** One space between words, no control characters, no blank lines (each line is already a
	 * paragraph), bullet characters as list items. Code blocks keep their spaces and lines. */
	fun tidy(text: String): String {
		val out = ArrayList<String>()
		var inCode = false
		for (raw in CONTROL_RE.replace(text.replace("\r\n", "\n").replace('\r', '\n'), "").split("\n")) {
			var line = raw
			if (line.trim() == "```") {
				inCode = !inCode
				out.add("```")
				continue
			}
			if (!inCode) {
				line = BULLET_CHAR_RE.replaceFirst(SPACES_RE.replace(line, " ").trim(), "- ")
				if (line.isEmpty()) continue
			}
			out.add(line.trimEnd())
		}
		while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
		return out.joinToString("\n")
	}

	/** (text, cut): the text, or its first lines, so that the note (HTML) is at most limit. */
	fun fitContent(text: String, limit: Int = Model.MAX_CONTENT): Pair<String, Boolean> {
		if (limit <= 0) return Pair("", text.isNotEmpty())
		if (Markup.textToHtml(text).length <= limit) return Pair(text, false)
		val lines = text.split("\n")
		var low = 0
		var high = lines.size
		// Binary search on the number of lines that fit.
		while (low < high) {
			val middle = (low + high + 1) / 2
			if (Markup.textToHtml(lines.subList(0, middle).joinToString("\n")).length <= limit) low = middle else high = middle - 1
		}
		if (low == 0) return Pair(lines[0].take(limit / 6), true)
		return Pair(lines.subList(0, low).joinToString("\n"), true)
	}

	// -- text and HTML -----------------------------------------------------------------------------

	fun decode(data: ByteArray, declared: String?): String {
		if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) {
			return String(data, 3, data.size - 3, Charsets.UTF_8)
		}
		if (data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) return String(data, 2, data.size - 2, Charsets.UTF_16LE)
		if (data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) return String(data, 2, data.size - 2, Charsets.UTF_16BE)
		for (name in listOf(declared, "UTF-8")) {
			if (name.isNullOrEmpty()) continue
			try {
				val decoder = Charset.forName(name).newDecoder()
				return decoder.decode(java.nio.ByteBuffer.wrap(data)).toString()
			} catch (e: Exception) {
				// Next one.
			}
		}
		return String(data, Charset.forName("windows-1252"))
	}

	private fun html(data: ByteArray): Pair<String, String> {
		val head = String(data, 0, minOf(4096, data.size), Charsets.ISO_8859_1)
		val match = META_CHARSET_RE.find(head)
		val source = decode(data, match?.groupValues?.get(1))
		return Pair(htmlTitle(source), Markup.htmlToText(source))
	}

	private fun htmlTitle(source: String): String {
		val match = HTML_TITLE_RE.find(source.take(20000)) ?: return ""
		return Markup.plainText(match.groupValues[1])
	}

	// -- Office documents (.docx, .pptx) --------------------------------------------------------------

	/** The parts of a .docx or .pptx, read with limits. */
	private class Package(data: ByteArray) {
		val parts = HashMap<String, ByteArray>()

		init {
			var total = 0L
			var any = false
			ZipInputStream(ByteArrayInputStream(data)).use { zip ->
				while (true) {
					val entry: ZipEntry = zip.nextEntry ?: break
					any = true
					if (entry.isDirectory) continue
					val name = entry.name
					// Only the parts read here are kept in memory.
					val wanted = name.endsWith(".xml") || name.endsWith(".rels")
					val buf = java.io.ByteArrayOutputStream()
					val chunk = ByteArray(64 * 1024)
					var size = 0L
					while (true) {
						val n = zip.read(chunk)
						if (n < 0) break
						size += n
						total += n
						if (size > MAX_PART || total > MAX_UNZIPPED) throw DocumentException("tooBig")
						if (wanted) buf.write(chunk, 0, n)
					}
					if (wanted) parts[name] = buf.toByteArray()
				}
			}
			if (!any) throw ZipException("not a zip")
		}

		val names: Set<String> get() = parts.keys

		fun xml(name: String, required: Boolean = true): Element? {
			val data = parts[name] ?: if (required) throw DocumentException("damaged") else return null
			// Office parts never have a DTD: refusing one avoids entity expansion attacks.
			val head = String(data, Charsets.ISO_8859_1)
			if ("<!DOCTYPE" in head || "<!ENTITY" in head) throw DocumentException("damaged")
			val factory = DocumentBuilderFactory.newInstance()
			factory.isNamespaceAware = true
			try {
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
			} catch (e: Exception) {
				// Not every parser knows this feature; the DTD was refused above anyway.
			}
			factory.isExpandEntityReferences = false
			return factory.newDocumentBuilder().parse(ByteArrayInputStream(data)).documentElement
		}

		fun title(): String {
			val core = xml("docProps/core.xml", required = false) ?: return ""
			return child(core, DC, "title")?.textContent ?: ""
		}

		/** id -> (type, part name) of the relationships of a part. */
		fun relations(part: String): Map<String, Pair<String, String>> {
			val folder = part.substringBeforeLast('/', "")
			val name = part.substringAfterLast('/')
			val relsName = (if (folder.isEmpty()) "" else "$folder/") + "_rels/$name.rels"
			val rels = xml(relsName, required = false) ?: return emptyMap()
			val out = HashMap<String, Pair<String, String>>()
			for (rel in descendants(rels, REL, "Relationship")) {
				if (rel.getAttribute("TargetMode") == "External") continue
				val target = rel.getAttribute("Target")
				out[rel.getAttribute("Id")] = Pair(rel.getAttribute("Type"), normalize(if (target.startsWith("/")) target else "$folder/$target"))
			}
			return out
		}
	}

	/** posixpath.normpath without the leading slash. */
	private fun normalize(path: String): String {
		val out = ArrayList<String>()
		for (seg in path.split("/")) {
			when (seg) {
				"", "." -> {}
				".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
				else -> out.add(seg)
			}
		}
		return out.joinToString("/")
	}

	private fun isTag(node: Node, ns: String, local: String) =
		node is Element && node.namespaceURI == ns && node.localName == local

	private fun child(parent: Element?, ns: String, local: String): Element? {
		if (parent == null) return null
		var node = parent.firstChild
		while (node != null) {
			if (isTag(node, ns, local)) return node as Element
			node = node.nextSibling
		}
		return null
	}

	private fun children(parent: Element?, ns: String? = null, local: String? = null): List<Element> {
		val out = ArrayList<Element>()
		var node = parent?.firstChild
		while (node != null) {
			if (node is Element && (ns == null || (node.namespaceURI == ns && node.localName == local))) out.add(node)
			node = node.nextSibling
		}
		return out
	}

	/** The element itself (when it matches) and every descendant that matches, in document order. */
	private fun descendants(root: Element, ns: String, local: String): List<Element> {
		val out = ArrayList<Element>()
		if (isTag(root, ns, local)) out.add(root)
		val list = root.getElementsByTagNameNS(ns, local)
		for (i in 0 until list.length) out.add(list.item(i) as Element)
		return out
	}

	private fun wAttr(e: Element?, name: String): String? {
		if (e == null || !e.hasAttributeNS(W, name)) return null
		return e.getAttributeNS(W, name)
	}

	private fun on(props: Element?, local: String): Boolean {
		val node = child(props, W, local) ?: return false
		return (wAttr(node, "val") ?: "1").lowercase() !in FALSE
	}

	/** Light markup of runs (text, marks): the same marks side by side are joined, and the marks go
	 * around the words only (spaces stay outside). */
	private fun runsText(runs: List<Pair<String, Set<String>>>): String {
		val joined = ArrayList<Pair<StringBuilder, Set<String>>>()
		for ((text, marks) in runs) {
			if (joined.isNotEmpty() && joined.last().second == marks) joined.last().first.append(text)
			else joined.add(Pair(StringBuilder(text), marks))
		}
		val out = StringBuilder()
		for ((sb, marks) in joined) {
			val text = sb.toString()
			val core = text.trim()
			if (marks.isEmpty() || core.isEmpty() || "\n" in core) {
				out.append(text)
				continue
			}
			val lead = text.substring(0, text.length - text.trimStart().length)
			val trail = text.substring(text.trimEnd().length)
			// Bold and italic together would be "***", which the editor reads wrongly: bold wins.
			val inner = if ("**" in marks) "**" else if ("*" in marks) "*" else ""
			val outer = if ("~~" in marks) "~~" else ""
			out.append(lead).append(outer).append(inner).append(core).append(inner).append(outer).append(trail)
		}
		return out.toString()
	}

	private class StyleInfo(val name: String, val based: String?, val outline: String?, val num: Element?)

	private class Docx(val pkg: Package) {
		val styles = HashMap<String, StyleInfo>()
		val formats = HashMap<String, Map<String, String>>()
		val counters = HashMap<String, Int>()

		init {
			pkg.xml("word/styles.xml", required = false)?.let { root ->
				for (style in descendants(root, W, "style")) {
					val ppr = child(style, W, "pPr")
					styles[wAttr(style, "styleId") ?: ""] = StyleInfo(
						name = (wAttr(child(style, W, "name"), "val") ?: "").lowercase(),
						based = wAttr(child(style, W, "basedOn"), "val"),
						outline = wAttr(child(ppr, W, "outlineLvl"), "val"),
						num = child(ppr, W, "numPr"),
					)
				}
			}
			pkg.xml("word/numbering.xml", required = false)?.let { root ->
				val abstract = HashMap<String, Map<String, String>>()
				for (node in descendants(root, W, "abstractNum")) {
					val levels = HashMap<String, String>()
					for (lvl in descendants(node, W, "lvl")) {
						levels[wAttr(lvl, "ilvl") ?: ""] = wAttr(child(lvl, W, "numFmt"), "val") ?: "decimal"
					}
					abstract[wAttr(node, "abstractNumId") ?: ""] = levels
				}
				for (num in descendants(root, W, "num")) {
					val ref = child(num, W, "abstractNumId") ?: continue
					formats[wAttr(num, "numId") ?: ""] = abstract[wAttr(ref, "val")] ?: emptyMap()
				}
			}
		}

		/** 1-6 for a heading (style Title, heading 1-6, or an outline level), otherwise null. */
		fun headingLevel(styleIdStart: String?, own: String?): Int? {
			own?.toIntOrNull()?.let { if (it in 0..5) return it + 1 }
			var styleId = styleIdStart
			repeat(10) {
				val style = styles[styleId ?: return null] ?: return null
				if (style.name == "title") return 1
				HEADING_STYLE_RE.find(style.name)?.let { return it.groupValues[1].toInt() }
				style.outline?.toIntOrNull()?.let { if (it in 0..5) return it + 1 }
				styleId = style.based
			}
			return null
		}

		/** ("bullet" or "ordered", key of the counter), or null when the paragraph is not a list. */
		fun listKind(numPrOwn: Element?, styleId: String?): Pair<String, String>? {
			val numPr = numPrOwn ?: styles[styleId ?: ""]?.num ?: return null
			val numId = wAttr(child(numPr, W, "numId"), "val")
			if (numId.isNullOrEmpty() || numId == "0") return null
			val ilvl = wAttr(child(numPr, W, "ilvl"), "val") ?: "0"
			val fmt = formats[numId]?.get(ilvl) ?: "bullet"
			return Pair(if (fmt == "bullet" || fmt == "none") "bullet" else "ordered", "$numId/$ilvl")
		}

		fun paragraphText(paragraph: Element): String {
			val runs = ArrayList<Pair<String, Set<String>>>()
			for (run in descendants(paragraph, W, "r")) {
				val props = child(run, W, "rPr")
				val marks = HashSet<String>()
				if (on(props, "b")) marks.add("**")
				if (on(props, "i")) marks.add("*")
				if (on(props, "strike") || on(props, "dstrike")) marks.add("~~")
				for (c in children(run)) {
					if (c.namespaceURI != W) continue
					when (c.localName) {
						"t" -> runs.add(Pair(c.textContent ?: "", marks))
						"tab", "ptab" -> runs.add(Pair(" ", marks))
						"br", "cr" -> runs.add(Pair("\n", emptySet()))
						"noBreakHyphen" -> runs.add(Pair("-", marks))
					}
				}
			}
			return runsText(runs)
		}

		fun paragraph(node: Element): List<String> {
			val ppr = child(node, W, "pPr")
			val styleId = wAttr(child(ppr, W, "pStyle"), "val")
			val own = wAttr(child(ppr, W, "outlineLvl"), "val")
			val numPr = child(ppr, W, "numPr")
			val text = paragraphText(node).trim()
			if (text.isEmpty()) return emptyList()
			val level = headingLevel(styleId, own)
			if (level != null) return listOf("#".repeat(level) + " " + text.split("\n").joinToString(" "))
			val kind = listKind(numPr, styleId)
			val lines = text.split("\n")
			if (kind == null) return lines
			if (kind.first == "bullet") return listOf("- " + lines[0]) + lines.drop(1)
			val number = (counters[kind.second] ?: 0) + 1
			counters[kind.second] = number
			return listOf("$number. ${lines[0]}") + lines.drop(1)
		}

		fun table(node: Element): List<String> {
			val lines = ArrayList<String>()
			for (row in descendants(node, W, "tr")) {
				val cells = children(row, W, "tc").map { cell ->
					descendants(cell, W, "p")
						.map { p -> paragraphText(p).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") }
						.filter { it.isNotEmpty() }
						.joinToString(" ")
				}
				if (cells.any { it.isNotEmpty() }) lines.add(cells.joinToString(" | "))
			}
			return lines
		}

		fun blocks(parent: Element): List<String> {
			val lines = ArrayList<String>()
			for (node in children(parent)) {
				if (node.namespaceURI != W) continue
				when (node.localName) {
					"p" -> lines += paragraph(node)
					"tbl" -> lines += table(node)
					"sdt", "sdtContent", "customXml", "smartTag", "ins" -> lines += blocks(node)
				}
			}
			return lines
		}
	}

	private fun docx(data: ByteArray): Pair<String, String> {
		val pkg = Package(data)
		val document = pkg.xml("word/document.xml")
		val body = child(document, W, "body") ?: throw DocumentException("damaged")
		return Pair(pkg.title(), Docx(pkg).blocks(body).joinToString("\n"))
	}

	private fun pptParagraph(paragraph: Element): String {
		val runs = ArrayList<Pair<String, Set<String>>>()
		for (node in children(paragraph)) {
			if (node.namespaceURI != A) continue
			when (node.localName) {
				"r", "fld" -> {
					val props = child(node, A, "rPr")
					val marks = HashSet<String>()
					if (props != null) {
						if ((props.getAttribute("b").ifEmpty { "0" }) !in FALSE) marks.add("**")
						if ((props.getAttribute("i").ifEmpty { "0" }) !in FALSE) marks.add("*")
						val strike = props.getAttribute("strike")
						if (strike.isNotEmpty() && strike != "noStrike") marks.add("~~")
					}
					runs.add(Pair(child(node, A, "t")?.textContent ?: "", marks))
				}
				"br" -> runs.add(Pair("\n", emptySet()))
			}
		}
		return runsText(runs).trim()
	}

	/** Lines of a text body; bullets: placeholder of a slide body (bulleted unless buNone). */
	private fun pptBody(body: Element, bullets: Boolean): List<String> {
		val lines = ArrayList<String>()
		val counters = HashMap<String, Int>()
		for (paragraph in children(body, A, "p")) {
			val text = pptParagraph(paragraph)
			if (text.isEmpty()) continue
			val props = child(paragraph, A, "pPr")
			val level = props?.getAttribute("lvl")?.ifEmpty { "0" } ?: "0"
			val prefix = when {
				props != null && child(props, A, "buAutoNum") != null -> {
					val n = (counters[level] ?: 0) + 1
					counters[level] = n
					"$n. "
				}
				(props != null && child(props, A, "buChar") != null) || (bullets && (props == null || child(props, A, "buNone") == null)) -> "- "
				else -> ""
			}
			val parts = text.split("\n")
			lines.add(prefix + parts[0])
			lines += parts.drop(1)
		}
		return lines
	}

	/** (title, lines) of the shapes of a slide (or of its speaker notes), in their order. */
	private fun pptShapes(tree: Element, notes: Boolean = false): Pair<List<String>, List<String>> {
		val title = ArrayList<String>()
		val lines = ArrayList<String>()
		for (shape in children(tree)) {
			if (shape.namespaceURI != P) continue
			when (shape.localName) {
				"grpSp" -> {
					val (t, l) = pptShapes(shape, notes)
					title += t
					lines += l
				}
				"sp" -> {
					val body = child(shape, P, "txBody") ?: continue
					val placeholder = child(child(child(shape, P, "nvSpPr"), P, "nvPr"), P, "ph")
					val kind = placeholder?.getAttribute("type")?.ifEmpty { null }
					when (kind) {
						"title", "ctrTitle" -> title += children(body, A, "p").map {
							pptParagraph(it).split(Regex("\\s+")).filter { w -> w.isNotEmpty() }.joinToString(" ")
						}
						"sldNum", "dt", "ftr", "hdr", "sldImg" -> {}
						else -> {
							val bullets = !notes && placeholder != null && kind in listOf(null, "body", "obj")
							lines += pptBody(body, bullets)
						}
					}
				}
				"graphicFrame" -> {
					for (table in descendants(shape, A, "tbl")) {
						for (row in descendants(table, A, "tr")) {
							val cells = children(row, A, "tc").map { cell ->
								descendants(cell, A, "p").joinToString(" ") { p ->
									pptParagraph(p).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
								}.trim()
							}
							if (cells.any { it.isNotEmpty() }) lines.add(cells.joinToString(" | "))
						}
					}
				}
			}
		}
		return Pair(title.filter { it.isNotEmpty() }, lines)
	}

	private fun pptx(data: ByteArray, labels: Map<String, String>): Pair<String, String> {
		val pkg = Package(data)
		val presentation = pkg.xml("ppt/presentation.xml")!!
		val relations = pkg.relations("ppt/presentation.xml")
		val slides = ArrayList<String>()
		child(presentation, P, "sldIdLst")?.let { order ->
			for (slide in children(order, P, "sldId")) {
				val rel = relations[slide.getAttributeNS(R, "id")]
				if (rel != null && rel.second in pkg.names) slides.add(rel.second)
			}
		}
		if (slides.isEmpty()) {
			val re = Regex("ppt/slides/slide(\\d+)\\.xml")
			slides += pkg.names.mapNotNull { n -> re.matchEntire(n)?.let { Pair(it.groupValues[1].toInt(), n) } }
				.sortedBy { it.first }.map { it.second }
		}
		val out = ArrayList<String>()
		slides.forEachIndexed { index, part ->
			val number = index + 1
			val slide = pkg.xml(part)
			val tree = child(child(slide, P, "cSld"), P, "spTree")
			val (title, lines) = if (tree != null) pptShapes(tree) else Pair(emptyList(), emptyList())
			if (title.isNotEmpty()) {
				out.add("## " + labels.getValue("slideTitled").replace("{number}", "$number").replace("{title}", title.joinToString(" ")))
			} else {
				out.add("## " + labels.getValue("slide").replace("{number}", "$number"))
			}
			out += lines
			for ((kind, target) in pkg.relations(part).values) {
				if (kind != NOTES_REL || target !in pkg.names) continue
				val notesRoot = pkg.xml(target)
				val notesTree = child(child(notesRoot, P, "cSld"), P, "spTree")
				val noteLines = (if (notesTree != null) pptShapes(notesTree, notes = true).second else emptyList()).filter { it.isNotBlank() }
				if (noteLines.isNotEmpty()) {
					out.add(labels.getValue("notes"))
					out += noteLines.map { "> $it" }
				}
			}
		}
		return Pair(pkg.title(), out.joinToString("\n"))
	}
}
