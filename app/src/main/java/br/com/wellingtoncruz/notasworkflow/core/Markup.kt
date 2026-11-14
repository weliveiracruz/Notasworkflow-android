// NotasWorkflow Android - Markup.kt
// The note content is stored as HTML (paragraphs, headings, lists, quotes), the same as the
// add-on and the web app. Here it is edited as plain text with a light markup, converted both
// ways without loss, and the formats of the editor are applied to a selection.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

object Markup {
	private val HEADING_RE = Regex("^(#{1,6}) (.*)$")
	private val BULLET_RE = Regex("^[-*] (.*)$")
	private val ORDERED_RE = Regex("^(\\d{1,9})\\. (.*)$")
	private val QUOTE_RE = Regex("^> ?(.*)$")
	private val RULE_RE = Regex("^-{3,}$")
	const val FENCE = "```"

	private val CODE_SPAN_RE = Regex("`([^`]+)`")
	private val BOLD_RE = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
	private val STRIKE_RE = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
	// * only marks italics at the start and end of words: 2*3*4 stays as it is.
	private val ITALIC_RE = Regex("(?<![\\p{L}\\p{N}_*])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![\\p{L}\\p{N}_*])")

	private fun inline(text: String): String {
		val out = StringBuilder()
		var pos = 0
		for (m in CODE_SPAN_RE.findAll(text)) {
			out.append(emphasis(text.substring(pos, m.range.first)))
			out.append("<code>").append(HtmlTokenizer.escape(m.groupValues[1])).append("</code>")
			pos = m.range.last + 1
		}
		out.append(emphasis(text.substring(pos)))
		return out.toString()
	}

	private fun emphasis(text: String): String {
		var t = HtmlTokenizer.escape(text)
		t = BOLD_RE.replace(t, "<strong>$1</strong>")
		t = STRIKE_RE.replace(t, "<s>$1</s>")
		return ITALIC_RE.replace(t, "<em>$1</em>")
	}

	/** Light markup (what the editor shows) -> HTML (what is stored). */
	fun textToHtml(text: String?): String {
		if (text.isNullOrEmpty()) return ""
		val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")
		val out = StringBuilder()
		var i = 0
		while (i < lines.size) {
			val line = lines[i]
			if (line.trim() == FENCE) {
				val code = ArrayList<String>()
				i++
				while (i < lines.size && lines[i].trim() != FENCE) {
					code.add(lines[i])
					i++
				}
				i++
				out.append("<pre><code>").append(HtmlTokenizer.escape(code.joinToString("\n"))).append("</code></pre>")
				continue
			}
			if (RULE_RE.matches(line.trim())) {
				out.append("<hr>")
				i++
				continue
			}
			val h = HEADING_RE.find(line)
			if (h != null) {
				val level = h.groupValues[1].length
				out.append("<h$level>").append(inline(h.groupValues[2])).append("</h$level>")
				i++
				continue
			}
			if (BULLET_RE.matches(line)) {
				out.append("<ul>")
				while (i < lines.size) {
					val m = BULLET_RE.find(lines[i]) ?: break
					out.append("<li><p>").append(inline(m.groupValues[1])).append("</p></li>")
					i++
				}
				out.append("</ul>")
				continue
			}
			val o = ORDERED_RE.find(line)
			if (o != null) {
				val start = o.groupValues[1].toInt()
				out.append(if (start != 1) "<ol start=\"$start\">" else "<ol>")
				while (i < lines.size) {
					val m = ORDERED_RE.find(lines[i]) ?: break
					out.append("<li><p>").append(inline(m.groupValues[2])).append("</p></li>")
					i++
				}
				out.append("</ol>")
				continue
			}
			if (QUOTE_RE.matches(line)) {
				out.append("<blockquote>")
				while (i < lines.size) {
					val m = QUOTE_RE.find(lines[i]) ?: break
					val body = m.groupValues[1]
					out.append(if (body.isNotEmpty()) "<p>${inline(body)}</p>" else "<p></p>")
					i++
				}
				out.append("</blockquote>")
				continue
			}
			out.append(if (line.isNotEmpty()) "<p>${inline(line)}</p>" else "<p></p>")
			i++
		}
		return out.toString()
	}

	/** Stored HTML -> light markup (plain=false) or text only (plain=true). */
	private class Converter(val plain: Boolean) : HtmlHandler() {
		companion object {
			val MARKS = mapOf("strong" to "**", "b" to "**", "em" to "*", "i" to "*", "s" to "~~", "strike" to "~~", "del" to "~~")
			val SKIP = setOf("script", "style", "template", "head", "title", "iframe", "object", "noscript")
		}

