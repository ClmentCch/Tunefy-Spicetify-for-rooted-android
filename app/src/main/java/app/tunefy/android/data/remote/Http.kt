package app.tunefy.android.data.remote

import app.tunefy.android.core.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun request(url: String): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", Constants.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .build()

    @Throws(IOException::class)
    fun getText(url: String): String {
        client.newCall(request(url)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Réponse vide")
        }
    }

    fun contentLength(url: String): Long? = try {
        client.newCall(
            Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", Constants.USER_AGENT)
                .build()
        ).execute().use { response ->
            when {
                !response.isSuccessful -> null
                response.header("Content-Length") != null -> response.header("Content-Length")?.toLongOrNull()
                else -> null
            }
        }
    } catch (_: Exception) {
        null
    }
}
