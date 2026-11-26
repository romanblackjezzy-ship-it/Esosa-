package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.core.app.ActivityCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.LocalProfilePreferences
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.local.EsosaDatabase
import com.example.data.media.VoiceCallAudioController
import com.example.data.model.CallType
import com.example.data.model.CreateFormat
import com.example.data.model.InAppInstallStage
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.UserProfile
import com.example.data.remote.FirebasePhotoStorageService
import com.example.data.remote.FirestoreChatSyncService
import com.example.data.remote.FirestoreSocialService
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.SocialRepository
import com.example.notifications.EsosaNotificationManager
import com.example.notifications.FcmTokenRegistrar
import com.example.ui.components.EsosaBrandLogo
import com.example.ui.components.EsosaWordmarkBanner
import com.example.ui.components.FlexibleUpdateFloatingBar
import com.example.ui.components.GlassUpdatePromptOverlay
import com.example.ui.components.InstagramCommentsSheet
import com.example.ui.components.InstagramPostDetailDialog
import com.example.ui.components.InstagramUserAvatar
import com.example.ui.components.SocialStoryViewerDialog
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AuthOnboardingScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.PendingSignupMetadata
import com.example.ui.screens.InstagramCreateScreen
import com.example.ui.screens.InstagramExploreScreen
import com.example.ui.screens.InstagramHomeScreen
import com.example.ui.screens.InstagramProfileScreen
import com.example.ui.screens.InstagramReelsScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.attemptAutoSignIn
import com.example.ui.screens.signOutFromGoogle
import com.example.ui.theme.EsosaTheme
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramHairline
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.RoomsViewModel
import com.example.ui.viewmodel.SocialViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class EsosaTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    EXPLORE("Explore", Icons.Filled.Search, Icons.Outlined.Search, "nav_explore"),
    CREATE("Create", Icons.Filled.AddBox, Icons.Outlined.AddBox, "nav_create"),
    REELS("Reels", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "nav_reels"),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
}

private enum class EsosaSecondaryRoute {
    MESSAGES,
    CALLS,
    SETTINGS
}

@Composable
fun EsosaApp(
    initialRoomId: String? = null,
    initialPostId: String? = null,
    initialStoryId: String? = null,
    initialTab: String? = null,
    initialShowUpdatePrompt: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val profilePreferences = remember(context) { LocalProfilePreferences(context) }
    val topLevelProfile by profilePreferences.profileFlow.collectAsStateWithLifecycle()

    val firestoreDb = remember(context) {
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
    }

    var firebaseUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isCheckingAutoSignIn by remember { mutableStateOf(Firebase.auth.currentUser == null) }
    var pendingSignupMetadata by remember { mutableStateOf(PendingSignupMetadata()) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { authState ->
            firebaseUser = authState.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { isCheckingAutoSignIn = false },
                onUnauthenticated = { isCheckingAutoSignIn = false },
                scope = scope
            )
        } else {
            isCheckingAutoSignIn = false
        }
    }

    val appUpdateViewModel: AppUpdateViewModel = viewModel(
        key = "app_update_checker",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AppUpdateViewModel(
                    appContext = app,
                    repository = AppUpdateRepository(),
                    preferences = AppUpdatePreferences(app),
                    notificationManager = EsosaNotificationManager(app)
                )
            }
        }
    )

    val playUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        appUpdateViewModel.onPlayUpdateFlowResult(result.resultCode)
    }

    LaunchedEffect(playUpdateLauncher) {
        appUpdateViewModel.registerPlayLauncher(playUpdateLauncher)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playUpdateLauncher) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appUpdateViewModel.onAppResumed(playUpdateLauncher)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val availableUpdate by appUpdateViewModel.availableUpdate.collectAsStateWithLifecycle()
    val showUpdateDialog by appUpdateViewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val installStage by appUpdateViewModel.installStage.collectAsStateWithLifecycle()
    val updateError by appUpdateViewModel.updateError.collectAsStateWithLifecycle()

    val openVerifiedHttpsUrl: (String) -> Unit = remember(context) {
        { url ->
            if (AppUpdateRepository.isSecureHttpsUrl(url)) {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdatesOnOpen()
    }

    LaunchedEffect(initialShowUpdatePrompt, availableUpdate) {
        if (initialShowUpdatePrompt && availableUpdate != null) {
            appUpdateViewModel.openUpdateDialog()
        }
    }

    EsosaTheme(themeMode = topLevelProfile.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            val signedInUser = firebaseUser
            when {
                signedInUser == null && isCheckingAutoSignIn -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EsosaWordmarkBanner(height = 48.dp)
                            CircularProgressIndicator(color = InstagramBlue)
                        }
                    }
                }

                signedInUser == null -> {
                    AuthOnboardingScreen(
                        existingProfile = topLevelProfile,
                        onAuthSuccess = { signupMetadata ->
                            pendingSignupMetadata = signupMetadata
                            firebaseUser = Firebase.auth.currentUser
                        }
                    )
                }

                else -> {
                    key(signedInUser.uid) {
                        val photoStorageService = remember {
                            FirebasePhotoStorageService.createConfigured(
                                app = FirebaseApp.getInstance(),
                                auth = Firebase.auth
                            )
                        }
                        val userRepository = remember(signedInUser.uid, firestoreDb) {
                            val app = context.applicationContext
                            ChatRepository(
                                appContext = app,
                                dao = EsosaDatabase.getInstance(app).esosaDao(),
                                profilePreferences = profilePreferences,
                                syncService = FirestoreChatSyncService(firestoreDb, Firebase.auth),
                                auth = Firebase.auth,
                                photoStorageService = photoStorageService
                            )
                        }
                        val socialRepository = remember(signedInUser.uid, firestoreDb) {
                            SocialRepository(
                                appContext = context.applicationContext,
                                socialService = FirestoreSocialService(firestoreDb, Firebase.auth),
                                photoStorageService = photoStorageService,
                                profilePreferences = profilePreferences,
                                auth = Firebase.auth
                            )
                        }

                        val roomsViewModel: RoomsViewModel = viewModel(
                            key = "esosa_rooms_${signedInUser.uid}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    RoomsViewModel(
                                        repository = userRepository,
                                        notificationManager = EsosaNotificationManager(app),
                                        selectionPreferences = ThreadSelectionPreferences(app)
                                    )
                                }
                            }
                        )
                        val socialViewModel: SocialViewModel = viewModel(
                            key = "esosa_social_${signedInUser.uid}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    SocialViewModel(
                                        repository = socialRepository,
                                        notificationManager = EsosaNotificationManager(app),
                                        profilePreferences = profilePreferences
                                    )
                                }
                            }
                        )

                        LaunchedEffect(signedInUser.uid) {
                            userRepository.syncSignedInUser(signedInUser)
                            val metadata = pendingSignupMetadata
                            if (metadata.fullName.isNotBlank() || metadata.username.isNotBlank()) {
                                val latest = profilePreferences.getProfile()
                                val cleanName = metadata.fullName.trim().ifBlank { latest.displayName }
                                val cleanUsername = metadata.username.trim()
                                    .removePrefix("@")
                                    .ifBlank { latest.username }
                                userRepository.saveProfileSetupToFirestore(
                                    displayName = cleanName,
                                    username = cleanUsername,
                                    bio = latest.bio,
                                    avatarKey = latest.avatarKey,
                                    phoneNumber = latest.phoneOrEmail
                                )
                                pendingSignupMetadata = PendingSignupMetadata()
                            }
                            FcmTokenRegistrar.syncCurrentToken(
                                context = context.applicationContext,
                                db = firestoreDb,
                                uid = signedInUser.uid
                            )
                        }

                        EsosaSocialShell(
                            initialRoomId = initialRoomId,
                            initialPostId = initialPostId,
                            initialStoryId = initialStoryId,
                            initialTab = initialTab,
                            socialViewModel = socialViewModel,
                            roomsViewModel = roomsViewModel,
                            appUpdateViewModel = appUpdateViewModel,
                            onSignOut = {
                                signOutFromGoogle(credentialManager, scope) {
                                    roomsViewModel.logout()
                                }
                            }
                        )
                    }
                }
            }

            GlassUpdatePromptOverlay(
                visible = showUpdateDialog || installStage is InAppInstallStage.MaintenanceBlocked,
                update = availableUpdate,
                installStage = installStage,
                errorState = updateError,
                onUpdateNow = {
                    appUpdateViewModel.startUpdate(
                        playLauncher = playUpdateLauncher,
                        onOpenVerifiedHttpsUrl = openVerifiedHttpsUrl
                    )
                },
                onRemindMeLater = { appUpdateViewModel.remindMeLater() },
                onSkipVersion = { appUpdateViewModel.skipAvailableUpdate() },
                onRestartAndInstall = { appUpdateViewModel.completeFlexibleUpdate() },
                onRetry = {
                    if (availableUpdate == null || installStage is InAppInstallStage.MaintenanceBlocked) {
                        appUpdateViewModel.checkForUpdatesNow()
                    } else {
                        appUpdateViewModel.startUpdate(
                            playLauncher = playUpdateLauncher,
                            onOpenVerifiedHttpsUrl = openVerifiedHttpsUrl
                        )
                    }
                },
                onOpenFallbackUrl = openVerifiedHttpsUrl,
                onDismiss = { appUpdateViewModel.dismissUpdateDialog() }
            )
        }
    }
}

