package com.torpille.app.ui.viewmodels

import com.torpille.app.repo.AuthenticationActions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Fake : AuthenticationActions {
        var calls = 0
        var receivedEmail = ""
        var receivedPassword = ""
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        private suspend fun request(email: String, password: String = "") {
            calls++; receivedEmail = email; receivedPassword = password
            gate?.await()
            failure?.let { throw it }
        }
        override suspend fun signIn(email: String, password: String) = request(email, password)
        override suspend fun signUp(email: String, password: String, acceptsRules: Boolean) = request(email, password)
        override suspend fun sendPasswordResetEmail(email: String) = request(email)
    }
    @Test fun registrationPreservesPasswordAndNormalizesEmail() = runTest(dispatcher) {
        val fake = Fake(); val vm = AuthViewModel(fake)
        var complete = false
        vm.signUp(" NEW@Example.com ", "  password  ", true) { complete = true }
        advanceUntilIdle()
        assertTrue(complete); assertEquals("new@example.com", fake.receivedEmail)
        assertEquals("  password  ", fake.receivedPassword); assertNull(vm.error); assertFalse(vm.isLoading)
    }
    @Test fun signInUsesTheSameExactPasswordAsRegistration() = runTest(dispatcher) {
        val fake = Fake(); val vm = AuthViewModel(fake)
        vm.signIn("user@example.com", "  password  ") { }
        advanceUntilIdle(); assertEquals("  password  ", fake.receivedPassword)
    }
    @Test fun invalidEmailDoesNotContactFirebase() = runTest(dispatcher) {
        val fake = Fake(); val vm = AuthViewModel(fake)
        vm.signIn("not an email", "password") { fail() }
        advanceUntilIdle(); assertEquals(0, fake.calls); assertNotNull(vm.error)
    }
    @Test fun signupRequiresConsentAndMinimumPasswordLength() = runTest(dispatcher) {
        val fake = Fake(); val vm = AuthViewModel(fake)
        vm.signUp("user@example.com", "password", false) { fail() }
        assertNotNull(vm.error)
        vm.signUp("user@example.com", "12345", true) { fail() }
        advanceUntilIdle(); assertNotNull(vm.error); assertEquals(0, fake.calls)
    }
    @Test fun duplicateSubmissionsAndValidationDoNotDisturbPendingRequest() = runTest(dispatcher) {
        val fake = Fake(); fake.gate = CompletableDeferred()
        val vm = AuthViewModel(fake)
        var completed = 0
        vm.signIn("user@example.com", "password") { completed++ }; runCurrent()
        vm.signIn("invalid", "") { fail() }
        assertNull(vm.error); assertEquals(1, fake.calls)
        fake.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, completed); assertFalse(vm.isLoading)
    }
    @Test fun networkFailureIsVisibleAndCanBeRetried() = runTest(dispatcher) {
        val fake = Fake(); fake.failure = IOException("private diagnostic")
        val vm = AuthViewModel(fake)
        vm.signIn("user@example.com", "password") { fail() }; advanceUntilIdle()
        assertNotNull(vm.error); assertFalse(vm.error!!.contains("private diagnostic"))
        fake.failure = null
        var complete = false
        vm.signIn("user@example.com", "password") { complete = true }; advanceUntilIdle()
        assertTrue(complete); assertNull(vm.error)
    }
    @Test fun signInTimeoutReenablesControlsAndIgnoresLateCompletion() = runTest(dispatcher) {
        val fake = Fake(); fake.gate = CompletableDeferred()
        val vm = AuthViewModel(fake, timeoutMillis = 100)
        var complete = false
        vm.signIn("user@example.com", "password") { complete = true }; advanceUntilIdle()
        assertFalse(vm.isLoading); assertNotNull(vm.error)
        fake.gate!!.complete(Unit); advanceUntilIdle(); assertFalse(complete)
    }
    @Test fun passwordResetTimeoutCanBeRetriedWithoutLateSuccess() = runTest(dispatcher) {
        val fake = Fake(); fake.gate = CompletableDeferred()
        val vm = AuthViewModel(fake, timeoutMillis = 100)
        var completed = 0
        vm.resetPassword("user@example.com") { completed++ }; advanceUntilIdle()
        assertNotNull(vm.error); assertFalse(vm.isLoading)
        fake.gate!!.complete(Unit); advanceUntilIdle(); assertEquals(0, completed)
        fake.gate = null
        vm.resetPassword("user@example.com") { completed++ }; advanceUntilIdle()
        assertEquals(1, completed); assertNull(vm.error)
    }
    @Test fun emailDeliveryFailureNeverReportsSuccessfulRegistration() = runTest(dispatcher) {
        val fake = Fake(); fake.failure = IOException("email delivery failed")
        val vm = AuthViewModel(fake)
        vm.signUp("user@example.com", "password", true) { fail("Must not continue before email delivery") }
        advanceUntilIdle(); assertNotNull(vm.error); assertFalse(vm.isLoading)
    }
}
