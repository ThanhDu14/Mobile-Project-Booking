package com.example.booking_app.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageUrlsTest {

    @Test
    fun `ghep dung URL public cua bucket`() {
        assertEquals(
            "http://10.0.2.2:54321/storage/v1/object/public/avatars/u1/avatar_1760500000000.jpg",
            StorageUrls.publicUrl("http://10.0.2.2:54321", "avatars", "u1/avatar_1760500000000.jpg"),
        )
    }

    @Test
    fun `SUPABASE_URL co hay khong co dau gach cuoi deu ra mot dau gach`() {
        val expected = "https://abc.supabase.co/storage/v1/object/public/avatars/u1/a.jpg"
        assertEquals(expected, StorageUrls.publicUrl("https://abc.supabase.co/", "avatars", "u1/a.jpg"))
        assertEquals(expected, StorageUrls.publicUrl("https://abc.supabase.co", "avatars", "/u1/a.jpg"))
    }

    @Test
    fun `ky tu dac biet duoc ma hoa tung doan va giu dau gach`() {
        assertEquals(
            "https://abc.supabase.co/storage/v1/object/public/avatars/u%201/%E1%BA%A3nh%23.jpg",
            StorageUrls.publicUrl("https://abc.supabase.co", "avatars", "u 1/ảnh#.jpg"),
        )
    }

    @Test
    fun `ham mot tham so dung SUPABASE_URL trong BuildConfig`() {
        val url = StorageUrls.publicUrl("avatars", "u1/a.jpg")
        assert(url.endsWith("/storage/v1/object/public/avatars/u1/a.jpg")) { url }
    }
}
