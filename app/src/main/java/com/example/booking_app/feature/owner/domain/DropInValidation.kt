package com.example.booking_app.feature.owner.domain

/** Owner-editable session fields from the existing `dropInSessions` schema.
 * `registeredCount` is intentionally absent; it is server-managed.
 */
data class DropInEditableInput(
    val capacity: Int,
    val pricePerPerson: Long
)

object DropInValidation {
    /**
     * Validates form values against the currently registered count read from the server.
     * The server must repeat these checks in the transaction; this is only client-side feedback.
     */
    fun validate(input: DropInEditableInput, registeredCount: Int): List<OwnerFieldError> = buildList {
        if (input.capacity <= 0) {
            add(OwnerFieldError("capacity", "Sức chứa phải là số nguyên dương"))
        }
        if (input.pricePerPerson < 0L) {
            add(OwnerFieldError("pricePerPerson", "Giá mỗi người không được âm"))
        }
        if (registeredCount < 0) {
            add(OwnerFieldError("capacity", "Số người đã đăng ký không hợp lệ"))
        } else if (input.capacity < registeredCount) {
            add(OwnerFieldError("capacity", "Sức chứa không được thấp hơn số người đã đăng ký"))
        }
    }
}
