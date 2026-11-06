package com.torpille.app.util

import com.torpille.app.TorpilleApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Excluded from backup/transfer: each account must consent on each new device. */
object DeviceLegalAcceptance {
    private const val VERSION = "2026-09-27"
    private val preferences get() = TorpilleApp.appContext.getSharedPreferences("device_legal_acceptance", 0)
    private val changes = MutableStateFlow(0)
    val revision = changes.asStateFlow()

    fun hasAccepted(uid: String?): Boolean = !uid.isNullOrBlank() && preferences.getString(uid, null) == VERSION

    fun record(uid: String) {
        require(uid.isNotBlank())
        check(preferences.edit().putString(uid, VERSION).commit()) { "Impossible de mémoriser l’acceptation sur cet appareil." }
        changes.value += 1
    }
}
