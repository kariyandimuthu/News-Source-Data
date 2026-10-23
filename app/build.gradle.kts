import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { stream -> this.load(stream) }
    }
}
val supabaseUrl = localProperties.getProperty("SUPABASE_URL")
    ?: System.getenv("SUPABASE_URL")
    ?: "https://cmvvcwcavopmpwkowhnz.supabase.co"
val supabasePublishableKey = localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY")
    ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
    ?: "sb_publishable_iY6fpaaFMuRpBpShDVSCtA_OeRglNDw"

// Main-branch builds may provide a private, GitHub-Secret-backed signing keystore.
// Pull-request builds have no signing secret and use the normal ephemeral debug key.
val permanentSigningKeystore = System.getenv("ANDROID_SIGNING_KEYSTORE")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
val permanentSigningStorePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
val permanentSigningKeyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")
val permanentSigningKeyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
val usePermanentSigning = permanentSigningKeystore != null &&
    permanentSigningKeystore.exists() &&
    !permanentSigningStorePassword.isNullOrBlank() &&
    !permanentSigningKeyPassword.isNullOrBlank() &&
    !permanentSigningKeyAlias.isNullOrBlank()

android {
    namespace = "in.newssourcedata.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.newssourcedata.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "2.4"

        // Limit this sideload APK to the user's 64-bit ARM Android phone.
        ndk {
            abiFilters += setOf("arm64-v8a")
        }

        buildConfigField("String", "SUPABASE_URL", "\"" + supabaseUrl + "\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"" + supabasePublishableKey + "\"")
    }

    // Use a permanent, private key only when the workflow supplies all credentials.
    // Otherwise Android Gradle Plugin retains its standard debug-signing behaviour.
    signingConfigs {
        if (usePermanentSigning) {
            create("permanent") {
                storeFile = checkNotNull(permanentSigningKeystore)
                storePassword = checkNotNull(permanentSigningStorePassword)
                keyAlias = checkNotNull(permanentSigningKeyAlias)
                keyPassword = checkNotNull(permanentSigningKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (usePermanentSigning) {
                signingConfig = signingConfigs.getByName("permanent")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}