package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
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
            context.getString(R.string.default_web_client_id).trim()
        } catch (_: Exception) {
            ""
        }
        if (clientId.isBlank()) {
            _isLoading.value = false
            _errorMessage.value =
                "Google Sign-In is not configured for this build (missing web client ID). Please sign in with Email & Password."
            return
        }

        val activityContext = context.findActivityOrNull()
        if (activityContext == null) {
            _isLoading.value = false
            _errorMessage.value = "Unable to launch Google Sign-In from this screen. Please try again."
            return
        }

        val wrappedContext = MutableContextWrapper(activityContext)
        val primaryOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(primaryOption)
            .build()

        viewModelScope.launch {
            try {
                val credentialResponse = try {
                    credentialManager.getCredential(wrappedContext, primaryRequest)
                } catch (noCred: NoCredentialException) {
                    Log.w(
                        "SignInViewModel",
                        "Primary Google Sign-In returned NoCredentialException; trying GetGoogleIdOption fallback: ${noCred.message}",
                        noCred
                    )
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    credentialManager.getCredential(wrappedContext, fallbackRequest)
                }

                val cred = credentialResponse.credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    _isLoading.value = false
                    onSuccess()
                } else {
                    _errorMessage.value =
                        "Unexpected Google credential response (${cred.type}). Please try Email & Password."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                val rawDetail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: e.message.orEmpty()
                Log.w(
                    "SignInViewModel",
                    "Google Sign-In cancelled or dismissed (type=${e.type}): $rawDetail",
                    e
                )
                _errorMessage.value = mapGoogleCancellationMessage(rawDetail)
            } catch (e: NoCredentialException) {
                Log.w("SignInViewModel", "No Google credentials available on device: ${e.message}", e)
                _errorMessage.value =
                    "No Google account is available on this device. Add a Google account in device Settings or sign in with Email & Password."
            } catch (e: GetCredentialProviderConfigurationException) {
                Log.e("SignInViewModel", "Credential Manager provider configuration error", e)
                _errorMessage.value =
                    "Google Play Services Credential Manager is not available on this device. Please sign in with Email & Password."
            } catch (e: GetCredentialUnsupportedException) {
                Log.e("SignInViewModel", "Credential Manager unsupported on device", e)
                _errorMessage.value =
                    "Google Sign-In is unsupported on this device. Please sign in with Email & Password."
            } catch (e: GoogleIdTokenParsingException) {
                Log.e("SignInViewModel", "Invalid Google ID token response", e)
                _errorMessage.value =
                    "Received an invalid Google Sign-In token. Please try again or use Email & Password."
            } catch (e: FirebaseAuthException) {
                Log.e("SignInViewModel", "Firebase Auth rejected Google credential (${e.errorCode})", e)
                _errorMessage.value = mapGoogleFirebaseError(e.errorCode, e.localizedMessage)
            } catch (e: FirebaseNetworkException) {
                Log.e("SignInViewModel", "Network error during Google Sign-In", e)
                _errorMessage.value = "Network error connecting to Firebase. Check your internet connection and try again."
            } catch (e: GetCredentialException) {
                val rawDetail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: e.message.orEmpty()
                Log.e("SignInViewModel", "Credential Manager error (type=${e.type}): $rawDetail", e)
                _errorMessage.value = when {
                    rawDetail.contains("10:") || rawDetail.contains("DEVELOPER_ERROR", ignoreCase = true) ->
                        "Google Sign-In configuration mismatch (DEVELOPER_ERROR). Ensure your installed APK matches Firebase's registered SHA-1 fingerprint, or log in with Email & Password."
                    rawDetail.contains("16:") ->
                        "Google account selection could not finish. Please check Google Play Services or use Email & Password."
                    rawDetail.contains("network", ignoreCase = true) ->
                        "Network error connecting to Google. Please check your internet connection."
                    else -> rawDetail.ifBlank {
                        "Google Sign-In failed. Please try again or use Email & Password."
                    }
                }
            } catch (e: Exception) {
                Log.e("SignInViewModel", "Unexpected Google Sign-In error", e)
                val raw = e.message.orEmpty()
                _errorMessage.value = when {
                    raw.contains("10:") || raw.contains("DEVELOPER_ERROR", ignoreCase = true) ->
                        "Google Sign-In configuration error (DEVELOPER_ERROR). Please use Email & Password or reinstall the latest signed APK."
                    raw.contains("network", ignoreCase = true) ->
                        "Network error connecting to Google. Please check your internet connection."
                    else -> e.localizedMessage ?: "Google Sign-In failed. Please try again or use Email & Password."
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        internal fun mapGoogleCancellationMessage(rawDetail: String): String {
            val cleaned = rawDetail.trim()
            val looksLikeUserBackPress = cleaned.isBlank() ||
                cleaned.equals("activity is cancelled by the user.", ignoreCase = true) ||
                cleaned.equals("user canceled", ignoreCase = true) ||
                cleaned.equals("cancelled", ignoreCase = true)
            return if (looksLikeUserBackPress) {
                "Google Sign-In did not complete. Tap \"Continue with Google\" to try again, or log in with Email & Password above."
            } else {
                "Google Sign-In could not complete ($cleaned). Check your device Google account or use Email & Password."
            }
        }

        internal fun mapGoogleFirebaseError(errorCode: String, localizedMessage: String?): String = when (errorCode) {
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "An account with this email already exists with Email & Password. Please log in with your password above."
            "ERROR_USER_DISABLED" ->
                "This account has been disabled. Please contact support."
            "ERROR_OPERATION_NOT_ALLOWED", "CONFIGURATION_NOT_FOUND" ->
                "Google Sign-In is not enabled in Firebase Console for this project."
            "ERROR_INVALID_CREDENTIAL" ->
                "Google Sign-In token was rejected by Firebase. Please ensure this APK's SHA-1 fingerprint matches your Firebase project."
            else -> localizedMessage ?: "Firebase rejected Google Sign-In ($errorCode)."
        }
    }
}

