package com.torpille.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.torpille.app.repo.CommunityRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

object LocationSyncManager {
    suspend fun syncPresenceIfPossible() {
        val repo = CommunityRepository()
        val communities = repo.observeCommunitiesForMe().first()
        if (communities.isNotEmpty()) repo.markMeOpenedInCommunities(communities.map { it.id })
    }


    suspend fun syncMyLocationIfPossible(context: Context) {
        val repo = CommunityRepository()
        val communities = repo.observeCommunitiesForMe().first()

        if (communities.isEmpty()) return

        runCatching {
            repo.markMeOpenedInCommunities(communities.map { it.id })
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) return

        val client = LocationServices.getFusedLocationProviderClient(context)

        val location = try {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
        } catch (_: Exception) {
            null
        }

        if (location != null) {
            repo.updateMyLocationInCommunities(
                communityIds = communities.map { it.id },
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
    }
}
