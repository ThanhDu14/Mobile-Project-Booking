package com.example.booking_app.feature.community.domain.model

/** Chế độ nhóm (data-model mục 1): công khai vào ngay, riêng tư phải được duyệt. */
enum class GroupVisibility { PUBLIC, PRIVATE }

/** Vai trò trong nhóm; đúng một [OWNER] mỗi nhóm. */
enum class GroupRole { OWNER, ADMIN, MEMBER }

/** `LOCKED` do admin hệ thống khóa (spec 120); `DELETED` là trạng thái xóa dở. */
enum class GroupStatus { ACTIVE, LOCKED, DELETED }

enum class PostType { POST, EVENT }

enum class PostStatus { VISIBLE, HIDDEN }

/** Ai ẩn bài: quản trị nhóm ([GROUP], nhóm gỡ ẩn được) hay admin hệ thống ([SYSTEM], nhóm không gỡ được). */
enum class HiddenBy { GROUP, SYSTEM }

/** 5 lý do báo cáo bài (research R14). */
enum class ReportReason { SPAM, OFFENSIVE, SCAM, INAPPROPRIATE, OTHER }

/** Kết quả `join`: vào ngay hay phải chờ duyệt. */
enum class JoinResult { JOINED, REQUESTED }

/** Cách xử lý ảnh đại diện khi `update` nhóm (contract mục 1). */
enum class AvatarAction { KEEP, REPLACE, CONFIRM, REMOVE }
