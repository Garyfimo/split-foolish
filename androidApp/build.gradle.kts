import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // google-services parses androidApp/google-services.json at build time and injects the
    // Firebase project config into resources. Without this plugin, FirebaseApp.initializeApp()
    // returns null at runtime and every auth call throws.
    alias(libs.plugins.googleServices)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    // MainActivity constructs the SQLDelight database directly (it's the only place with a
    // Context) so it imports from :data. :shared also depends on :data, but Gradle doesn't
    // propagate `implementation` deps transitively to app modules.
    implementation(project(":data"))
    // :feature:auth types (AuthState, FirebaseAuthRepository) are instantiated here for the
    // same reason — top-level wiring belongs in the app module.
    implementation(project(":feature:auth"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.garyfimo.splitfoolish"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.garyfimo.splitfoolish"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}