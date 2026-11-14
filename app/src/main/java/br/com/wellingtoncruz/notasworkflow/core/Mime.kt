// NotasWorkflow Android - Mime.kt
// Reads an e-mail message (.eml, RFC 5322 with MIME parts): From, To, Cc, Date, the names of the
// attachments and the body (HTML preferred, otherwise plain text). Android has no javax.mail,
// so this is a small reader of its own; it never fails on odd messages, it reads what it can.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

object Mime {
	private const val MAX_DEPTH = 20

	class Part(val headers: List<Pair<String, String>>, val body: ByteArray) {
		fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

		val contentType: String get() = (header("Content-Type") ?: "text/plain").substringBefore(';').trim().lowercase()

		fun param(headerName: String, param: String): String? = Mime.param(header(headerName), param)

		val charset: String? get() = param("Content-Type", "charset")

		val isAttachment: Boolean
			get() = (header("Content-Disposition") ?: "").substringBefore(';').trim().equals("attachment", ignoreCase = true)

		val fileName: String?
			get() = param("Content-Disposition", "filename") ?: param("Content-Type", "name")

		/** The body after the Content-Transfer-Encoding. */
		fun decoded(): ByteArray = when ((header("Content-Transfer-Encoding") ?: "").trim().lowercase()) {
			"base64" -> decodeBase64(body)
			"quoted-printable" -> decodeQuotedPrintable(body, header = false)
			else -> body
		}

		fun text(): String = Importer.decode(decoded(), charset)
	}

	/** Splits the bytes of a message (or of a part) into headers and body. */
	fun parse(data: ByteArray): Part {
		var split = -1
		var bodyStart = data.size
		var i = 0
		while (i < data.size) {
			if (data[i] == '\n'.code.toByte()) {
				if (i + 1 < data.size && data[i + 1] == '\n'.code.toByte()) {
					split = i
					bodyStart = i + 2
					break
				}
				if (i + 2 < data.size && data[i + 1] == '\r'.code.toByte() && data[i + 2] == '\n'.code.toByte()) {
					split = i
					bodyStart = i + 3
					break
				}
			}
			i++
		}
		if (split < 0) split = data.size
		val headText = String(data, 0, split, Charsets.ISO_8859_1)
		val headers = ArrayList<Pair<String, String>>()
		for (line in headText.replace("\r\n", "\n").split("\n")) {
			if (line.isEmpty()) continue
			if ((line[0] == ' ' || line[0] == '\t') && headers.isNotEmpty()) {
				val last = headers.removeAt(headers.size - 1)
				headers.add(Pair(last.first, last.second + " " + line.trim()))
				continue
			}
			val colon = line.indexOf(':')
			if (colon <= 0) continue
			headers.add(Pair(line.substring(0, colon).trim(), line.substring(colon + 1).trim()))
		}
		val body = if (bodyStart < data.size) data.copyOfRange(bodyStart, data.size) else ByteArray(0)
		return Part(headers, body)
	}

	/** The parts of a multipart body (empty when it is not one). */
	fun subparts(part: Part): List<Part> {
		if (!part.contentType.startsWith("multipart/")) return emptyList()
		val boundary = part.param("Content-Type", "boundary") ?: return emptyList()
		val text = String(part.body, Charsets.ISO_8859_1)
		val marker = "--$boundary"
		val out = ArrayList<Part>()
		var pos = text.indexOf(marker)
		while (pos >= 0) {
			var start = pos + marker.length
			if (text.startsWith("--", start)) break
			val lineEnd = text.indexOf('\n', start)
			if (lineEnd < 0) break
			start = lineEnd + 1
			val next = text.indexOf(marker, start)
			var end = if (next < 0) text.length else next
			// The line break before the boundary belongs to the boundary.
			if (end > start && text[end - 1] == '\n') end--
			if (end > start && text[end - 1] == '\r') end--
			out.add(parse(text.substring(start, maxOf(start, end)).toByteArray(Charsets.ISO_8859_1)))
			pos = next
		}
		return out
	}

