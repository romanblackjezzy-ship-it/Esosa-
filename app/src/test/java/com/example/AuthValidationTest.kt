package com.example

import com.example.ui.screens.AuthFormValidator
import com.example.ui.viewmodel.SignInViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `validateEmail rejects empty or malformed email and accepts valid address`() {
        assertEquals("Enter your email address.", AuthFormValidator.validateEmail("   "))
        assertEquals("Enter a valid email address.", AuthFormValidator.validateEmail("alex@invalid"))
        assertNull(AuthFormValidator.validateEmail("alex.mercer@esosa.app"))
    }

    @Test
    fun `validateLogin requires valid email and minimum password length`() {
        assertEquals("Enter your password.", AuthFormValidator.validateLogin("alex@esosa.app", ""))
        assertEquals("Password must be at least 6 characters.", AuthFormValidator.validateLogin("alex@esosa.app", "12345"))
        assertNull(AuthFormValidator.validateLogin("alex@esosa.app", "secret6"))
    }

    @Test
    fun `validateSignUp verifies full name username handle and password`() {
        assertNotNull(AuthFormValidator.validateSignUp("alex@esosa.app", "A", "alex_m", "secret6"))
        assertNotNull(AuthFormValidator.validateSignUp("alex@esosa.app", "Alex Mercer", "ab", "secret6"))
        assertNull(AuthFormValidator.validateSignUp("alex@esosa.app", "Alex Mercer", "@alex_mercer", "secret6"))
    }

    @Test
    fun `google cancellation never returns blank message and preserves play services diagnostic`() {
        val generic = SignInViewModel.mapGoogleCancellationMessage("activity is cancelled by the user.")
        assertTrue(generic.contains("Continue with Google"))
        assertTrue(generic.contains("Email & Password"))

        val diagnostic = SignInViewModel.mapGoogleCancellationMessage("Unable to get sync account")
        assertTrue(diagnostic.contains("Unable to get sync account"))
    }

    @Test
    fun `firebase auth error codes map clearly for email login and sign up`() {
        val invalidCred = AuthFormValidator.mapEmailLoginError("ERROR_INVALID_CREDENTIAL", null)
        assertTrue(invalidCred.contains("Continue with Google"))
        assertTrue(invalidCred.contains("Forgot password?"))

        val alreadyInUse = AuthFormValidator.mapEmailSignUpError("ERROR_EMAIL_ALREADY_IN_USE", null)
        assertTrue(alreadyInUse.contains("Log in"))

        val shaMismatch = SignInViewModel.mapGoogleFirebaseError("ERROR_INVALID_CREDENTIAL", null)
        assertTrue(shaMismatch.contains("SHA-1"))
    }
}

