package com.torpille.app.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityMembershipRepositoryTest {
    @Test fun appleProfileMembershipsSurviveUnavailableGroupQuery() {
        assertEquals(listOf("apple-community"), membershipCommunityIds(
            Result.success(listOf("apple-community")), Result.failure(IllegalStateException("permission-denied"))))
    }

    @Test fun legacyMembershipsWorkWithoutProfileIndex() {
        assertEquals(listOf("legacy"), membershipCommunityIds(Result.success(emptyList()), Result.success(listOf("legacy"))))
    }

    @Test fun mergeNormalizesAndPreservesBothPlatformsMemberships() {
        assertEquals(listOf("apple", "android"), membershipCommunityIds(
            Result.success(listOf(" apple ", "", "invalid/path")), Result.success(listOf("apple", "android"))))
    }

    @Test(expected = IllegalStateException::class)
    fun failedQueryWithoutFallbackDoesNotPretendAccountHasNoCommunities() {
        membershipCommunityIds(Result.success(emptyList()), Result.failure(IllegalStateException("permission-denied")))
    }

    @Test fun trulyEmptyAccountHasNoCommunities() {
        assertEquals(emptyList<String>(), membershipCommunityIds(Result.success(emptyList()), Result.success(emptyList())))
    }
}