		val lines = ArrayList<String>()
		val buf = ArrayList<String>()
		var prefix = ""
		val lists = ArrayList<IntArray>() // [isOrdered(0/1), counter]
		var quote = 0
		var pre = false
		var skip = 0
		/** Cells already written in the table row (a row becomes one line: "a | b | c"). */
		var cells: Int? = null

		private fun isHeading(tag: String) = tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6'

		fun emit() {
			val q = if (plain) "" else "> ".repeat(quote)
			lines.add(q + prefix + buf.joinToString(""))
			buf.clear()
			prefix = ""
		}

		fun flush() {
			if (buf.isNotEmpty()) emit()
		}

		fun mark(text: String) {
			if (!plain) buf.add(text)
		}

		fun emitRaw(text: String) {
			val q = if (plain) "" else "> ".repeat(quote)
			lines.add(q + text)
		}

		override fun startTag(tag: String, attrs: List<Pair<String, String?>>) {
			if ((tag == "p" || tag == "div") && cells != null) {
				// Paragraphs inside a table cell stay on the line of the row.
				if (buf.isNotEmpty() && buf.last() != " | ") buf.add(" ")
				return
			}
			if (tag in SKIP) {
				skip++
				return
			}
			if (skip > 0) return
			when {
				tag == "p" || tag == "div" -> flush()
				isHeading(tag) -> {
					flush()
					mark("#".repeat(tag[1] - '0') + " ")
				}
				tag == "ul" || tag == "ol" -> {
					flush()
					var start = 1
					for ((name, value) in attrs) {
						if (name == "start" && value != null && value.isNotEmpty() && value.all { it in '0'..'9' } && value.length < 10) {
							start = value.toInt()
						}
					}
					lists.add(intArrayOf(if (tag == "ol") 1 else 0, start - 1))
				}
				tag == "li" -> {
					flush()
					if (lists.isNotEmpty() && !plain) {
						val ctx = lists.last()
						val indent = "  ".repeat(lists.size - 1)
						if (ctx[0] == 1) {
							ctx[1] += 1
							prefix = "$indent${ctx[1]}. "
						} else {
							prefix = "$indent- "
						}
					}
				}
				tag == "blockquote" -> {
					flush()
					quote++
				}
				tag == "pre" -> {
					flush()
					if (!plain) emitRaw(FENCE)
					pre = true
				}
				tag == "hr" -> {
					flush()
					if (!plain) emitRaw("---")
				}
				tag == "br" -> emit()
				tag == "tr" -> {
					flush()
					cells = 0
				}
				tag == "td" || tag == "th" -> {
					if ((cells ?: 0) > 0) buf.add(" | ")
					cells = (cells ?: 0) + 1
				}
				tag in MARKS -> mark(MARKS.getValue(tag))
				tag == "code" && !pre -> mark("`")
			}
		}

		override fun startEndTag(tag: String, attrs: List<Pair<String, String?>>) {
			startTag(tag, attrs)
			if (tag != "br" && tag != "hr") endTag(tag)
		}

		override fun endTag(tag: String) {
			if (tag in SKIP) {
				skip = maxOf(0, skip - 1)
				return
			}
			if (skip > 0) return
			when {
				tag == "p" || tag == "div" || isHeading(tag) -> if (cells == null) emit()
				tag == "tr" -> {
					flush()
					cells = null
				}
				tag == "li" -> if (buf.isNotEmpty() || prefix.isNotEmpty()) emit()
				tag == "ul" || tag == "ol" -> {
					flush()
					if (lists.isNotEmpty()) lists.removeAt(lists.size - 1)
				}
				tag == "blockquote" -> {
					flush()
					quote = maxOf(0, quote - 1)
				}
				tag == "pre" -> {
					flush()
					if (!plain) emitRaw(FENCE)
					pre = false
				}
				tag in MARKS -> mark(MARKS.getValue(tag))
				tag == "code" && !pre -> mark("`")
			}
		}

		override fun data(text: String) {
			if (skip > 0) return
			if (pre) {
				val parts = text.replace("\r\n", "\n").split("\n")
				parts.forEachIndexed { n, part ->
					if (n > 0) emit()
					buf.add(part)
				}
				return
			}
			buf.add(text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' '))
		}

