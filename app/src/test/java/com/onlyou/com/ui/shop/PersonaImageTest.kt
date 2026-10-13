package com.onlyou.com.ui.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonaImageTest {
    private val base = "http://10.0.2.2:8080/"

    @Test
    fun `상대 경로는 BASE_URL 기준으로 해석한다`() {
        assertEquals("http://10.0.2.2:8080/persona-images/a.jpg", resolvePersonaImageUrl("persona-images/a.jpg", base))
        assertEquals("http://10.0.2.2:8080/persona-images/a.jpg", resolvePersonaImageUrl("/persona-images/a.jpg", base))
    }

    @Test
    fun `절대 URL은 그대로, 비어 있으면 null`() {
        assertEquals("https://cdn.example/a.jpg", resolvePersonaImageUrl("https://cdn.example/a.jpg", base))
        assertNull(resolvePersonaImageUrl(null, base))
        assertNull(resolvePersonaImageUrl("  ", base))
    }
}
