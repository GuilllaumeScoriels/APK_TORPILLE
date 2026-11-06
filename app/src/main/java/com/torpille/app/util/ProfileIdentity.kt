package com.torpille.app.util

import java.text.Normalizer
import java.util.Locale

fun normalizePseudoKey(pseudo: String): String = Normalizer.normalize(pseudo.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun formatResponseTime(seconds: Long): String = when {
    seconds % 86400L == 0L -> "${seconds / 86400} j"
    seconds % 3600L == 0L -> "${seconds / 3600} h"
    seconds % 60L == 0L -> "${seconds / 60} min"
    else -> "$seconds s"
}
