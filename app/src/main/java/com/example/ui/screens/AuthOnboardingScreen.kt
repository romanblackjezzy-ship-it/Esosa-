package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.EsosaHeaderWordmark
import com.example.ui.theme.EsosaOnlineGreen
import com.example.ui.theme.InstagramBlack
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramBlueDeep
import com.example.ui.viewmodel.SignInViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.InstagramBlueSoft
import com.example.ui.theme.InstagramHairline
import com.example.ui.theme.InstagramMutedText
import com.example.ui.theme.InstagramOffWhite
import com.example.ui.theme.InstagramWhite
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class AuthScreenMode {
    LOGIN,
    SIGN_UP,
    FORGOT_PASSWORD
}

data class PendingSignupMetadata(
    val fullName: String = "",
    val username: String = ""
)

private enum class AuthBannerTone {
    ERROR,
    INFO,
    SUCCESS
}

private data class AuthStatusBanner(
    val message: String,
    val tone: AuthBannerTone
)

object AuthFormValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val USERNAME_REGEX = Regex("^[a-z0-9._]{3,24}$")

    fun validateEmail(email: String): String? {
        val cleaned = email.trim()
        return when {
            cleaned.isEmpty() -> "Enter your email address."
            !EMAIL_REGEX.matches(cleaned) -> "Enter a valid email address."
            else -> null
        }
    }

    fun validateLogin(email: String, password: String): String? {
        validateEmail(email)?.let { return it }
        return when {
            password.isEmpty() -> "Enter your password."
            password.length < 6 -> "Password must be at least 6 characters."
            else -> null
        }
    }

    fun validateSignUp(
        email: String,
        fullName: String,
        username: String,
        password: String
    ): String? {
        validateEmail(email)?.let { return it }
        if (fullName.trim().length < 2) return "Enter your full name."
        val cleanHandle = username.trim().removePrefix("@").lowercase()
        if (!USERNAME_REGEX.matches(cleanHandle)) {
            return "Username must be 3-24 characters using letters, numbers, '.' or '_'."
        }
        if (password.length < 6) return "Password must be at least 6 characters."
        return null
    }
}

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activityContext = context.findActivityOrNull() ?: context
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        "1074191788431-ln79cuak607jj1bti393i0n6ev73nkdo.apps.googleusercontent.com"
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activityContext = context.findActivityOrNull() ?: context
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected Google credential response. Please try signing in with email and password.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            val msg = e.localizedMessage ?: "Google Sign-In requires Google Play Services or a configured Google account on this device. Please use Email and Password to log in or sign up."
            onAuthError(msg)
        }
    }
}

