package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
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
    var countryCode by rememberSaveable { mutableStateOf("+1") }
    var credentialInput by rememberSaveable {
        mutableStateOf(
            if (existingProfile.phoneOrEmail.contains("@")) existingProfile.phoneOrEmail
            else "555 234 8910"
        )
    }
    var otpInput by rememberSaveable { mutableStateOf("") }
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
            label = "auth_step"
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
                            modifier = Modifier.widthIn(max = 460.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                EsosaBrandLogo(size = 44.dp)
                                Text(
                                    text = "Esosa",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Image(
                                painter = painterResource(id = R.drawable.img_auth_hero),
                                contentDescription = "Esosa messaging illustration",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            )

                            Text(
                                text = "Private messaging, crafted for clarity",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Direct chats, groups, broadcast channels, voice notes, and crystal-clear calls — unified in one responsive workspace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FeatureHighlightRow(
                                        icon = Icons.Filled.Lock,
                                        title = "Disappearing Messages & 2-Step PIN",
                                        subtitle = "Control privacy, read receipts & active device sessions"
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Filled.Groups,
                                        title = "Groups & Broadcast Channels",
                                        subtitle = "Admin roles, comments, reactions, polls & pinned posts"
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Filled.Call,
                                        title = "HD Voice & Video Calls",
                                        subtitle = "Instant call history, mute, speaker & camera controls"
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    isLoginMode = false
                                    step = OnboardingStep.CREDENTIALS
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("auth_get_started_button")
                            ) {
                                Text("Get Started", style = MaterialTheme.typography.titleSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    isLoginMode = true
                                    step = OnboardingStep.CREDENTIALS
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("auth_login_button")
                            ) {
                                Text("Log In to Existing Account")
                            }
                        }
                    }
                }

                OnboardingStep.CREDENTIALS -> {
                    val formattedAddress = if (authMethod == "phone") {
                        "$countryCode ${credentialInput.trim()}"
                    } else {
                        credentialInput.trim()
                    }
                    val isCredentialValid = if (authMethod == "phone") {
                        credentialInput.count { it.isDigit() } >= 6
                    } else {
                        credentialInput.contains("@") && credentialInput.contains(".")
                    }

                    OnboardingCardShell(
                        title = if (isLoginMode) "Welcome back" else "Create your account",
                        subtitle = "Verify with a one-time code via phone number or email.",
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
                                    if (credentialInput.contains("@")) credentialInput = "555 234 8910"
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
                                    if (!credentialInput.contains("@")) credentialInput = "alex@esosa.app"
                                },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Email") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_method_email")
                            )
                        }

                        if (authMethod == "phone") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("+1", "+44", "+234", "+81").forEach { code ->
                                    FilterChip(
                                        selected = countryCode == code,
                                        onClick = { countryCode = code },
                                        label = { Text(code) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = credentialInput,
                                onValueChange = { if (it.length <= 24) credentialInput = it },
                                label = { Text("Phone Number") },
                                prefix = { Text("$countryCode ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_credential_input")
                            )
                        } else {
                            OutlinedTextField(
                                value = credentialInput,
                                onValueChange = { if (it.length <= 80) credentialInput = it },
                                label = { Text("Email Address") },
                                placeholder = { Text("you@domain.com") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_credential_input")
                            )
                        }

                        Button(
                            onClick = {
                                otpError = null
                                otpInput = ""
                                step = OnboardingStep.OTP_VERIFY
                            },
                            enabled = isCredentialValid,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_send_otp_button")
                        ) {
                            Text("Send Verification Code")
                        }

                        Text(
                            text = "We will send a 6-digit confirmation code to $formattedAddress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OnboardingStep.OTP_VERIFY -> {
                    val destinationLabel = if (authMethod == "phone") {
                        "$countryCode ${credentialInput.trim()}"
                    } else {
                        credentialInput.trim()
                    }
                    OnboardingCardShell(
                        title = "Enter verification code",
                        subtitle = "Code sent to $destinationLabel",
                        onBack = { step = OnboardingStep.CREDENTIALS }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Demo OTP: ${LocalProfilePreferences.DEMO_OTP_CODE}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                AssistChip(
                                    onClick = {
                                        otpInput = LocalProfilePreferences.DEMO_OTP_CODE
                                        otpError = null
                                    },
                                    label = { Text("Auto-fill") },
                                    modifier = Modifier.testTag("auth_autofill_otp_button")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(6)
                                otpInput = digits
                                otpError = null
                            },
                            label = { Text("6-digit OTP Code") },
                            placeholder = { Text("123456") },
                            singleLine = true,
                            isError = otpError != null,
                            supportingText = otpError?.let { msg -> { Text(msg) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_otp_input")
                        )

                        Button(
                            onClick = {
                                if (otpInput == LocalProfilePreferences.DEMO_OTP_CODE || otpInput.length == 6) {
                                    if (isLoginMode) {
                                        onCompleteLogin(authMethod, destinationLabel)
                                    } else {
                                        step = OnboardingStep.PROFILE_SETUP
                                    }
                                } else {
                                    otpError = "Enter 6 digits (demo code: ${LocalProfilePreferences.DEMO_OTP_CODE})."
                                }
                            },
                            enabled = otpInput.length == 6,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_verify_otp_button")
                        ) {
                            Text("Verify & Continue")
                        }

                        TextButton(
                            onClick = { otpInput = LocalProfilePreferences.DEMO_OTP_CODE },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Resend Code")
                        }
                    }
                }

                OnboardingStep.PROFILE_SETUP -> {
                    val destinationLabel = if (authMethod == "phone") {
                        "$countryCode ${credentialInput.trim()}"
                    } else {
                        credentialInput.trim()
                    }
                    OnboardingCardShell(
                        title = "Set up your profile",
                        subtitle = "Choose how teammates and friends see you on Esosa.",
                        onBack = { step = OnboardingStep.OTP_VERIFY }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("default", "maya", "liam", "esosa").forEach { preset ->
                                val selected = avatarKey == preset
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .border(
                                            BorderStroke(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            CircleShape
                                        )
                                        .padding(3.dp)
                                        .clickable { avatarKey = preset }
                                        .testTag("auth_avatar_$preset"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TelegramAvatarCircle(
                                        title = displayName.ifBlank { "Esosa" },
                                        seedKey = preset,
                                        chatKind = ChatKind.DIRECT,
                                        avatarKey = preset,
                                        size = 48.dp
                                    )
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected avatar",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { if (it.length <= 40) displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_display_name_input")
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { if (it.length <= 32) username = it.filter { ch -> !ch.isWhitespace() } },
                            label = { Text("Username") },
                            prefix = { Text("@") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_username_input")
                        )

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 120) bio = it },
                            label = { Text("Bio") },
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_bio_input")
                        )

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
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_finish_setup_button")
                        ) {
                            Text("Enter Esosa")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingCardShell(
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
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("auth_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    EsosaBrandLogo(size = 32.dp)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
