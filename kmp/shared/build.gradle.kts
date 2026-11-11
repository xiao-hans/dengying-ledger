plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
            }
        }
    }

    // 桌面 JVM 目标只用于跑 commonTest，不产出应用。
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // sqlite-driver 只在 JVM 上能跑，所以仓储测试放在 jvmTest。
        getByName("jvmTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("androidMain").dependencies {
            // okhttp engine 传递 okhttp-android 5.5.0，要求 compileSdk 37；
            // CIO 是纯 Kotlin 实现，Android 与 JVM 共用，无额外 SDK 要求。
            implementation(libs.ktor.client.cio)
        }
        getByName("jvmMain").dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

android {
    namespace = "com.meow.ledger.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("MeowDatabase") {
            packageName.set("com.meow.ledger.shared.db")
            // 1 版是已发布的库。后来加的表必须走 migrations/ 的迁移，
            // 否则老安装查询不存在的表会直接崩溃。
            version = 3
        }
    }
}
