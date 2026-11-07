package com.example.booking_app.feature.review.data

import com.example.booking_app.feature.review.data.dto.CreateReviewRequest
import com.example.booking_app.feature.review.data.dto.DeleteReviewRequest
import com.example.booking_app.feature.review.data.dto.DeleteReviewResponse
import com.example.booking_app.feature.review.data.dto.RatingsDto
import com.example.booking_app.feature.review.data.dto.ReplyReviewRequest
import com.example.booking_app.feature.review.data.dto.ReplyReviewResponse
import com.example.booking_app.feature.review.data.dto.ReviewFunctionJson
import com.example.booking_app.feature.review.data.dto.ReviewWriteResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Body và response theo contracts/edge-function-review.md. */
class ReviewFunctionDtosTest {

    private val json = ReviewFunctionJson

    private inline fun <reified T> encode(value: T): JsonObject =
        Json.parseToJsonElement(json.encodeToString(value)).jsonObject

    @Test
    fun `create - body co action va du truong`() {
        val body = encode(
            CreateReviewRequest(
                bookingId = "b1",
                ratings = RatingsDto(court = 5, cleanliness = 4, service = 3),
                comment = "Sân sạch",
                photoPaths = listOf("u1/b1/a.jpg"),
            ),
        )
        assertEquals("create", body["action"]!!.jsonPrimitive.content)
        assertEquals("b1", body["bookingId"]!!.jsonPrimitive.content)
        assertEquals("4", body["ratings"]!!.jsonObject["cleanliness"]!!.jsonPrimitive.content)
        assertEquals(setOf("action", "bookingId", "ratings", "comment", "photoPaths"), body.keys)
    }

    @Test
    fun `delete va reply - action dung ten`() {
        assertEquals("delete", encode(DeleteReviewRequest("r1"))["action"]!!.jsonPrimitive.content)
        assertEquals("reply", encode(ReplyReviewRequest("r1", "Cảm ơn"))["action"]!!.jsonPrimitive.content)
    }

    @Test
    fun `response create - doc duoc, bo qua truong la`() {
        val response = json.decodeFromString<ReviewWriteResponse>(
            """
            { "review": { "id": "b1", "overall": 4.0, "createdAt": "2026-10-05T10:00:00Z",
                          "canEditUntil": "2026-10-12T09:00:00Z", "extra": 1 },
              "venue": { "ratingAvg": 4.3, "ratingCount": 12 } }
            """.trimIndent(),
        )
        assertEquals(4.0, response.review.overall, 0.0)
        assertEquals("2026-10-12T09:00:00Z", response.review.canEditUntil)
        assertEquals(12, response.venue.ratingCount)
    }

    @Test
    fun `response delete va reply - doc duoc, editedAt co the null`() {
        val deleted = json.decodeFromString<DeleteReviewResponse>("""{ "venue": { "ratingAvg": 4.2, "ratingCount": 11 } }""")
        assertEquals(11, deleted.venue.ratingCount)

        val reply = json.decodeFromString<ReplyReviewResponse>(
            """{ "ownerReply": { "text": "Cảm ơn", "repliedAt": "2026-10-06T08:00:00Z", "editedAt": null } }""",
        )
        assertEquals("Cảm ơn", reply.ownerReply.text)
        assertNull(reply.ownerReply.editedAt)
    }
}
