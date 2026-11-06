package com.torpille.app.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AvatarUrl {
    fun diceBearPng(seed: String, style: String = "bottts"): String {
        val safeSeed = URLEncoder.encode(seed, StandardCharsets.UTF_8.toString())
        return "https://api.dicebear.com/9.x/$style/png?seed=$safeSeed"
    }
}