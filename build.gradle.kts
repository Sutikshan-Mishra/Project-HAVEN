// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so org.jetbrains.kotlin.android is
// intentionally NOT applied anywhere. To use a newer Kotlin than the one AGP ships with,
// the Android docs say to put the Kotlin Gradle plugin on the root buildscript classpath.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Keep in sync with `kotlin` in gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
