import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingFile = rootProject.file("signing.properties")
val signing = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use(::load) }

android {
    namespace = "dev.quicktransfer.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.quicktransfer.app"
        minSdk = 33
        targetSdk = 36
        versionCode = providers.gradleProperty("appVersionCode").get().toInt()
        versionName = providers.gradleProperty("appVersionName").get()
    }
    buildFeatures { compose = true; aidl = true; buildConfig = true }
    signingConfigs {
        // Certificate rotation is applied by the external signing pipeline.
        if (signingFile.isFile && signing.getProperty("lineageFile").isNullOrBlank()) create("release") {
            storeFile = rootProject.file(requireNotNull(signing.getProperty("storeFile")))
            storePassword = requireNotNull(signing.getProperty("storePassword"))
            keyAlias = requireNotNull(signing.getProperty("keyAlias"))
            keyPassword = requireNotNull(signing.getProperty("keyPassword"))
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_17

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.3")
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("com.google.zxing:core:3.5.3")
}
