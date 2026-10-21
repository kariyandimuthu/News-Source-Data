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

android {
    namespace = "in.newssourcedata.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.newssourcedata.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"

        buildConfigField("String", "SUPABASE_URL", "\"" + supabaseUrl + "\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"" + supabasePublishableKey + "\"")
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
    implementation("com.rmtheis:tess-two:9.1.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
