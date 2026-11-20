package com.example.ui.viewmodel

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.attemptAutoSignIn
import com.example.core.authStateFlow
import com.example.core.checkPendingGoogleProviderSignIn
import com.example.core.onGoogleSignInClicked
import com.example.core.signInWithGoogleProvider
import com.example.core.signOut
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data class Unauthenticated(val errorMessage: String? = null) : AuthState
    data class Authenticated(val user: FirebaseUser) : AuthState
}

class AuthViewModel(
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(
        auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Loading
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var initialCheckCompleted = auth.currentUser != null

    init {
        viewModelScope.launch {
            auth.authStateFlow().collect { user ->
                if (user != null) {
                    initialCheckCompleted = true
                    _isSigningIn.value = false
                    _authError.value = null
                    _authState.value = AuthState.Authenticated(user)
                } else if (initialCheckCompleted) {
                    _authState.value = AuthState.Unauthenticated(_authError.value)
                }
            }
        }
    }

    fun initializeSessionCheck(context: Context, credentialManager: CredentialManager) {
        if (auth.currentUser != null) {
            initialCheckCompleted = true
            _authState.value = AuthState.Authenticated(auth.currentUser!!)
            return
        }
        if (initialCheckCompleted) return

        _authState.value = AuthState.Loading

        checkPendingGoogleProviderSignIn(
            onAuthSuccess = {
                initialCheckCompleted = true
                _authError.value = null
                auth.currentUser?.let { _authState.value = AuthState.Authenticated(it) }
            },
            onAuthError = { msg ->
                initialCheckCompleted = true
                _authError.value = msg
                _authState.value = AuthState.Unauthenticated(msg)
            },
            scope = viewModelScope
        )

        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                initialCheckCompleted = true
                _authError.value = null
                auth.currentUser?.let { _authState.value = AuthState.Authenticated(it) }
            },
            onUnauthenticated = {
                initialCheckCompleted = true
                if (auth.currentUser == null) {
                    _authState.value = AuthState.Unauthenticated(_authError.value)
                }
            },
            scope = viewModelScope
        )
    }

    fun startGoogleSignIn(context: Context, credentialManager: CredentialManager) {
        _authError.value = null
        _isSigningIn.value = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                _isSigningIn.value = false
                auth.currentUser?.let { _authState.value = AuthState.Authenticated(it) }
            },
            onAuthError = { msg ->
                _isSigningIn.value = false
                _authError.value = msg
                _authState.value = AuthState.Unauthenticated(msg)
            },
            scope = viewModelScope,
            onAuthCancelled = {
                _isSigningIn.value = false
            }
        )
    }

    fun startGoogleBrowserSignIn(context: Context) {
        _authError.value = null
        _isSigningIn.value = true
        signInWithGoogleProvider(
            context = context,
            onAuthSuccess = {
                _isSigningIn.value = false
                auth.currentUser?.let { _authState.value = AuthState.Authenticated(it) }
            },
            onAuthError = { msg ->
                _isSigningIn.value = false
                _authError.value = msg
                _authState.value = AuthState.Unauthenticated(msg)
            },
            scope = viewModelScope,
            onAuthCancelled = {
                _isSigningIn.value = false
            }
        )
    }

    fun signOutUser(credentialManager: CredentialManager) {
        signOut(
            credentialManager = credentialManager,
            scope = viewModelScope,
            onSignOutComplete = {
                _isSigningIn.value = false
                _authError.value = null
                _authState.value = AuthState.Unauthenticated(null)
            }
        )
    }

    fun clearError() {
        _authError.value = null
        val current = _authState.value
        if (current is AuthState.Unauthenticated) {
            _authState.value = AuthState.Unauthenticated(null)
        }
    }
}
