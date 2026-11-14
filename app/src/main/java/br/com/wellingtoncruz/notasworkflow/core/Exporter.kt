// NotasWorkflow Android - Exporter.kt
// Exports a note, a notebook or a folder as a Word (.docx), Markdown (.md) or HTML (.html)
// document, in one file or one file for each note. The texts come already translated; where the
// files go (a folder of the device or a folder chosen by the person) comes in through ExportFolder.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A run of text with its marks: bold, italic, strike, code. */
data class Run(val text: String, val marks: Set<String> = emptySet())

/** A block of a document, the same for the three formats.
 * kind: h (heading), p (paragraph; meta: status and dates of a note), li, quote, code, hr. */
data class Block(
	val kind: String,
	val runs: List<Run> = emptyList(),
	val level: Int = 0,
	val meta: Boolean = false,
	val ordered: Boolean = false,
	val start: Int = 1,
	val listId: Int = 0,
	val number: Int = 0,
	val text: String = "",
)

/** A folder where exported files are written. */
interface ExportFolder {
	/** Lower-case names of what is already in the folder. */
	fun existingNames(): Set<String>

	fun createFolder(name: String): ExportFolder

	fun createFile(name: String, mimeType: String): OutputStream

	/** What to tell the person about where this folder is. */
	val displayName: String
}

/** An ExportFolder on the file system (tests, or a folder of the app). */
class FileExportFolder(val dir: File) : ExportFolder {
	override fun existingNames(): Set<String> = (dir.list() ?: emptyArray()).map { it.lowercase() }.toSet()

	override fun createFolder(name: String): ExportFolder {
		val sub = File(dir, name)
		if (!sub.mkdirs()) throw java.io.IOException("mkdir failed")
		return FileExportFolder(sub)
	}

	override fun createFile(name: String, mimeType: String): OutputStream = File(dir, name).outputStream()

	override val displayName: String get() = dir.name
}

object Exporter {
	val FORMATS = listOf("docx", "md", "html")
	val EXTENSIONS = mapOf("docx" to ".docx", "md" to ".md", "html" to ".html")
	val MIME_TYPES = mapOf(
		"docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
		"md" to "text/markdown",
		"html" to "text/html",
	)

