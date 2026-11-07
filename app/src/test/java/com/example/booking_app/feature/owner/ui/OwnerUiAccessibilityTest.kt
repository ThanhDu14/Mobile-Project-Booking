package com.example.booking_app.feature.owner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class OwnerUiAccessibilityTest {

    @Test
    fun allOwnerLayoutsHaveCompliantTouchTargetsAndAccessibilityLabels() {
        val layoutDirs = listOf(
            File("src/main/res/layout"),
            File("src/main/res/layout-w600dp")
        )

        var totalInteractiveElements = 0
        val nonCompliantElements = mutableListOf<String>()

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()

        for (dir in layoutDirs) {
            val files = dir.listFiles { _, name -> name.contains("owner") && name.endsWith(".xml") } ?: emptyArray()
            for (file in files) {
                val doc = builder.parse(file)
                val nodes = doc.getElementsByTagName("*")
                for (i in 0 until nodes.length) {
                    val elem = nodes.item(i) as org.w3c.dom.Element
                    val tag = elem.tagName

                    val isClickable = (elem.getAttributeNS("http://schemas.android.com/apk/res/android", "clickable") == "true" && !tag.contains("CardView")) ||
                            tag.contains("Button") || tag.contains("CheckBox") || tag.contains("EditText") || tag.contains("Switch")

                    if (isClickable) {
                        totalInteractiveElements++
                        val minHeight = elem.getAttributeNS("http://schemas.android.com/apk/res/android", "minHeight")
                        val height = elem.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_height")
                        val desc = elem.getAttributeNS("http://schemas.android.com/apk/res/android", "contentDescription")
                        val text = elem.getAttributeNS("http://schemas.android.com/apk/res/android", "text")
                        var hint = elem.getAttributeNS("http://schemas.android.com/apk/res/android", "hint")

                        // If inside TextInputLayout, hint may be on parent
                        if (hint.isEmpty()) {
                            val parent = elem.parentNode
                            if (parent is org.w3c.dom.Element && parent.tagName.contains("TextInputLayout")) {
                                hint = parent.getAttributeNS("http://schemas.android.com/apk/res/android", "hint")
                            }
                        }

                        val hasValidHeight = minHeight.isNotEmpty() || height == "48dp" || height == "wrap_content" || height == "match_parent"
                        val hasLabel = desc.isNotEmpty() || text.isNotEmpty() || hint.isNotEmpty()

                        if (!hasValidHeight || !hasLabel) {
                            nonCompliantElements.add("${file.name} -> $tag (minHeight=$minHeight, label=${desc.ifEmpty { text.ifEmpty { hint } }})")
                        }
                    }
                }
            }
        }

        assertTrue("Interactive elements must be present in layouts", totalInteractiveElements > 0)
        assertEquals("All interactive elements must have >=48dp touch target and label: $nonCompliantElements", 0, nonCompliantElements.size)
    }

    @Test
    fun allOwnerLayoutsExistInBothPhoneAndTabletConfigurations() {
        val phoneDir = File("src/main/res/layout")
        val tabletDir = File("src/main/res/layout-w600dp")

        val phoneFragments = phoneDir.listFiles { _, name -> name.startsWith("fragment_owner") && name.endsWith(".xml") }
            ?.map { it.name } ?: emptyList()
        val tabletFragments = tabletDir.listFiles { _, name -> name.startsWith("fragment_owner") && name.endsWith(".xml") }
            ?.map { it.name } ?: emptyList()

        assertTrue("Phone fragments must not be empty", phoneFragments.isNotEmpty())
        for (fragmentName in phoneFragments) {
            assertTrue(
                "Fragment $fragmentName must have a corresponding w600dp tablet layout",
                tabletFragments.contains(fragmentName)
            )
        }
    }
}
