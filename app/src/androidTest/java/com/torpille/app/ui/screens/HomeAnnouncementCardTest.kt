package com.torpille.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.torpille.app.data.HomeAnnouncement
import com.torpille.app.ui.components.HomeAnnouncementCard
import com.torpille.app.ui.theme.TorpilleTheme
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeAnnouncementCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun rendersTextAndImageWithoutProductionData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File(context.cacheDir, "announcement-preview.png")
        context.resources.openRawResource(com.torpille.app.R.drawable.torpille_logo).use { input -> image.outputStream().use { input.copyTo(it) } }
        val loaded = runBlocking { context.imageLoader.execute(ImageRequest.Builder(context).data(image.toURI().toString()).build()) }
        assertTrue("The announcement image must decode successfully", loaded is SuccessResult)
        compose.setContent {
            TorpilleTheme { Surface { Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                HomeAnnouncementCard(HomeAnnouncement("preview", "Les nouvelles de Torpille", "Un nouveau rendez-vous pour votre communauté.\nRetrouvez ici toutes les informations du capitaine.", listOf(image.toURI().toString()), 0))
            } } }
        }
        compose.onNodeWithText("Les nouvelles de Torpille").assertIsDisplayed()
        compose.onNodeWithText("Un nouveau rendez-vous pour votre communauté.\nRetrouvez ici toutes les informations du capitaine.").assertIsDisplayed()
        compose.waitForIdle()
        File(context.filesDir, "announcement-preview.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
