package com.aiceviri.core.model

/** Uygulama genelindeki hata türleri; UI bunları anlaşılır Türkçe mesaja çevirir. */
sealed class CeviriException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ApiKeyMissingException : CeviriException("API anahtarı ayarlanmamış")

class ApiHttpException(val code: Int, val body: String) :
    CeviriException("Servis hatası (HTTP $code): ${body.take(300)}")

class NetworkException(cause: Throwable) : CeviriException("Ağ hatası: ${cause.message}", cause)

class TranslationParseException(message: String) : CeviriException(message)

fun Throwable.userMessage(): String = when (this) {
    is ApiKeyMissingException -> "Ayarlar'dan API anahtarınızı girin."
    is ApiHttpException -> when (code) {
        401 -> "API anahtarı geçersiz (401)."
        429 -> "İstek limiti aşıldı ya da bakiye yetersiz (429)."
        in 500..599 -> "Servis geçici olarak yanıt vermiyor ($code)."
        else -> "Servis hatası ($code)."
    }
    is NetworkException -> "İnternet bağlantısı yok veya zayıf."
    is TranslationParseException -> "Çeviri cevabı okunamadı, tekrar deneyin."
    else -> message ?: "Bilinmeyen hata"
}
