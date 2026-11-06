package com.torpille.app.data

import org.junit.Assert.*
import org.junit.Test

class HomeAnnouncementTest {
    @Test fun draftsAndEmptyContentAreHidden() {
        assertNull(HomeAnnouncement.from("draft", mapOf("published" to false, "title" to "Draft")))
        assertNull(HomeAnnouncement.from("empty", mapOf("published" to true, "body" to "  ")))
    }
    @Test fun textAndImagesAreDecodedAndUnsafeUrlsIgnored() {
        val item = HomeAnnouncement.from("news", mapOf("published" to true, "title" to " News ",
            "body" to "First\nSecond", "priority" to 4L,
            "imageUrls" to listOf("https://example.com/a.jpg", "http://example.com/b.jpg", "file:///private/a", "https://example.com/a.jpg")))!!
        assertEquals("News", item.title)
        assertEquals("First\nSecond", item.body)
        assertEquals(listOf("https://example.com/a.jpg"), item.imageUrls)
        assertEquals(4L, item.priority)
    }
    @Test fun imageOnlyAnnouncementIsSupported() {
        assertNotNull(HomeAnnouncement.from("photo", mapOf("published" to true, "imageUrls" to listOf("https://example.com/a.jpg"))))
    }
}
