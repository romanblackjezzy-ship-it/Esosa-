package com.example

import com.example.ui.viewmodel.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStateTest {

    @Test
    fun `sealed AuthState models loading and unauthenticated error states`() {
        val loading: AuthState = AuthState.Loading
        assertTrue(loading is AuthState.Loading)

        val unauthEmpty = AuthState.Unauthenticated()
        assertNull(unauthEmpty.errorMessage)

        val unauthError = AuthState.Unauthenticated(errorMessage = "Sign-in cancelled")
        assertEquals("Sign-in cancelled", unauthError.errorMessage)
    }
}
