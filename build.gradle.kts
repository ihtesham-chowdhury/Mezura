// Top-level build file: declares plugin versions once, applied per-module below.
plugins {
    id("com.android.application") version "9.0.1" apply false
    // Built against AGP 9's built-in Kotlin support is real but very new (Jan 2026) and I
    // can't fully verify its DSL from here, so this project opts back into the classic,
    // long-stable Kotlin Android plugin instead - see gradle.properties for the two flags
    // that enable that compatibility path, and README.md "About the toolchain versions"
    // for why. Drop this plugin line (and those two properties) if you'd rather use AGP's
    // new built-in Kotlin support directly.
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
}
