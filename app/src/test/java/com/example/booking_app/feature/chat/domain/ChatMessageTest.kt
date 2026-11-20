package com.example.booking_app.feature.chat.domain

import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.Delivery
import com.example.booking_app.feature.chat.domain.model.MessageStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** T048: unit test cho [ChatMessage.canRecall] tại mốc 10 phút theo `createdAt` server và [ChatMessage.canReport]. */
class ChatMessageTest {

    private val sentAt = Instant.parse("2026-10-20T12:00:00Z")

    private fun message(
        isMine: Boolean = true,
        delivery: Delivery = Delivery.SENT,
        status: MessageStatus = MessageStatus.NORMAL,
        createdAt: Instant? = sentAt,
    ) = ChatMessage(
        id = "m1",
        senderId = if (isMine) "me" else "other",
        text = "Xin chào",
        createdAt = createdAt,
        status = status,
        delivery = delivery,
        isMine = isMine,
    )

    @Test
    fun `canRecall dung truoc moc 10 phut va sai tu dung moc 10 phut tro di`() {
        val msg = message()

        // 5 phút sau khi gửi -> được thu hồi.
        assertTrue(msg.canRecall(sentAt.plusSeconds(5 * 60)))
        // 9 phút 59.999 giây -> vẫn được thu hồi.
        assertTrue(msg.canRecall(sentAt.plusMillis(10 * 60_000L - 1)))
        // Đúng mốc 10 phút -> hết hạn (khớp server `now >= createdAt + 10 phút`).
        assertFalse(msg.canRecall(sentAt.plusSeconds(10 * 60)))
        // 11 phút -> hết hạn.
        assertFalse(msg.canRecall(sentAt.plusSeconds(11 * 60)))
    }

    @Test
    fun `canRecall sai voi tin nguoi khac tin chua len server tin thu hoi bi an hoac thieu createdAt`() {
        val within5Min = sentAt.plusSeconds(300)

        assertFalse(message(isMine = false).canRecall(within5Min))
        assertFalse(message(delivery = Delivery.PENDING).canRecall(within5Min))
        assertFalse(message(delivery = Delivery.FAILED).canRecall(within5Min))
        assertFalse(message(status = MessageStatus.RECALLED).canRecall(within5Min))
        assertFalse(message(status = MessageStatus.HIDDEN).canRecall(within5Min))
        assertFalse(message(createdAt = null).canRecall(within5Min))
    }

    @Test
    fun `canReport chi dung voi tin nguoi khac da gui o trang thai NORMAL`() {
        assertTrue(message(isMine = false, delivery = Delivery.SENT, status = MessageStatus.NORMAL).canReport)
        assertFalse(message(isMine = true, delivery = Delivery.SENT, status = MessageStatus.NORMAL).canReport)
        assertFalse(message(isMine = false, delivery = Delivery.SENT, status = MessageStatus.RECALLED).canReport)
        assertFalse(message(isMine = false, delivery = Delivery.SENT, status = MessageStatus.HIDDEN).canReport)
        assertFalse(message(isMine = false, delivery = Delivery.PENDING, status = MessageStatus.NORMAL).canReport)
    }
}
