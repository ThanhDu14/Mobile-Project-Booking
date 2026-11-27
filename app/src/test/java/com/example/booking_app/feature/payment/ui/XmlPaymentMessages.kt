package com.example.booking_app.feature.payment.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [PaymentMessages] cho test JVM, đọc thẳng `app/src/main/res/values/strings_payment.xml` để câu trong test
 * là câu app hiển thị thật. Định dạng `%1$s` như `Resources.getString(id, args)`.
 */
class XmlPaymentMessages : PaymentMessages {

    private val strings: Map<String, String> by lazy {
        val file = listOf("src/main/res/values/strings_payment.xml", "app/src/main/res/values/strings_payment.xml")
            .map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
        }
    }

    private fun get(name: String, vararg args: Any): String =
        requireNotNull(strings[name]) { "Thiếu chuỗi $name trong strings_payment.xml" }.format(*args)

    override fun string(key: PaymentMessages.Key, vararg args: Any): String = get(key.resName, *args)
}
