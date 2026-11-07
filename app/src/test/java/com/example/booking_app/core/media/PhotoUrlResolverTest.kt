package com.example.booking_app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoUrlResolverTest {

    private val resolver = PhotoUrlResolver("http://10.0.2.2:54321")

    @Test
    fun `URL day du giu nguyen`() {
        val alobo = "https://m-files.alobo.vn/branch/sport_san_cau_long_k3/main/cover-1782574106072.jpg"
        assertEquals(alobo, resolver.venuePhoto(alobo))
        assertEquals("http://example.com/a.jpg", resolver.venuePhoto("http://example.com/a.jpg"))
    }

    @Test
    fun `path Supabase ghep URL public cua bucket venue-photos`() {
        assertEquals(
            "http://10.0.2.2:54321/storage/v1/object/public/venue-photos/demo-k3/cover.jpg",
            resolver.venuePhoto("demo-k3/cover.jpg"),
        )
    }

    @Test
    fun `path rong hoac chi khoang trang tra null`() {
        assertNull(resolver.venuePhoto(""))
        assertNull(resolver.venuePhoto("   "))
    }

    @Test
    fun `khong bi hai dau gach cheo`() {
        val withSlash = PhotoUrlResolver("http://host/")
        assertEquals(
            "http://host/storage/v1/object/public/venue-photos/demo-k3/cover.jpg",
            withSlash.venuePhoto("/demo-k3/cover.jpg"),
        )
    }
}
