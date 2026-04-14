plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bitchat.android.ai"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    androidResources {
        noCompress += "tflite"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Required by Robolectric so JVM tests can open assets (model, tokenizer, label map)
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (for VoiceVisualizer)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Vosk offline speech recognition (moved from :app)
    implementation(libs.vosk.android)

    // TensorFlow Lite (message priority classifier + vision classifier)
    implementation(libs.tflite)

    // Testing
    testImplementation(libs.bundles.testing)
    // org.json is stubbed in Android unit tests; provide the real implementation
    // so @BeforeClass can parse tokenizer.json and label_map.json on the JVM.
    testImplementation("org.json:json:20231013")

    // Instrumented tests (androidTest/)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
