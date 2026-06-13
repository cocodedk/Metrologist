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

    defaultConfig {
        applicationId = "com.cocode.measureapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}