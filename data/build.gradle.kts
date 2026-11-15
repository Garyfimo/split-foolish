import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
    // kotlinx.serialization: gitlive firestore uses @Serializable data classes as its typed
    // encode/decode surface. Applying the plugin here generates the serializers for our DTOs.
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    android {
        namespace = "com.garyfimo.splitfoolish.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withHostTest { }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.kotlinx.coroutines.core)
            // Firestore: primary store for shared data (groups, members, expenses). Friends
            // stay in SQLDelight because they're per-user quick-pick cache, not shared.
            implementation(libs.firebase.firestore)
            implementation(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidHostTest").dependencies {
            // JDBC driver lets host (JVM) tests exercise the schema without an Android emulator.
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("SplitFoolishDb") {
            packageName.set("com.garyfimo.splitfoolish.data.db")
        }
    }
}

// Firebase BoM so gitlive's firestore transitives (firebase-firestore-ktx etc.) resolve to a
// tested compatibility set. Same pattern as :feature:auth — KMP's source-set DSL doesn't expose
// platform(...) so we add it at the project level against the generated configuration.
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}
