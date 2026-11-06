package com.torpille.app.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.torpille.app.TorpilleApp
import org.json.JSONObject

object AppLanguage {
    private val prefs get() = TorpilleApp.appContext.getSharedPreferences("language", Context.MODE_PRIVATE)
    var code by mutableStateOf(prefs.getString("code", "fr") ?: "fr")
        private set
    private val catalog by lazy { JSONObject(TorpilleApp.appContext.assets.open("ios_translations.json").bufferedReader().use { it.readText() }) }
    val choices = listOf("fr" to "Français", "en" to "English", "es" to "Español", "de" to "Deutsch", "it" to "Italiano", "pt" to "Português")
    fun select(value: String) {
        require(choices.any { it.first == value })
        code = value
        prefs.edit().putString("code", value).apply()
        com.google.firebase.auth.FirebaseAuth.getInstance().setLanguageCode(value)
    }
    fun resetPasswordInfo(email: String): String = when (code) {
        "en" -> "If an account matches $email, a password reset email will be sent. Check your spam folder too."
        "es" -> "Si existe una cuenta para $email, recibirás un correo para restablecer la contraseña. Revisa también el correo no deseado."
        "de" -> "Falls ein Konto für $email existiert, wird eine E-Mail zum Zurücksetzen gesendet. Prüfe auch den Spamordner."
        "it" -> "Se esiste un account per $email, riceverai un'email per reimpostare la password. Controlla anche lo spam."
        "pt" -> "Se existir uma conta para $email, será enviado um email para redefinir a senha. Verifica também o spam."
        else -> "Si un compte correspond à $email, un email de réinitialisation lui sera envoyé. Vérifie aussi les courriers indésirables."
    }
    fun phrase(french: String): String = if (code == "fr") french else catalog.optJSONObject(code)?.optString(french, french) ?: french
}
fun t(french: String): String = AppLanguage.phrase(french)
