package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.EsosaBrandLogo
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CountryItem(val name: String, val code: String, val flag: String)

val euAndAfricanCountries = listOf(
    // European Union (EU) 27 Member States
    CountryItem("Austria", "+43", "🇦🇹"),
    CountryItem("Belgium", "+32", "🇧🇪"),
    CountryItem("Bulgaria", "+359", "🇧🇬"),
    CountryItem("Croatia", "+385", "🇭🇷"),
    CountryItem("Cyprus", "+357", "🇨🇾"),
    CountryItem("Czech Republic", "+420", "🇨🇿"),
    CountryItem("Denmark", "+45", "🇩🇰"),
    CountryItem("Estonia", "+372", "🇪🇪"),
    CountryItem("Finland", "+358", "🇫🇮"),
    CountryItem("France", "+33", "🇫🇷"),
    CountryItem("Germany", "+49", "🇩🇪"),
    CountryItem("Greece", "+30", "🇬🇷"),
    CountryItem("Hungary", "+36", "🇭🇺"),
    CountryItem("Ireland", "+353", "🇮🇪"),
    CountryItem("Italy", "+39", "🇮🇹"),
    CountryItem("Latvia", "+371", "🇱🇻"),
    CountryItem("Lithuania", "+370", "🇱🇹"),
    CountryItem("Luxembourg", "+352", "🇱🇺"),
    CountryItem("Malta", "+356", "🇲🇹"),
    CountryItem("Netherlands", "+31", "🇳🇱"),
    CountryItem("Poland", "+48", "🇵🇱"),
    CountryItem("Portugal", "+351", "🇵🇹"),
    CountryItem("Romania", "+40", "🇷🇴"),
    CountryItem("Slovakia", "+421", "🇸🇰"),
    CountryItem("Slovenia", "+386", "🇸🇮"),
    CountryItem("Spain", "+34", "🇪🇸"),
    CountryItem("Sweden", "+46", "🇸🇪"),

    // African Countries (54 nations)
    CountryItem("Algeria", "+213", "🇩🇿"),
    CountryItem("Angola", "+244", "🇦🇴"),
    CountryItem("Benin", "+229", "🇧🇯"),
    CountryItem("Botswana", "+267", "🇧🇼"),
    CountryItem("Burkina Faso", "+226", "🇧🇫"),
    CountryItem("Burundi", "+257", "🇧🇮"),
    CountryItem("Cabo Verde", "+238", "🇨🇻"),
    CountryItem("Cameroon", "+237", "🇨🇲"),
    CountryItem("Central African Republic", "+236", "🇨🇫"),
    CountryItem("Chad", "+235", "🇹🇩"),
    CountryItem("Comoros", "+269", "🇰🇲"),
    CountryItem("Congo", "+242", "🇨🇬"),
    CountryItem("Democratic Republic of the Congo", "+243", "🇨🇩"),
    CountryItem("Djibouti", "+253", "🇩🇯"),
    CountryItem("Egypt", "+20", "🇪🇬"),
    CountryItem("Equatorial Guinea", "+240", "🇬🇶"),
    CountryItem("Eritrea", "+291", "🇪🇷"),
    CountryItem("Eswatini", "+268", "🇸🇿"),
    CountryItem("Ethiopia", "+251", "🇪🇹"),
    CountryItem("Gabon", "+241", "🇬🇦"),
    CountryItem("Gambia", "+220", "🇬🇲"),
    CountryItem("Ghana", "+233", "🇬🇭"),
    CountryItem("Guinea", "+224", "🇬🇳"),
    CountryItem("Guinea-Bissau", "+245", "🇬🇼"),
    CountryItem("Ivory Coast", "+225", "🇨🇮"),
    CountryItem("Kenya", "+254", "🇰🇪"),
    CountryItem("Lesotho", "+266", "🇱🇸"),
    CountryItem("Liberia", "+231", "🇱🇷"),
    CountryItem("Libya", "+218", "🇱🇾"),
    CountryItem("Madagascar", "+261", "🇲🇬"),
    CountryItem("Malawi", "+265", "🇲🇼"),
    CountryItem("Mali", "+223", "🇲🇱"),
    CountryItem("Mauritania", "+222", "🇲🇷"),
    CountryItem("Mauritius", "+230", "🇲🇺"),
    CountryItem("Morocco", "+212", "🇲🇦"),
    CountryItem("Mozambique", "+258", "🇲🇿"),
    CountryItem("Namibia", "+264", "🇳🇦"),
    CountryItem("Niger", "+227", "🇳🇪"),
    CountryItem("Nigeria", "+234", "🇳🇬"),
    CountryItem("Rwanda", "+250", "🇷🇼"),
    CountryItem("Sao Tome and Principe", "+239", "🇸🇹"),
    CountryItem("Senegal", "+221", "🇸🇳"),
    CountryItem("Seychelles", "+248", "🇸🇨"),
    CountryItem("Sierra Leone", "+232", "🇸🇱"),
    CountryItem("Somalia", "+252", "🇸🇴"),
    CountryItem("South Africa", "+27", "🇿🇦"),
    CountryItem("South Sudan", "+211", "🇸🇸"),
    CountryItem("Sudan", "+249", "🇸🇩"),
    CountryItem("Tanzania", "+255", "🇹🇿"),
    CountryItem("Togo", "+228", "🇹🇬"),
    CountryItem("Tunisia", "+216", "🇹🇳"),
    CountryItem("Uganda", "+256", "🇺🇬"),
    CountryItem("Zambia", "+260", "🇿🇲"),
    CountryItem("Zimbabwe", "+263", "🇿🇼"),

    // Global Hubs
    CountryItem("United States", "+1", "🇺🇸"),
    CountryItem("United Kingdom", "+44", "🇬🇧"),
    CountryItem("Canada", "+1", "🇨🇦"),
    CountryItem("Australia", "+61", "🇦🇺"),
    CountryItem("India", "+91", "🇮🇳"),
    CountryItem("Japan", "+81", "🇯🇵")
).sortedBy { it.name }

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
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
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
                onAuthError("Unexpected Google credential response.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please check your connection and try again.")
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
    onAuthSuccess: (optionalPhoneNumber: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var showPhoneSection by rememberSaveable { mutableStateOf(false) }
    var selectedCountryName by rememberSaveable { mutableStateOf("Nigeria") }
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }
    var localPhoneInput by rememberSaveable {
        mutableStateOf(
            if (!existingProfile.phoneOrEmail.contains("@")) existingProfile.phoneOrEmail else ""
        )
    }

    val selectedCountry = remember(selectedCountryName) {
        euAndAfricanCountries.find { it.name == selectedCountryName } ?: euAndAfricanCountries.first()
    }

    val formattedOptionalPhone = remember(localPhoneInput, selectedCountry) {
        val cleaned = localPhoneInput.trim()
        if (cleaned.isEmpty()) ""
        else if (cleaned.startsWith("+")) cleaned
        else "${selectedCountry.code} $cleaned"
    }

    if (showCountryPicker) {
        BackHandler {
            showCountryPicker = false
            countrySearchQuery = ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EsosaBrandLogo(size = 48.dp)
                    }
                }

                Text(
                    text = "Esosa Messenger",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Real-time cloud messaging, groups, channels, and HD voice calls synced securely with Cloud Firestore.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FeatureHighlightRow(
                            icon = Icons.Filled.CloudDone,
                            title = "Real-Time Cloud Sync",
                            subtitle = "Messages, typing status & profile saved across devices"
                        )
                        FeatureHighlightRow(
                            icon = Icons.Filled.Groups,
                            title = "Groups & Channels",
                            subtitle = "Invite teammates by email or shareable room code"
                        )
                        FeatureHighlightRow(
                            icon = Icons.Filled.Lock,
                            title = "Google Account Security",
                            subtitle = "Zero-trust per-user access control powered by Firebase Auth"
                        )
                        FeatureHighlightRow(
                            icon = Icons.Filled.Call,
                            title = "Crystal-Clear Calls",
                            subtitle = "HD voice and video communication"
                        )
                    }
                }

                // Optional EU & African Phone Linker Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showPhoneSection = !showPhoneSection }
                                .padding(vertical = 4.dp)
                                .testTag("auth_toggle_phone_section"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Link EU / Africa Phone Number (Optional)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (formattedOptionalPhone.isNotBlank()) formattedOptionalPhone
                                        else "All EU & African dial codes supported",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showPhoneSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showPhoneSection) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showPhoneSection,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCountryPicker = true }
                                        .testTag("auth_country_selector")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(text = selectedCountry.flag, fontSize = 22.sp)
                                            Column {
                                                Text(
                                                    text = selectedCountry.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = selectedCountry.code,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Change",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = localPhoneInput,
                                    onValueChange = { if (it.length <= 24) localPhoneInput = it },
                                    label = { Text("Phone Number") },
                                    placeholder = { Text("801 234 5678") },
                                    prefix = { Text("${selectedCountry.code} ") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_phone_input")
                                )
                            }
                        }
                    }
                }

                if (authError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = authError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        keyboardController?.hide()
                        authError = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess(formattedOptionalPhone)
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                authError = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Signing in…",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    } else {
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Text(
                    text = "Sign in with your Google Account to synchronize your chats, contacts, and profile in Cloud Firestore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showCountryPicker) {
            AlertDialog(
                onDismissRequest = {
                    showCountryPicker = false
                    countrySearchQuery = ""
                },
                title = {
                    Text(
                        text = "Select Country (EU & Africa)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    val filteredCountries = remember(countrySearchQuery) {
                        if (countrySearchQuery.isBlank()) {
                            euAndAfricanCountries
                        } else {
                            euAndAfricanCountries.filter {
                                it.name.contains(countrySearchQuery, ignoreCase = true) ||
                                    it.code.contains(countrySearchQuery, ignoreCase = true)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = countrySearchQuery,
                            onValueChange = { countrySearchQuery = it },
                            placeholder = { Text("Search country or code...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("country_search_input")
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filteredCountries, key = { "${it.name}_${it.code}" }) { country ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCountryName = country.name
                                            showCountryPicker = false
                                            countrySearchQuery = ""
                                        },
                                    color = if (selectedCountryName == country.name)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(text = country.flag, fontSize = 20.sp)
                                            Text(
                                                text = country.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (selectedCountryName == country.name) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        Text(
                                            text = country.code,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            showCountryPicker = false
                            countrySearchQuery = ""
                        }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
