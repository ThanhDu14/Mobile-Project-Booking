package com.example.booking_app.feature.owner.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OwnerDocumentTransferTest {
    @Test
    fun expiredOrFailedSignedUrlIsRenewedOnceAndRetried() = runTest {
        var signedUrlRequests = 0
        val fetchedUrls = mutableListOf<String>()

        val bytes = downloadWithFreshSignedUrl(
            signedUrl = {
                signedUrlRequests++
                "https://private.example/${if (signedUrlRequests == 1) "expired" else "fresh"}"
            },
            fetch = { url ->
                fetchedUrls += url
                if (url.endsWith("expired")) error("expired URL")
                byteArrayOf(4, 5, 6)
            },
        )

        assertArrayEquals(byteArrayOf(4, 5, 6), bytes)
        assertEquals(2, signedUrlRequests)
        assertEquals(listOf("https://private.example/expired", "https://private.example/fresh"), fetchedUrls)
    }

    @Test
    fun failedRenewalPropagatesTheRetryErrorAfterExactlyOneRetry() = runTest {
        var signedUrlRequests = 0
        var fetchRequests = 0

        val error = runCatching {
            downloadWithFreshSignedUrl(
                signedUrl = { "https://private.example/${++signedUrlRequests}" },
                fetch = { fetchRequests++; error("still unavailable") },
            )
        }
        val failure = error.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("still unavailable", failure?.message)
        assertEquals(2, signedUrlRequests)
        assertEquals(2, fetchRequests)
    }
}