fun signOutFromGoogle(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthOnboardingScreen(
    existingProfile: UserProfile,
    onAuthSuccess: (PendingSignupMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var screenMode by rememberSaveable { mutableStateOf(AuthScreenMode.LOGIN) }

    var emailInput by rememberSaveable {
        mutableStateOf(
            if (existingProfile.phoneOrEmail.contains("@")) existingProfile.phoneOrEmail else ""
        )
    }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var fullNameInput by rememberSaveable { mutableStateOf(existingProfile.displayName) }
    var usernameInput by rememberSaveable { mutableStateOf(existingProfile.username) }
    var resetEmailInput by rememberSaveable { mutableStateOf(emailInput) }

    val signInViewModel: SignInViewModel = viewModel()
    val isGoogleLoading by signInViewModel.isLoading.collectAsStateWithLifecycle()
    val signInError by signInViewModel.errorMessage.collectAsStateWithLifecycle()

    var isResetSending by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<AuthStatusBanner?>(null) }

    LaunchedEffect(signInError) {
        if (signInError != null) {
            statusBanner = AuthStatusBanner(signInError!!, AuthBannerTone.ERROR)
            signInViewModel.clearError()
        }
    }

    if (screenMode != AuthScreenMode.LOGIN) {
        BackHandler {
            statusBanner = null
            screenMode = AuthScreenMode.LOGIN
        }
    }

    val pendingMetadata = remember(screenMode, fullNameInput, usernameInput) {
        if (screenMode == AuthScreenMode.SIGN_UP) {
            PendingSignupMetadata(
                fullName = fullNameInput.trim(),
                username = usernameInput.trim().removePrefix("@")
            )
        } else {
            PendingSignupMetadata()
        }
    }

    fun triggerGoogleAuth() {
        keyboardController?.hide()
        statusBanner = null
        signInViewModel.signInWithGoogle(
            context = context,
            credentialManager = credentialManager,
            onSuccess = {
                onAuthSuccess(pendingMetadata)
            }
        )
    }

    var isEmailAuthLoading by remember { mutableStateOf(false) }

    fun submitEmailLogin() {
        keyboardController?.hide()
        val error = AuthFormValidator.validateLogin(emailInput, passwordInput)
        if (error != null) {
            statusBanner = AuthStatusBanner(error, AuthBannerTone.ERROR)
            return
        }
        isEmailAuthLoading = true
        statusBanner = null
        scope.launch {
            try {
                Firebase.auth.signInWithEmailAndPassword(emailInput.trim(), passwordInput).await()
                isEmailAuthLoading = false
                onAuthSuccess(pendingMetadata)
            } catch (e: Exception) {
                isEmailAuthLoading = false
                val msg = when {
                    e.message?.contains("no user record", ignoreCase = true) == true ||
                    e.message?.contains("user-not-found", ignoreCase = true) == true ->
                        "No account found for this email. Tap \"Sign up\" below to create your account."
                    e.message?.contains("wrong-password", ignoreCase = true) == true ||
                    e.message?.contains("invalid-credential", ignoreCase = true) == true ->
                        "Incorrect password or email. Please check your credentials or tap \"Forgot password\"."
                    e.message?.contains("disabled", ignoreCase = true) == true ||
                    e.message?.contains("operation-not-allowed", ignoreCase = true) == true ->
                        "Email/Password sign-in is disabled in your Firebase console. Please enable Email/Password under Authentication → Sign-in method, or use Google Sign-In."
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your internet connection and try again."
                    else -> e.localizedMessage ?: "Sign in failed. Please check your credentials."
                }
                statusBanner = AuthStatusBanner(msg, AuthBannerTone.ERROR)
            }
        }
    }

    fun submitEmailSignUp() {
        keyboardController?.hide()
        val error = AuthFormValidator.validateSignUp(
            email = emailInput,
            fullName = fullNameInput,
            username = usernameInput,
            password = passwordInput
        )
        if (error != null) {
            statusBanner = AuthStatusBanner(error, AuthBannerTone.ERROR)
            return
        }
        isEmailAuthLoading = true
        statusBanner = null
        scope.launch {
            try {
                Firebase.auth.createUserWithEmailAndPassword(emailInput.trim(), passwordInput).await()
                isEmailAuthLoading = false
                onAuthSuccess(pendingMetadata)
            } catch (e: Exception) {
                isEmailAuthLoading = false
                val msg = when {
                    e.message?.contains("email-already-in-use", ignoreCase = true) == true ||
                    e.message?.contains("already in use", ignoreCase = true) == true ->
                        "An account with this email already exists. Tap \"Log in\" below to sign in."
                    e.message?.contains("weak-password", ignoreCase = true) == true ->
                        "Password is too weak. Please use at least 6 characters with letters and numbers."
                    e.message?.contains("disabled", ignoreCase = true) == true ||
                    e.message?.contains("operation-not-allowed", ignoreCase = true) == true ->
                        "Email/Password sign-up is disabled in Firebase console. Please enable Email/Password under Authentication → Sign-in method, or sign up with Google."
                    else -> e.localizedMessage ?: "Sign up failed. Please check your details."
                }
                statusBanner = AuthStatusBanner(msg, AuthBannerTone.ERROR)
            }
        }
    }

    fun submitForgotPassword() {
        keyboardController?.hide()
        val cleanEmail = resetEmailInput.trim()
        val validationError = AuthFormValidator.validateEmail(cleanEmail)
        if (validationError != null) {
            statusBanner = AuthStatusBanner(validationError, AuthBannerTone.ERROR)
            return
        }
        isResetSending = true
        statusBanner = null
        scope.launch {
            try {
                Firebase.auth.sendPasswordResetEmail(cleanEmail).await()
                isResetSending = false
                statusBanner = AuthStatusBanner(
                    message = "Password reset link sent to $cleanEmail. Check your inbox and spam folder.",
                    tone = AuthBannerTone.SUCCESS
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseAuthException) {
                isResetSending = false
                val msg = when (e.errorCode) {
                    "ERROR_OPERATION_NOT_ALLOWED", "CONFIGURATION_NOT_FOUND" ->
                        "Email/Password reset isn't enabled in Firebase Console yet. Use \"Continue with Google\" to access your account immediately."
                    "ERROR_USER_NOT_FOUND" ->
                        "No email/password account was found for $cleanEmail. Try \"Sign up\" or \"Continue with Google\"."
                    else -> e.localizedMessage ?: "Unable to send reset email right now."
                }
                statusBanner = AuthStatusBanner(msg, AuthBannerTone.ERROR)
            } catch (e: Exception) {
                isResetSending = false
                statusBanner = AuthStatusBanner(
                    message = e.localizedMessage ?: "Unable to send reset email right now.",
                    tone = AuthBannerTone.ERROR
                )
            }
        }
    }

    Surface(
        color = InstagramWhite,
        contentColor = InstagramBlack,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (screenMode == AuthScreenMode.FORGOT_PASSWORD) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            statusBanner = null
                            screenMode = AuthScreenMode.LOGIN
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("forgot_password_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.auth_back_to_login),
                            tint = InstagramBlack
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = screenMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "auth_screen_mode"
                    ) { mode ->
                        Column(
                            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (mode) {
                                AuthScreenMode.LOGIN -> {
                                    Spacer(Modifier.height(12.dp))
                                    EsosaHeaderWordmark(height = 50.dp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Log in to see photos and reels from friends.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InstagramMutedText,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(28.dp))

                                    InstagramAuthTextField(
                                        value = emailInput,
                                        onValueChange = {
                                            emailInput = it
                                            statusBanner = null
                                        },
                                        placeholder = "Email",
                                        leadingIcon = Icons.Outlined.Email,
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next,
                                        modifier = Modifier.testTag("auth_email_input")
                                    )
                                    Spacer(Modifier.height(12.dp))

                                    InstagramPasswordTextField(
                                        value = passwordInput,
                                        onValueChange = {
                                            passwordInput = it
                                            statusBanner = null
                                        },
                                        passwordVisible = passwordVisible,
                                        onToggleVisibility = { passwordVisible = !passwordVisible },
                                        onDone = ::submitEmailLogin,
                                        modifier = Modifier.testTag("auth_password_input")
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        TextButton(
                                            onClick = {
                                                statusBanner = null
                                                if (resetEmailInput.isBlank() && emailInput.isNotBlank()) {
                                                    resetEmailInput = emailInput
                                                }
                                                screenMode = AuthScreenMode.FORGOT_PASSWORD
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("forgot_password_button")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.auth_forgot_password_cta),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = InstagramBlue
                                            )
                                        }
                                    }

                                    AuthBannerAnimated(banner = statusBanner)

                                    Spacer(Modifier.height(8.dp))

                                    Button(
                                        onClick = ::submitEmailLogin,
                                        enabled = !isGoogleLoading && !isEmailAuthLoading,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = InstagramBlue,
                                            contentColor = InstagramWhite,
                                            disabledContainerColor = InstagramBlue.copy(alpha = 0.45f),
                                            disabledContentColor = InstagramWhite
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("auth_login_submit_button")
                                    ) {
                                        if (isEmailAuthLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = InstagramWhite,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.auth_login_cta),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            )
                                        )
                                    }

                                    Spacer(Modifier.height(22.dp))
                                    OrHairlineDivider()
                                    Spacer(Modifier.height(22.dp))

                                    GoogleAuthOutlinedButton(
                                        label = stringResource(R.string.auth_google_continue_cta),
                                        isLoading = isGoogleLoading,
                                        onClick = ::triggerGoogleAuth
                                    )
                                }

                                AuthScreenMode.SIGN_UP -> {
                                    EsosaHeaderWordmark(height = 46.dp)
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Sign up to see photos and reels from your friends.",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = InstagramMutedText,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(20.dp))

                                    GoogleAuthPrimaryButton(
                                        label = stringResource(R.string.auth_google_signup_cta),
                                        isLoading = isGoogleLoading,
                                        onClick = ::triggerGoogleAuth
                                    )

                                    Spacer(Modifier.height(18.dp))
                                    OrHairlineDivider()
                                    Spacer(Modifier.height(18.dp))

                                    InstagramAuthTextField(
                                        value = emailInput,
                                        onValueChange = {
                                            emailInput = it
                                            statusBanner = null
                                        },
                                        placeholder = "Email",
                                        leadingIcon = Icons.Outlined.Email,
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next,
                                        modifier = Modifier.testTag("auth_email_input")
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    InstagramAuthTextField(
                                        value = fullNameInput,
                                        onValueChange = {
                                            if (it.length <= 48) {
                                                fullNameInput = it
                                                statusBanner = null
                                            }
                                        },
                                        placeholder = "Full Name",
                                        leadingIcon = Icons.Outlined.Person,
                                        keyboardType = KeyboardType.Text,
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Next,
                                        modifier = Modifier.testTag("auth_fullname_input")
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    InstagramAuthTextField(
                                        value = usernameInput,
                                        onValueChange = { raw ->
                                            val sanitized = raw.lowercase().filter {
                                                it.isLetterOrDigit() || it == '.' || it == '_'
                                            }.take(24)
                                            usernameInput = sanitized
                                            statusBanner = null
                                        },
                                        placeholder = "Username",
                                        leadingIcon = Icons.Outlined.AlternateEmail,
                                        keyboardType = KeyboardType.Ascii,
                                        imeAction = ImeAction.Next,
                                        modifier = Modifier.testTag("auth_username_input")
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    InstagramPasswordTextField(
                                        value = passwordInput,
                                        onValueChange = {
                                            passwordInput = it
                                            statusBanner = null
                                        },
                                        passwordVisible = passwordVisible,
                                        onToggleVisibility = { passwordVisible = !passwordVisible },
                                        onDone = ::submitEmailSignUp,
                                        modifier = Modifier.testTag("auth_password_input")
                                    )

                                    Spacer(Modifier.height(10.dp))
                                    AuthBannerAnimated(banner = statusBanner)
                                    Spacer(Modifier.height(6.dp))

                                    Button(
                                        onClick = ::submitEmailSignUp,
                                        enabled = !isGoogleLoading && !isEmailAuthLoading,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = InstagramBlue,
                                            contentColor = InstagramWhite,
                                            disabledContainerColor = InstagramBlue.copy(alpha = 0.45f),
                                            disabledContentColor = InstagramWhite
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("auth_signup_submit_button")
                                    ) {
                                        if (isEmailAuthLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = InstagramWhite,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.auth_signup_cta),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            )
                                        )
                                    }

                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "By signing up, you agree to Esosa's Terms and Community Guidelines.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InstagramMutedText,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                AuthScreenMode.FORGOT_PASSWORD -> {
                                    Box(
                                        modifier = Modifier
                                            .size(92.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, InstagramBlack, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Lock,
                                            contentDescription = null,
                                            tint = InstagramBlack,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(18.dp))

                                    Text(
                                        text = stringResource(R.string.auth_forgot_password_title),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = InstagramBlack,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.auth_forgot_password_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InstagramMutedText,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(22.dp))

                                    InstagramAuthTextField(
                                        value = resetEmailInput,
                                        onValueChange = {
                                            resetEmailInput = it
                                            statusBanner = null
                                        },
                                        placeholder = "Email",
                                        leadingIcon = Icons.Outlined.Email,
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Done,
                                        onDone = ::submitForgotPassword,
                                        modifier = Modifier.testTag("forgot_password_email_input")
                                    )

                                    Spacer(Modifier.height(10.dp))
                                    AuthBannerAnimated(banner = statusBanner)
                                    Spacer(Modifier.height(6.dp))

                                    Button(
                                        onClick = ::submitForgotPassword,
                                        enabled = !isResetSending && !isGoogleLoading,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = InstagramBlue,
                                            contentColor = InstagramWhite
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("forgot_password_submit_button")
                                    ) {
                                        if (isResetSending) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = InstagramWhite,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Text(
                                            text = stringResource(R.string.auth_send_reset_link),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            )
                                        )
                                    }

                                    Spacer(Modifier.height(22.dp))
                                    OrHairlineDivider()
                                    Spacer(Modifier.height(16.dp))

                                    GoogleAuthOutlinedButton(
                                        label = stringResource(R.string.auth_google_continue_cta),
                                        isLoading = isGoogleLoading,
                                        onClick = ::triggerGoogleAuth
                                    )

                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            statusBanner = null
                                            screenMode = AuthScreenMode.SIGN_UP
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("switch_to_signup_from_forgot")
                                    ) {
                                        Text(
                                            text = "Create new account",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = InstagramBlack
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Classic Instagram pinned bottom switcher bar
            HorizontalDivider(thickness = 0.8.dp, color = InstagramHairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (screenMode) {
                    AuthScreenMode.LOGIN -> {
                        Text(
                            text = stringResource(R.string.auth_no_account_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InstagramMutedText
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                statusBanner = null
                                screenMode = AuthScreenMode.SIGN_UP
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("switch_to_signup_button")
                        ) {
                            Text(
                                text = stringResource(R.string.auth_signup_cta) + ".",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = InstagramBlue
                            )
                        }
                    }

                    AuthScreenMode.SIGN_UP -> {
                        Text(
                            text = stringResource(R.string.auth_have_account_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InstagramMutedText
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                statusBanner = null
                                screenMode = AuthScreenMode.LOGIN
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("switch_to_login_button")
                        ) {
                            Text(
                                text = stringResource(R.string.auth_login_cta) + ".",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = InstagramBlue
                            )
                        }
                    }

                    AuthScreenMode.FORGOT_PASSWORD -> {
                        TextButton(
                            onClick = {
                                statusBanner = null
                                screenMode = AuthScreenMode.LOGIN
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("back_to_login_footer")
                        ) {
                            Text(
                                text = stringResource(R.string.auth_back_to_login),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = InstagramBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstagramAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = InstagramMutedText,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = InstagramMutedText,
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = InstagramOffWhite,
            unfocusedContainerColor = InstagramOffWhite,
            focusedBorderColor = InstagramMutedText,
            unfocusedBorderColor = InstagramHairline,
            focusedTextColor = InstagramBlack,
            unfocusedTextColor = InstagramBlack,
            cursorColor = InstagramBlue
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone?.invoke() }
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun InstagramPasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = "Password",
                color = InstagramMutedText,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = InstagramMutedText,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("auth_password_visibility_toggle")
            ) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    tint = InstagramMutedText
                )
            }
        },
        singleLine = true,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = InstagramOffWhite,
            unfocusedContainerColor = InstagramOffWhite,
            focusedBorderColor = InstagramMutedText,
            unfocusedBorderColor = InstagramHairline,
            focusedTextColor = InstagramBlack,
            unfocusedTextColor = InstagramBlack,
            cursorColor = InstagramBlue
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun GoogleAuthPrimaryButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = InstagramBlue,
            contentColor = InstagramWhite
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("google_sign_in_button")
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = InstagramWhite,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Connecting…",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(InstagramWhite),
                contentAlignment = Alignment.Center
            ) {
                GoogleGEmblem(size = 14.dp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
        }
    }
}

@Composable
private fun GoogleAuthOutlinedButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, InstagramHairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = InstagramWhite,
            contentColor = InstagramBlueDeep
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("google_sign_in_button")
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = InstagramBlue,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Signing in…",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = InstagramBlue
            )
        } else {
            GoogleGEmblem(size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = InstagramBlack
            )
        }
    }
}

@Composable
private fun OrHairlineDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.8.dp,
            color = InstagramHairline
        )
        Text(
            text = "OR",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            ),
            color = InstagramMutedText,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.8.dp,
            color = InstagramHairline
        )
    }
}

