package com.torpille.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.torpille.app.data.UserProfile
import com.torpille.app.ui.theme.TorpilleTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileEditorTest {
    @get:Rule val compose = createComposeRule()
    private val profile = UserProfile(uid = "preview-only", firstName = "Camille", lastName = "Martin", pseudo = "camille",
        email = "camille@example.test", profileIcon = "🎯", xpTotal = 120,
        photoUrl = "android.resource://com.torpille.app/drawable/torpille_logo",
        termsAcceptedAt = Timestamp.now(), moderationRulesAcceptedAt = Timestamp.now())

    @Test fun optionalBirthdayAndEditedIdentityAreSavedWithoutResettingExistingAcceptance() {
        var saved: UserProfile? = null
        var accepted = false
        var clearFocus: () -> Unit = {}
        compose.setContent {
            val focus = LocalFocusManager.current
            SideEffect { clearFocus = { focus.clearFocus(force = true) } }
            TorpilleTheme { Surface { Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ProfileEditor(profile, true, false, null) { draft, _, terms -> saved = draft; accepted = terms }
            } } }
        }
        compose.onNodeWithContentDescription("Emoji personnalisé").assertDoesNotExist()
        compose.onNodeWithText("J’accepte les règles EULA").assertDoesNotExist()
        screenshot("profile-top.png")
        compose.onNodeWithText("Camille").performScrollTo().performTextReplacement("Camille Android")
        compose.runOnIdle { clearFocus() }
        compose.onNodeWithText("Ajouter ma date de naissance").performScrollTo()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Date de naissance").assertExists()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Date de naissance").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer les modifications").performScrollTo()
        screenshot("profile-form-bottom.png")
        compose.onNodeWithText("Enregistrer les modifications").performClick()
        compose.runOnIdle { assertEquals("Camille Android", saved?.firstName); assertNull(saved?.birthDate); assertTrue(accepted); assertEquals(profile.photoUrl, saved?.photoUrl) }
    }

    @Test fun emojiSelectorAppearsOnlyWithoutPhoto() {
        compose.setContent {
            TorpilleTheme { Surface { Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ProfileEditor(profile.copy(photoUrl = null), true, false, null) { _, _, _ -> }
            } } }
        }
        compose.onNodeWithContentDescription("Emoji personnalisé").assertExists()
        compose.onNodeWithContentDescription("Emoji personnalisé").performScrollTo()
        screenshot("profile-avatars.png")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
