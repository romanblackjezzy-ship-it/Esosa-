package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val label: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreError"

    fun handleFirestoreError(
        error: Throwable,
        operationType: OperationType,
        path: String?,
        auth: FirebaseAuth? = null
    ) {
        val currentUser = auth?.currentUser
        val authInfo = JSONObject().apply {
            put("userId", currentUser?.uid ?: JSONObject.NULL)
            put("email", currentUser?.email ?: JSONObject.NULL)
            put("emailVerified", currentUser?.isEmailVerified ?: false)
            put("isAnonymous", currentUser?.isAnonymous ?: false)
            put("tenantId", currentUser?.tenantId ?: JSONObject.NULL)
            val providerArray = JSONArray()
            currentUser?.providerData?.forEach { info ->
                providerArray.put(
                    JSONObject().apply {
                        put("providerId", info.providerId)
                        put("displayName", info.displayName ?: JSONObject.NULL)
                        put("email", info.email ?: JSONObject.NULL)
                        put("photoUrl", info.photoUrl?.toString() ?: JSONObject.NULL)
                    }
                )
            }
            put("providerInfo", providerArray)
        }

        val errInfo = JSONObject().apply {
            put("error", error.localizedMessage ?: error.message ?: error.toString())
            put("operationType", operationType.label)
            put("path", path ?: JSONObject.NULL)
            put("authInfo", authInfo)
        }
        Log.e(TAG, "Firestore Error: ${errInfo.toString()}")
    }
}
