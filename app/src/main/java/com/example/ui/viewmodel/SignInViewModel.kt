package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}

class SignInViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun signInWithGoogle(
        context: Context,
        credentialManager: CredentialManager,
        onSuccess: () -> Unit
    ) {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null

        val clientId = try {
            context.getString(com.example.R.string.default_web_client_id)
        } catch (_: Exception) {
            "1074191788431-ln79cuak607jj1bti393i0n6ev73nkdo.apps.googleusercontent.com"
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        viewModelScope.launch {
            try {
                val activityContext = context.findActivityOrNull() ?: context
                val cred = credentialManager.getCredential(activityContext, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    _isLoading.value = false
                    onSuccess()
                } else {
                    _isLoading.value = false
                    _errorMessage.value = "Unexpected Google credential response."
                }
            } catch (e: GetCredentialCancellationException) {
                _isLoading.value = false
                Log.w("SignInViewModel", "Google Sign-In cancelled: ${e.message}")
            } catch (e: Exception) {
                _isLoading.value = false
                val msg = e.localizedMessage ?: "Google Sign-In failed. Please check your connection and try again."
                _errorMessage.value = msg
                Log.e("SignInViewModel", "Google Sign-In error", e)
            }
        }
    }
}
