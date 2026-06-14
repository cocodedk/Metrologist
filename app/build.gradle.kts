plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

android {
    namespace = "com.cocode.measureapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // Version driven by VERSION_NAME env var in CI/release; falls back to "1.0.0" locally.
    val versionNameStr = System.getenv("VERSION_NAME") ?: "1.0.0"
    val versionParts = versionNameStr.split(".").map { it.toIntOrNull() ?: 0 }
    val vMajor = versionParts.getOrElse(0) { 0 }
    val vMinor = versionParts.getOrElse(1) { 0 }
    val vPatch = versionParts.getOrElse(2) { 0 }

    defaultConfig {
        applicationId = "com.cocode.measureapp"
        minSdk = 24
        targetSdk = 36
        versionCode = vMajor * 1_000_000 + vMinor * 1_000 + vPatch
        versionName = versionNameStr

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing: only configured when all four env vars are present and the keystore exists.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("KEY_ALIAS")
    val keyPassword = System.getenv("KEY_PASSWORD")
    val keystoreFile = if (keystorePath != null) rootProject.file(keystorePath) else null

    if (keystorePath != null && keystorePassword != null && keyAlias != null &&
        keyPassword != null && keystoreFile != null && keystoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Wire release signing config when available.
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

// Exclude compiler-generated members (data-class copy/component*/equals/hashCode/
// toString and Android/Compose boilerplate) so they do not create phantom branches.
private val jacocoClassExcludes = listOf(
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*Test*.*", "**/*\$\$serializer.*", "**/*ComposableSingletons*.*",
    "**/*_Factory.*", "**/databinding/**"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "JaCoCo line+branch coverage for debug JVM unit tests."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // Main Kotlin sources for the coverage report.
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

    val buildDirFile = layout.buildDirectory.get().asFile

    // Debug Kotlin classes. AGP 9 (built-in kotlinc) writes here; the second path
    // covers older AGP/Kotlin layouts. Whichever exists is used.
    val classDirs = files(
        fileTree("$buildDirFile/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
            exclude(jacocoClassExcludes)
        },
        fileTree("$buildDirFile/tmp/kotlin-classes/debug") {
            exclude(jacocoClassExcludes)
        }
    )
    classDirectories.setFrom(classDirs)

    // Coverage exec data produced by testDebugUnitTest (enableUnitTestCoverage).
    executionData.setFrom(
        fileTree(buildDirFile) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/*.exec",
                "jacoco/testDebugUnitTest.exec"
            )
        }
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.opencv)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}