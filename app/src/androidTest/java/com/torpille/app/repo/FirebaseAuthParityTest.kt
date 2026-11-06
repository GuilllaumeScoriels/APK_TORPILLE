package com.torpille.app.repo

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.data.UserProfile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Native Firebase SDK integration. Every endpoint is a fixed local emulator. */
class FirebaseAuthParityTest {
    private val http = OkHttpClient()
    private fun request(url: String, body: JSONObject? = null, owner: Boolean = false, patch: Boolean = false): JSONObject {
        val builder = Request.Builder().url(url)
        if (owner) builder.header("Authorization", "Bearer owner")
        if (body != null) {
            val data = body.toString().toRequestBody("application/json".toMediaType())
            if (patch) builder.patch(data) else builder.post(data)
        }
        http.newCall(builder.build()).execute().use {
            check(it.isSuccessful) { "Local emulator HTTP ${it.code}" }
            return JSONObject(it.body!!.string())
        }
    }
    @Test fun registrationVerificationAndCrossPlatformProfileRoundTrip() = runBlocking {
        withTimeout(60_000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val options = FirebaseOptions.Builder().setApplicationId("1:123456789:android:abcdef1234567890")
                .setApiKey("fake-api-key").setProjectId("demo-torpille-parity").build()
            val app = FirebaseApp.initializeApp(context, options, "parity-" + UUID.randomUUID())
            val auth = FirebaseAuth.getInstance(app)
            auth.useEmulator("10.0.2.2", 9198)
            val db = FirebaseFirestore.getInstance(app)
            db.useEmulator("10.0.2.2", 8186)
            val repo = AuthRepository(auth)
            val email = "native-${UUID.randomUUID()}@example.invalid"
            val password = "  password with spaces  "
            try {
                repo.signUp(email, password, true)
                val uid = auth.currentUser!!.uid
                assertFalse(auth.currentUser!!.isEmailVerified)
                // Retrying registration authenticates first and resends the verification link.
                repo.signUp(email, password, true)
                val codes = request("http://10.0.2.2:9198/emulator/v1/projects/demo-torpille-parity/oobCodes").getJSONArray("oobCodes")
                val matches = (0 until codes.length()).map { codes.getJSONObject(it) }.filter { it.optString("email") == email }
                assertTrue(matches.size >= 2)
                request("http://10.0.2.2:9198/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key",
                    JSONObject().put("oobCode", matches.last().getString("oobCode")))
                assertTrue(repo.refreshVerification())
                auth.signOut()
                try { repo.signIn(email, password.trim()); fail("Trimmed password must not work") }
                catch (_: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) { }
                repo.signIn(email, password)
                assertEquals(uid, auth.currentUser!!.uid)
                assertTrue(auth.currentUser!!.isEmailVerified)
                val fields = JSONObject()
                fun text(key: String, value: String) { fields.put(key, JSONObject().put("stringValue", value)) }
                text("uid", uid); text("email", email); text("firstName", "Test"); text("lastName", "Parity")
                text("pseudo", "nativeparity"); text("pseudoKey", "nativeparity"); text("profileIcon", "🚀")
                fields.put("xpTotal", JSONObject().put("integerValue", "42"))
                fields.put("communityXP", JSONObject().put("mapValue", JSONObject().put("fields", JSONObject().put("shared", JSONObject().put("integerValue", "42")))))
                fields.put("communityIds", JSONObject().put("arrayValue", JSONObject().put("values", org.json.JSONArray().put(JSONObject().put("stringValue", "shared")))))
                for (name in listOf("termsAcceptedAt", "moderationRulesAcceptedAt")) fields.put(name, JSONObject().put("timestampValue", Instant.now().toString()))
                val url = "http://10.0.2.2:8186/v1/projects/demo-torpille-parity/databases/(default)/documents/users/$uid"
                // Seed the same field types and names used by the Apple client.
                request(url, JSONObject().put("fields", fields), owner = true, patch = true)
                val profile = db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)!!
                assertTrue(profile.hasRequiredIdentity)
                assertEquals(42L, profile.effectiveTotalXP)
                assertEquals(listOf("shared"), profile.communityIds)
                db.collection("users").document(uid).update("bio", "Android interoperability check").await()
                val after = request(url, owner = true).getJSONObject("fields")
                assertEquals("🚀", after.getJSONObject("profileIcon").getString("stringValue"))
                assertEquals("42", after.getJSONObject("xpTotal").getString("integerValue"))
                assertEquals("Android interoperability check", after.getJSONObject("bio").getString("stringValue"))
                val communityId = "native-" + uid
                val communityUrl = "http://10.0.2.2:8186/v1/projects/demo-torpille-parity/databases/(default)/documents/communities/$communityId"
                val communityFields = JSONObject().put("adminUid", JSONObject().put("stringValue", uid))
                    .put("name", JSONObject().put("stringValue", "Local parity"))
                    .put("isPublic", JSONObject().put("booleanValue", true))
                request(communityUrl, JSONObject().put("fields", communityFields), owner = true, patch = true)
                val messageFields = JSONObject().put("id", JSONObject().put("stringValue", "shared-message"))
                    .put("type", JSONObject().put("stringValue", "text"))
                    .put("senderUid", JSONObject().put("stringValue", "apple-user"))
                    .put("reactions", JSONObject().put("mapValue", JSONObject().put("fields",
                        JSONObject().put("apple-user", JSONObject().put("stringValue", "❤️")))))
                request(communityUrl + "/messages/shared-message", JSONObject().put("fields", messageFields), owner = true, patch = true)
                val communities = CommunityRepository(auth = auth, db = db)
                communities.setReaction(communityId, "shared-message", "👍")
                val messageRef = db.collection("communities").document(communityId).collection("messages").document("shared-message")
                val reacted = messageRef.get().await().toObject(com.torpille.app.data.Message::class.java)!!
                assertEquals("❤️", reacted.reactions!!["apple-user"])
                assertEquals("👍", reacted.reactions[uid])
                communities.setReaction(communityId, "shared-message", null)
                val removed = messageRef.get().await().toObject(com.torpille.app.data.Message::class.java)!!
                assertEquals(mapOf("apple-user" to "❤️"), removed.reactions)
            } finally {
                runCatching { auth.currentUser?.delete()?.await() }
                auth.signOut()
                db.terminate().await()
                app.delete()
            }
        }
    }
}
