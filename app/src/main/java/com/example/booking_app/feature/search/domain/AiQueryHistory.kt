package com.example.booking_app.feature.search.domain

/**
 * Lịch sử câu hỏi "Tìm bằng AI" của từng tài khoản, chỉ lưu trên máy (spec 022 FR-031).
 * Giữ tối đa [LIMIT] câu mới nhất, đã che số điện thoại và email; xóa khi đăng xuất.
 */
interface AiQueryHistory {
    fun read(uid: String): List<String>
    fun add(uid: String, query: String)
    fun remove(uid: String, query: String)
    fun clear(uid: String)

    companion object {
        const val LIMIT = 5

        private val email = Regex("""[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.\p{L}{2,}""")
        private val phone = Regex("""(?<![\p{L}\p{N}])(?:\+?84|0)(?:[\s.()-]*[0-9]){8,10}(?![0-9])""")

        /** Cùng quy tắc với `maskQuery` của Edge Function `ai-search-parse`. */
        fun mask(query: String): String = phone.replace(email.replace(query, "[email]"), "[phone]")

        /** Câu mới lên đầu, bỏ bản trùng (không phân biệt hoa thường), cắt còn [LIMIT]. */
        fun push(items: List<String>, query: String): List<String> {
            val value = mask(query.trim())
            if (value.isEmpty()) return items
            return (listOf(value) + items.filterNot { it.equals(value, ignoreCase = true) }).take(LIMIT)
        }
    }
}

/** Bản lưu trong bộ nhớ, dùng cho test. */
class MemoryAiQueryHistory : AiQueryHistory {
    private val items = mutableMapOf<String, List<String>>()
    override fun read(uid: String) = items[uid].orEmpty()
    override fun add(uid: String, query: String) { items[uid] = AiQueryHistory.push(read(uid), query) }
    override fun remove(uid: String, query: String) { items[uid] = read(uid) - query }
    override fun clear(uid: String) { items.remove(uid) }
}
