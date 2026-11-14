// NotasWorkflow Android - Json.kt
// A small JSON reader and writer (no library needed, the same on Android and on the JVM).
// Objects are LinkedHashMap<String, Any?>, arrays ArrayList<Any?>, numbers Long or Double.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.core

class JsonException(message: String) : Exception(message)

object Json {
	/** Deepest nesting accepted: a hostile file with thousands of nested lists is refused. */
	const val MAX_DEPTH = 200

	fun parse(text: String): Any? {
		val reader = Reader(text)
		reader.skipSpaces()
		val value = reader.value(0)
		reader.skipSpaces()
		if (reader.pos != text.length) throw JsonException("extra data")
		return value
	}

	private class Reader(val s: String) {
		var pos = 0

		fun skipSpaces() {
			while (pos < s.length) {
				val c = s[pos]
				if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') pos++ else break
			}
		}

		fun value(depth: Int): Any? {
			if (depth > MAX_DEPTH) throw JsonException("too deeply nested")
			if (pos >= s.length) throw JsonException("unexpected end")
			return when (val c = s[pos]) {
				'{' -> obj(depth)
				'[' -> array(depth)
				'"' -> string()
				't' -> literal("true", true)
				'f' -> literal("false", false)
				'n' -> literal("null", null)
				else -> if (c == '-' || c in '0'..'9') number() else throw JsonException("unexpected character")
			}
		}

		fun literal(word: String, result: Any?): Any? {
			if (!s.startsWith(word, pos)) throw JsonException("bad literal")
			pos += word.length
			return result
		}

		fun obj(depth: Int): Map<String, Any?> {
			pos++
			val out = LinkedHashMap<String, Any?>()
			skipSpaces()
			if (pos < s.length && s[pos] == '}') {
				pos++
				return out
			}
			while (true) {
				skipSpaces()
				if (pos >= s.length || s[pos] != '"') throw JsonException("expected key")
				val key = string()
				skipSpaces()
				if (pos >= s.length || s[pos] != ':') throw JsonException("expected colon")
				pos++
				skipSpaces()
				out[key] = value(depth + 1)
				skipSpaces()
				if (pos >= s.length) throw JsonException("unexpected end")
				when (s[pos]) {
					',' -> pos++
					'}' -> {
						pos++
						return out
					}
					else -> throw JsonException("expected comma")
				}
			}
		}

		fun array(depth: Int): List<Any?> {
			pos++
			val out = ArrayList<Any?>()
			skipSpaces()
			if (pos < s.length && s[pos] == ']') {
				pos++
				return out
			}
			while (true) {
				skipSpaces()
				out.add(value(depth + 1))
				skipSpaces()
				if (pos >= s.length) throw JsonException("unexpected end")
				when (s[pos]) {
					',' -> pos++
					']' -> {
						pos++
						return out
					}
					else -> throw JsonException("expected comma")
				}
			}
		}

		fun string(): String {
			pos++
			val sb = StringBuilder()
			while (true) {
				if (pos >= s.length) throw JsonException("unterminated string")
				val c = s[pos++]
				when {
					c == '"' -> return sb.toString()
					c == '\\' -> {
						if (pos >= s.length) throw JsonException("bad escape")
						when (val e = s[pos++]) {
							'"' -> sb.append('"')
							'\\' -> sb.append('\\')
							'/' -> sb.append('/')
							'b' -> sb.append('\b')
							'f' -> sb.append('\u000C')
							'n' -> sb.append('\n')
							'r' -> sb.append('\r')
							't' -> sb.append('\t')
							'u' -> {
								if (pos + 4 > s.length) throw JsonException("bad escape")
								val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: throw JsonException("bad escape")
								sb.append(code.toChar())
								pos += 4
							}
							else -> throw JsonException("bad escape $e")
						}
					}
					c < ' ' -> throw JsonException("control character in string")
					else -> sb.append(c)
				}
			}
		}

		fun number(): Any {
			val start = pos
			if (s[pos] == '-') pos++
			while (pos < s.length && s[pos] in "0123456789+-.eE") pos++
			val text = s.substring(start, pos)
			if (text.none { it == '.' || it == 'e' || it == 'E' }) {
				text.toLongOrNull()?.let { return it }
			}
			return text.toDoubleOrNull() ?: throw JsonException("bad number")
		}
	}

	// -- writing --------------------------------------------------------------------------------

	fun write(value: Any?, out: Appendable) {
		when (value) {
			null -> out.append("null")
			is String -> quote(value, out)
			is Boolean -> out.append(if (value) "true" else "false")
			is Int, is Long -> out.append(value.toString())
			is Number -> {
				val d = value.toDouble()
				out.append(if (d.isFinite()) d.toString() else "null")
			}
			is Map<*, *> -> {
				out.append('{')
				var first = true
				for ((k, v) in value) {
					if (!first) out.append(',')
					first = false
					quote(k.toString(), out)
					out.append(':')
					write(v, out)
				}
				out.append('}')
			}
			is Iterable<*> -> {
				out.append('[')
				var first = true
				for (v in value) {
					if (!first) out.append(',')
					first = false
					write(v, out)
				}
				out.append(']')
			}
			else -> quote(value.toString(), out)
		}
	}

	fun stringify(value: Any?): String {
		val sb = StringBuilder()
		write(value, sb)
		return sb.toString()
	}

	fun quote(text: String, out: Appendable) {
		out.append('"')
		for (c in text) {
			when {
				c == '"' -> out.append("\\\"")
				c == '\\' -> out.append("\\\\")
				c == '\n' -> out.append("\\n")
				c == '\r' -> out.append("\\r")
				c == '\t' -> out.append("\\t")
				c < ' ' || c == ' ' || c == ' ' -> out.append(String.format("\\u%04x", c.code))
				else -> out.append(c)
			}
		}
		out.append('"')
	}
}
