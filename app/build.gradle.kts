import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Derive version from git tags so it stays in sync automatically.
// Tag format: v1.0.0 or v1.0.0-alpha.1
fun gitVersionName(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .redirectErrorStream(true)
            .start()
        val tag = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (tag.startsWith("v")) tag.substring(1) else tag
    } catch (_: Exception) {
        "0.0.0"
    }
}

fun gitVersionCode(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "HEAD", "--count")
            .redirectErrorStream(true)
            .start()
        val count = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        count.toIntOrNull() ?: 1
    } catch (_: Exception) {
        1
    }
}

// Read local.properties for sensitive config (file is gitignored)
val localProps = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

@Suppress("DEPRECATION")
android {
    namespace = "fr.bsodium.cron"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "fr.bsodium.cron"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode()
        versionName = gitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject Google Routes API key from local.properties (gitignored)
        buildConfigField(
            "String",
            "GOOGLE_ROUTES_API_KEY",
            "\"${localProps.getProperty("GOOGLE_ROUTES_API_KEY", "")}\""
        )
    }

    signingConfigs {
        create("release") {
            val keyStorePath = System.getenv("RELEASE_KEYSTORE_PATH") ?: localProps.getProperty("STORE_FILE")
            if (keyStorePath != null) {
                storeFile = file(keyStorePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: localProps.getProperty("STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: localProps.getProperty("KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: localProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
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
}

tasks.configureEach {
    if (name == "assembleRelease") {
        doLast {
            val versionName = android.defaultConfig.versionName ?: "unknown"
            val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            apkDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apk ->
                val target = File(apkDir, "cron-${versionName}.apk")
                apk.renameTo(target)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
