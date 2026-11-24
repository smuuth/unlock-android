import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun signingValue(propKey: String, envKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

val telemetryAppId = signingValue("telemetryAppId", "UNLOCK_TELEMETRY_APP_ID").orEmpty()

val releaseStoreFile = signingValue("storeFile", "UNLOCK_KEYSTORE_PATH")?.let { file(it) }
val releaseStorePassword = signingValue("storePassword", "UNLOCK_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "UNLOCK_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "UNLOCK_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

// Im Release-Workflow gesetzt: lieber abbrechen, als eine mit dem Debug-Key signierte APK zu veröffentlichen
// (die ließe sich nicht über die installierte App aktualisieren).
if (System.getenv("UNLOCK_REQUIRE_RELEASE_SIGNING") == "true" && !hasReleaseSigning) {
    throw GradleException(
        "Release-Signing fehlt: UNLOCK_KEYSTORE_PATH, UNLOCK_KEYSTORE_PASSWORD, UNLOCK_KEY_ALIAS und UNLOCK_KEY_PASSWORD setzen.",
    )
}

android {
    namespace = "dev.unlock"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.unlock"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.0"
        buildConfigField("String", "TELEMETRY_APP_ID", "\"${telemetryAppId.replace("\"", "")}\"")
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "/META-INF/*.version")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.graphics.shapes)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