	/** Characters that cannot be in an XML document (and so in a .docx), and lone surrogates. */
	private val BAD_CHARS_RE = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]|[\\ud800-\\udbff](?![\\udc00-\\udfff])|(?<![\\ud800-\\udbff])[\\udc00-\\udfff]")
	/** Characters not accepted in a file name (Windows, Android and the cloud drives). */
	private val BAD_NAME_RE = Regex("[<>:\"/\\\\|?*\\x00-\\x1f]")
	private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL") + (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }
	const val MAX_NAME = 80

	fun clean(text: String?): String = BAD_CHARS_RE.replace(text ?: "", "")

	fun heading(level: Int, text: String) = Block("h", listOf(Run(clean(text))), level = level.coerceIn(1, 6))

	fun paragraph(text: String, meta: Boolean = false) = Block("p", listOf(Run(clean(text))), meta = meta)

	/** The HTML written by Markup.textToHtml (only safe tags) -> blocks. The headings of the content
	 * go below the heading of the note (level noteLevel). */
	private class BlockParser(val noteLevel: Int, val listIds: IntArray) : HtmlHandler() {
		companion object {
			val MARKS = mapOf("strong" to "bold", "em" to "italic", "s" to "strike", "code" to "code")
		}

		val blocks = ArrayList<Block>()
		var current: Block? = null
		val runs = ArrayList<Run>()
		val code = StringBuilder()
		val marks = HashSet<String>()
		val lists = ArrayList<IntArray>() // [ordered, start, listId, counter]
		var quote = 0
		var pre = false

		private fun isHeading(tag: String) = tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6'

		fun start(block: Block) {
			end()
			current = block
			runs.clear()
			code.setLength(0)
		}

		fun end() {
			val block = current ?: return
			current = null
			if (block.kind == "code") {
				blocks.add(block.copy(text = code.toString()))
			} else if (runs.any { it.text.isNotBlank() }) {
				blocks.add(block.copy(runs = runs.toList()))
			}
		}

		override fun startTag(tag: String, attrs: List<Pair<String, String?>>) {
			when {
				tag == "pre" -> {
					start(Block("code"))
					pre = true
				}
				tag == "p" || isHeading(tag) -> {
					if (tag != "p") {
						val level = minOf(6, maxOf(noteLevel + 1, (tag[1] - '0') + noteLevel - 1))
						start(Block("h", level = level))
					} else if (lists.isNotEmpty()) {
						val l = lists.last()
						start(Block("li", ordered = l[0] == 1, start = l[1], listId = l[2], number = l[3]))
					} else if (quote > 0) {
						start(Block("quote"))
					} else {
						start(Block("p"))
					}
				}
				tag == "ul" || tag == "ol" -> {
					var startAt = 1
					for ((name, value) in attrs) {
						if (name == "start" && value != null && value.isNotEmpty() && value.all { it in '0'..'9' } && value.length < 10) {
							startAt = value.toInt()
						}
					}
					listIds[0] += 1
					lists.add(intArrayOf(if (tag == "ol") 1 else 0, startAt, listIds[0], startAt - 1))
				}
				tag == "li" && lists.isNotEmpty() -> lists.last()[3] += 1
				tag == "blockquote" -> quote++
				tag == "hr" -> {
					end()
					blocks.add(Block("hr"))
				}
				tag in MARKS && !pre -> marks.add(MARKS.getValue(tag))
			}
		}

		override fun startEndTag(tag: String, attrs: List<Pair<String, String?>>) {
			startTag(tag, attrs)
			if (tag != "hr" && tag != "br") endTag(tag)
		}

		override fun endTag(tag: String) {
			when {
				tag == "pre" -> {
					pre = false
					end()
				}
				tag == "p" || isHeading(tag) -> end()
				(tag == "ul" || tag == "ol") && lists.isNotEmpty() -> lists.removeAt(lists.size - 1)
				tag == "blockquote" -> quote = maxOf(0, quote - 1)
				tag in MARKS -> marks.remove(MARKS.getValue(tag))
			}
		}

		override fun data(text: String) {
			val data = clean(text)
			if (current == null) {
				if (data.isBlank()) return
				start(Block("p"))
			}
			if (current!!.kind == "code") code.append(data) else runs.add(Run(data, marks.toSet()))
		}

		fun result(): List<Block> {
			end()
			return blocks
		}
	}

	/** Blocks of the content of a note (stored HTML). The content is first read as the editor shows
	 * it and written again as HTML: only the formats of the editor reach the export. */
	fun contentBlocks(content: String?, noteLevel: Int = 1, listIds: IntArray = IntArray(1)): List<Block> {
		val safe = Markup.textToHtml(Markup.htmlToText(content ?: ""))
		val parser = BlockParser(noteLevel, listIds)
		HtmlTokenizer.feed(safe, parser)
		return parser.result()
	}

	/** Heading of the note, its meta paragraphs and its content. */
	fun noteBlocks(note: Note, level: Int, title: String, meta: List<String>, listIds: IntArray): List<Block> {
		val out = ArrayList<Block>()
		out.add(heading(level, title))
		meta.forEach { out.add(paragraph(it, meta = true)) }
		out += contentBlocks(note.content, level, listIds)
		return out
	}

	/** Blocks of one document. title: heading 1 (null for a single note: the note is the heading 1).
	 * sections: (heading or null, notes); a section with a heading puts its notes one level below it. */
	fun buildDocument(
		title: String?,
		intro: String?,
		sections: List<Pair<String?, List<Note>>>,
		describe: (Note) -> List<String>,
		noteTitle: (Note) -> String,
	): List<Block> {
		val blocks = ArrayList<Block>()
		val listIds = IntArray(1)
		var level = 1
		if (title != null) {
			blocks.add(heading(1, title))
			level = 2
		}
		if (!intro.isNullOrEmpty()) blocks.add(paragraph(intro))
		for ((sectionTitle, notes) in sections) {
			if (notes.isEmpty()) continue
			var noteLevel = level
			if (sectionTitle != null) {
				blocks.add(heading(level, sectionTitle))
				noteLevel = level + 1
			}
			for (note in notes) blocks += noteBlocks(note, noteLevel, noteTitle(note), describe(note), listIds)
		}
		return blocks
	}

	// -- Markdown ----------------------------------------------------------------------------------

	private val MD_ESCAPE_RE = Regex("([\\\\`*_~\\[\\]<>])")
	private val MD_LINE_START_RE = Regex("^(\\s*)(#|[-+]\\s|\\d+[.)]\\s|>)")

	private fun mdText(text: String) = MD_ESCAPE_RE.replace(text) { "\\" + it.value }

	private fun mdRuns(runs: List<Run>): String {
		val out = StringBuilder()
		for ((text, marks) in runs) {
			if (text.isEmpty()) continue
			if ("code" in marks) {
				val fence = if ("`" in text) "``" else "`"
				val pad = if (text.startsWith("`") || text.endsWith("`")) " " else ""
				out.append(fence).append(pad).append(text).append(pad).append(fence)
				continue
			}
			if (text.isBlank()) {
				out.append(text)
				continue
			}
			val lead = text.substring(0, text.length - text.trimStart().length)
			val trail = text.substring(text.trimEnd().length)
			var core = mdText(text.trim())
			if ("bold" in marks) core = "**$core**"
			if ("italic" in marks) core = "*$core*"
			if ("strike" in marks) core = "~~$core~~"
			out.append(lead).append(core).append(trail)
		}
		var line = out.toString().replace("\n", " ").trim()
		val m = MD_LINE_START_RE.find(line)
		if (m != null) {
			val g2 = m.groups[2]!!
			// "1. " becomes "1\. " (a backslash before a digit is not an escape); the others get it before them.
			val at = if (g2.value[0].isDigit()) g2.range.last + 1 - 2 else m.groups[1]!!.range.last + 1
			line = line.substring(0, at) + "\\" + line.substring(at)
		}
		return line
	}

	fun toMarkdown(blocks: List<Block>): String {
		val parts = ArrayList<String>()
		var prev: Block? = null
		for (block in blocks) {
			val text = when (block.kind) {
				"h" -> "#".repeat(block.level) + " " + mdRuns(block.runs)
				"li" -> (if (block.ordered) "${block.number}." else "-") + " " + mdRuns(block.runs)
				"quote" -> "> " + mdRuns(block.runs)
				"code" -> {
					val fence = if ("```" in block.text) "````" else "```"
					fence + "\n" + block.text.trimEnd('\n') + "\n" + fence
				}
				"hr" -> "---"
				else -> mdRuns(block.runs)
			}
			// Items of the same list (and lines of the same quote) stay together.
			val together = prev != null && (
				(block.kind == "li" && prev.kind == "li" && prev.listId == block.listId) ||
					(block.kind == "quote" && prev.kind == "quote")
				)
			if (block.kind == "quote" && together) parts.add(">") else if (parts.isNotEmpty() && !together) parts.add("")
			parts.add(text)
			prev = block
		}
		return parts.joinToString("\n") + "\n"
	}

	// -- HTML ---------------------------------------------------------------------------------------

	private val HTML_TAGS = listOf("code" to "code", "bold" to "strong", "italic" to "em", "strike" to "s")

	private const val CSS = """body{font-family:Segoe UI,Roboto,Arial,sans-serif;line-height:1.5;max-width:50em;margin:2em auto;padding:0 1em;color:#1a1a1a;background:#fff}
h1,h2,h3,h4,h5,h6{color:#1F4E79;line-height:1.25}
.meta{color:#444;margin:.2em 0}
blockquote{margin:1em 0;padding:.2em 1em;border-left:4px solid #1F4E79;background:#f3f6fa}
pre{background:#f4f4f4;padding:.8em;overflow-x:auto;white-space:pre-wrap}
code{font-family:Consolas,monospace}
@media (prefers-color-scheme:dark){body{color:#eee;background:#1e1e1e}h1,h2,h3,h4,h5,h6{color:#9cc3ea}.meta{color:#bbb}blockquote{background:#26303a}pre{background:#2a2a2a}}"""

	private fun htmlRuns(runs: List<Run>): String {
		val out = StringBuilder()
		for ((text, marks) in runs) {
			var piece = HtmlTokenizer.escape(text)
			for ((mark, tag) in HTML_TAGS) if (mark in marks) piece = "<$tag>$piece</$tag>"
			out.append(piece)
		}
		return out.toString()
	}

	fun toHtml(blocks: List<Block>, title: String, lang: String): String {
		val body = ArrayList<String>()
		var openList: Pair<String, Int>? = null
		var inQuote = false
		for (block in blocks) {
			if (openList != null && (block.kind != "li" || block.listId != openList.second)) {
				body.add("</${openList.first}>")
				openList = null
			}
			if (inQuote && block.kind != "quote") {
				body.add("</blockquote>")
				inQuote = false
			}
			when (block.kind) {
				"h" -> body.add("<h${block.level}>${htmlRuns(block.runs)}</h${block.level}>")
				"li" -> {
					if (openList == null) {
						val tag = if (block.ordered) "ol" else "ul"
						val start = if (block.ordered && block.start != 1) " start=\"${block.start}\"" else ""
						body.add("<$tag$start>")
						openList = Pair(tag, block.listId)
					}
					body.add("<li>${htmlRuns(block.runs)}</li>")
				}
				"quote" -> {
					if (!inQuote) {
						body.add("<blockquote>")
						inQuote = true
					}
					body.add("<p>${htmlRuns(block.runs)}</p>")
				}
				"code" -> body.add("<pre><code>${HtmlTokenizer.escape(block.text.trimEnd('\n'))}</code></pre>")
				"hr" -> body.add("<hr>")
				else -> body.add((if (block.meta) "<p class=\"meta\">" else "<p>") + htmlRuns(block.runs) + "</p>")
			}
		}
		if (openList != null) body.add("</${openList.first}>")
		if (inQuote) body.add("</blockquote>")
		return "<!DOCTYPE html>\n<html lang=\"${HtmlTokenizer.escape(lang, true)}\">\n<head>\n<meta charset=\"utf-8\">\n" +
			"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
			"<title>${HtmlTokenizer.escape(clean(title))}</title>\n<style>\n$CSS\n</style>\n</head>\n<body>\n<main>\n" +
			body.joinToString("\n") + "\n</main>\n</body>\n</html>\n"
	}

	// -- Word -----------------------------------------------------------------------------------------

	private const val W_NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
	private const val ACCENT = "1F4E79"

	private fun x(text: String) = HtmlTokenizer.escape(text, quote = true)

	private fun docxRuns(runs: List<Run>): String {
		val out = StringBuilder()
		for ((text, marks) in runs) {
			if (text.isEmpty()) continue
			var props = ""
			if ("bold" in marks) props += "<w:b/>"
			if ("italic" in marks) props += "<w:i/>"
			if ("strike" in marks) props += "<w:strike/>"
			if ("code" in marks) props += "<w:rStyle w:val=\"CodeChar\"/>"
			val rpr = if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else ""
			out.append("<w:r>$rpr<w:t xml:space=\"preserve\">${x(text)}</w:t></w:r>")
		}
		return out.toString()
	}

	private fun para(style: String?, content: String, extra: String = ""): String {
		val ppr = if (style != null) "<w:pPr><w:pStyle w:val=\"$style\"/>$extra</w:pPr>" else ""
		return "<w:p>$ppr$content</w:p>"
	}

	private fun docxBody(blocks: List<Block>): Pair<List<String>, List<Pair<Int, Int>>> {
		val out = ArrayList<String>()
		val numbered = ArrayList<Pair<Int, Int>>()
		val numOf = HashMap<Int, Int>()
		for (block in blocks) {
			when (block.kind) {
				"h" -> out.add(para("Heading${block.level}", docxRuns(block.runs)))
				"li" -> {
					val numId = if (block.ordered) {
						numOf.getOrPut(block.listId) {
							val id = 2 + numbered.size
							numbered.add(Pair(id, block.start))
							id
						}
					} else 1
					out.add(para("ListParagraph", docxRuns(block.runs), "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"$numId\"/></w:numPr>"))
				}
				"quote" -> out.add(para("Quote", docxRuns(block.runs)))
				"code" -> {
					val lines = block.text.trimEnd('\n').split("\n")
					val runs = lines.joinToString("<w:r><w:br/></w:r>") { "<w:r><w:t xml:space=\"preserve\">${x(it)}</w:t></w:r>" }
					out.add(para("CodeBlock", runs))
				}
				"hr" -> out.add(para("Divider", ""))
				else -> out.add(para(if (block.meta) "Meta" else null, docxRuns(block.runs)))
			}
		}
		return Pair(out, numbered)
	}

	private fun stylesXml(lang: String): String {
		fun pstyle(sid: String, name: String, pprIn: String = "", rpr: String = "", outline: Int? = null): String {
			val ppr = pprIn + if (outline != null) "<w:outlineLvl w:val=\"$outline\"/>" else ""
			return "<w:style w:type=\"paragraph\" w:styleId=\"$sid\"><w:name w:val=\"$name\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
				"<w:pPr>$ppr</w:pPr><w:rPr>$rpr</w:rPr></w:style>"
		}
		val sizes = mapOf(1 to 36, 2 to 30, 3 to 26, 4 to 24, 5 to 22, 6 to 22)
		val headings = (1..6).joinToString("") { level ->
			pstyle(
				"Heading$level", "heading $level",
				"<w:keepNext/><w:spacing w:before=\"${if (level < 3) 360 else 240}\" w:after=\"120\"/>",
				"<w:b/><w:color w:val=\"$ACCENT\"/><w:sz w:val=\"${sizes[level]}\"/><w:szCs w:val=\"${sizes[level]}\"/>",
				outline = level - 1,
			)
		}
		val l = x(lang)
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:styles $W_NS>" +
			"<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Calibri\"/>" +
			"<w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/><w:lang w:val=\"$l\" w:eastAsia=\"$l\" w:bidi=\"$l\"/></w:rPr></w:rPrDefault>" +
			"<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
			"<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
			headings +
			pstyle("Meta", "Note details", "", "<w:color w:val=\"404040\"/>") +
			pstyle("ListParagraph", "List Paragraph", "<w:ind w:left=\"720\"/><w:contextualSpacing/>") +
			pstyle("Quote", "Quote", "<w:ind w:left=\"567\" w:right=\"567\"/><w:pBdr><w:left w:val=\"single\" w:sz=\"18\" w:space=\"8\" w:color=\"$ACCENT\"/></w:pBdr>", "<w:i/>") +
			pstyle("CodeBlock", "Code", "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/><w:spacing w:after=\"120\" w:line=\"240\" w:lineRule=\"auto\"/>", "<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:cs=\"Consolas\"/><w:sz w:val=\"20\"/>") +
			pstyle("Divider", "Divider", "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"A6A6A6\"/></w:pBdr>") +
			"<w:style w:type=\"character\" w:styleId=\"CodeChar\"><w:name w:val=\"Code Char\"/><w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:cs=\"Consolas\"/></w:rPr></w:style>" +
			"</w:styles>"
	}

	private fun numberingXml(numbered: List<Pair<Int, Int>>): String {
		fun abstract(aid: Int, fmt: String, text: String) =
			"<w:abstractNum w:abstractNumId=\"$aid\"><w:multiLevelType w:val=\"singleLevel\"/><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/>" +
				"<w:numFmt w:val=\"$fmt\"/><w:lvlText w:val=\"$text\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>"
		var nums = "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
		for ((numId, start) in numbered) {
			nums += "<w:num w:numId=\"$numId\"><w:abstractNumId w:val=\"1\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"$start\"/></w:lvlOverride></w:num>"
		}
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:numbering $W_NS>" +
			abstract(0, "bullet", "•") + abstract(1, "decimal", "%1.") + nums + "</w:numbering>"
	}

	private const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
		"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
		"<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
		"<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
		"<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
		"<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
		"<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" +
		"<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
		"<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>" +
		"</Types>"
	private const val ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
		"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
		"<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
		"<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
		"<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>" +
		"</Relationships>"
	private const val DOC_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
		"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
		"<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
		"<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>" +
		"</Relationships>"
	private const val APP = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
		"<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>NotasWorkflow Android</Application></Properties>"

	private fun coreXml(title: String, lang: String) =
		"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
			"<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
			"xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
			"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
			"<dc:title>${x(clean(title))}</dc:title><dc:language>${x(lang)}</dc:language><dc:creator>NotasWorkflow Android</dc:creator></cp:coreProperties>"

	/** Fixed date inside the zip: the same notes always give the same file. */
	private val ZIP_TIME = java.time.LocalDateTime.of(2026, 1, 1, 0, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

	fun writeDocx(out: OutputStream, blocks: List<Block>, title: String, lang: String) {
		val (paragraphs, numbered) = docxBody(blocks)
		val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:document $W_NS><w:body>" +
			(if (paragraphs.isEmpty()) "<w:p/>" else paragraphs.joinToString("")) +
			"<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" " +
			"w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
		val parts = listOf(
			"[Content_Types].xml" to CONTENT_TYPES,
			"_rels/.rels" to ROOT_RELS,
			"word/document.xml" to document,
			"word/_rels/document.xml.rels" to DOC_RELS,
			"word/styles.xml" to stylesXml(lang),
			"word/numbering.xml" to numberingXml(numbered),
			"docProps/core.xml" to coreXml(title, lang),
			"docProps/app.xml" to APP,
		)
		val zip = ZipOutputStream(out)
		for ((name, data) in parts) {
			val entry = ZipEntry(name)
			entry.time = ZIP_TIME
			zip.putNextEntry(entry)
			zip.write(data.toByteArray(Charsets.UTF_8))
			zip.closeEntry()
		}
		zip.finish()
		zip.flush()
	}

	// -- files --------------------------------------------------------------------------------------

	/** Writes the blocks in the format (docx, md or html). The stream is closed. */
	fun writeDocument(out: OutputStream, fmt: String, blocks: List<Block>, title: String, lang: String) {
		out.use { stream ->
			if (fmt == "docx") {
				writeDocx(stream, blocks, title, lang)
			} else {
				val text = if (fmt == "md") toMarkdown(blocks) else toHtml(blocks, title, lang)
				stream.write(text.toByteArray(Charsets.UTF_8))
			}
		}
	}

	/** A file or folder name every system accepts, made from a title or a name. */
	fun safeName(text: String?, fallback: String): String {
		var name = BAD_NAME_RE.replace(clean(text), "").trim()
		name = name.replace(Regex("\\s+"), " ").take(MAX_NAME).trimEnd('.', ' ')
		if (name.isEmpty()) name = fallback
		if (name.substringBefore('.').uppercase() in RESERVED_NAMES) name = "_$name"
		return name
	}

	/** name+ext, or "name (2)"+ext... when that name exists in the folder or was already used.
	 * used: lower-case names already given in this folder (it is updated). */
	fun uniqueName(existing: Set<String>, name: String, ext: String, used: MutableSet<String>): String {
		var candidate = name + ext
		var n = 2
		while (candidate.lowercase() in used || candidate.lowercase() in existing) {
			candidate = "$name ($n)$ext"
			n++
		}
		used.add(candidate.lowercase())
		return candidate
	}

	/** One file for each note, in a new folder called rootName inside folder ("rootName (2)" if it
	 * exists, so nothing is overwritten). groups: (subfolder name or null, notes).
	 * Returns (the folder created, number of files written). */
	fun writeSeparate(
		folder: ExportFolder,
		rootName: String,
		fmt: String,
		groups: List<Pair<String?, List<Note>>>,
		makeBlocks: (Note) -> List<Block>,
		noteTitle: (Note) -> String,
		lang: String,
		fallback: String,
	): Pair<ExportFolder, Int> {
		val ext = EXTENSIONS.getValue(fmt)
		val root = folder.createFolder(uniqueName(folder.existingNames(), safeName(rootName, fallback), "", HashSet()))
		val subfolders = HashSet<String>()
		var count = 0
		for ((groupName, notes) in groups) {
			if (notes.isEmpty()) continue
			var target = root
			if (groupName != null) {
				target = root.createFolder(uniqueName(emptySet(), safeName(groupName, fallback), "", subfolders))
			}
			val used = HashSet<String>()
			for (note in notes) {
				val title = noteTitle(note)
				val name = uniqueName(emptySet(), safeName(title, fallback), ext, used)
				writeDocument(target.createFile(name, MIME_TYPES.getValue(fmt)), fmt, makeBlocks(note), title, lang)
				count++
			}
		}
		return Pair(root, count)
	}
}
