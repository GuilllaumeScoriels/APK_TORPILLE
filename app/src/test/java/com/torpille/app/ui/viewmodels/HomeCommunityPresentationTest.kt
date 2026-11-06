package com.torpille.app.ui.viewmodels

import com.torpille.app.data.Community
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCommunityPresentationTest {

    @Test
    fun pinnedCommunities_preservePinnedOrder_andLimitToThree() {
        val communities = listOf(
            community("c1", "One"),
            community("c2", "Two"),
            community("c3", "Three"),
            community("c4", "Four"),
        )

        val result = HomeCommunityPresentation.pinnedCommunities(
            communities = communities,
            pinnedCommunityIds = listOf("c3", "c1", "c4", "c2"),
        )

        assertEquals(listOf("c3", "c1", "c4"), result.map { it.id })
    }

    @Test
    fun homeCommunities_fallbackToLatestWhenNothingPinned() {
        val communities = listOf(
            community("recent", "Recent"),
            community("older", "Older"),
        )

        val result = HomeCommunityPresentation.homeCommunities(
            communities = communities,
            pinnedCommunityIds = emptyList(),
        )

        assertEquals(listOf("recent"), result.map { it.id })
    }

    @Test
    fun orderedCommunities_placePinnedFirst_andKeepOtherOrder() {
        val communities = listOf(
            community("recent", "Recent"),
            community("older", "Older"),
            community("oldest", "Oldest"),
        )

        val result = HomeCommunityPresentation.orderedCommunities(
            communities = communities,
            pinnedCommunityIds = listOf("oldest", "recent"),
        )

        assertEquals(listOf("oldest", "recent", "older"), result.map { it.id })
    }

    private fun community(id: String, name: String) = Community(
        id = id,
        name = name,
        isPublic = true,
        adminUid = "user",
        responseTimeSeconds = 3600,
        createdAt = null,
    )
}
