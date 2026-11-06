package com.torpille.app.util

import java.net.URI
import java.net.URLDecoder

data class PendingCommunityLink(val id: String, val opensDiscussion: Boolean = false)

object CommunityLink {
    fun validId(value: String?): Boolean = value != null && Regex("[A-Za-z0-9_-]{1,128}").matches(value)
    fun parse(value: String): PendingCommunityLink? = runCatching {
        val uri = URI(value)
        val valid = uri.rawUserInfo == null && uri.port == -1 && (
            (uri.scheme == "torpille" && uri.host == "join" && uri.path.orEmpty() in setOf("", "/")) ||
            (uri.scheme == "https" && uri.host == "torpille-38783.web.app" && uri.path in setOf("/join", "/join.html")))
        if (!valid) return null
        val id = uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "cid" }
            ?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }
        if (validId(id)) PendingCommunityLink(id!!) else null
    }.getOrNull()
}
