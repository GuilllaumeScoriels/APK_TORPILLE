package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpille.app.repo.AuthRepository
import com.torpille.app.repo.AuthenticationActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

class AuthViewModel(
    private val repo: AuthenticationActions = AuthRepository(),
    private val timeoutMillis: Long = 25_000,
) : ViewModel() {
    var error by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    private fun validatedEmail(value: String): String? {
        val clean = value.trim().lowercase(Locale.ROOT)
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(clean)) {
            error = "Renseigne une adresse e-mail valide."
            return null
        }
        return clean
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        if (isLoading) return
        val clean = validatedEmail(email) ?: return
        if (password.isEmpty()) { error = "Renseigne ton mot de passe."; return }
        run {
            repo.signIn(clean, password)
            onSuccess()
        }
    }

    fun signUp(email: String, password: String, acceptsRules: Boolean, onSuccess: () -> Unit) {
        if (isLoading) return
        if (!acceptsRules) { error = "Lis et accepte les règles avant de créer ton compte."; return }
        val clean = validatedEmail(email) ?: return
        if (password.length < 6) { error = "Choisis un mot de passe d’au moins 6 caractères."; return }
        run {
            repo.signUp(clean, password, acceptsRules)
            onSuccess()
        }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit) {
        if (isLoading) return
        val clean = validatedEmail(email) ?: return
        run {
            repo.sendPasswordResetEmail(clean)
            onSuccess()
        }
    }

    private fun run(operation: suspend () -> Unit) {
        if (isLoading) return
        error = null
        isLoading = true
        viewModelScope.launch {
            try { withTimeout(timeoutMillis) { operation() } }
            catch (_: TimeoutCancellationException) {
                error = "La connexion prend trop de temps. Vérifie ta connexion Internet puis réessaie."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = userErrorMessage(failure) }
            finally { isLoading = false }
        }
    }
}
