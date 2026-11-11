package com.example.booking_app.feature.owner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fetches a private document with one fresh signed-URL retry; URLs are kept only in call scope. */
internal suspend fun downloadWithFreshSignedUrl(
    signedUrl: suspend () -> String,
    fetch: suspend (String) -> ByteArray = ::fetchSignedDocument,
): ByteArray {
    val firstUrl = signedUrl()
    return try {
        fetch(firstUrl)
    } catch (firstFailure: Exception) {
        val freshUrl = signedUrl()
        try {
            fetch(freshUrl)
        } catch (retryFailure: Exception) {
            retryFailure.addSuppressed(firstFailure)
            throw retryFailure
        }
    }
}

private suspend fun fetchSignedDocument(signedUrl: String): ByteArray = withContext(Dispatchers.IO) {
    val connection = URL(signedUrl).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("Signed document request failed")
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var totalBytes = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                totalBytes += count
                if (totalBytes > MAX_DOCUMENT_BYTES) throw IOException("Signed document exceeds size limit")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}

private const val MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
