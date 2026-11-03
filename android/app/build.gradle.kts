plugins {
    alias(libs.plugins.android.application)
}

val aniApiBaseUrl = providers.gradleProperty("aniApiBaseUrl")
    .getOrElse("http://10.0.2.2:8000/")

android {
    namespace = "com.dysrupit.ani"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.dysrupit.ani"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "ANI_API_BASE_URL",
            "\"${aniApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    //noinspection WrongGradleMethod
    //noinspection WrongGradleMethod
    dependencies {
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.appcompat:appcompat:1.7.0")
        implementation("com.google.android.material:material:1.12.0")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

        implementation("com.squareup.retrofit2:retrofit:2.11.0")
        implementation("com.squareup.retrofit2:converter-gson:2.11.0")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")

        implementation("com.github.bumptech.glide:glide:4.16.0")

        implementation("androidx.datastore:datastore-preferences:1.1.1")

        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
        implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.2")
        implementation("androidx.fragment:fragment-ktx:1.8.0")

        implementation("androidx.camera:camera-core:1.4.2")
        implementation("androidx.camera:camera-camera2:1.4.2")
        implementation("androidx.camera:camera-lifecycle:1.4.2")
        implementation("androidx.camera:camera-view:1.4.2")

        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.2.1")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

        // ONNX Runtime for Android
        implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")

        // TensorFlow Lite Interpreter
        implementation("org.tensorflow:tensorflow-lite:2.16.1")

        // On-device generative AI for offline crop recommendations
        implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    }
}
