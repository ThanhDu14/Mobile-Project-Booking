package com.example.booking_app.feature.owner.data

/** Server venue schema stores HHmm; editable application forms use HH:mm. */
internal fun ownerApplicationFormTime(value: String): String =
    if (Regex("(?:[01][0-9]|2[0-3])[0-5][0-9]").matches(value))
        value.substring(0, 2) + ":" + value.substring(2)
    else value
