package com.torpille.app.ui.viewmodels

import com.torpille.app.data.Community

object HomeCommunityPresentation {
    fun pinnedCommunities(
        communities: List<Community>,
        pinnedCommunityIds: List<String>,
    ): List<Community> {
        val communitiesById = communities.associateBy { it.id }
        return pinnedCommunityIds
            .take(3)
            .mapNotNull { communitiesById[it] }
    }

    fun homeCommunities(
        communities: List<Community>,
        pinnedCommunityIds: List<String>,
    ): List<Community> {
        val pinned = pinnedCommunities(communities, pinnedCommunityIds)
        return if (pinned.isEmpty()) communities.take(1) else pinned
    }

    fun orderedCommunities(
        communities: List<Community>,
        pinnedCommunityIds: List<String>,
    ): List<Community> {
        val pinned = pinnedCommunities(communities, pinnedCommunityIds)
        val pinnedIds = pinned.map { it.id }.toSet()
        return pinned + communities.filterNot { pinnedIds.contains(it.id) }
    }
}
