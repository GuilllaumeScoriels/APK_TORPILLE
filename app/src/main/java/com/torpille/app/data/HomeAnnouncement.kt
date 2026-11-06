package com.torpille.app.data

import java.net.URI

data class HomeAnnouncement(val id: String, val title: String, val body: String, val imageUrls: List<String>, val priority: Long) {
    companion object {
        fun from(id: String, data: Map<String, Any?>): HomeAnnouncement? {
            if (data["published"] != true) return null
            val title = (data["title"] as? String).orEmpty().trim().take(160)
            val body = (data["body"] as? String).orEmpty().trim().take(6000)
            val images = (data["imageUrls"] as? List<*>)?.filterIsInstance<String>()?.map(String::trim)
                ?.filter { runCatching { val u = URI(it); u.scheme == "https" && !u.host.isNullOrEmpty() }.getOrDefault(false) }
                ?.distinct()?.take(5).orEmpty()
            if (title.isEmpty() && body.isEmpty() && images.isEmpty()) return null
            return HomeAnnouncement(id, title, body, images, (data["priority"] as? Number)?.toLong() ?: 0)
        }
    }
}