	/** Value of a parameter of a header (charset, boundary, filename), with RFC 2231 and 2047. */
	fun param(header: String?, name: String): String? {
		if (header == null) return null
		val pieces = splitParams(header)
		var value: String? = null
		var extended: String? = null
		val continued = java.util.TreeMap<Int, String>()
		for (piece in pieces.drop(1)) {
			val eq = piece.indexOf('=')
			if (eq < 0) continue
			val key = piece.substring(0, eq).trim().lowercase()
			var v = piece.substring(eq + 1).trim()
			if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1).replace("\\\"", "\"")
			val lname = name.lowercase()
			when {
				key == lname -> value = v
				key == "$lname*" -> extended = decode2231(v)
				key.startsWith("$lname*") -> {
					val rest = key.removePrefix("$lname*")
					val n = rest.removeSuffix("*").toIntOrNull() ?: continue
					continued[n] = if (rest.endsWith("*")) decode2231(v, n == 0) else v
				}
			}
		}
		if (extended != null) return extended
		if (continued.isNotEmpty()) return continued.values.joinToString("")
		return value?.let { decodeWords(it) }
	}

	private fun splitParams(header: String): List<String> {
		val out = ArrayList<String>()
		val sb = StringBuilder()
		var inQuote = false
		for (c in header) {
			when {
				c == '"' -> {
					inQuote = !inQuote
					sb.append(c)
				}
				c == ';' && !inQuote -> {
					out.add(sb.toString())
					sb.setLength(0)
				}
				else -> sb.append(c)
			}
		}
		out.add(sb.toString())
		return out
	}

	/** charset'lang'percent-encoded (RFC 2231). */
	private fun decode2231(value: String, withCharset: Boolean = true): String {
		var charset = "UTF-8"
		var text = value
		if (withCharset) {
			val parts = value.split("'", limit = 3)
			if (parts.size == 3) {
				if (parts[0].isNotEmpty()) charset = parts[0]
				text = parts[2]
			}
		}
		val bytes = ByteArrayOutputStream()
		var i = 0
		while (i < text.length) {
			val c = text[i]
			if (c == '%' && i + 2 < text.length + 0 && i + 2 <= text.length - 1) {
				val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
				if (hex != null) {
					bytes.write(hex)
					i += 3
					continue
				}
			}
			bytes.write(c.code and 0xFF)
			i++
		}
		return Importer.decode(bytes.toByteArray(), charset)
	}

	private val ENCODED_WORD = Regex("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=")
	private val BETWEEN_WORDS = Regex("(=\\?[^?]+\\?[bBqQ]\\?[^?]*\\?=)\\s+(?==\\?)")

	/** Decodes the encoded words of a header (RFC 2047): =?utf-8?B?...?= */
	fun decodeWords(value: String): String {
		if ("=?" !in value) return value
		val joined = BETWEEN_WORDS.replace(value, "$1")
		return ENCODED_WORD.replace(joined) { m ->
			val charset = m.groupValues[1].substringBefore('*')
			val data = m.groupValues[3]
			try {
				val bytes = if (m.groupValues[2].equals("B", ignoreCase = true)) {
					decodeBase64(data.toByteArray(Charsets.ISO_8859_1))
				} else {
					decodeQuotedPrintable(data.toByteArray(Charsets.ISO_8859_1), header = true)
				}
				String(bytes, Charset.forName(charset))
			} catch (e: Exception) {
				m.value
			}
		}
	}

	fun decodeBase64(data: ByteArray): ByteArray {
		val clean = ByteArrayOutputStream(data.size)
		for (b in data) {
			val c = b.toInt().toChar()
			if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/') clean.write(b.toInt())
		}
		val bytes = clean.toByteArray()
		// Pads what is missing; a broken last group is dropped.
		val usable = bytes.size - bytes.size % 4
		val rest = bytes.size % 4
		val padded = if (rest >= 2) bytes + ByteArray(4 - rest) { '='.code.toByte() } else bytes.copyOf(usable)
		return try {
			Base64.getDecoder().decode(padded)
		} catch (e: IllegalArgumentException) {
			ByteArray(0)
		}
	}

	fun decodeQuotedPrintable(data: ByteArray, header: Boolean): ByteArray {
		val out = ByteArrayOutputStream(data.size)
		var i = 0
		while (i < data.size) {
			val b = data[i].toInt() and 0xFF
			when {
				b == '='.code -> {
					if (i + 1 < data.size && (data[i + 1] == '\n'.code.toByte())) {
						i += 2
						continue
					}
					if (i + 2 < data.size && data[i + 1] == '\r'.code.toByte() && data[i + 2] == '\n'.code.toByte()) {
						i += 3
						continue
					}
					if (i + 2 < data.size) {
						val hex = String(data, i + 1, 2, Charsets.ISO_8859_1).toIntOrNull(16)
						if (hex != null) {
							out.write(hex)
							i += 3
							continue
						}
					}
					out.write(b)
				}
				b == '_'.code && header -> out.write(' '.code)
				else -> out.write(b)
			}
			i++
		}
		return out.toByteArray()
	}

	/** The body to show: text/html first, then text/plain (not attachments), searching the parts. */
	fun body(part: Part, depth: Int = 0): Part? {
		if (depth > MAX_DEPTH) return null
		val type = part.contentType
		if (!type.startsWith("multipart/")) {
			return if (!part.isAttachment && (type == "text/html" || type == "text/plain")) part else null
		}
		val children = subparts(part)
		for (wanted in listOf("text/html", "text/plain")) {
			for (child in children) {
				val found = body(child, depth + 1)
				if (found != null && found.contentType == wanted) return found
			}
		}
		return null
	}

	fun attachments(part: Part, depth: Int = 0, out: MutableList<String> = ArrayList()): MutableList<String> {
		if (depth > MAX_DEPTH) return out
		if (part.contentType.startsWith("multipart/")) {
			for (child in subparts(part)) attachments(child, depth + 1, out)
		} else if (depth > 0) {
			val name = part.fileName
			if (name != null && (part.isAttachment || !part.contentType.startsWith("text/"))) {
				out.add(name.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" "))
			}
		}
		return out
	}

	private fun oneLine(text: String) = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

	fun parseDate(value: String): ZonedDateTime? {
		// "Fri, 13 Nov 2026 10:00:00 -0300 (BRT)": the comment is dropped.
		val text = oneLine(value.replace(Regex("\\([^)]*\\)"), ""))
		return try {
			ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
		} catch (e: Exception) {
			try {
				ZonedDateTime.parse(text, DateTimeFormatter.ofPattern("d MMM yyyy HH:mm[:ss] Z", Locale.ENGLISH))
			} catch (e2: Exception) {
				null
			}
		}
	}

	/** (subject, text) of an e-mail: From, To, Cc, Date and Attachments in bold, a divider, the body. */
	fun readEmail(data: ByteArray, labels: Map<String, String>, formatDate: ((ZonedDateTime) -> String)?): Pair<String, String> {
		val message = parse(data)
		val lines = ArrayList<String>()
		for ((key, header) in listOf("from" to "From", "to" to "To", "cc" to "Cc")) {
			val value = message.header(header)
			if (!value.isNullOrBlank()) lines.add("**${labels.getValue(key)}** ${oneLine(decodeWords(value))}")
		}
		val date = message.header("Date")
		if (!date.isNullOrBlank()) {
			val parsed = parseDate(date)
			val shown = if (parsed == null) date else formatDate?.invoke(parsed) ?: parsed.toString()
			lines.add("**${labels.getValue("date")}** $shown")
		}
		val attached = attachments(message)
		if (attached.isNotEmpty()) lines.add("**${labels.getValue("attachments")}** ${attached.joinToString(", ")}")
		val body = body(message)
		if (body != null) {
			val content = body.text()
			if (lines.isNotEmpty()) lines.add("---")
			lines.add(if (body.contentType == "text/html") Markup.htmlToText(content) else content)
		}
		val subject = message.header("Subject")
		return Pair(if (subject != null) oneLine(decodeWords(subject)) else "", lines.joinToString("\n"))
	}
}