private sealed interface PendingVoiceCallRequest {
    data class Outgoing(
        val peerRoomId: String,
        val peerName: String,
        val avatarKey: String,
        val callType: CallType
    ) : PendingVoiceCallRequest

    data class OutgoingEmail(val email: String) : PendingVoiceCallRequest
    data object AcceptIncoming : PendingVoiceCallRequest
    data object EnableMicOnly : PendingVoiceCallRequest
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EsosaSocialShell(
    initialRoomId: String?,
    initialPostId: String?,
    initialStoryId: String?,
    initialTab: String?,
    socialViewModel: SocialViewModel,
    roomsViewModel: RoomsViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationManager = remember(context) { EsosaNotificationManager(context) }

    var selectedTab by rememberSaveable { mutableStateOf(EsosaTab.HOME) }
    var createFormat by rememberSaveable { mutableStateOf(CreateFormat.POST) }
    var secondaryRoute by rememberSaveable { mutableStateOf<EsosaSecondaryRoute?>(null) }

    var activeCommentsPost by remember { mutableStateOf<SocialPost?>(null) }
    var activeDetailPost by remember { mutableStateOf<SocialPost?>(null) }
    var activeStoryAuthorId by remember { mutableStateOf<String?>(null) }
    var activeStoryInitialId by remember { mutableStateOf<String?>(null) }
    var sharingPost by remember { mutableStateOf<SocialPost?>(null) }
    var activeTopicFilter by remember { mutableStateOf<SocialTopicSelection?>(null) }
    var pendingExploreQuery by remember { mutableStateOf<String?>(null) }

    var hasMicPermission by remember {
        mutableStateOf(VoiceCallAudioController.hasMicrophonePermission(context))
    }
    var pendingCallRequest by remember { mutableStateOf<PendingVoiceCallRequest?>(null) }
    var showMicRationaleDialog by remember { mutableStateOf(false) }
    var micPermanentlyDenied by remember { mutableStateOf(false) }

    fun executePendingCall(request: PendingVoiceCallRequest, granted: Boolean) {
        when (request) {
            is PendingVoiceCallRequest.Outgoing -> roomsViewModel.startCall(
                peerRoomId = request.peerRoomId,
                peerName = request.peerName,
                avatarKey = request.avatarKey,
                callType = request.callType,
                hasMicPermission = granted
            )
            is PendingVoiceCallRequest.OutgoingEmail -> roomsViewModel.startVoiceCallByEmail(
                email = request.email,
                hasMicPermission = granted
            )
            PendingVoiceCallRequest.AcceptIncoming -> roomsViewModel.acceptIncomingCall(
                hasMicPermission = granted
            )
            PendingVoiceCallRequest.EnableMicOnly -> roomsViewModel.showBanner(
                "Microphone enabled for voice calls"
            )
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        roomsViewModel.updateCallMicPermission(granted)
        val pending = pendingCallRequest
        if (granted) {
            pendingCallRequest = null
            showMicRationaleDialog = false
            micPermanentlyDenied = false
            if (pending != null) executePendingCall(pending, granted = true)
        } else {
            val activity = context.findActivity()
            val canShowRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                )
            micPermanentlyDenied = !canShowRationale
            showMicRationaleDialog = true
        }
    }

    fun requestMicAndExecute(request: PendingVoiceCallRequest) {
        val granted = VoiceCallAudioController.hasMicrophonePermission(context)
        hasMicPermission = granted
        if (granted) {
            executePendingCall(request, granted = true)
        } else {
            pendingCallRequest = request
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = VoiceCallAudioController.hasMicrophonePermission(context)
                hasMicPermission = grantedNow
                roomsViewModel.updateCallMicPermission(grantedNow)
                if (grantedNow && showMicRationaleDialog) {
                    showMicRationaleDialog = false
                    micPermanentlyDenied = false
                    val pending = pendingCallRequest
                    pendingCallRequest = null
                    if (pending != null) executePendingCall(pending, granted = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* system dialog handled gracefully */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !notificationManager.hasNotificationPermission()
        ) {
            val prefs = context.getSharedPreferences("esosa_fcm_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("asked_post_notifications", false)) {
                prefs.edit().putBoolean("asked_post_notifications", true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(initialRoomId, initialTab) {
        if (!initialRoomId.isNullOrBlank()) {
            secondaryRoute = EsosaSecondaryRoute.MESSAGES
            roomsViewModel.openRoom(initialRoomId)
        } else if (initialTab == EsosaNotificationManager.TAB_HOME) {
            secondaryRoute = null
            selectedTab = EsosaTab.HOME
        }
    }

    val userProfile by roomsViewModel.userProfile.collectAsStateWithLifecycle()
    val activeSessions by roomsViewModel.activeSessions.collectAsStateWithLifecycle()
    val roomStories by roomsViewModel.stories.collectAsStateWithLifecycle()
    val callLogs by roomsViewModel.callLogs.collectAsStateWithLifecycle()
    val activeCallState by roomsViewModel.activeCallState.collectAsStateWithLifecycle()
    val channelComments by roomsViewModel.channelComments.collectAsStateWithLifecycle()
    val roomBanner by roomsViewModel.inAppBanner.collectAsStateWithLifecycle()

    val roomsState by roomsViewModel.roomsState.collectAsStateWithLifecycle()
    val selectedRoomId by roomsViewModel.selectedRoomId.collectAsStateWithLifecycle()
    val activeRoom by roomsViewModel.activeRoom.collectAsStateWithLifecycle()
    val activeMessagesState by roomsViewModel.activeMessagesState.collectAsStateWithLifecycle()
    val roomTypingUsers by roomsViewModel.typingUsers.collectAsStateWithLifecycle()
    val roomTypingMap by roomsViewModel.roomTypingMap.collectAsStateWithLifecycle()
    val roomSearchQuery by roomsViewModel.searchQuery.collectAsStateWithLifecycle()
    val matchingMessages by roomsViewModel.matchingMessages.collectAsStateWithLifecycle()
    val isSearchingMessages by roomsViewModel.isSearchingMessages.collectAsStateWithLifecycle()
    val highlightedMessageId by roomsViewModel.highlightedMessageId.collectAsStateWithLifecycle()
    val roomActionError by roomsViewModel.actionError.collectAsStateWithLifecycle()
    val isRoomBusy by roomsViewModel.isBusy.collectAsStateWithLifecycle()
    val isSendingMessage by roomsViewModel.isSendingMessage.collectAsStateWithLifecycle()
    val photoUploadProgress by roomsViewModel.photoUploadProgress.collectAsStateWithLifecycle()
    val photoUploadPreviewUri by roomsViewModel.photoUploadPreviewUri.collectAsStateWithLifecycle()
    val replyingToMessage by roomsViewModel.replyingToMessage.collectAsStateWithLifecycle()
    val editingMessage by roomsViewModel.editingMessage.collectAsStateWithLifecycle()

    val postsState by socialViewModel.postsState.collectAsStateWithLifecycle()
    val socialStories by socialViewModel.stories.collectAsStateWithLifecycle()
    val repliesByStory by socialViewModel.repliesByStory.collectAsStateWithLifecycle()
    val savedPostIds by socialViewModel.savedPostIds.collectAsStateWithLifecycle()
    val followEdges by socialViewModel.followEdges.collectAsStateWithLifecycle()
    val commentsByPost by socialViewModel.commentsByPost.collectAsStateWithLifecycle()
    val isPublishingSocial by socialViewModel.isPublishing.collectAsStateWithLifecycle()
    val socialUploadProgress by socialViewModel.uploadProgress.collectAsStateWithLifecycle()
    val socialActionError by socialViewModel.actionError.collectAsStateWithLifecycle()
    val isRefreshingFeed by socialViewModel.isRefreshingFeed.collectAsStateWithLifecycle()
    val isLoadingMorePosts by socialViewModel.isLoadingMorePosts.collectAsStateWithLifecycle()
    val hasMorePosts by socialViewModel.hasMorePosts.collectAsStateWithLifecycle()
    val socialBanner by socialViewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(initialPostId, postsState) {
        if (!initialPostId.isNullOrBlank()) {
            val target = (postsState as? UiState.Success)?.data?.firstOrNull { it.id == initialPostId }
            if (target != null) {
                secondaryRoute = null
                selectedTab = EsosaTab.HOME
                activeDetailPost = target
            }
        }
    }

    LaunchedEffect(initialStoryId, socialStories) {
        if (!initialStoryId.isNullOrBlank()) {
            val target = socialStories.firstOrNull { it.id == initialStoryId }
            if (target != null) {
                secondaryRoute = null
                selectedTab = EsosaTab.HOME
                activeStoryAuthorId = target.authorId
                activeStoryInitialId = target.id
            }
        }
    }

    val availableUpdate by appUpdateViewModel.availableUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdates by appUpdateViewModel.isChecking.collectAsStateWithLifecycle()
    val autoUpdateCheckEnabled by appUpdateViewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    val githubUpdateRepo by appUpdateViewModel.githubRepo.collectAsStateWithLifecycle()
    val remoteManifestUrl by appUpdateViewModel.remoteManifestUrl.collectAsStateWithLifecycle()
    val installStage by appUpdateViewModel.installStage.collectAsStateWithLifecycle()
    val updateError by appUpdateViewModel.updateError.collectAsStateWithLifecycle()
    val updateStatusSummary by appUpdateViewModel.statusSummary.collectAsStateWithLifecycle()

    val allRooms = (roomsState as? UiState.Success)?.data.orEmpty()
    val blockedRooms = remember(allRooms) { allRooms.filter { it.isBlocked } }
    val unreadDmCount = remember(allRooms) { allRooms.sumOf { it.unreadCount } }

    LaunchedEffect(secondaryRoute) {
        roomsViewModel.setOnMessagesScreen(secondaryRoute == EsosaSecondaryRoute.MESSAGES)
    }

    if (secondaryRoute != null) {
        BackHandler {
            if (secondaryRoute == EsosaSecondaryRoute.MESSAGES && selectedRoomId != null) {
                roomsViewModel.openRoom(null)
            } else {
                secondaryRoute = null
            }
        }
    }

    val openCommentsSheet: (SocialPost) -> Unit = { post ->
        socialViewModel.observeCommentsForPost(post.id)
        activeCommentsPost = post
    }

    val combinedBanner = socialBanner ?: roomBanner

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 700.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!useRail && secondaryRoute == null && selectedTab != EsosaTab.CREATE) {
                        EsosaInstagramBottomBar(
                            selectedTab = selectedTab,
                            isDarkBar = selectedTab == EsosaTab.REELS,
                            userProfile = userProfile,
                            currentUid = socialViewModel.currentUserId,
                            onSelectTab = { tab ->
                                if (tab == EsosaTab.CREATE) {
                                    createFormat = CreateFormat.POST
                                }
                                selectedTab = tab
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    FlexibleUpdateFloatingBar(
                        installStage = installStage,
                        onRestartAndInstall = { appUpdateViewModel.completeFlexibleUpdate() }
                    )

                    val bannerUpdate = availableUpdate
                    val showPromptStrip = bannerUpdate != null &&
                        installStage !is InAppInstallStage.Downloading &&
                        installStage !is InAppInstallStage.DownloadedReadyToInstall &&
                        installStage !is InAppInstallStage.Installing
                    if (showPromptStrip && bannerUpdate != null && selectedTab != EsosaTab.REELS) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { appUpdateViewModel.openUpdateDialog() }
                                .testTag("app_update_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Filled.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Update v${bannerUpdate.versionName} available (${bannerUpdate.sourceLabel})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = "View",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InstagramBlue
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (useRail && secondaryRoute == null) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                header = {
                                    EsosaBrandLogo(size = 34.dp, modifier = Modifier.padding(top = 8.dp))
                                }
                            ) {
                                EsosaTab.entries.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    NavigationRailItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (tab == EsosaTab.CREATE) createFormat = CreateFormat.POST
                                            selectedTab = tab
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }

                        when (secondaryRoute) {
                            EsosaSecondaryRoute.MESSAGES -> Column(modifier = Modifier.weight(1f)) {
                                if (selectedRoomId == null) {
                                    SecondaryOverlayHeader(
                                        title = "Messages",
                                        onBack = { secondaryRoute = null }
                                    )
                                }
                                MessagesScreen(
                                    currentUserId = roomsViewModel.currentUserId,
                                    roomsState = roomsState,
                                    selectedRoomId = selectedRoomId,
                                    activeRoom = activeRoom,
                                    activeMessagesState = activeMessagesState,
                                    userProfile = userProfile,
                                    stories = roomStories,
                                    typingUsers = roomTypingUsers,
                                    roomTypingMap = roomTypingMap,
                                    searchQuery = roomSearchQuery,
                                    matchingMessages = matchingMessages,
                                    isSearchingMessages = isSearchingMessages,
                                    highlightedMessageId = highlightedMessageId,
                                    actionError = roomActionError,
                                    isBusy = isRoomBusy,
                                    isSendingMessage = isSendingMessage,
                                    photoUploadProgress = photoUploadProgress,
                                    photoUploadPreviewUri = photoUploadPreviewUri,
                                    replyingToMessage = replyingToMessage,
                                    editingMessage = editingMessage,
                                    channelCommentsMap = channelComments,
                                    onOpenRoom = { roomsViewModel.openRoom(it) },
                                    onSearchQueryChanged = { roomsViewModel.onSearchQueryChanged(it) },
                                    onOpenSearchResult = { roomId, msgId ->
                                        roomsViewModel.openSearchResult(roomId, msgId)
                                    },
                                    onClearHighlight = { roomsViewModel.clearHighlightedMessage() },
                                    onCreateRoom = { title, topic, kind ->
                                        roomsViewModel.createRoom(title, topic, kind)
                                    },
                                    onJoinRoomByCode = { code -> roomsViewModel.joinRoomByCode(code) },
                                    onTogglePinRoom = { roomsViewModel.togglePinRoom(it) },
                                    onToggleArchiveRoom = { roomsViewModel.toggleArchiveRoom(it) },
                                    onToggleMuteRoom = { roomsViewModel.toggleMuteRoom(it) },
                                    onToggleBlockRoom = { rId, blocked -> roomsViewModel.setRoomBlocked(rId, blocked) },
                                    onReportRoom = { room -> roomsViewModel.showBanner("Reported ${room.title}") },
                                    onToggleChannelSubscribe = { roomsViewModel.toggleChannelSubscription(it) },
                                    onAddGroupMember = { rId, name -> roomsViewModel.addGroupMember(rId, name) },
                                    onRemoveGroupMember = { rId, name -> roomsViewModel.removeGroupMember(rId, name) },
                                    onToggleGroupAdmin = { rId, name -> roomsViewModel.toggleGroupAdmin(rId, name) },
                                    onUpdateGroupDescription = { rId, desc -> roomsViewModel.updateGroupDescription(rId, desc) },
                                    onSetDisappearingTimer = { rId, sec -> roomsViewModel.setRoomDisappearingTimer(rId, sec) },
                                    onSetRoomWallpaper = { rId, wpId -> roomsViewModel.setRoomWallpaper(rId, wpId) },
                                    onDraftChanged = { room, text -> roomsViewModel.onDraftChanged(room, text) },
                                    onStopTyping = { room -> roomsViewModel.stopTyping(room) },
                                    onSendMessage = { room, text -> roomsViewModel.sendMessage(room, text) },
                                    onScheduleMessage = { room, text, millis ->
                                        roomsViewModel.sendMessage(room, text, scheduledForMillis = millis)
                                    },
                                    onSendVoiceNote = { room, sec -> roomsViewModel.sendVoiceNote(room, sec) },
                                    onSendStickerOrGif = { room, text, isGif, lbl ->
                                        roomsViewModel.sendStickerOrGif(room, text, isGif, lbl)
                                    },
                                    onSendDocumentAttachment = { room, uri ->
                                        roomsViewModel.sendDocumentAttachment(room, uri)
                                    },
                                    onSendCapturedPhoto = { room, uri, caption, cleanup ->
                                        roomsViewModel.sendPhotoMessage(
                                            room = room,
                                            photoUri = uri,
                                            caption = caption,
                                            onCleanup = cleanup
                                        )
                                    },
                                    onReplyMessage = { roomsViewModel.startReply(it) },
                                    onEditMessage = { roomsViewModel.startEdit(it) },
                                    onCancelComposerMode = { roomsViewModel.cancelComposerMode() },
                                    onForwardMessage = { msg, target -> roomsViewModel.forwardMessage(msg, target) },
                                    onPinMessage = { rId, msg -> roomsViewModel.pinMessageInRoom(rId, msg) },
                                    onAddChannelComment = { rId, pId, txt ->
                                        roomsViewModel.addChannelComment(rId, pId, txt)
                                    },
                                    onStartCall = { room, callType ->
                                        requestMicAndExecute(
                                            PendingVoiceCallRequest.Outgoing(
                                                peerRoomId = room.id,
                                                peerName = room.title,
                                                avatarKey = room.avatarKey,
                                                callType = callType
                                            )
                                        )
                                    },
                                    onOpenStory = { story -> roomsViewModel.markStoryViewed(story.id) },
                                    onPostStory = { text -> roomsViewModel.postMyStory(text) },
                                    onReportError = { roomsViewModel.reportActionError(it) },
                                    onToggleReaction = { roomId, msgId, emoji ->
                                        roomsViewModel.toggleReaction(roomId, msgId, emoji)
                                    },
                                    onDeleteMessage = { rId, mId -> roomsViewModel.deleteMessage(rId, mId) },
                                    onDeleteRoomMessage = { rId, msg ->
                                        roomsViewModel.deleteMessage(rId, msg.id, msg.storagePath)
                                    },
                                    onDeleteRoom = { room -> roomsViewModel.deleteRoom(room) },
                                    onClearError = { roomsViewModel.clearActionError() },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            EsosaSecondaryRoute.CALLS -> Column(modifier = Modifier.weight(1f)) {
                                SecondaryOverlayHeader(
                                    title = "Calls",
                                    onBack = { secondaryRoute = null }
                                )
                                CallsScreen(
                                    callLogs = callLogs,
                                    rooms = allRooms,
                                    hasMicrophonePermission = hasMicPermission,
                                    onRequestMicPermission = {
                                        requestMicAndExecute(PendingVoiceCallRequest.EnableMicOnly)
                                    },
                                    onStartCall = { peerId, peerName, avatarKey, callType ->
                                        requestMicAndExecute(
                                            PendingVoiceCallRequest.Outgoing(
                                                peerRoomId = peerId,
                                                peerName = peerName,
                                                avatarKey = avatarKey,
                                                callType = callType
                                            )
                                        )
                                    },
                                    onStartVoiceCallByEmail = { email ->
                                        requestMicAndExecute(PendingVoiceCallRequest.OutgoingEmail(email))
                                    },
                                    onSimulateIncomingCall = {
                                        roomsViewModel.simulateIncomingCall(hasMicPermission = hasMicPermission)
                                    },
                                    onDeleteCallLog = { roomsViewModel.deleteCallLog(it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            EsosaSecondaryRoute.SETTINGS -> Column(modifier = Modifier.weight(1f)) {
                                SecondaryOverlayHeader(
                                    title = "Settings & Privacy",
                                    onBack = { secondaryRoute = null }
                                )
                                SettingsScreen(
                                    userProfile = userProfile,
                                    notificationManager = notificationManager,
                                    availableUpdate = availableUpdate,
                                    updateStatusSummary = updateStatusSummary,
                                    isCheckingUpdates = isCheckingUpdates,
                                    autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                                    githubUpdateRepo = githubUpdateRepo,
                                    remoteManifestUrl = remoteManifestUrl,
                                    installStage = installStage,
                                    updateError = updateError,
                                    isPlayStoreDistribution = appUpdateViewModel.isPlayStoreDistribution,
                                    activeSessions = activeSessions,
                                    blockedRooms = blockedRooms,
                                    onUpdateLocalProfile = { name, username, bio ->
                                        roomsViewModel.updateLocalProfile(name, username, bio)
                                    },
                                    onToggleNotifications = { roomsViewModel.setNotificationsEnabled(it) },
                                    onSendTestNotification = { roomsViewModel.sendTestNotification() },
                                    onUpdateThemeMode = { roomsViewModel.updateThemeMode(it) },
                                    onUpdateWallpaper = { roomsViewModel.updateWallpaper(it) },
                                    onUpdateLanguage = { roomsViewModel.updateLanguage(it) },
                                    onSetTwoStepPin = { en, pin -> roomsViewModel.setTwoStepPin(en, pin) },
                                    onToggleContactSync = { roomsViewModel.setContactSyncEnabled(it) },
                                    onToggleReadReceipts = { roomsViewModel.setReadReceiptsEnabled(it) },
                                    onUpdateLastSeenVisibility = { roomsViewModel.setLastSeenVisibility(it) },
                                    onUpdateDefaultDisappearSeconds = { roomsViewModel.setDefaultDisappearSeconds(it) },
                                    onTerminateSession = { roomsViewModel.terminateSession(it) },
                                    onTerminateAllOtherSessions = { roomsViewModel.terminateAllOtherSessions() },
                                    onUnblockRoom = { roomsViewModel.setRoomBlocked(it, false) },
                                    onLogout = onSignOut,
                                    onToggleAutoUpdateCheck = { appUpdateViewModel.setAutoCheckEnabled(it) },
                                    onSaveGithubRepo = { repo -> appUpdateViewModel.saveGithubRepository(repo) },
                                    onSaveRemoteManifestUrl = { url -> appUpdateViewModel.saveRemoteManifestUrl(url) },
                                    onCheckForUpdatesNow = { appUpdateViewModel.checkForUpdatesNow() },
                                    onOpenUpdateDialog = { appUpdateViewModel.openUpdateDialog() },
                                    onRestartAndInstall = { appUpdateViewModel.completeFlexibleUpdate() },
                                    onUpdateProfilePhoto = { bytes, onDone ->
                                        roomsViewModel.updateProfilePhoto(bytes, onDone)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            null -> when (selectedTab) {
                                EsosaTab.HOME -> InstagramHomeScreen(
                                    currentUid = socialViewModel.currentUserId,
                                    userProfile = userProfile,
                                    postsState = postsState,
                                    stories = socialStories,
                                    savedPostIds = savedPostIds,
                                    followEdges = followEdges,
                                    unreadDmCount = unreadDmCount,
                                    commentsByPost = commentsByPost,
                                    isRefreshing = isRefreshingFeed,
                                    onRefreshFeed = { socialViewModel.refreshFeed() },
                                    isLoadingMore = isLoadingMorePosts,
                                    hasMorePosts = hasMorePosts,
                                    onLoadMorePosts = { socialViewModel.loadMorePosts() },
                                    onHashtagClick = { tag ->
                                        activeTopicFilter = SocialTopicSelection.Hashtag(tag)
                                    },
                                    onUsernameClick = { handle ->
                                        activeTopicFilter = SocialTopicSelection.Username(handle)
                                    },
                                    onOpenDirectMessages = { secondaryRoute = EsosaSecondaryRoute.MESSAGES },
                                    onAddStoryClick = {
                                        createFormat = CreateFormat.STORY
                                        selectedTab = EsosaTab.CREATE
                                    },
                                    onOpenCreatePost = {
                                        createFormat = CreateFormat.POST
                                        selectedTab = EsosaTab.CREATE
                                    },
                                    onOpenStory = { story ->
                                        socialViewModel.markStoryViewed(story)
                                        socialViewModel.observeRepliesForStory(story)
                                        activeStoryAuthorId = story.authorId
                                        activeStoryInitialId = story.id
                                    },
                                    onToggleLike = { socialViewModel.toggleLike(it) },
                                    onDoubleTapLike = { socialViewModel.likeOnly(it) },
                                    onOpenComments = openCommentsSheet,
                                    onToggleSave = { socialViewModel.toggleSave(it.id) },
                                    onToggleFollow = { uid, username -> socialViewModel.toggleFollow(uid, username) },
                                    onShareToDm = { sharingPost = it },
                                    onDeletePost = { socialViewModel.deletePost(it) },
                                    modifier = Modifier.weight(1f)
                                )

                                EsosaTab.EXPLORE -> InstagramExploreScreen(
                                    currentUid = socialViewModel.currentUserId,
                                    postsState = postsState,
                                    followEdges = followEdges,
                                    externalQuery = pendingExploreQuery,
                                    onExternalQueryConsumed = { pendingExploreQuery = null },
                                    onSelectPost = { activeDetailPost = it },
                                    modifier = Modifier.weight(1f)
                                )

                                EsosaTab.CREATE -> {
                                    val suggestedTagUsernames = remember(postsState, followEdges, userProfile.username) {
                                        val currentHandle = userProfile.username.trim().removePrefix("@")
                                        val fromPosts = (postsState as? UiState.Success)?.data.orEmpty().map { it.authorUsername }
                                        val fromFollows = followEdges.map { it.targetUsername }
                                        (fromFollows + fromPosts)
                                            .map { it.trim().removePrefix("@") }
                                            .filter { it.isNotBlank() && it != currentHandle }
                                            .distinct()
                                            .take(15)
                                    }
                                    InstagramCreateScreen(
                                        initialFormat = createFormat,
                                        isPublishing = isPublishingSocial,
                                        uploadProgress = socialUploadProgress,
                                        actionError = socialActionError,
                                        suggestedUsernames = suggestedTagUsernames,
                                        onPublishPostOrReel = { uri, type, caption, location, audio, ratio, isVideo, cropSpec, filter, taggedUsernames ->
                                            socialViewModel.publishPostOrReel(
                                                imageUri = uri,
                                                mediaType = type,
                                                caption = caption,
                                                location = location,
                                                audioLabel = audio,
                                                aspectRatio = ratio,
                                                isVideo = isVideo,
                                                cropSpec = cropSpec,
                                                filter = filter,
                                                taggedUsernames = taggedUsernames,
                                                onPublished = {
                                                    selectedTab = if (type == com.example.data.model.SocialMediaType.REEL) {
                                                        EsosaTab.REELS
                                                    } else {
                                                        EsosaTab.HOME
                                                    }
                                                }
                                            )
                                        },
                                        onPublishStory = { uri, caption, isVideo, cropSpec, filter ->
                                            socialViewModel.publishStory(
                                                imageUri = uri,
                                                caption = caption,
                                                isVideo = isVideo,
                                                cropSpec = cropSpec,
                                                filter = filter,
                                                onPublished = { selectedTab = EsosaTab.HOME }
                                            )
                                        },
                                        onClearError = { socialViewModel.clearError() },
                                        onCancel = { selectedTab = EsosaTab.HOME },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                EsosaTab.REELS -> InstagramReelsScreen(
                                    currentUid = socialViewModel.currentUserId,
                                    postsState = postsState,
                                    savedPostIds = savedPostIds,
                                    followEdges = followEdges,
                                    onCreateReel = {
                                        createFormat = CreateFormat.REEL
                                        selectedTab = EsosaTab.CREATE
                                    },
                                    onToggleLike = { socialViewModel.toggleLike(it) },
                                    onDoubleTapLike = { socialViewModel.likeOnly(it) },
                                    onOpenComments = openCommentsSheet,
                                    onToggleSave = { socialViewModel.toggleSave(it.id) },
                                    onToggleFollow = { uid, username -> socialViewModel.toggleFollow(uid, username) },
                                    onShareToDm = { sharingPost = it },
                                    onDeleteReel = { socialViewModel.deletePost(it) },
                                    modifier = Modifier.weight(1f)
                                )

                                EsosaTab.PROFILE -> InstagramProfileScreen(
                                    currentUid = socialViewModel.currentUserId,
                                    userProfile = userProfile,
                                    postsState = postsState,
                                    savedPostIds = savedPostIds,
                                    followEdges = followEdges,
                                    stories = socialStories,
                                    onOpenCreate = {
                                        createFormat = CreateFormat.POST
                                        selectedTab = EsosaTab.CREATE
                                    },
                                    onOpenStory = { story ->
                                        socialViewModel.markStoryViewed(story)
                                        socialViewModel.observeRepliesForStory(story)
                                        activeStoryAuthorId = story.authorId
                                        activeStoryInitialId = story.id
                                    },
                                    onAddStoryClick = {
                                        createFormat = CreateFormat.STORY
                                        selectedTab = EsosaTab.CREATE
                                    },
                                    onSelectPost = { activeDetailPost = it },
                                    onOpenMessages = { secondaryRoute = EsosaSecondaryRoute.MESSAGES },
                                    onOpenCalls = { secondaryRoute = EsosaSecondaryRoute.CALLS },
                                    onOpenSettings = { secondaryRoute = EsosaSecondaryRoute.SETTINGS },
                                    onUpdateLocalProfile = { name, username, bio ->
                                        roomsViewModel.updateLocalProfile(name, username, bio)
                                    },
                                    onUpdateProfilePhoto = { bytes, onDone ->
                                        roomsViewModel.updateProfilePhoto(bytes, onDone)
                                    },
                                    onSignOut = onSignOut,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Story Viewer (supports creator multi-segment sequence, video, progress bars, & replies)
        val storyAuthorId = activeStoryAuthorId
        if (storyAuthorId != null) {
            val authorSequence = remember(socialStories, storyAuthorId) {
                socialStories.filter { it.authorId == storyAuthorId }.reversed()
            }
            if (authorSequence.isEmpty()) {
                activeStoryAuthorId = null
            } else {
                val initialIndex = authorSequence
                    .indexOfFirst { it.id == activeStoryInitialId }
                    .let { if (it >= 0) it else 0 }
                SocialStoryViewerDialog(
                    stories = authorSequence,
                    initialStoryIndex = initialIndex,
                    currentUid = socialViewModel.currentUserId,
                    repliesByStory = repliesByStory,
                    onStoryChanged = { current ->
                        socialViewModel.markStoryViewed(current)
                        socialViewModel.observeRepliesForStory(current)
                    },
                    onSendReply = { targetStory, replyText ->
                        socialViewModel.sendStoryReply(targetStory, replyText)
                    },
                    onDeleteStory = { targetStory ->
                        socialViewModel.deleteStory(targetStory)
                        if (authorSequence.size <= 1) {
                            activeStoryAuthorId = null
                        }
                    },
                    onAddAnotherStory = {
                        activeStoryAuthorId = null
                        createFormat = CreateFormat.STORY
                        selectedTab = EsosaTab.CREATE
                    },
                    onDismiss = {
                        activeStoryAuthorId = null
                        activeStoryInitialId = null
                    }
                )
            }
        }

        // Post Detail Viewer (from Explore or Profile grid)
        val detailTarget = activeDetailPost
        if (detailTarget != null) {
            val freshPost = (postsState as? UiState.Success)?.data
                ?.firstOrNull { it.id == detailTarget.id } ?: detailTarget
            val followingSet = remember(followEdges, socialViewModel.currentUserId) {
                followEdges.filter { it.followerId == socialViewModel.currentUserId }
                    .map { it.targetUid }.toSet()
            }
            InstagramPostDetailDialog(
                post = freshPost,
                currentUid = socialViewModel.currentUserId,
                isSaved = savedPostIds.contains(freshPost.id),
                isFollowingAuthor = followingSet.contains(freshPost.authorId),
                previewComments = commentsByPost[freshPost.id].orEmpty(),
                onHashtagClick = { tag ->
                    activeDetailPost = null
                    activeTopicFilter = SocialTopicSelection.Hashtag(tag)
                },
                onUsernameClick = { handle ->
                    activeDetailPost = null
                    activeTopicFilter = SocialTopicSelection.Username(handle)
                },
                onDismiss = { activeDetailPost = null },
                onToggleLike = { socialViewModel.toggleLike(freshPost) },
                onDoubleTapLike = { socialViewModel.likeOnly(freshPost) },
                onOpenComments = { openCommentsSheet(freshPost) },
                onToggleSave = { socialViewModel.toggleSave(freshPost.id) },
                onToggleFollow = {
                    socialViewModel.toggleFollow(freshPost.authorId, freshPost.authorUsername)
                },
                onShareToDm = { sharingPost = freshPost },
                onDeletePost = { socialViewModel.deletePost(freshPost) }
            )
        }

        // Hashtag / Creator Profile Sheet from Feed taps
        val topicSelection = activeTopicFilter
        if (topicSelection != null) {
            val allFeedPosts = (postsState as? UiState.Success)?.data.orEmpty()
            SocialTopicOrUserSheet(
                selection = topicSelection,
                allPosts = allFeedPosts,
                followEdges = followEdges,
                currentUid = socialViewModel.currentUserId,
                onDismiss = { activeTopicFilter = null },
                onSelectPost = { post ->
                    activeTopicFilter = null
                    activeDetailPost = post
                },
                onToggleFollow = { targetUid, targetUsername ->
                    socialViewModel.toggleFollow(targetUid, targetUsername)
                },
                onOpenInExplore = { queryText ->
                    activeTopicFilter = null
                    pendingExploreQuery = queryText
                    selectedTab = EsosaTab.EXPLORE
                }
            )
        }

        // Comments Bottom Sheet
        val commentTarget = activeCommentsPost
        if (commentTarget != null) {
            val freshPost = (postsState as? UiState.Success)?.data
                ?.firstOrNull { it.id == commentTarget.id } ?: commentTarget
            InstagramCommentsSheet(
                post = freshPost,
                comments = commentsByPost[freshPost.id].orEmpty(),
                currentUid = socialViewModel.currentUserId,
                currentUsername = userProfile.username.ifBlank { userProfile.displayName },
                currentUserPhotoUrl = userProfile.photoUrl,
                onDismiss = { activeCommentsPost = null },
                onSendComment = { txt -> socialViewModel.addComment(freshPost, txt) },
                onDeleteComment = { comment ->
                    socialViewModel.deleteComment(freshPost.id, comment.id)
                }
            )
        }

        // Share Post Sheet (to Esosa Chat Room or External Share)
        val postToShare = sharingPost
        if (postToShare != null) {
            ModalBottomSheet(
                onDismissRequest = { sharingPost = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Share post",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (allRooms.isNotEmpty()) {
                        Text(
                            text = "Send to an Esosa chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier.height(180.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(allRooms.take(10), key = { it.id }) { room ->
                                ListItem(
                                    headlineContent = { Text(room.title, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text("Send @${postToShare.authorUsername}'s post") },
                                    modifier = Modifier.clickable {
                                        val shareBody = buildString {
                                            append("Shared @${postToShare.authorUsername}'s ")
                                            append(postToShare.mediaType.id)
                                            if (postToShare.caption.isNotBlank()) {
                                                append(": \"${postToShare.caption.take(120)}\"")
                                            }
                                        }
                                        roomsViewModel.sendMessage(room, shareBody)
                                        socialViewModel.showStatusBanner("Sent to ${room.title}")
                                        sharingPost = null
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Check out @${postToShare.authorUsername} on Esosa: ${postToShare.caption}"
                                )
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(sendIntent, "Share via"))
                            }
                            sharingPost = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share via other apps")
                    }
                }
            }
        }

        // In-app status toast banner
        AnimatedVisibility(
            visible = combinedBanner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                color = Color(0xFF1F1F1F),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clickable {
                        socialViewModel.dismissBanner()
                        roomsViewModel.dismissBanner()
                    }
                    .testTag("in_app_notification_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = combinedBanner.orEmpty(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Full-Screen Voice/Video Call Overlay
        val callState = activeCallState
        if (callState != null) {
            ActiveCallOverlay(
                callState = callState,
                onAcceptIncoming = {
                    requestMicAndExecute(PendingVoiceCallRequest.AcceptIncoming)
                },
                onToggleMute = { roomsViewModel.toggleCallMute() },
                onToggleSpeaker = { roomsViewModel.toggleCallSpeaker() },
                onToggleCamera = { roomsViewModel.toggleCallCamera() },
                onRequestMicPermission = {
                    requestMicAndExecute(PendingVoiceCallRequest.EnableMicOnly)
                },
                onEndCall = { roomsViewModel.endCall() }
            )
        }

        if (showMicRationaleDialog) {
            AlertDialog(
                onDismissRequest = {
                    showMicRationaleDialog = false
                    pendingCallRequest = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text(stringResource(R.string.voice_call_mic_permission_title)) },
                text = {
                    Text(
                        text = if (micPermanentlyDenied) {
                            stringResource(R.string.voice_call_mic_permission_settings_body)
                        } else {
                            stringResource(R.string.voice_call_mic_permission_body)
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (micPermanentlyDenied) {
                                showMicRationaleDialog = false
                                val settingsIntent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(settingsIntent)
                            } else {
                                showMicRationaleDialog = false
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.testTag("mic_permission_confirm_button")
                    ) {
                        Text(
                            if (micPermanentlyDenied) {
                                stringResource(R.string.voice_call_mic_open_settings)
                            } else {
                                stringResource(R.string.voice_call_mic_grant_action)
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showMicRationaleDialog = false
                            pendingCallRequest = null
                        }
                    ) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("mic_permission_rationale_dialog")
            )
        }
    }
}

@Composable
private fun EsosaInstagramBottomBar(
    selectedTab: EsosaTab,
    isDarkBar: Boolean,
    userProfile: UserProfile,
    currentUid: String,
    onSelectTab: (EsosaTab) -> Unit
) {
    val backgroundColor = if (isDarkBar) Color.Black else MaterialTheme.colorScheme.surface
    val iconColor = if (isDarkBar) Color.White else MaterialTheme.colorScheme.onBackground
    val mutedColor = if (isDarkBar) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onBackground
    val dividerColor = if (isDarkBar) Color(0xFF262626) else InstagramHairline

    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EsosaTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .minimumInteractiveComponentSize()
                            .clickable { onSelectTab(tab) }
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tab == EsosaTab.PROFILE) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .let { mod ->
                                        if (isSelected) mod.border(1.75.dp, iconColor, CircleShape) else mod
                                    }
                                    .padding(if (isSelected) 2.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                InstagramUserAvatar(
                                    displayName = userProfile.displayName.ifBlank { "You" },
                                    photoUrl = userProfile.photoUrl,
                                    seedKey = currentUid,
                                    size = if (isSelected) 24.dp else 26.dp
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = if (isSelected) iconColor else mutedColor,
                                modifier = Modifier.size(27.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryOverlayHeader(
    title: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("secondary_overlay_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)
    }
}

private sealed interface SocialTopicSelection {
    data class Hashtag(val tag: String) : SocialTopicSelection
    data class Username(val username: String) : SocialTopicSelection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialTopicOrUserSheet(
    selection: SocialTopicSelection,
    allPosts: List<SocialPost>,
    followEdges: List<com.example.data.model.UserFollowEdge>,
    currentUid: String,
    onDismiss: () -> Unit,
    onSelectPost: (SocialPost) -> Unit,
    onToggleFollow: (String, String) -> Unit,
    onOpenInExplore: (String) -> Unit
) {
    val cleanQuery = when (selection) {
        is SocialTopicSelection.Hashtag -> "#${selection.tag.removePrefix("#")}"
        is SocialTopicSelection.Username -> "@${selection.username.removePrefix("@")}"
    }
    val matchingPosts = remember(selection, allPosts) {
        when (selection) {
            is SocialTopicSelection.Hashtag -> {
                val needle = "#${selection.tag.removePrefix("#").lowercase()}"
                allPosts.filter { it.caption.lowercase().contains(needle) }
            }
            is SocialTopicSelection.Username -> {
                val handle = selection.username.removePrefix("@").lowercase()
                allPosts.filter { post ->
                    post.authorUsername.removePrefix("@").lowercase() == handle ||
                        post.taggedUsernames.any { it.removePrefix("@").lowercase() == handle } ||
                        post.caption.lowercase().contains("@$handle")
                }
            }
        }
    }
    val matchedAuthor = remember(selection, allPosts) {
        if (selection is SocialTopicSelection.Username) {
            val handle = selection.username.removePrefix("@").lowercase()
            allPosts.firstOrNull { it.authorUsername.removePrefix("@").lowercase() == handle }
        } else {
            null
        }
    }
    val isFollowing = remember(followEdges, currentUid, matchedAuthor) {
        val targetUid = matchedAuthor?.authorId ?: return@remember false
        followEdges.any { it.followerId == currentUid && it.targetUid == targetUid }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 520.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .testTag("topic_or_user_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (matchedAuthor != null) {
                        InstagramUserAvatar(
                            displayName = matchedAuthor.authorDisplayName,
                            photoUrl = matchedAuthor.authorPhotoUrl,
                            seedKey = matchedAuthor.authorId,
                            size = 44.dp
                        )
                    }
                    Column {
                        Text(
                            text = cleanQuery,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${matchingPosts.size} ${if (matchingPosts.size == 1) "post" else "posts"} in feed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (matchedAuthor != null && matchedAuthor.authorId != currentUid) {
                    Button(
                        onClick = {
                            onToggleFollow(matchedAuthor.authorId, matchedAuthor.authorUsername)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isFollowing) "Following" else "Follow")
                    }
                } else {
                    TextButton(onClick = { onOpenInExplore(cleanQuery) }) {
                        Text("Explore")
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (matchingPosts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No loaded posts match $cleanQuery yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(matchingPosts, key = { it.id }) { post ->
                        AsyncImage(
                            model = post.imageUrl,
                            contentDescription = post.caption.ifBlank { post.authorUsername },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSelectPost(post) }
                        )
                    }
                }
            }
        }
    }
}
