package com.falahpro.app.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.falahpro.app.BuildConfig
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Supabase client singleton with Auth, session persistence, auto-refresh, and encrypted storage.
 */
object SupabaseClientProvider {

    private const val ENCRYPTED_PREFS_NAME = "falahpro_supabase_auth"
    private const val AUTH_STORAGE_TAG = "AuthStorage"

    val client: SupabaseClient by lazy {
        val secureSettings = SharedPreferencesSettings(createEncryptedPrefs(applicationContext()))
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
                sessionManager = SettingsSessionManager(settings = secureSettings)
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            openEncryptedPrefs(context)
        } catch (e: GeneralSecurityException) {
            recoverCorruptedEncryptedPrefs(context, e)
            openEncryptedPrefs(context)
        } catch (e: IOException) {
            recoverCorruptedEncryptedPrefs(context, e)
            openEncryptedPrefs(context)
        }
    }

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Keystore/keyset mismatch (reinstall, backup restore, OEM keystore) makes
     * EncryptedSharedPreferences.create throw AEADBadTagException on launch.
     * Drop the unreadable file so a fresh keyset can be written. Session is
     * already unreadable, so the user is asked to sign in again.
     */
    private fun recoverCorruptedEncryptedPrefs(context: Context, cause: Exception) {
        Log.e(AUTH_STORAGE_TAG, "Encrypted session storage unreadable; resetting", cause)
        context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
    }

    private fun applicationContext(): Context {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val app = activityThreadClass
            .getMethod("currentApplication")
            .invoke(null) as? Context
        return checkNotNull(app?.applicationContext) {
            "Application context not available for Supabase session storage"
        }
    }
}
