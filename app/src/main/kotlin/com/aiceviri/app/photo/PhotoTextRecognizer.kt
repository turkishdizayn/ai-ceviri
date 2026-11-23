package com.aiceviri.app.photo

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fotoğraftaki yazıyı cihaz üzerinde okur (Google ML Kit, internet gerektirmez).
 * Latin alfabeli dilleri (Türkçe, İngilizce, Almanca, Fransızca, İspanyolca…) destekler.
 */
class PhotoTextRecognizer(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri): String {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    // Satırları blok blok birleştir: tabela/menü yapısı korunur
                    val text = result.textBlocks.joinToString("\n") { block -> block.text }
                    if (cont.isActive) cont.resume(text.trim())
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    fun close() = recognizer.close()
}
