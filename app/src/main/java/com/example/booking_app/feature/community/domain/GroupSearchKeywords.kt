package com.example.booking_app.feature.community.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Chuẩn hóa từ khóa tìm nhóm (research R5), cùng quy tắc với `supabase/functions/_shared/keywords.ts` (sinh
 * `groups.nameKeywords`): chữ thường, bỏ dấu tiếng Việt, `đ` thành `d`, tách từ theo ký tự không phải chữ/số.
 */
object GroupSearchKeywords {

    /** Độ dài tiền tố tối đa mà server lưu cho mỗi từ; từ khóa dài hơn chỉ có thể khớp tới mức này. */
    const val MAX_KEYWORD_LENGTH = 20

    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(text: String): String =
        Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)

    /** Các từ đã chuẩn hóa, không rỗng, không trùng, giữ thứ tự nhập. */
    fun tokens(query: String): List<String> =
        normalize(query).split(separators).filter { it.isNotEmpty() }.distinct()

    /** Từ khóa gửi cho ô tìm: các từ cách nhau một dấu cách (rỗng nếu không có từ nào). */
    fun normalizeQuery(query: String): String = tokens(query).joinToString(" ")

    /** Từ dài nhất (gặp trước thì lấy trước) dùng cho `array-contains`, cắt còn [MAX_KEYWORD_LENGTH]; `null` nếu không có từ. */
    fun primary(tokens: List<String>): String? = tokens.maxByOrNull { it.length }?.take(MAX_KEYWORD_LENGTH)

    /** `true` khi mọi từ trong [tokens] (đã cắt tới [MAX_KEYWORD_LENGTH]) đều có trong [nameKeywords] của nhóm. */
    fun matches(nameKeywords: Collection<String>, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val set = nameKeywords as? Set<String> ?: nameKeywords.toSet()
        return tokens.all { it.take(MAX_KEYWORD_LENGTH) in set }
    }
}
