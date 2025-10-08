import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

// Read machine-local secrets from local.properties (which is gitignored).
// Keeps the FCM service-account private key out of version control.
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val fcmServiceAccountPrivateKey: String =
    localProperties.getProperty("fcm.serviceAccount.privateKey", "")

android {
    namespace = "com.example.smd_a1"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smd_a1"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "FCM_SERVICE_ACCOUNT_PRIVATE_KEY",
            "\"$fcmServiceAccountPrivateKey\""
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Firebase dependencies
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    // Firebase App Check (Debug provider for dev builds)
    implementation("com.google.firebase:firebase-appcheck-ktx")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    
    // Glide for image loading
    implementation(libs.glide)
    
    // Agora RTC SDK for video calling
    implementation("io.agora.rtc:full-sdk:4.1.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // Additional Espresso dependencies for comprehensive testing
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.5.1")
    
    // Test rules and runner
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    
    // UI Automator for advanced UI testing
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
    
    // Firebase testing
    androidTestImplementation("com.google.firebase:firebase-auth:22.3.0")
    androidTestImplementation("com.google.firebase:firebase-database:20.3.0")
}
