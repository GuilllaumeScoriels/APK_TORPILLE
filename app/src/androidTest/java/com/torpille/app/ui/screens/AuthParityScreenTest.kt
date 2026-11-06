package com.torpille.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.torpille.app.repo.AuthenticationActions
import com.torpille.app.ui.viewmodels.AuthViewModel
import com.torpille.app.repo.EmailVerificationActions
import com.torpille.app.ui.components.IllustratedActionButton
import com.torpille.app.ui.components.ActionAccent
import com.torpille.app.ui.theme.TorpilleTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class AuthParityScreenTest {
    @get:Rule val compose = createComposeRule()
    private class Fake : EmailVerificationActions {
        override val currentEmail = "local-test@example.invalid"
        var verified = false
        var failure: Exception? = null
        override suspend fun refreshVerification(): Boolean = verified
        override suspend fun resendVerification() { failure?.let { throw it } }
    }
    @Test fun verificationNoticeScrollsIntoViewOnSmallScreen() {
        val fake = Fake()
        compose.setContent { TorpilleTheme { Surface { Box(Modifier.height(180.dp)) {
            VerifyEmailScreen({}, {}, fake)
        } } } }
        compose.onNodeWithText("Renvoyer le lien").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("auth.verificationNotice").assertIsDisplayed()
        compose.onNodeWithText("E-mail de vérification envoyé.").assertIsDisplayed()
    }
    @Test fun failedEmailDoesNotShowASuccessNotice() {
        val fake = Fake(); fake.failure = IOException("network unavailable")
        compose.setContent { TorpilleTheme { Surface { VerifyEmailScreen({}, {}, fake) } } }
        compose.onNodeWithText("Renvoyer le lien").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("E-mail de vérification envoyé.").assertDoesNotExist()
        compose.onNodeWithTag("auth.verificationNotice").assertIsDisplayed()
    }
    @Test fun unverifiedAccountCannotContinueButVerifiedAccountCan() {
        val fake = Fake(); var completed = 0
        compose.setContent { TorpilleTheme { Surface { VerifyEmailScreen({ completed++ }, {}, fake) } } }
        compose.onNodeWithText("J’ai vérifié mon e-mail").performScrollTo().performClick()
        compose.waitForIdle(); assertEquals(0, completed)
        fake.verified = true
        compose.onNodeWithText("J’ai vérifié mon e-mail").performScrollTo().performClick()
        compose.waitForIdle(); assertEquals(1, completed)
    }
    @Test fun signInValidationErrorScrollsIntoView() {
        val fake = object : AuthenticationActions {
            override suspend fun signIn(email: String, password: String) { fail("Empty form must not contact Firebase") }
            override suspend fun signUp(email: String, password: String, acceptsRules: Boolean) { fail() }
            override suspend fun sendPasswordResetEmail(email: String) { fail() }
        }
        compose.setContent { TorpilleTheme { Surface { AuthScreen({}, AuthViewModel(fake)) } } }
        compose.onNodeWithText("Se connecter").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("auth.errorMessage").assertIsDisplayed()
    }
    @Test fun compactActionKeepsAnAccessibleTouchTarget() {
        compose.setContent { TorpilleTheme {
            IllustratedActionButton("Se connecter", "", Icons.Default.Lock, Icons.Default.Lock,
                ActionAccent.Meadow, modifier = Modifier.testTag("compact-action"))
        } }
        compose.onNodeWithText("Se connecter").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("compact-action").getUnclippedBoundsInRoot()
        val height = (bounds.bottom - bounds.top).value
        assertTrue("Touch target too small: $height", height >= 48f)
        assertTrue("Action card is still oversized: $height", height <= 72f)
    }
}
