package com.aiceviri.core.model

import com.aiceviri.core.util.Json
import com.aiceviri.core.util.path

/** Uygulama genelindeki hata türleri; UI bunları anlaşılır Türkçe mesaja çevirir. */
sealed class CeviriException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ApiKeyMissingException : CeviriException("API anahtarı ayarlanmamış")

/**
 * @param step Hatanın oluştuğu adım: "Konuşma tanıma", "Çeviri", "Seslendirme".
 */
class ApiHttpException(val code: Int, val body: String, val step: String = "") :
    CeviriException("Servis hatası (HTTP $code): ${body.take(300)}") {

    /** OpenAI uyumlu servislerin {"error":{"message":"..."}} açıklaması. */
    val serviceMessage: String?
        get() = runCatching { Json.parse(body).path("error", "message") as? String }.getOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
}

class NetworkException(cause: Throwable) : CeviriException("Ağ hatası: ${cause.message}", cause)

class TranslationParseException(message: String) : CeviriException(message)

fun Throwable.userMessage(): String = when (this) {
    is ApiKeyMissingException -> "Ayarlar'dan API anahtarınızı girin."
    is ApiHttpException -> {
        val where = if (step.isNotEmpty()) "$step: " else ""
        val base = when (code) {
            401 -> "API anahtarı geçersiz (401)."
            403 -> "Bu modele veya servise erişim izniniz yok (403)."
            404 -> "Model veya servis adresi bulunamadı (404). Ayarlar'daki model adını kontrol edin."
            429 -> "İstek limiti aşıldı ya da bakiye yetersiz (429)."
            in 500..599 -> "Servis geçici olarak yanıt vermiyor ($code)."
            else -> "Servis hatası ($code)."
        }
        val detail = serviceMessage?.let { "\n$it" } ?: ""
        where + base + detail
    }
    is NetworkException -> "İnternet bağlantısı yok veya zayıf."
    is TranslationParseException -> "Çeviri cevabı okunamadı, tekrar deneyin."
    else -> message ?: "Bilinmeyen hata"
}
