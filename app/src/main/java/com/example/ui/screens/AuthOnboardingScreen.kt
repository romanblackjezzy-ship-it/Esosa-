package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.LocalProfilePreferences
import com.example.data.model.ChatKind
import com.example.data.model.UserProfile
import com.example.ui.components.EsosaBrandLogo
import com.example.ui.components.TelegramAvatarCircle

private enum class OnboardingStep {
    WELCOME,
    CREDENTIALS,
    OTP_VERIFY,
    PROFILE_SETUP
}

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

@Composable
fun AuthOnboardingScreen(
    existingProfile: UserProfile,
    onCompleteSignUp: (
        authMethod: String,
        phoneOrEmail: String,
        displayName: String,
        username: String,
        bio: String,
        avatarKey: String
    ) -> Unit,
    onCompleteLogin: (authMethod: String, phoneOrEmail: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var isLoginMode by rememberSaveable { mutableStateOf(false) }
    var authMethod by rememberSaveable { mutableStateOf("phone") }
    var selectedCountryCode by rememberSaveable { mutableStateOf("+234") } // Default Nigeria or +1
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }

    val selectedCountry = euAndAfricanCountries.find { it.code == selectedCountryCode } ?: euAndAfricanCountries[0]

    var credentialInput by rememberSaveable {
        mutableStateOf(
            if (existingProfile.phoneOrEmail.contains("@")) existingProfile.phoneOrEmail
            else ""
        )
    }
    var otpDigits by rememberSaveable { mutableStateOf(listOf("", "", "", "", "", "")) }
    var otpError by remember { mutableStateOf<String?>(null) }
    var displayName by rememberSaveable { mutableStateOf(existingProfile.displayName) }
    var username by rememberSaveable { mutableStateOf(existingProfile.username) }
    var bio by rememberSaveable { mutableStateOf(existingProfile.bio) }
    var avatarKey by rememberSaveable { mutableStateOf(existingProfile.avatarKey) }

    if (step != OnboardingStep.WELCOME) {
        BackHandler {
            step = when (step) {
                OnboardingStep.CREDENTIALS -> OnboardingStep.WELCOME
                OnboardingStep.OTP_VERIFY -> OnboardingStep.CREDENTIALS
                OnboardingStep.PROFILE_SETUP -> OnboardingStep.OTP_VERIFY
                OnboardingStep.WELCOME -> OnboardingStep.WELCOME
            }
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
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                fadeIn() + slideInHorizontally { if (targetState.ordinal > initialState.ordinal) it / 3 else -it / 3 } togetherWith
                    fadeOut() + slideOutHorizontally { if (targetState.ordinal > initialState.ordinal) -it / 3 else it / 3 }
            },
            label = "telegram_auth_transition"
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.WELCOME -> {
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
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    EsosaBrandLogo(size = 46.dp)
                                }
                            }

                            Text(
                                text = "Esosa Messenger",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                text = "Fast. Secure. Private messaging supporting all EU and African nations.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(4.dp))

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
                                        icon = Icons.Filled.Lock,
                                        title = "End-to-End Privacy",
                                        subtitle = "Disappearing messages & 2-step PIN protection"
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Filled.Groups,
                                        title = "Groups & Channels",
                                        subtitle = "Admin controls, polls, comments and reactions"
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Filled.Call,
                                        title = "Crystal-Clear Calls",
                                        subtitle = "HD voice and video communication"
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    isLoginMode = false
                                    step = OnboardingStep.CREDENTIALS
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("auth_get_started_button")
                            ) {
                                Text(
                                    "Start Messaging",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    isLoginMode = true
                                    step = OnboardingStep.CREDENTIALS
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5f.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("auth_login_button")
                            ) {
                                Text(
                                    "Log In to Existing Account",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }

                OnboardingStep.CREDENTIALS -> {
                    val formattedAddress = if (authMethod == "phone") {
                        "${selectedCountry.code} ${credentialInput.trim()}"
                    } else {
                        credentialInput.trim()
                    }
                    val isCredentialValid = if (authMethod == "phone") {
                        credentialInput.count { it.isDigit() } >= 6
                    } else {
                        credentialInput.contains("@") && credentialInput.contains(".")
                    }

                    TelegramAuthCardShell(
                        title = if (isLoginMode) "Enter your account" else "Your phone or email",
                        subtitle = if (authMethod == "phone") {
                            "Select your country and enter your phone number to sign in or register."
                        } else {
                            "We will send a verification code to your email address."
                        },
                        onBack = { step = OnboardingStep.WELCOME }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = authMethod == "phone",
                                onClick = {
                                    authMethod = "phone"
                                    if (credentialInput.contains("@")) credentialInput = ""
                                },
                                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Phone Number") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_method_phone")
                            )
                            FilterChip(
                                selected = authMethod == "email",
                                onClick = {
                                    authMethod = "email"
                                    if (!credentialInput.contains("@")) credentialInput = ""
                                },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Email Address") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_method_email")
                            )
                        }

                        if (authMethod == "phone") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Country (EU & Africa Supported)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCountryPicker = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
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
                            }
                        }

                        OutlinedTextField(
                            value = credentialInput,
                            onValueChange = { if (it.length <= 80) credentialInput = it },
                            label = { Text(if (authMethod == "phone") "Phone Number" else "Email Address") },
                            placeholder = { Text(if (authMethod == "phone") "801 234 5678" else "you@domain.com") },
                            prefix = if (authMethod == "phone") { { Text("${selectedCountry.code} ") } } else null,
                            leadingIcon = if (authMethod == "email") { { Icon(Icons.Filled.Email, contentDescription = null) } } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (authMethod == "phone") KeyboardType.Phone else KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_credential_input")
                        )

                        Spacer(Modifier.height(4.dp))

                        val keyboardController = LocalSoftwareKeyboardController.current
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                otpDigits = listOf("", "", "", "", "", "")
                                otpError = null
                                step = OnboardingStep.OTP_VERIFY
                            },
                            enabled = isCredentialValid,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_send_otp_button")
                        ) {
                            Text(
                                "Continue",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Text(
                            text = "A secure verification code will be sent to $formattedAddress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OnboardingStep.OTP_VERIFY -> {
                    val destinationLabel = if (authMethod == "phone") {
                        "${selectedCountry.code} ${credentialInput.trim()}"
                    } else {
                        credentialInput.trim()
                    }

                    TelegramAuthCardShell(
                        title = "Verify your code",
                        subtitle = "We have sent a verification code to $destinationLabel",
                        onBack = { step = OnboardingStep.CREDENTIALS }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Verification Code",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Code: ${LocalProfilePreferences.DEMO_OTP_CODE}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                AssistChip(
                                    onClick = {
                                        val code = LocalProfilePreferences.DEMO_OTP_CODE
                                        otpDigits = code.map { it.toString() }
                                        otpError = null
                                    },
                                    label = { Text("Auto-fill") },
                                    modifier = Modifier.testTag("auth_autofill_otp_button")
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val focusRequesters = remember { List(6) { FocusRequester() } }
                        val keyboardController = LocalSoftwareKeyboardController.current

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (i in 0 until 6) {
                                    OutlinedTextField(
                                        value = otpDigits[i],
                                        onValueChange = { rawInput ->
                                            val filtered = rawInput.filter { it.isDigit() }
                                            if (filtered.isNotEmpty()) {
                                                val char = filtered.last().toString()
                                                val updated = otpDigits.toMutableList()
                                                updated[i] = char
                                                otpDigits = updated
                                                otpError = null

                                                if (i < 5) {
                                                    focusRequesters[i + 1].requestFocus()
                                                } else {
                                                    keyboardController?.hide()
                                                }
                                            } else {
                                                val updated = otpDigits.toMutableList()
                                                updated[i] = ""
                                                otpDigits = updated
                                                if (i > 0 && otpDigits[i].isEmpty()) {
                                                    focusRequesters[i - 1].requestFocus()
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.titleLarge.copy(
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 56.dp)
                                            .focusRequester(focusRequesters[i])
                                            .testTag("auth_otp_digit_$i")
                                    )
                                }
                            }

                            if (otpError != null) {
                                Text(
                                    text = otpError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val fullOtp = otpDigits.joinToString("")
                        val isOtpComplete = fullOtp.length == 6

                        Button(
                            onClick = {
                                if (fullOtp == LocalProfilePreferences.DEMO_OTP_CODE || fullOtp.length == 6) {
                                    if (isLoginMode) {
                                        onCompleteLogin(authMethod, destinationLabel)
                                    } else {
                                        step = OnboardingStep.PROFILE_SETUP
                                    }
                                } else {
                                    otpError = "Please enter all 6 digits."
                                }
                            },
                            enabled = isOtpComplete,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_verify_otp_button")
                        ) {
                            Text("Verify Code", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        }

                        TextButton(
                            onClick = {
                                otpDigits = listOf("", "", "", "", "", "")
                                otpError = null
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Didn't get the code? Resend")
                        }
                    }
                }

                OnboardingStep.PROFILE_SETUP -> {
                    val destinationLabel = if (authMethod == "phone") {
                        "${selectedCountry.code} ${credentialInput.trim()}"
                    } else {
                        credentialInput.trim()
                    }

                    TelegramAuthCardShell(
                        title = "Your Profile",
                        subtitle = "Enter your name and add a profile photo for your Esosa account.",
                        onBack = { step = OnboardingStep.OTP_VERIFY }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelegramAvatarCircle(
                                title = displayName.ifBlank { "Esosa" },
                                seedKey = avatarKey,
                                chatKind = ChatKind.DIRECT,
                                avatarKey = avatarKey,
                                size = 80.dp
                            )
                            Text(
                                text = "Choose Avatar Accent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("default", "ocean", "emerald", "esosa").forEach { preset ->
                                    val selected = avatarKey == preset
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .border(
                                                BorderStroke(
                                                    width = if (selected) 3.dp else 1.dp,
                                                    color = if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                CircleShape
                                            )
                                            .padding(2.dp)
                                            .clickable { avatarKey = preset }
                                            .testTag("auth_avatar_$preset"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TelegramAvatarCircle(
                                            title = displayName.ifBlank { "Esosa" },
                                            seedKey = preset,
                                            chatKind = ChatKind.DIRECT,
                                            avatarKey = preset,
                                            size = 40.dp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { if (it.length <= 40) displayName = it },
                            label = { Text("First Name (required)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_display_name_input")
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { if (it.length <= 32) username = it.filter { ch -> !ch.isWhitespace() } },
                            label = { Text("Username (optional)") },
                            prefix = { Text("@") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_username_input")
                        )

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 120) bio = it },
                            label = { Text("Bio (optional)") },
                            maxLines = 2,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_bio_input")
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                onCompleteSignUp(
                                    authMethod,
                                    destinationLabel,
                                    displayName,
                                    username,
                                    bio,
                                    avatarKey
                                )
                            },
                            enabled = displayName.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_finish_setup_button")
                        ) {
                            Text(
                                "Start Messaging",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }

        // Country Selection Dialog (All EU & African Countries)
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filteredCountries) { country ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCountryCode = country.code
                                            showCountryPicker = false
                                            countrySearchQuery = ""
                                        },
                                    color = if (selectedCountryCode == country.code)
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
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }
                                        Text(
                                            text = country.code,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCountryPicker = false
                        countrySearchQuery = ""
                    }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun TelegramAuthCardShell(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    EsosaBrandLogo(size = 34.dp)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                content()
            }
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
