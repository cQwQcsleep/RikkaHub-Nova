package me.rerere.ai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EncodedImageCacheTest {

    private fun image(chars: Int) = EncodedImage(base64 = "x".repeat(chars), mimeType = "image/jpeg")

    @Test
    fun `returns the cached image for the same key`() {
        val cache = EncodedImageCache(maxChars = 100)
        val encoded = image(10)
        cache.put("a", encoded)

        assertSame(encoded, cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `evicts the least recently used entry when over the limit`() {
        val cache = EncodedImageCache(maxChars = 100)
        cache.put("a", image(60))
        cache.put("b", image(40))
        // 访问 a，使 b 成为最久未使用的一项
        cache.get("a")
        cache.put("c", image(30))

        assertNull(cache.get("b"))
        assertEquals(60, cache.get("a")?.base64?.length)
        assertEquals(30, cache.get("c")?.base64?.length)
    }

    @Test
    fun `replacing a key keeps the accounting correct`() {
        val cache = EncodedImageCache(maxChars = 100)
        cache.put("a", image(80))
        cache.put("a", image(10))
        cache.put("b", image(80))

        // 若旧条目没有被扣减，总重量会变成 160 而提前淘汰掉刚放入的 b
        assertEquals(80, cache.get("b")?.base64?.length)
        assertEquals(10, cache.get("a")?.base64?.length)
    }

    @Test
    fun `clear drops every entry`() {
        val cache = EncodedImageCache(maxChars = 100)
        cache.put("a", image(10))
        cache.clear()

        assertNull(cache.get("a"))
    }
}
