package com.torpille.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class UserProfile(
    val uid: String = "",
    val pseudo: String = "",
    val pseudoKey: String = "",
    val photoUrl: String? = null,
    val email: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val profileIcon: String? = null,
    val communityIds: List<String> = emptyList(),
    val communityXP: Map<String, Long> = emptyMap(),
    val languageCode: String? = null,
    @get:PropertyName("isLanguagePublic") val isLanguagePublic: Boolean = true,
    @get:PropertyName("isLeaderboardPublic") val isLeaderboardPublic: Boolean = true,
    @get:PropertyName("isGhostMode") val isGhostMode: Boolean = false,
    val friendsCount: Long = 0,
    val birthDate: Timestamp? = null,
    val sex: String? = null,
    val city: String? = null,
    val bio: String? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationUpdatedAt: Timestamp? = null,
    val termsAcceptedAt: Timestamp? = null,
    val moderationRulesAcceptedAt: Timestamp? = null,
    val pinnedCommunityIds: List<String> = emptyList(),
    val xpTotal: Long = 0,
    val fcmToken: String? = null,
    val updatedAt: Timestamp? = null
) {
    @get:Exclude val effectiveTotalXP: Long get() = if (communityXP.isEmpty()) xpTotal else communityXP.values.sum()
    @get:Exclude val hasRequiredIdentity: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank() &&
        pseudo.isNotBlank() && (!photoUrl.isNullOrBlank() || !profileIcon.isNullOrBlank()) &&
        termsAcceptedAt != null && moderationRulesAcceptedAt != null
}

data class Community(
    val rankingSchedule: RankingSchedule? = null,
    val id: String = "",
    val name: String = "",
    @get:PropertyName("isPublic") val isPublic: Boolean = true,
    val adminUid: String = "",
    val responseTimeSeconds: Long = 3600,
    val eliminationMode: Boolean = false,
    val createdAt: Timestamp? = null,
)

data class Member(
    val uid: String = "",
    val pseudo: String = "",
    val photoUrl: String? = null,
    val profileIcon: String? = null,
    val lastTorpilleSentAt: Timestamp? = null,
    val lastSeenMessageAt: Timestamp? = null,
    val lastActiveAt: Timestamp? = null,
    val xpInCommunity: Long = 0,
    val pendingTorpilleId: String? = null,
    val pendingDeadlineAt: Timestamp? = null,
    val pendingFromPseudo: String? = null,
    val lastTorpilledAt: Timestamp? = null,
    val lastRespondedAt: Timestamp? = null,
    val overdueCount: Long = 0,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationUpdatedAt: Timestamp? = null,
    val lastSeenTorpilleAt: Timestamp? = null,
    val lastOpenedAt: Timestamp? = null
)

data class UserCommunityXp(
    val communityId: String = "",
    val communityName: String = "",
    val xpInCommunity: Long = 0,
    val lastTorpilledAt: Timestamp? = null,
)

data class Message(
    val reactions: Map<String, String>? = null,
    val responseRequired: Boolean? = null,
    val systemEvent: String? = null,
    val id: String = "",
    val type: String = "text",
    val senderUid: String = "",
    val senderPseudo: String = "",
    val text: String? = null,
    val videoUrl: String? = null,
    val videoBucket: String? = null,
    val videoPath: String? = null,
    val audioUrl: String? = null,
    val audioBucket: String? = null,
    val audioPath: String? = null,
    val imageUrl: String? = null,
    val imageBucket: String? = null,
    val imagePath: String? = null,
    val taggedUid: String? = null,
    val taggedPseudo: String? = null,
    val tagX: Double? = null,
    val tagY: Double? = null,
    val torpilleId: String? = null,
    val audioDurationSeconds: Double? = null,
    val savedAt: Timestamp? = null,
    val savedByUid: String? = null,
    val savedByPseudo: String? = null,
    val moderationStatus: String? = null,
    val reportCount: Long? = null,
    val createdAt: Timestamp? = null,
)

data class Torpille(
    val responseRequired: Boolean = true,
    val id: String = "",
    val fromUid: String = "",
    val fromPseudo: String? = null,
    val toUid: String = "",
    val communityId: String = "",
    val videoUrl: String? = null,
    val videoBucket: String? = null,
    val videoPath: String? = null,
    val audioUrl: String? = null,
    val audioBucket: String? = null,
    val audioPath: String? = null,
    val taggedPseudo: String? = null,
    val tagX: Double? = null,
    val tagY: Double? = null,
    val createdAt: Timestamp? = null,
    val deadlineAt: Timestamp? = null,
    val responded: Boolean = false,
    val respondedAt: Timestamp? = null,
    val nextToUid: String? = null,
    val penaltyApplied: Boolean = false,
    @get:PropertyName("isPublished") val isPublished: Boolean = false,
    val publishedAt: Timestamp? = null,
    val publishedByUid: String? = null,
    val publishedByPseudo: String? = null,
    val communityName: String? = null,
    val moderationStatus: String? = null,
    val reportCount: Long? = null,
)



data class PublishedReel(
    val id: String = "",
    val torpilleId: String = "",
    val communityId: String = "",
    val communityName: String = "",
    val authorUid: String = "",
    val authorPseudo: String = "",
    val sourceMessageId: String? = null,
    val authorPhotoUrl: String? = null,
    val authorProfileIcon: String? = null,
    val moderationStatus: String? = null,
    val reportCount: Long? = null,
    val videoUrl: String? = null,
    val videoBucket: String? = null,
    val videoPath: String? = null,
    val taggedPseudo: String? = null,
    val tagX: Double? = null,
    val tagY: Double? = null,
    val createdAt: Timestamp? = null,
    val publishedAt: Timestamp? = null,
)

data class UnseenTorpilleStory(
    val communityId: String = "",
    val communityName: String = "",
    val torpille: Torpille = Torpille(),
)

data class AppNotification(
    val id: String = "",
    val type: String = "generic",
    val title: String = "",
    val body: String = "",
    val communityId: String? = null,
    val communityName: String? = null,
    val torpilleId: String? = null,
    val xpDelta: Long = 0,
    val read: Boolean = false,
    val createdAt: Timestamp? = null,
)

object MessageReaction {
    val allowed = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
    fun summary(reactions: Map<String, String>?): List<Pair<String, Int>> =
        allowed.mapNotNull { emoji -> reactions.orEmpty().values.count { it == emoji }.takeIf { it > 0 }?.let { emoji to it } }
}
