import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val sharedTestAssetsDir = layout.buildDirectory.dir("generated/shared-test-assets")
val syncSharedTestAssets by tasks.registering(Sync::class) {
    from("src/test/resources")
    into(sharedTestAssetsDir)
}

android {
    namespace = "com.receiptscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.receiptscanner"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    sourceSets {
        getByName("test") {
            java.srcDir("src/sharedTest/kotlin")
        }
        getByName("androidTest") {
            java.srcDir("src/sharedTest/kotlin")
            assets.srcDir(sharedTestAssetsDir.get().asFile)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.exifinterface)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.bundles.networking)
    ksp(libs.moshi.kotlin.codegen)

    // CameraX
    implementation(libs.bundles.camerax)

    // ML Kit
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.entity.extraction)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Unit Testing
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)

    // Android / Instrumentation Testing
    androidTestImplementation(libs.bundles.android.testing)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("AndroidTestAssets") }
    .configureEach {
        dependsOn(syncSharedTestAssets)
    }
