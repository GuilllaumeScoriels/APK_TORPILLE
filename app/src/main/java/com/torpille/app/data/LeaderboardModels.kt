package com.torpille.app.data

data class LeaderboardEntry(
    val uid: String = "",
    val pseudo: String = "",
    val photoUrl: String? = null,
    val xpTotal: Long = 0,
)
