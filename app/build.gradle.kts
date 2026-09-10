import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
plugins {
    alias(libs.plugins.androidApplication)
    id("org.jetbrains.kotlin.android")
    id("org.lsposed.lsplugin.jgit") version "1.1" 
    id("org.lsposed.lsplugin.resopt") version "1.6" 
    id("org.lsposed.lsplugin.apksign") version "1.4"
    id("org.lsposed.lsplugin.apktransform") version "1.2" 
}

fun gitOutput(vararg args: String): String? {
    return runCatching {
        providers.exec {
            commandLine("git", *args)
        }
            .standardOutput
            .asText
            .get()
            .trim()
            .takeIf { it.isNotEmpty() }
    }.getOrNull()
}

val releaseCode = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val releaseName: String by rootProject
val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(name: String): String? {
    val envName = when (name) {
        "releaseStoreFile" -> "RELEASE_STORE_FILE"
        "releaseStorePassword" -> "RELEASE_STORE_PASSWORD"
        "releaseKeyAlias" -> "RELEASE_KEY_ALIAS"
        "releaseKeyPassword" -> "RELEASE_KEY_PASSWORD"
        else -> null
    }
    return envName?.let { System.getenv(it) }
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }
}

fun abiFiltersFromProperty(): List<String> {
    val raw = providers.gradleProperty("bbzqAbiFilters").orNull?.trim().orEmpty()
    return raw
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf("arm64-v8a") }
}

fun buildOutputSuffix(): String {
    return providers.gradleProperty("bbzqOutputSuffix").orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "-$it" }
        .orEmpty()
}

listOf(
    "releaseStoreFile",
    "releaseStorePassword",
    "releaseKeyAlias",
    "releaseKeyPassword",
).forEach { name ->
    signingValue(name)?.let { extensions.extraProperties.set(name, it) }
}

apksign {
    storeFileProperty = "releaseStoreFile"
    storePasswordProperty = "releaseStorePassword"
    keyAliasProperty = "releaseKeyAlias"
    keyPasswordProperty = "releaseKeyPassword"
}

apktransform {
    copy {
        when (it.buildType) {
            "release" -> file("${it.name}/bbzq_v${releaseName}-${releaseCode}${buildOutputSuffix()}.apk")
            else -> null
        }
    }
}

android {
    namespace = "io.github.bbzq"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.bbzq"
        minSdk = 24
        targetSdk = 37
        versionCode = releaseCode
        versionName = "v${releaseName}-${releaseCode}"
        buildConfigField("String", "RELEASE_NAME", "\"$releaseName\"")

        ndk {
            abiFilters += abiFiltersFromProperty()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        jniLibs {
            // LSPosed loads module JNI libs from base.apk!/lib/<abi>, which requires STORED entries.
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/native-image/**",
                "META-INF/version-control-info.textproto",
                "kotlin-tooling-metadata.json",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-language-version=2.0",
            )
        }
    }
}

configurations.all {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.dexkit)
    implementation(libs.okhttp)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
}
