import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Serialization / time
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Sensors
    implementation(libs.androidx.health.connect)
    implementation(libs.play.services.location)

    // Image loading (for route map in debug card)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Markdown rendering for AI thinking / response bodies
    implementation(libs.markdown.renderer.m3)

    // Unit tests (JVM + Robolectric — see app/src/test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Hard cap on Kotlin file length. A regression backstop, not the real rule: the real rule is
 * "one file, one responsibility" (see AGENTS.md). Files that hit this are doing too much — split
 * them into atomic files rather than suppressing. Detekt has no per-file line rule (LargeClass
 * measures class bodies, and most large files here are top-level @Composable functions), so this
 * focused task enforces it. Wired into `check` and run explicitly in CI.
 */
val maxKotlinFileLines = 500

tasks.register("checkFileLength") {
    group = "verification"
    description = "Fails if any src/main Kotlin file exceeds $maxKotlinFileLines lines."
    val mainSrc = layout.projectDirectory.dir("src/main")
    val projectRoot = rootDir
    doLast {
        val ktFiles = mainSrc.asFile.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders = ktFiles
            .mapNotNull { file ->
                val lines = file.readLines().size
                if (lines > maxKotlinFileLines) lines to file.relativeTo(projectRoot).path else null
            }
            .sortedByDescending { it.first }
        logger.lifecycle(
            "checkFileLength: scanned ${ktFiles.size} Kotlin files, " +
                "largest ${ktFiles.maxOfOrNull { it.readLines().size } ?: 0} lines (cap $maxKotlinFileLines).",
        )
        if (offenders.isNotEmpty()) {
            val report = offenders.joinToString("\n") { (lines, path) -> "  $lines  $path" }
            throw GradleException(
                "These Kotlin files exceed $maxKotlinFileLines lines — split into atomic files " +
                    "(one file, one responsibility), don't suppress:\n$report",
            )
        }
    }
}

tasks.named("check") { dependsOn("checkFileLength") }
