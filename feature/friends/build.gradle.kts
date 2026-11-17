import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    android {
        namespace = "com.garyfimo.splitfoolish.feature.friends"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withHostTest { }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Compose UI tests live under src/iosTest/ because the Android host test target (JVM
        // unit tests) can't run them without Robolectric + a main-thread looper setup. The iOS
        // sim target is the Compose UI test harness the project supports today.
        iosTest.dependencies {
            implementation(libs.compose.ui.test)
        }
    }
}