		fun result(): String {
			flush()
			return lines.joinToString("\n")
		}
	}

	private fun convert(content: String?, plain: Boolean): String {
		if (content.isNullOrEmpty()) return ""
		if ('<' !in content) {
			// Plain text (for example, imported from another tool).
			return HtmlTokenizer.unescape(content).replace("\r\n", "\n")
		}
		return try {
			val conv = Converter(plain)
			HtmlTokenizer.feed(content, conv)
			conv.result()
		} catch (e: Exception) {
			content.replace(Regex("<[^>]*>"), " ")
		}
	}

	/** Stored HTML -> light markup (shown in the editor). */
	fun htmlToText(content: String?): String = convert(content, plain = false)

	/** Only the words of the note, without markup (search, preview). */
	fun plainText(content: String?): String = convert(content, plain = true)

	/** One line with the first characters of the note. */
	fun preview(content: String?, limit: Int = 100): String {
		val text = plainText(content).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
		return text.take(limit)
	}

	// -- formatting of the editor ------------------------------------------------------------

	val INLINE_MARKS = mapOf("bold" to "**", "italic" to "*", "strike" to "~~", "code" to "`")
	val LINE_PREFIXES = mapOf("heading2" to "## ", "heading3" to "### ", "bullet" to "- ", "ordered" to "1. ", "quote" to "> ")
	private val LINE_PREFIX_RE = mapOf(
		"heading2" to Regex("^## "),
		"heading3" to Regex("^### "),
		"bullet" to Regex("^[-*] "),
		"ordered" to Regex("^\\d{1,9}\\. "),
		"quote" to Regex("^> ?"),
	)
	private val ANY_HEADING_RE = Regex("^#{1,6} ")
	val FORMATS = listOf("bold", "italic", "strike", "code", "heading2", "heading3", "bullet", "ordered", "quote", "divider")

	/** Result of applyFormat: the new text and selection; applied is false when the format was removed. */
	data class Formatted(val text: String, val start: Int, val end: Int, val applied: Boolean)

	/** Applies (or removes) a format to the selection [start, end) of text. */
	fun applyFormat(text: String, start: Int, end: Int, kind: String): Formatted {
		val a = start.coerceIn(0, text.length)
		val b = end.coerceIn(0, text.length)
		val s = minOf(a, b)
		val e = maxOf(a, b)
		INLINE_MARKS[kind]?.let { return inlineFormat(text, s, e, it) }
		if (kind in LINE_PREFIXES) return lineFormat(text, s, e, kind)
		if (kind == "divider") {
			val before = if (s == 0 || text[s - 1] == '\n') "" else "\n"
			val after = if (e < text.length && text[e] == '\n') "" else "\n"
			val insert = before + "---" + after
			val pos = s + insert.length
			return Formatted(text.substring(0, s) + insert + text.substring(e), pos, pos, true)
		}
		throw IllegalArgumentException(kind)
	}

	private fun inlineFormat(text: String, start: Int, end: Int, mark: String): Formatted {
		val size = mark.length
		if (start == end) {
			val new = text.substring(0, start) + mark + mark + text.substring(end)
			return Formatted(new, start + size, start + size, true)
		}
		val sel = text.substring(start, end)
		if (sel.length > 2 * size && sel.startsWith(mark) && sel.endsWith(mark)) {
			val inner = sel.substring(size, sel.length - size)
			return Formatted(text.substring(0, start) + inner + text.substring(end), start, start + inner.length, false)
		}
		if (start >= size && text.substring(start - size, start) == mark && text.startsWith(mark, end)) {
			return Formatted(
				text.substring(0, start - size) + sel + text.substring(end + size), start - size, end - size, false,
			)
		}
		val new = text.substring(0, start) + mark + sel + mark + text.substring(end)
		return Formatted(new, start + size, end + size, true)
	}

	private fun lineFormat(text: String, start: Int, end: Int, kind: String): Formatted {
		val lineStart = text.lastIndexOf('\n', maxOf(0, start - 1)).let { if (start == 0) 0 else it + 1 }
		var lineEnd = text.indexOf('\n', end)
		if (lineEnd == -1) lineEnd = text.length
		val lines = text.substring(lineStart, lineEnd).split("\n")
		val pattern = LINE_PREFIX_RE.getValue(kind)
		val remove = lines.all { pattern.containsMatchIn(it) }
		val out = ArrayList<String>()
		lines.forEachIndexed { n, raw ->
			var line = raw
			if (remove) {
				out.add(pattern.replaceFirst(line, ""))
				return@forEachIndexed
			}
			if (kind.startsWith("heading")) {
				line = ANY_HEADING_RE.replaceFirst(line, "")
			} else if (pattern.containsMatchIn(line)) {
				line = pattern.replaceFirst(line, "")
			}
			val prefix = if (kind == "ordered") "${n + 1}. " else LINE_PREFIXES.getValue(kind)
			out.add(prefix + line)
		}
		val segment = out.joinToString("\n")
		val new = text.substring(0, lineStart) + segment + text.substring(lineEnd)
		if (start == end) {
			val pos = lineStart + out[0].length
			return Formatted(new, pos, pos, !remove)
		}
		return Formatted(new, lineStart, lineStart + segment.length, !remove)
	}
}
