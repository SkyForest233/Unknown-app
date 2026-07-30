import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 读取签名保密文件（不入库）：本地使用 keystore.properties，
// CI 上不存在该文件时回退到环境变量。
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) {
        FileInputStream(f).use { load(it) }
    }
}

fun signingProp(key: String, envKey: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(envKey)

android {
    namespace = "com.agon.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.unknown.suiji.rng2941"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("${rootProject.projectDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            // 凭据来源优先级：keystore.properties（本地）> 环境变量（CI）
            // 两者都缺失时 release 签名不配置，assembleRelease 会明确报错而不是静默出未签名包
            val storePath = signingProp("storeFile", "RELEASE_KEYSTORE_PATH")
            val storePwd = signingProp("storePassword", "RELEASE_KEYSTORE_PASSWORD")
            val alias = signingProp("keyAlias", "RELEASE_KEY_ALIAS")
            val keyPwd = signingProp("keyPassword", "RELEASE_KEY_PASSWORD")
            if (storePath != null && storePwd != null && alias != null && keyPwd != null) {
                storeFile = rootProject.file(storePath)
                storePassword = storePwd
                keyAlias = alias
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // R8 代码压缩 + 资源压缩，但不混淆（proguard-rules.pro 中 -dontobfuscate）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.core:core-ktx:1.15.0")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // M3 Expressive shape morphing (RoundedPolygon / Morph)
    implementation("androidx.graphics:graphics-shapes:1.0.1")

    // \u4e3b\u9898\u79cd\u5b50\u8272\u751f\u6210\u5b8c\u6574 MD3 \u914d\u8272\u65b9\u6848\uff08\u540c Tomato \u9879\u76ee\u65b9\u6848\uff09
    implementation("com.materialkolor:material-kolor:4.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
