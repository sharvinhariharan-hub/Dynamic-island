plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.dynamicisland.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.dynamicisland.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    val signingNames =
        listOf(
            "SIGNING_STORE_FILE",
            "SIGNING_STORE_PASSWORD",
            "SIGNING_KEY_ALIAS",
            "SIGNING_KEY_PASSWORD",
        )
    val signingValues = signingNames.map { System.getenv(it) }
    val hasReleaseSigning = signingValues.all { !it.isNullOrBlank() }
    if (signingValues.any { !it.isNullOrBlank() } && !hasReleaseSigning) {
        throw GradleException(
            "Incomplete release signing configuration: provide all four SIGNING_* variables or none. Never use debug signing for release."
        )
    }
    if (hasReleaseSigning && !file(signingValues.first()!!).isFile) {
        throw GradleException("Configured release keystore is unavailable.")
    }
    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(System.getenv("SIGNING_STORE_FILE"))
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
