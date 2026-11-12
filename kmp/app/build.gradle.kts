plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":ui"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.androidx.core.ktx)
            // 截屏采集服务复用 ui 模块同一套本地中文 OCR 引擎。
            implementation(libs.mlkit.text.recognition.chinese)
        }
    }
}

android {
    namespace = "com.meow.ledger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.meow.ledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.8.8"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
