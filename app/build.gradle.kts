plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutlibraries)
}

/**
 * AboutLibraries プラグインが AGP 9.x の AppExtension を検出できないため、
 * 非 Android タスク exportLibraryDefinitions の出力を
 * AGP 9.x の addGeneratedSourceDirectory API 経由で raw リソースとして登録する。
 */
abstract class GenerateAboutLibrariesResTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputJson: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val rawDir = outputDir.get().dir("raw").asFile
        rawDir.mkdirs()
        inputJson.get().asFile.copyTo(
            rawDir.resolve("aboutlibraries.json"),
            overwrite = true,
        )
    }
}

val generateAboutLibrariesRes by tasks.registering(GenerateAboutLibrariesResTask::class) {
    dependsOn("exportLibraryDefinitions")
    inputJson.set(layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json"))
    outputDir.set(layout.buildDirectory.dir("generated/aboutLibrariesRes"))
}

android {
    namespace = "org.ukky.notitrace"
    compileSdk {
        version = release(36)
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("NOTITRACE_ANDROID_JKS_PATH")
            val keystorePassword = System.getenv("NOTITRACE_ANDROID_JKS_PASSWORD")
            val alias = System.getenv("NOTITRACE_ANDROID_JKS_ALIAS")
            val keyPassword = System.getenv("NOTITRACE_ANDROID_JKS_KEY_PASSWORD")

            if (listOf(keystorePath, keystorePassword, alias, keyPassword).any { it.isNullOrBlank() }) {
                throw GradleException("Signing env vars are missing.")
            }

            storeFile = file(keystorePath!!)
            storePassword = keystorePassword
            keyAlias = alias
            this.keyPassword = keyPassword
        }
    }

    defaultConfig {
        applicationId = "org.ukky.notitrace"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "(debug)"
            resValue("string", "app_name", "(debug)NotiTrace")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// AGP 9.x の正規 API でバリアントごとに生成リソースディレクトリを登録
androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(
        generateAboutLibrariesRes,
        GenerateAboutLibrariesResTask::outputDir,
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // SQLCipher
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // AboutLibraries
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    // Unit Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)

    // Android Test
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}