@Composable
private fun AuthBannerAnimated(banner: AuthStatusBanner?) {
    AnimatedVisibility(
        visible = banner != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        if (banner != null) {
            val containerColor: Color
            val contentColor: Color
            val icon: ImageVector
            val tag: String
            when (banner.tone) {
                AuthBannerTone.ERROR -> {
                    containerColor = MaterialTheme.colorScheme.errorContainer
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                    icon = Icons.Outlined.ErrorOutline
                    tag = "auth_error_banner"
                }
                AuthBannerTone.INFO -> {
                    containerColor = InstagramBlueSoft
                    contentColor = InstagramBlueDeep
                    icon = Icons.Outlined.Info
                    tag = "auth_info_banner"
                }
                AuthBannerTone.SUCCESS -> {
                    containerColor = EsosaOnlineGreen.copy(alpha = 0.14f)
                    contentColor = Color(0xFF14532D)
                    icon = Icons.Outlined.CheckCircle
                    tag = "auth_success_banner"
                }
            }
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag(tag)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(18.dp)
                    )
                    Text(
                        text = banner.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleGEmblem(size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.22f
        val inset = strokeWidth / 2f
        val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -15f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 55f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 150f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 210f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(this.size.width * 0.52f, this.size.height * 0.5f),
            end = Offset(this.size.width * 0.94f, this.size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}
