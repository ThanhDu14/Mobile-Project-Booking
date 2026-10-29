package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.OwnerAccountState
import com.example.booking_app.feature.owner.domain.OwnerApplication
import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.OwnerRole
import com.example.booking_app.feature.owner.domain.OwnerStatus
import com.example.booking_app.feature.owner.domain.VenueDraft
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.time.Instant

/** Converts the already documented Firestore fields into client read models. */
object OwnerApplicationMapper {
    fun accountState(fields: Map<String, Any?>): OwnerAccountState = OwnerAccountState(
        role = OwnerRole.fromWire(fields["role"] as? String),
        ownerStatus = OwnerStatus.fromWire(fields["ownerStatus"] as? String),
    )

    /** Returns null for incomplete or incompatible application data instead of inventing defaults. */
    fun application(id: String, fields: Map<String, Any?>): OwnerApplication? {
        val uid = fields["uid"] as? String ?: return null
        val venue = venueDraft(fields["venueDraft"] as? Map<*, *> ?: return null) ?: return null
        val representativeName = fields["representativeName"] as? String ?: return null
        val representativePhone = fields["representativePhone"] as? String ?: return null
        val documentPaths = fields.stringList("documentPaths") ?: return null
        val status = OwnerApplicationReviewStatus.fromWire(fields["status"] as? String) ?: return null

        return OwnerApplication(
            id = id,
            uid = uid,
            venueDraft = venue,
            representativeName = representativeName,
            representativePhone = representativePhone,
            documentPaths = documentPaths,
            status = status,
            rejectReason = fields["rejectReason"] as? String,
            reviewedBy = fields["reviewedBy"] as? String,
            reviewedAt = fields.instant("reviewedAt"),
            submittedAt = fields.instant("submittedAt"),
        )
    }

    private fun venueDraft(fields: Map<*, *>): VenueDraft? {
        val location = fields["location"] as? GeoPoint ?: return null
        return VenueDraft(
            name = fields["name"] as? String ?: return null,
            address = fields["address"] as? String ?: return null,
            districtId = fields["districtId"] as? String ?: return null,
            latitude = location.latitude,
            longitude = location.longitude,
            phone = fields["phone"] as? String ?: return null,
            courtCount = (fields["courtCount"] as? Number)?.toIntExactOrNull() ?: return null,
            openTime = fields["openTime"] as? String ?: return null,
            closeTime = fields["closeTime"] as? String ?: return null,
            photoPaths = fields.stringList("photoPaths") ?: return null,
        )
    }

    private fun Map<*, *>.stringList(key: String): List<String>? {
        val values = this[key] as? List<*> ?: return null
        if (values.any { it !is String }) return null
        return values.filterIsInstance<String>()
    }

    private fun Map<String, Any?>.instant(key: String): Instant? = (this[key] as? Timestamp)?.toDate()?.toInstant()

    private fun Number.toIntExactOrNull(): Int? {
        val value = toLong()
        if (toDouble() != value.toDouble() || value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
        return value.toInt()
    }
}
