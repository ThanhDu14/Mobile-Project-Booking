package com.example.booking_app.feature.search.data

import android.content.Context
import com.example.booking_app.core.session.SignOutHook
import com.example.booking_app.feature.search.domain.AiQueryHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/** Lưu lịch sử câu hỏi AI trong SharedPreferences riêng, mỗi tài khoản một khóa. Không gửi lên server. */
@Singleton
class PrefsAiQueryHistory @Inject constructor(
    @param:ApplicationContext context: Context,
) : AiQueryHistory, SignOutHook {
    private val prefs = context.getSharedPreferences("ai_query_history", Context.MODE_PRIVATE)

    override fun read(uid: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key(uid), null) ?: return emptyList())
        List(array.length()) { array.getString(it) }.take(AiQueryHistory.LIMIT)
    }.getOrDefault(emptyList())

    override fun add(uid: String, query: String) = write(uid, AiQueryHistory.push(read(uid), query))

    override fun remove(uid: String, query: String) = write(uid, read(uid) - query)

    override fun clear(uid: String) {
        prefs.edit().remove(key(uid)).apply()
    }

    override suspend fun beforeSignOut(uid: String) = clear(uid)

    private fun write(uid: String, items: List<String>) {
        prefs.edit().putString(key(uid), JSONArray(items).toString()).apply()
    }

    private fun key(uid: String) = "queries_$uid"
}
