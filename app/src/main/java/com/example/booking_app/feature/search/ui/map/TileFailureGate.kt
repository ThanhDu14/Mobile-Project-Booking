package com.example.booking_app.feature.search.ui.map

/** Trì hoãn báo lỗi cho tới khi có lỗi tile và không tile nào thành công trong khoảng chờ. */
internal class TileFailureGate {
    private var visible = false
    private var loaded = false
    private var pending = false
    private var reported = false

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        pending = false
        if (value) reported = false
    }

    fun failure(): Boolean {
        if (!visible || loaded || pending || reported) return false
        pending = true
        return true
    }

    fun success() {
        loaded = true
        pending = false
    }

    fun timeout(): Boolean {
        if (!visible || !pending || loaded || reported) return false
        pending = false
        reported = true
        return true
    }
}
