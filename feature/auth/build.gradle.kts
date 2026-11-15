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
        namespace = "com.garyfimo.splitfoolish.feature.auth"
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
            // gitlive Firebase Auth: one commonMain API that delegates to Google's native SDKs
            // under the hood (Firebase Android SDK on Android, Firebase iOS SDK via SPM on iOS).
            implementation(libs.firebase.auth)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// KMP's source-set DSL doesn't expose `platform(...)` directly, so the Firebase BoM is applied
// at the project `dependencies` level against the generated `androidMainImplementation`
// configuration. Gitlive's firebase-auth-android artifact declares its Firebase child libraries
// (firebase-auth-ktx, firebase-common-ktx) without versions, so without the BoM Gradle can't
// resolve them. The BoM pins the whole family to one tested compatibility set.
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}
