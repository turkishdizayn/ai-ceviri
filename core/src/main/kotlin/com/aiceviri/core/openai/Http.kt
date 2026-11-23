package com.aiceviri.core.openai

import java.io.ByteArrayOutputStream
import java.util.UUID

class HttpResponse(val code: Int, val body: ByteArray) {
    val isSuccessful: Boolean get() = code in 200..299
    fun bodyText(): String = body.toString(Charsets.UTF_8)
}

/**
 * Ağ katmanı soyutlaması. Android'de OkHttp ile uygulanır; testlerde sahte
 * (fake) bir uygulama kullanılır. IO hataları NetworkException olarak fırlatılmalıdır.
 */
interface HttpTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: ByteArray, contentType: String): HttpResponse
}

/** multipart/form-data gövdesi oluşturucu (dosya yükleme için). */
class MultipartBuilder(val boundary: String = "----AiCeviri" + UUID.randomUUID().toString().replace("-", "")) {
    private val out = ByteArrayOutputStream()

    val contentType: String get() = "multipart/form-data; boundary=$boundary"

    fun field(name: String, value: String): MultipartBuilder {
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"$name\"")
        line("")
        line(value)
        return this
    }

    fun file(name: String, fileName: String, mimeType: String, bytes: ByteArray): MultipartBuilder {
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"")
        line("Content-Type: $mimeType")
        line("")
        out.write(bytes)
        line("")
        return this
    }

    fun build(): ByteArray {
        line("--$boundary--")
        return out.toByteArray()
    }

    private fun line(s: String) {
        out.write(s.toByteArray(Charsets.UTF_8))
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
    }
}
