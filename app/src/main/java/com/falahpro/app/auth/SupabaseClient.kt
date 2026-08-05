package com.falahpro.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.falahpro.app.BuildConfig
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient

/**
 * Supabase client singleton with Auth, session persistence, auto-refresh, and encrypted storage.
 */
object SupabaseClientProvider {

    private const val ENCRYPTED_PREFS_NAME = "falahpro_supabase_auth"

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

    private fun createEncryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

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
