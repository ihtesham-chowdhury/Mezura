import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Signing credentials live in keystore.properties at the project root, which is gitignored -
// they never belong in a build script. If that file isn't there (a fresh clone, someone
// else's machine) the project still syncs and debug builds still work; only the release
// signing is skipped. See SIGNING.md.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "rocks.ndot.widgets"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.instrument.mezura"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // View binding for the widget configuration screens, Compose for the instruments.
        viewBinding = true
        compose = true
    }
}

// The old android { kotlinOptions { jvmTarget = "17" } } DSL is hard-deprecated (an error,
// not just a warning) as of this Kotlin Gradle Plugin version - this top-level block is the
// current replacement. See https://kotlinlang.org/docs/gradle-compiler-options.html
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Background work for widgets that can't tick live (Sundial, Age in Motion) - the
    // correct, battery-respectful way to refresh a RemoteViews widget periodically.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Location for the Sundial widget's real sunrise/sunset lookup (optional at runtime -
    // the widget works from a manually-set city too, see SolarMath.kt / ConfigureSundialActivity).
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coroutines, used by the WorkManager CoroutineWorkers.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Jetpack Compose, for the instruments app. Pinned to the newest line that still builds
    // against compileSdk 36: Compose 1.12 and Lifecycle 2.11 require compileSdk 37, which
    // this machine's SDK doesn't have yet. Material 3 is deliberately absent - every control
    // here is drawn to match its instrument, so a Material look would be something to fight.
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.animation:animation:1.11.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
