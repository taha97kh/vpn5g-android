plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.taha97kh.vpn5g"
        minSdk = 24
        targetSdk = 37
        versionCode = 27
        versionName = "1.1.25"

        // Build date stamped into BuildConfig.BUILD_TIME (VPN 5G About screen).
        buildConfigField("String", "BUILD_TIME", "\"2026-11-25\"")

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // VPN 5G (v1.1.3): one fixed signing key so future versions update in
        // place instead of forcing an uninstall — every CI build so far was
        // signed with a fresh auto-generated debug key. The keystore is
        // decoded into V2rayNG/app/vpn5g-debug.keystore by CI from a secret;
        // without the file and the env vars below, the default debug signing
        // stays, so local builds are unaffected.
        getByName("debug") {
            val vpn5gKeystoreFile = file("vpn5g-debug.keystore")
            val vpn5gStorePassword = System.getenv("VPN5G_KEYSTORE_PASSWORD")
            val vpn5gKeyAlias = System.getenv("VPN5G_KEY_ALIAS")
            val vpn5gKeyPassword = System.getenv("VPN5G_KEY_PASSWORD")
            if (vpn5gKeystoreFile.exists() && !vpn5gStorePassword.isNullOrEmpty() &&
                !vpn5gKeyAlias.isNullOrEmpty() && !vpn5gKeyPassword.isNullOrEmpty()
            ) {
                storeFile = vpn5gKeystoreFile
                storePassword = vpn5gStorePassword
                keyAlias = vpn5gKeyAlias
                keyPassword = vpn5gKeyPassword
            }
        }
        // v1.1.4: the Release build is signed with the same fixed key as
        // Debug (same keystore file + env vars), so the Play Store
        // universal APK updates in place. Without the file/env vars the
        // build fails loudly at signing time instead of shipping an
        // unsigned release — never fall back to the debug key here.
        create("release") {
            val vpn5gKeystoreFile = file("vpn5g-debug.keystore")
            val vpn5gStorePassword = System.getenv("VPN5G_KEYSTORE_PASSWORD")
            val vpn5gKeyAlias = System.getenv("VPN5G_KEY_ALIAS")
            val vpn5gKeyPassword = System.getenv("VPN5G_KEY_PASSWORD")
            if (vpn5gKeystoreFile.exists() && !vpn5gStorePassword.isNullOrEmpty() &&
                !vpn5gKeyAlias.isNullOrEmpty() && !vpn5gKeyPassword.isNullOrEmpty()
            ) {
                storeFile = vpn5gKeystoreFile
                storePassword = vpn5gStorePassword
                keyAlias = vpn5gKeyAlias
                keyPassword = vpn5gKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            // v1.1.17 (connect crash, 1.1.15/1.1.16): R8 minification is
            // OFF again — exactly like 1.1.14, which connected fine.
            // The native JNI contract lives in C strings R8 cannot
            // see: hev-jni.c JNI_OnLoad does
            // FindClass("com/v2ray/ang/service/TProxyService") and
            // RegisterNatives for TProxyStartService/StopService/
            // IsRunning/GetStats. With R8 on, the class was renamed,
            // FindClass returned NULL, JNI_OnLoad returned JNI_ERR
            // and System.loadLibrary threw UnsatisfiedLinkError on
            // the main thread inside CoreVpnService.onStartCommand
            // (crash report 2026-11-24 02:59:43Z, 1.1.16) — a
            // deterministic crash on EVERY connect, uncatchable from
            // the Activity because the throw is service-side. The
            // 1.1.15 R8 experiment also broke Gson parsing (1.1.16
            // keeps). Keep rules for the JNI classes stay in
            // proguard-rules.pro as documentation and insurance for
            // any future re-enable, but they are NOT the fix: the
            // fix is minification OFF. The real anti-crack protection
            // is server-side enforcement (H1/H2), never obfuscation.
            // Side effect: the 1.1.16 -assumenosideeffects log
            // stripping only applies under minification, so it stops
            // applying here; LogUtil's default is already warning.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "VPN5G_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            val versionCodes =
                mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "VPN5G_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en",
            "zh-rCN",
            "zh-rTW",
            "vi",
            "ru",
            "fa",
            "ar",
            "az",
            "bn",
            "bqi-rIR"
        )
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // Treat Jupiter discovery warnings as failures: JUnit 4 failed on an invalid @Test
            // method, such as one that returns a value, while Jupiter only warns and skips it.
            // The key is @API(status = EXPERIMENTAL) in JUnit 6; after a JUnit upgrade, check
            // that a temporary `@Test fun probe() = 1` still fails the unit test task.
            it.systemProperty("junit.platform.discovery.issue.severity.critical", "WARNING")
        }
    }

}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    // Coil 3 ships no network stack: without this, remote announcement
    // images (v1.1.5) fail silently and only the text shows.
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Testing Libraries
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
