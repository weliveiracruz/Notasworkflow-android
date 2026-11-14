// NotasWorkflow Android - HtmlTokenizer.kt
// A small, forgiving HTML tokenizer (like Python's html.parser): start tags, end tags and text,
// with character references already decoded. It never runs anything and never fails: what it
// cannot read is given as text. The text of script and style is raw (not read as tags).
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

abstract class HtmlHandler {
	open fun startTag(tag: String, attrs: List<Pair<String, String?>>) {}
	open fun endTag(tag: String) {}
	open fun data(text: String) {}

	/** <br/>: start then end, as html.parser does. */
	open fun startEndTag(tag: String, attrs: List<Pair<String, String?>>) {
		startTag(tag, attrs)
		endTag(tag)
	}
}

object HtmlTokenizer {
	private val RAW_TEXT = setOf("script", "style")
	private val TAG_NAME = Regex("[a-zA-Z][^\\s/>\\x00]*")
	private val ATTR = Regex("""([^\s/>"'=][^\s/>=]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?""")
	private val CHARREF = Regex("&(#[0-9]{1,8};?|#[xX][0-9a-fA-F]{1,8};?|[A-Za-z][A-Za-z0-9]{0,31};?)")

	fun feed(html: String, handler: HtmlHandler) {
		val n = html.length
		var pos = 0
		val text = StringBuilder()

		fun flushText() {
			if (text.isNotEmpty()) {
				handler.data(unescape(text.toString()))
				text.setLength(0)
			}
		}

		while (pos < n) {
			val lt = html.indexOf('<', pos)
			if (lt < 0) {
				text.append(html, pos, n)
				break
			}
			text.append(html, pos, lt)
			pos = lt
			when {
				html.startsWith("<!--", pos) -> {
					flushText()
					val end = html.indexOf("-->", pos + 4)
					pos = if (end < 0) n else end + 3
				}
				html.startsWith("<!", pos) || html.startsWith("<?", pos) -> {
					flushText()
					val end = html.indexOf('>', pos + 2)
					pos = if (end < 0) n else end + 1
				}
				html.startsWith("</", pos) -> {
					val m = TAG_NAME.matchAt(html, pos + 2)
					if (m == null) {
						// "</ " or "</>": not a tag, as html.parser.
						val end = html.indexOf('>', pos + 2)
						if (end < 0) {
							text.append(html, pos, n)
							pos = n
						} else {
							flushText()
							pos = end + 1
						}
						continue
					}
					flushText()
					val end = html.indexOf('>', m.range.last + 1)
					handler.endTag(m.value.lowercase())
					pos = if (end < 0) n else end + 1
				}
				else -> {
					val m = TAG_NAME.matchAt(html, pos + 1)
					if (m == null) {
						text.append('<')
						pos++
						continue
					}
					val tagEnd = findTagEnd(html, m.range.last + 1)
					if (tagEnd < 0) {
						// An unfinished tag at the end: kept as text.
						text.append(html, pos, n)
						pos = n
						continue
					}
					flushText()
					val tag = m.value.lowercase()
					var inside = html.substring(m.range.last + 1, tagEnd)
					val selfClosing = inside.trimEnd().endsWith("/")
					if (selfClosing) inside = inside.trimEnd().dropLast(1)
					val attrs = ArrayList<Pair<String, String?>>()
					for (a in ATTR.findAll(inside)) {
						val value = a.groups[2]?.value ?: a.groups[3]?.value ?: a.groups[4]?.value
						attrs.add(a.groupValues[1].lowercase() to value?.let { unescape(it) })
					}
					pos = tagEnd + 1
					if (selfClosing) {
						handler.startEndTag(tag, attrs)
					} else {
						handler.startTag(tag, attrs)
						if (tag in RAW_TEXT) {
							val close = Regex("</$tag\\s*>", RegexOption.IGNORE_CASE).find(html, pos)
							val rawEnd = close?.range?.first ?: n
							if (rawEnd > pos) handler.data(html.substring(pos, rawEnd))
							if (close != null) handler.endTag(tag)
							pos = close?.range?.last?.plus(1) ?: n
						}
					}
				}
			}
		}
		flushText()
	}

	/** Index of the ">" that ends a tag, skipping quoted attribute values, or -1. */
	private fun findTagEnd(html: String, from: Int): Int {
		var i = from
		var quote = 0.toChar()
		while (i < html.length) {
			val c = html[i]
			if (quote != 0.toChar()) {
				if (c == quote) quote = 0.toChar()
			} else if (c == '"' || c == '\'') {
				// Only a quote after "=" starts a value.
				var k = i - 1
				while (k >= from && html[k].isWhitespace()) k--
				if (k >= from && html[k] == '=') quote = c
			} else if (c == '>') {
				return i
			}
			i++
		}
		return -1
	}

	/** Decodes character references (&amp; &#233; &eacute;). */
	fun unescape(text: String): String {
		if ('&' !in text) return text
		return CHARREF.replace(text) { m ->
			val ref = m.groupValues[1]
			if (ref.startsWith("#")) {
				val body = ref.removePrefix("#").removeSuffix(";")
				val code = if (body.startsWith("x") || body.startsWith("X")) body.substring(1).toIntOrNull(16) else body.toIntOrNull()
				when {
					code == null -> m.value
					code == 0 || code in 0xD800..0xDFFF || code > 0x10FFFF -> "�"
					else -> String(Character.toChars(code))
				}
			} else {
				val name = ref.removeSuffix(";")
				val code = Entities.NAMES[name]
				if (code != null) String(Character.toChars(code)) else m.value
			}
		}
	}

	/** Escapes text for HTML (quote=false: only &, < and >). */
	fun escape(text: String, quote: Boolean = false): String {
		val sb = StringBuilder(text.length + 16)
		for (c in text) {
			when (c) {
				'&' -> sb.append("&amp;")
				'<' -> sb.append("&lt;")
				'>' -> sb.append("&gt;")
				'"' -> if (quote) sb.append("&quot;") else sb.append(c)
				'\'' -> if (quote) sb.append("&#x27;") else sb.append(c)
				else -> sb.append(c)
			}
		}
		return sb.toString()
	}
}
