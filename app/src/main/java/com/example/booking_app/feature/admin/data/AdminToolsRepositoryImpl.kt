package com.example.booking_app.feature.admin.data

import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.admin.domain.model.AdminDistrict
import com.example.booking_app.feature.admin.domain.model.AdminCategoryKind
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.example.booking_app.feature.admin.domain.model.AdminSystemStats
import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import com.example.booking_app.feature.admin.domain.model.BroadcastNotificationRequest
import com.example.booking_app.feature.admin.domain.repository.AdminToolsRepository
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AdminBroadcastRequest(
    val title: String,
    val body: String,
    val requestId: String,
)

@Serializable
data class AdminBroadcastResponse(
    val success: Boolean = false,
    val count: Int = 0,
)

@Serializable
data class AdminManageVoucherRequest(
    val action: String,
    val code: String? = null,
    val type: String? = null,
    val value: Long? = null,
    val maxDiscount: Long? = null,
    val minOrderTotal: Long? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
    val usageLimit: Long? = null,
    val perUserLimit: Long? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class AdminManageVoucherResponse(
    val success: Boolean = false,
    val code: String? = null,
    val isActive: Boolean? = null,
)

@Singleton
class AdminToolsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : AdminToolsRepository {

    override suspend fun getSystemStats(): Result<AdminSystemStats> = getSystemStats(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))

    override suspend fun getSystemStats(date: String): Result<AdminSystemStats> = adminRunCatching {
        require(Regex("[0-9]{8}").matches(date))
        val doc = firestore.collection("systemStats").document(date).get(com.google.firebase.firestore.Source.SERVER).await()
        if (!doc.exists()) {
            return@adminRunCatching AdminSystemStats(hasData = false)
        }
        AdminSystemStats(
            totalUsers = doc.getLong("totalUsers") ?: 0L,
            totalVenues = doc.getLong("totalVenues") ?: 0L,
            totalBookings = doc.getLong("totalBookings") ?: 0L,
            totalRevenue = doc.getLong("totalRevenue") ?: 0L,
            updatedAt = when (val value = doc.get("updatedAt")) {
                is com.google.firebase.Timestamp -> value.toDate().toInstant().toString()
                is String -> value
                else -> null
            },
            hasData = true,
            metrics = doc.data.orEmpty().filterValues { it is Number }.mapValues { (_, value) -> (value as Number).toLong() },
            source = "systemStats/${doc.id}",
            date = date,
        )
    }

    override suspend fun getSystemVouchers(): Result<List<AdminSystemVoucher>> = adminRunCatching {
        val snapshot = firestore.collection("systemVouchers").get().await()
        snapshot.documents.mapNotNull { doc ->
            val code = doc.id
            val type = doc.getString("type") ?: "PERCENTAGE"
            val value = doc.getLong("value") ?: 0L
            val maxDiscount = doc.getLong("maxDiscount") ?: 0L
            val minOrderTotal = doc.getLong("minOrderTotal") ?: 0L
            val validFrom = doc.getString("validFrom") ?: ""
            val validTo = doc.getString("validTo") ?: ""
            val usageLimit = doc.getLong("usageLimit") ?: 0L
            val perUserLimit = doc.getLong("perUserLimit") ?: 0L
            val usedCount = doc.getLong("usedCount") ?: 0L
            val isActive = doc.getBoolean("isActive") ?: true

            AdminSystemVoucher(
                code = code,
                type = type,
                value = value,
                maxDiscount = maxDiscount,
                minOrderTotal = minOrderTotal,
                validFrom = validFrom,
                validTo = validTo,
                usageLimit = usageLimit,
                perUserLimit = perUserLimit,
                usedCount = usedCount,
                isActive = isActive,
            )
        }.sortedByDescending { it.code }
    }

    override suspend fun createSystemVoucher(voucher: AdminSystemVoucher): Result<Unit> = persistVoucher(voucher, "CREATE")
    override suspend fun updateSystemVoucher(voucher: AdminSystemVoucher): Result<Unit> = persistVoucher(voucher, "UPDATE")

    private suspend fun persistVoucher(voucher: AdminSystemVoucher, action: String): Result<Unit> = adminRunCatching {
        val validationErr = voucher.validate()
        if (validationErr != null) {
            throw IllegalArgumentException(validationErr)
        }

        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_MANAGE_VOUCHER,
                body = AdminManageVoucherRequest(
                    action = action,
                    code = voucher.code.trim().uppercase(Locale.ROOT),
                    type = voucher.type.trim().uppercase(Locale.ROOT).replace("PERCENTAGE", "PERCENT"),
                    value = voucher.value,
                    maxDiscount = voucher.maxDiscount,
                    minOrderTotal = voucher.minOrderTotal,
                    validFrom = voucher.validFrom,
                    validTo = voucher.validTo,
                    usageLimit = voucher.usageLimit,
                    perUserLimit = voucher.perUserLimit,
                    isActive = voucher.isActive,
                ),
            ).body<AdminManageVoucherResponse>()
        }
    }

    override suspend fun toggleVoucherActive(code: String, isActive: Boolean): Result<Unit> = adminRunCatching {
        functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_MANAGE_VOUCHER,
                body = AdminManageVoucherRequest(
                    action = "TOGGLE_ACTIVE",
                    code = code.trim().uppercase(Locale.ROOT),
                    isActive = isActive,
                ),
            ).body<AdminManageVoucherResponse>()
        }
    }

    override suspend fun getDistricts(): Result<List<AdminDistrict>> = getCategories(AdminCategoryKind.DISTRICTS)

    override suspend fun getCategories(kind: AdminCategoryKind): Result<List<AdminDistrict>> = adminRunCatching {
        val snapshot = firestore.collection(kind.collection).get().await()
        snapshot.documents.mapNotNull { doc ->
            AdminDistrict(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0,
                isActive = doc.getBoolean("isActive") ?: true,
                kind = kind,
            )
        }.sortedBy { it.sortOrder }
    }

    override suspend fun saveDistrict(district: AdminDistrict): Result<Unit> = adminRunCatching {
        if (district.id.isBlank()) throw IllegalArgumentException("Mã quận/huyện không hợp lệ")
        if (district.name.isBlank()) throw IllegalArgumentException("Tên quận/huyện không được để trống")

        require(!district.id.contains('/')) { "Mã danh mục không hợp lệ" }
        val data = mapOf(
            "name" to district.name.trim(),
            "sortOrder" to district.sortOrder,
            "isActive" to district.isActive,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        firestore.collection(district.kind.collection).document(district.id.trim()).set(data, SetOptions.merge()).await()
    }

    override suspend fun broadcastSystemNotification(request: BroadcastNotificationRequest): Result<Int> = adminRunCatching {
        val err = request.validate()
        if (err != null) throw IllegalArgumentException(err)

        val response = functionErrors.invokeWithAuthRetry {
            supabase.functions.invoke(
                FUNCTION_BROADCAST,
                body = AdminBroadcastRequest(
                    title = request.title.trim(),
                    body = request.body.trim(),
                    requestId = request.requestId,
                ),
            ).body<AdminBroadcastResponse>()
        }
        response.count
    }

    companion object {
        private const val FUNCTION_BROADCAST = "admin-broadcast-notification"
        private const val FUNCTION_MANAGE_VOUCHER = "admin-manage-voucher"
    }
}
