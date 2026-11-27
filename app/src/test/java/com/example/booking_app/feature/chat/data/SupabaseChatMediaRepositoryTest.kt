package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.feature.chat.data.dto.ChatMediaResponse
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatLocalMediaStore
import com.example.booking_app.feature.chat.domain.model.UploadState
import io.github.jan.supabase.exceptions.HttpRequestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit test trực tiếp cho [SupabaseChatMediaRepository] (review Argon US4 #1, #2, #5). */
class SupabaseChatMediaRepositoryTest {

    private val imageCompressor = mockk<ImageCompressor>()
    private val storageUploader = mockk<ChatStorageUploader>(relaxed = true)
    private val mediaApi = mockk<ChatMediaUrlApi>()
    private val precompressed = mutableMapOf<String, ByteArray>()
    private val localMediaStore = object : ChatLocalMediaStore {
        override suspend fun saveCompressed(messageId: String, jpegBytes: ByteArray): String {
            val uri = "file:///cache/chat/outbox_$messageId.jpg"
            precompressed[uri] = jpegBytes
            return uri
        }
        override suspend fun readPrecompressed(uri: String): ByteArray? = precompressed[uri]
        override fun deleteIfOwned(uri: String?) {
            if (uri != null) precompressed.remove(uri)
        }
    }

    private fun createRepo() = SupabaseChatMediaRepository(
        imageCompressor = imageCompressor,
        localMediaStore = localMediaStore,
        storageUploader = storageUploader,
        mediaApi = mediaApi,
    )

    @Test
    fun `anh qua 2MB hoac khong doc duoc phat Failed dung ChatError`() = runTest {
        val repo = createRepo()

        coEvery { imageCompressor.compress("content://huge") } throws ImageTooLargeException()
        val largeStates = repo.upload("c1", "m1", "content://huge").toList()
        assertEquals(UploadState.Uploading, largeStates[0])
        assertTrue((largeStates[1] as UploadState.Failed).error is ChatError.ImageTooLarge)

        coEvery { imageCompressor.compress("content://bad") } throws ImageUnreadableException()
        val badStates = repo.upload("c1", "m2", "content://bad").toList()
        assertEquals(UploadState.Uploading, badStates[0])
        assertTrue((badStates[1] as UploadState.Failed).error is ChatError.ImageUnreadable)
    }

    @Test
    fun `mediaApi tra token rong hoac loi phat Failed`() = runTest {
        val repo = createRepo()
        coEvery { imageCompressor.compress("content://ok") } returns ByteArray(512)
        coEvery { mediaApi.invoke(any()) } returns ChatMediaResponse(
            path = "c1/m1.jpg",
            signedUrl = "https://storage/sign",
            token = "",
        )

        val states = repo.upload("c1", "m1", "content://ok").toList()
        assertEquals(UploadState.Uploading, states[0])
        assertTrue((states[1] as UploadState.Failed).error is ChatError.Unknown)
    }

    @Test
    fun `uploadToSignedUrl thanh cong va bo qua loi Expected an id cua supabase-kt 3_6_0`() = runTest {
        val repo = createRepo()
        val jpeg = ByteArray(1024) { 7 }
        coEvery { imageCompressor.compress("content://ok") } returns jpeg
        coEvery { mediaApi.invoke(any()) } returns ChatMediaResponse(
            path = "c1/m1.jpg",
            signedUrl = "https://storage/sign",
            token = "tok-1",
        )

        // Trường hợp thành công bình thường.
        val okStates = repo.upload("c1", "m1", "content://ok").toList()
        assertEquals(listOf(UploadState.Uploading, UploadState.Uploaded("c1/m1.jpg")), okStates)

        // Trường hợp supabase-kt 3.6.0 ném IllegalStateException("Expected an id in a upload response") sau khi PUT 200.
        coEvery { storageUploader.uploadToSignedUrl("chat-media", "c1/m1.jpg", "tok-1", jpeg) } throws
            IllegalStateException("Expected an id in a upload response")
        val compatStates = repo.upload("c1", "m1", "content://ok").toList()
        assertEquals(listOf(UploadState.Uploading, UploadState.Uploaded("c1/m1.jpg")), compatStates)
    }

    @Test
    fun `loi mang HttpRequestException khi upload phat ChatError Network retryable`() = runTest {
        val repo = createRepo()
        val jpeg = ByteArray(256)
        coEvery { imageCompressor.compress("content://ok") } returns jpeg
        coEvery { mediaApi.invoke(any()) } returns ChatMediaResponse(
            path = "c1/m1.jpg",
            signedUrl = "https://storage/sign",
            token = "tok-1",
        )
        coEvery { storageUploader.uploadToSignedUrl(any(), any(), any(), any()) } throws
            HttpRequestException("connection reset", mockk(relaxed = true))

        val states = repo.upload("c1", "m1", "content://ok").toList()
        val failed = states[1] as UploadState.Failed
        assertTrue(failed.error is ChatError.Network)
        assertTrue(failed.error.isRetryable)
    }

    @Test
    fun `file da nen san trong ChatLocalMediaStore duoc PUT thang khong nen lai lan 2`() = runTest {
        val repo = createRepo()
        val preBytes = ByteArray(2048) { 3 }
        val savedUri = localMediaStore.saveCompressed("m-pre", preBytes)
        coEvery { mediaApi.invoke(any()) } returns ChatMediaResponse(
            path = "c1/m-pre.jpg",
            signedUrl = "https://storage/sign",
            token = "tok-pre",
        )

        val states = repo.upload("c1", "m-pre", savedUri).toList()
        assertEquals(listOf(UploadState.Uploading, UploadState.Uploaded("c1/m-pre.jpg")), states)
        coVerify(exactly = 0) { imageCompressor.compress(any()) }
        coVerify(exactly = 1) { storageUploader.uploadToSignedUrl("chat-media", "c1/m-pre.jpg", "tok-pre", preBytes) }
    }

    @Test
    fun `signedUrls va discard tra dung ket qua va anh xa loi`() = runTest {
        val repo = createRepo()
        coEvery { mediaApi.invoke(match { it.action == "download" }) } returns ChatMediaResponse(
            urls = mapOf("c1/m1.jpg" to "https://signed/c1/m1.jpg"),
            expiresAt = "2026-11-20T10:00:00Z",
        )
        coEvery { mediaApi.invoke(match { it.action == "discard" }) } returns ChatMediaResponse()

        val dl = repo.signedUrls(listOf("c1/m1.jpg")).getOrThrow()
        assertEquals("https://signed/c1/m1.jpg", dl.urls["c1/m1.jpg"])

        assertTrue(repo.discard("c1", "m1").isSuccess)
    }
}
