package com.aiceviri.core.util

/**
 * Bağımlılıksız, küçük JSON yardımcıları.
 * Çekirdek modül saf Kotlin kalsın ve her yerde test edilebilsin diye kullanılır.
 *
 * Ayrıştırma sonucu: Map<String, Any?>, List<Any?>, String, Double, Long, Boolean veya null.
 */
object Json {

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> quote(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> sb.append(if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString())
            is Float -> write(sb, value.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, value.asList())
            else -> quote(sb, value.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.readValue()
        p.skipWs()
        if (!p.atEnd()) throw JsonException("Beklenmeyen karakter, konum ${p.pos}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonException("JSON nesnesi bekleniyordu")

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            if (atEnd()) throw JsonException("Beklenmeyen son")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw JsonException("Geçersiz karakter '$c', konum $pos")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Geçersiz değer, konum $pos")
            pos += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (s.getOrNull(pos) == '}') { pos++; return map }
            while (true) {
                skipWs()
                if (s.getOrNull(pos) != '"') throw JsonException("Anahtar bekleniyordu, konum $pos")
                val key = readString()
                skipWs()
                if (s.getOrNull(pos) != ':') throw JsonException("':' bekleniyordu, konum $pos")
                pos++
                skipWs()
                map[key] = readValue()
                skipWs()
                when (s.getOrNull(pos)) {
                    ',' -> pos++
                    '}' -> { pos++; return map }
                    else -> throw JsonException("',' veya '}' bekleniyordu, konum $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (s.getOrNull(pos) == ']') { pos++; return list }
            while (true) {
                skipWs()
                list.add(readValue())
                skipWs()
                when (s.getOrNull(pos)) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> throw JsonException("',' veya ']' bekleniyordu, konum $pos")
                }
            }
        }

        private fun readString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Kapanmamış metin")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Kapanmamış kaçış dizisi")
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
                                if (pos + 4 > s.length) throw JsonException("Eksik \\u dizisi")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Geçersiz kaçış '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val str = s.substring(start, pos)
            return if (str.any { it in ".eE" }) {
                str.toDoubleOrNull() ?: throw JsonException("Geçersiz sayı '$str'")
            } else {
                str.toLongOrNull() ?: str.toDoubleOrNull() ?: throw JsonException("Geçersiz sayı '$str'")
            }
        }
    }
}

class JsonException(message: String) : RuntimeException(message)

/** Map içinde güvenli yol takibi: json.path("choices", 0, "message", "content") */
fun Any?.path(vararg keys: Any): Any? {
    var cur: Any? = this
    for (k in keys) {
        cur = when {
            k is String && cur is Map<*, *> -> cur[k]
            k is Int && cur is List<*> -> cur.getOrNull(k)
            else -> return null
        }
    }
    return cur
}
