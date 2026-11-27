package com.example.booking_app.feature.chat.domain.model

enum class ChatType {
    GROUP,
    DIRECT,
}

enum class ChatStatus {
    ACTIVE,
    READ_ONLY,
    DELETED,
}

enum class ChatMode {
    PLAYER,
    OWNER,
}

enum class MessageType {
    TEXT,
    IMAGE,
}

enum class MessageStatus {
    NORMAL,
    RECALLED,
    HIDDEN,
}

enum class Delivery {
    PENDING,
    FAILED,
    SENT,
}

/** 5 lý do báo cáo tin nhắn, dùng chung danh sách với spec 090 (data-model mục 4). */
enum class ReportReason {
    SPAM,
    OFFENSIVE,
    SCAM,
    INAPPROPRIATE,
    OTHER,
}
