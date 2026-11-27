package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.core.LocalProfilePreferences
import com.example.data.model.ModeratorAlertDecision
import com.example.data.model.ModeratorNotificationGate
import com.example.data.model.StorageMediaCategory
import com.example.data.model.StorageUsageSummary
import com.example.data.model.UserProfile
import com.example.data.remote.FirestoreErrorHandler
import com.example.data.remote.FirestoreSnapshotMappers
import com.example.data.remote.OperationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.max

interface StorageUsageTracker {
    suspend fun recordUpload(bytes: Long, category: StorageMediaCategory, backendLabel: String)
    suspend fun recordDeletion(bytes: Long, category: StorageMediaCategory)
}

class StorageUsageRepository(
    context: Context,
    private val db: FirebaseFirestore? = null,
    private val auth: FirebaseAuth? = null,
    private val profilePreferences: LocalProfilePreferences? = null
) : StorageUsageTracker {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _usageSummary = MutableStateFlow(readCachedSummary())
    val usageSummary: StateFlow<StorageUsageSummary> = _usageSummary.asStateFlow()

    private val _isModeratorGrantDocPresent = MutableStateFlow(false)
    val isModeratorGrantDocPresent: StateFlow<Boolean> = _isModeratorGrantDocPresent.asStateFlow()

    fun observeRemoteUsageSync(): Flow<StorageUsageSummary> {
        val firestore = db ?: return MutableStateFlow(_usageSummary.value)
        val docPath = "$COLLECTION_STORAGE_USAGE/$DOC_GLOBAL"
        return callbackFlow {
            val listener = firestore.collection(COLLECTION_STORAGE_USAGE)
                .document(DOC_GLOBAL)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        FirestoreErrorHandler.handleFirestoreError(
                            error = error,
                            operationType = OperationType.GET,
                            path = docPath,
                            auth = auth
                        )
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val parsed = parseSummary(snapshot)
                        cacheAndEmit(parsed)
                        trySend(parsed)
                    }
                }
            awaitClose { listener.remove() }
        }
    }

    fun observeModeratorGrant(uid: String): Flow<Boolean> {
        val firestore = db ?: return MutableStateFlow(false)
        if (uid.isBlank() || uid == LocalProfilePreferences.LOCAL_USER_ID) {
            return MutableStateFlow(false)
        }
        val docPath = "$COLLECTION_MODERATORS/$uid"
        return callbackFlow {
            val listener = firestore.collection(COLLECTION_MODERATORS)
                .document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        FirestoreErrorHandler.handleFirestoreError(
                            error = error,
                            operationType = OperationType.GET,
                            path = docPath,
                            auth = auth
                        )
                        return@addSnapshotListener
                    }
                    val granted = snapshot != null && snapshot.exists()
                    _isModeratorGrantDocPresent.value = granted
                    if (granted) {
                        profilePreferences?.updateRole(UserProfile.ROLE_MODERATOR)
                    }
                    trySend(granted)
                }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun recordUpload(
        bytes: Long,
        category: StorageMediaCategory,
        backendLabel: String
    ) {
        if (bytes <= 0L) return
        val cleanBackend = backendLabel.trim().ifBlank { StorageUsageSummary.DEFAULT_BACKEND_LABEL }.take(80)
        val current = _usageSummary.value
        val updatedLocal = current.copy(
            usedBytes = current.safeUsedBytes + bytes,
            fileCount = current.fileCount + 1,
            photoBytes = current.photoBytes + if (category == StorageMediaCategory.PHOTO) bytes else 0L,
            videoBytes = current.videoBytes + if (category == StorageMediaCategory.VIDEO) bytes else 0L,
            audioBytes = current.audioBytes + if (category == StorageMediaCategory.AUDIO) bytes else 0L,
            activeBackendLabel = cleanBackend,
            updatedAtMillis = System.currentTimeMillis()
        )
        cacheAndEmit(updatedLocal)

        val firestore = db ?: return
        if (auth?.currentUser == null) return
        val docRef = firestore.collection(COLLECTION_STORAGE_USAGE).document(DOC_GLOBAL)
        val docPath = "$COLLECTION_STORAGE_USAGE/$DOC_GLOBAL"
        try {
            firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    val initial = hashMapOf<String, Any>(
                        "usedBytes" to bytes,
                        "quotaBytes" to StorageUsageSummary.DEFAULT_FREE_QUOTA_BYTES,
                        "warningThresholdPercent" to StorageUsageSummary.DEFAULT_WARNING_THRESHOLD_PERCENT,
                        "fileCount" to 1,
                        "photoBytes" to if (category == StorageMediaCategory.PHOTO) bytes else 0L,
                        "videoBytes" to if (category == StorageMediaCategory.VIDEO) bytes else 0L,
                        "audioBytes" to if (category == StorageMediaCategory.AUDIO) bytes else 0L,
                        "activeBackendLabel" to cleanBackend,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(docRef, initial)
                } else {
                    val existing = parseSummary(snap)
                    tx.update(
                        docRef,
                        mapOf(
                            "usedBytes" to (existing.safeUsedBytes + bytes),
                            "fileCount" to (existing.fileCount + 1),
                            "photoBytes" to (existing.photoBytes + if (category == StorageMediaCategory.PHOTO) bytes else 0L),
                            "videoBytes" to (existing.videoBytes + if (category == StorageMediaCategory.VIDEO) bytes else 0L),
                            "audioBytes" to (existing.audioBytes + if (category == StorageMediaCategory.AUDIO) bytes else 0L),
                            "activeBackendLabel" to cleanBackend,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, docPath, auth)
            Log.w(TAG, "Failed to sync storage upload increment to Firestore: ${e.message}")
        }
    }

    override suspend fun recordDeletion(bytes: Long, category: StorageMediaCategory) {
        if (bytes <= 0L) return
        val current = _usageSummary.value
        val updatedLocal = current.copy(
            usedBytes = max(0L, current.safeUsedBytes - bytes),
            fileCount = max(0, current.fileCount - 1),
            photoBytes = max(0L, current.photoBytes - if (category == StorageMediaCategory.PHOTO) bytes else 0L),
            videoBytes = max(0L, current.videoBytes - if (category == StorageMediaCategory.VIDEO) bytes else 0L),
            audioBytes = max(0L, current.audioBytes - if (category == StorageMediaCategory.AUDIO) bytes else 0L),
            updatedAtMillis = System.currentTimeMillis()
        )
        cacheAndEmit(updatedLocal)

        val firestore = db ?: return
        if (auth?.currentUser == null) return
        val docRef = firestore.collection(COLLECTION_STORAGE_USAGE).document(DOC_GLOBAL)
        val docPath = "$COLLECTION_STORAGE_USAGE/$DOC_GLOBAL"
        try {
            firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    val existing = parseSummary(snap)
                    tx.update(
                        docRef,
                        mapOf(
                            "usedBytes" to max(0L, existing.safeUsedBytes - bytes),
                            "fileCount" to max(0, existing.fileCount - 1),
                            "photoBytes" to max(0L, existing.photoBytes - if (category == StorageMediaCategory.PHOTO) bytes else 0L),
                            "videoBytes" to max(0L, existing.videoBytes - if (category == StorageMediaCategory.VIDEO) bytes else 0L),
                            "audioBytes" to max(0L, existing.audioBytes - if (category == StorageMediaCategory.AUDIO) bytes else 0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, docPath, auth)
            Log.w(TAG, "Failed to sync storage deletion decrement to Firestore: ${e.message}")
        }
    }

    suspend fun updateWarningThreshold(thresholdPercent: Int, isModerator: Boolean): Result<Unit> {
        if (!isModerator) {
            return Result.failure(SecurityException("Only moderators can adjust the storage warning threshold."))
        }
        val safePercent = thresholdPercent.coerceIn(
            StorageUsageSummary.MIN_THRESHOLD_PERCENT,
            StorageUsageSummary.MAX_THRESHOLD_PERCENT
        )
        val current = _usageSummary.value
        val updatedLocal = current.copy(
            warningThresholdPercent = safePercent,
            updatedAtMillis = System.currentTimeMillis()
        )
        cacheAndEmit(updatedLocal)

        val firestore = db ?: return Result.success(Unit)
        if (auth?.currentUser == null) return Result.success(Unit)
        val docRef = firestore.collection(COLLECTION_STORAGE_USAGE).document(DOC_GLOBAL)
        val docPath = "$COLLECTION_STORAGE_USAGE/$DOC_GLOBAL"
        return try {
            firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    tx.set(
                        docRef,
                        mapOf(
                            "usedBytes" to updatedLocal.safeUsedBytes,
                            "quotaBytes" to updatedLocal.safeQuotaBytes,
                            "warningThresholdPercent" to safePercent,
                            "fileCount" to updatedLocal.fileCount,
                            "photoBytes" to updatedLocal.photoBytes,
                            "videoBytes" to updatedLocal.videoBytes,
                            "audioBytes" to updatedLocal.audioBytes,
                            "activeBackendLabel" to updatedLocal.activeBackendLabel,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.update(
                        docRef,
                        mapOf(
                            "warningThresholdPercent" to safePercent,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, docPath, auth)
            Result.failure(e)
        }
    }

    fun evaluateAndRecordModeratorAlert(
        uid: String,
        isModerator: Boolean,
        summary: StorageUsageSummary = _usageSummary.value
    ): ModeratorAlertDecision {
        val lastKey = prefs.getString(episodePrefKey(uid), null)
        val decision = ModeratorNotificationGate.evaluate(
            isModerator = isModerator,
            summary = summary,
            lastNotifiedEpisodeKey = lastKey
        )
        if (!isModerator || !summary.isWarningThresholdReached) {
            if (lastKey != null) {
                prefs.edit().remove(episodePrefKey(uid)).apply()
            }
        } else if (decision.shouldNotify && decision.episodeKey != null) {
            prefs.edit().putString(episodePrefKey(uid), decision.episodeKey).apply()
        }
        return decision
    }

    private fun parseSummary(doc: DocumentSnapshot): StorageUsageSummary {
        val estimate = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        return StorageUsageSummary(
            usedBytes = (doc.get("usedBytes") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
            quotaBytes = (doc.get("quotaBytes") as? Number)?.toLong()
                ?.takeIf { it > 0L }
                ?: StorageUsageSummary.DEFAULT_FREE_QUOTA_BYTES,
            warningThresholdPercent = (doc.get("warningThresholdPercent") as? Number)?.toInt()
                ?.coerceIn(StorageUsageSummary.MIN_THRESHOLD_PERCENT, StorageUsageSummary.MAX_THRESHOLD_PERCENT)
                ?: StorageUsageSummary.DEFAULT_WARNING_THRESHOLD_PERCENT,
            fileCount = (doc.get("fileCount") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
            photoBytes = (doc.get("photoBytes") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
            videoBytes = (doc.get("videoBytes") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
            audioBytes = (doc.get("audioBytes") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
            activeBackendLabel = doc.getString("activeBackendLabel")
                ?.takeIf { it.isNotBlank() }
                ?: StorageUsageSummary.DEFAULT_BACKEND_LABEL,
            updatedAtMillis = FirestoreSnapshotMappers.timestampToMillis(
                doc.getTimestamp("updatedAt", estimate),
                System.currentTimeMillis()
            )
        )
    }

    private fun cacheAndEmit(summary: StorageUsageSummary) {
        prefs.edit()
            .putLong(KEY_USED_BYTES, summary.safeUsedBytes)
            .putLong(KEY_QUOTA_BYTES, summary.safeQuotaBytes)
            .putInt(KEY_THRESHOLD_PERCENT, summary.safeThresholdPercent)
            .putInt(KEY_FILE_COUNT, summary.fileCount.coerceAtLeast(0))
            .putLong(KEY_PHOTO_BYTES, summary.photoBytes.coerceAtLeast(0L))
            .putLong(KEY_VIDEO_BYTES, summary.videoBytes.coerceAtLeast(0L))
            .putLong(KEY_AUDIO_BYTES, summary.audioBytes.coerceAtLeast(0L))
            .putString(KEY_BACKEND_LABEL, summary.activeBackendLabel)
            .putLong(KEY_UPDATED_AT, summary.updatedAtMillis)
            .apply()
        _usageSummary.value = summary
    }

    private fun readCachedSummary(): StorageUsageSummary {
        return StorageUsageSummary(
            usedBytes = prefs.getLong(KEY_USED_BYTES, 0L).coerceAtLeast(0L),
            quotaBytes = prefs.getLong(KEY_QUOTA_BYTES, StorageUsageSummary.DEFAULT_FREE_QUOTA_BYTES)
                .takeIf { it > 0L } ?: StorageUsageSummary.DEFAULT_FREE_QUOTA_BYTES,
            warningThresholdPercent = prefs.getInt(
                KEY_THRESHOLD_PERCENT,
                StorageUsageSummary.DEFAULT_WARNING_THRESHOLD_PERCENT
            ).coerceIn(StorageUsageSummary.MIN_THRESHOLD_PERCENT, StorageUsageSummary.MAX_THRESHOLD_PERCENT),
            fileCount = prefs.getInt(KEY_FILE_COUNT, 0).coerceAtLeast(0),
            photoBytes = prefs.getLong(KEY_PHOTO_BYTES, 0L).coerceAtLeast(0L),
            videoBytes = prefs.getLong(KEY_VIDEO_BYTES, 0L).coerceAtLeast(0L),
            audioBytes = prefs.getLong(KEY_AUDIO_BYTES, 0L).coerceAtLeast(0L),
            activeBackendLabel = prefs.getString(KEY_BACKEND_LABEL, StorageUsageSummary.DEFAULT_BACKEND_LABEL)
                ?.takeIf { it.isNotBlank() } ?: StorageUsageSummary.DEFAULT_BACKEND_LABEL,
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    private fun episodePrefKey(uid: String): String = "${KEY_LAST_ALERT_PREFIX}${uid.ifBlank { "local" }}"

    companion object {
        private const val TAG = "StorageUsageRepo"
        private const val PREFS_NAME = "esosa_storage_usage"
        const val COLLECTION_STORAGE_USAGE = "storage_usage"
        const val DOC_GLOBAL = "global"
        const val COLLECTION_MODERATORS = "moderators"

        private const val KEY_USED_BYTES = "used_bytes"
        private const val KEY_QUOTA_BYTES = "quota_bytes"
        private const val KEY_THRESHOLD_PERCENT = "warning_threshold_percent"
        private const val KEY_FILE_COUNT = "file_count"
        private const val KEY_PHOTO_BYTES = "photo_bytes"
        private const val KEY_VIDEO_BYTES = "video_bytes"
        private const val KEY_AUDIO_BYTES = "audio_bytes"
        private const val KEY_BACKEND_LABEL = "active_backend_label"
        private const val KEY_UPDATED_AT = "updated_at_millis"
        private const val KEY_LAST_ALERT_PREFIX = "last_alert_episode_"
    }
}
