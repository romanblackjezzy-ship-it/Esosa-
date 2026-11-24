package com.example

import com.example.ui.screens.AuthFormValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
