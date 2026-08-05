package com.falahpro.app.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Supabase Authentication facade used by Login, AuthGate, and Profile.
 */
object SupabaseAuthManager {

    private val auth get() = SupabaseClientProvider.client.auth

    val sessionStatus: StateFlow<SessionStatus>
        get() = auth.sessionStatus

    suspend fun awaitInitialization() {
        auth.awaitInitialization()
    }

    suspend fun currentUser(): UserInfo? {
        auth.awaitInitialization()
        return auth.currentUserOrNull()
    }

    suspend fun currentSession(): UserSession? {
        auth.awaitInitialization()
        return auth.currentSessionOrNull()
    }

    fun isLoggedIn(): Boolean {
        return auth.currentSessionOrNull() != null
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUpWithEmail(fullName: String, email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName.trim())
            }
        }
    }

    suspend fun resetPasswordForEmail(email: String) {
        auth.resetPasswordForEmail(email = email.trim())
    }

    suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Secure account deletion is NOT implemented on-device.
     *
     * Required backend (Supabase Edge Function or other trusted server):
     *   POST /functions/v1/delete-account
     *   Authorization: Bearer <user access_token>
     *   Server uses the Supabase **service role** key to call admin.deleteUser(uid),
     *   then the client signs out locally.
     *
     * Never embed the service role key in the Android app.
     */
    suspend fun deleteAccount(): DeleteAccountResult {
        // TODO(SECURE_DELETE): Call Edge Function `delete-account` with the user JWT,
        // then invoke signOut() only after the backend confirms deletion.
        return DeleteAccountResult.NotImplemented
    }

    fun displayName(user: UserInfo?): String {
        if (user == null) return "Falah Pro User"
        return user.userMetadataString("full_name")
            ?: user.email?.substringBefore("@")
            ?: "Falah Pro User"
    }

    fun email(user: UserInfo?): String {
        return user?.email ?: "No Email"
    }

    private fun UserInfo.userMetadataString(key: String): String? {
        return userMetadata?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}

sealed interface DeleteAccountResult {
    data object NotImplemented : DeleteAccountResult
}
