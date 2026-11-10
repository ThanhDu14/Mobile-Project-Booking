package com.example.booking_app.testing

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ghi/xóa document trực tiếp trên Firestore Emulator qua REST với `Authorization: Bearer owner` (bỏ qua Rules),
 * chỉ để chuẩn bị dữ liệu test. Không dùng với project thật: host cố định là Emulator của máy ảo (10.0.2.2:8080).
 */
class EmulatorAdmin(private val projectId: String, private val host: String = "10.0.2.2:8080") {

    private fun url(path: String) = URL("http://$host/v1/projects/$projectId/databases/(default)/documents/$path")

    /** Ghi đè cả document: xóa rồi tạo lại (HttpURLConnection không gửi được PATCH). Không xóa collection con. */
    fun put(path: String, fields: Map<String, Any?>) {
        val body = JSONObject().put("fields", JSONObject().apply { fields.forEach { (k, v) -> put(k, encode(v)) } })
        delete(path)
        val collection = path.substringBeforeLast('/')
        val id = path.substringAfterLast('/')
        request("POST", "$collection?documentId=$id", body.toString())
    }

    fun delete(path: String) {
        request("DELETE", path, null)
    }

    private fun request(method: String, path: String, body: String?) {
        val connection = url(path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            check(code in 200..299 || (method == "DELETE" && code == 404)) {
                "$method $path → $code ${connection.errorStream?.bufferedReader()?.readText()}"
            }
        } finally {
            connection.disconnect()
        }
    }

    data class Geo(val lat: Double, val lng: Double)

    private fun encode(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", v)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int, is Long -> JSONObject().put("integerValue", v.toString())
        is Double -> JSONObject().put("doubleValue", v)
        is Geo -> JSONObject().put("geoPointValue", JSONObject().put("latitude", v.lat).put("longitude", v.lng))
        is java.time.Instant -> JSONObject().put("timestampValue", v.toString())
        is List<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(v.map(::encode))))
        is Map<*, *> -> JSONObject().put(
            "mapValue",
            JSONObject().put("fields", JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), encode(x)) } }),
        )
        else -> error("Kiểu chưa hỗ trợ: ${v::class}")
    }
}
