package com.aiceviri.app.net

import com.aiceviri.core.model.NetworkException
import com.aiceviri.core.openai.HttpResponse
import com.aiceviri.core.openai.HttpTransport
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : HttpTransport {

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
        contentType: String,
    ): HttpResponse = suspendCancellableCoroutine { cont ->
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody(contentType.toMediaType()))
            .build()
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(NetworkException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val bytes = it.body?.bytes() ?: ByteArray(0)
                        if (cont.isActive) cont.resume(HttpResponse(it.code, bytes))
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(NetworkException(e))
                    }
                }
            }
        })
    }
}
