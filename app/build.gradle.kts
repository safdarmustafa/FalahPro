plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.falahpro.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.falahpro.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"https://srdqdvavbznhjbaiajxk.supabase.co\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNyZHFkdmF2YnpuaGpiYWlhanhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU4ODI3NTIsImV4cCI6MjEwMTQ1ODc1Mn0.BYOAuR9ZTiMRIyi3WORzaG2AMcw5tu_Or9m4q4OMvcU\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.batoulapps.adhan:adhan2:0.0.6")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.7")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ✅ SUPABASE AUTH
    // 3.2.6 is the newest stable auth-kt compatible with this project's Kotlin 2.2.x
    // (3.3+ ships Kotlin 2.4 metadata and fails compilation without a Kotlin upgrade).
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.3.1")
    implementation("androidx.security:security-crypto:1.1.0")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}