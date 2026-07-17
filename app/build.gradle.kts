plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")

    }

android {
    namespace = "com.thesis.thesisapplication"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.thesis.thesisapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))

    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    implementation("com.mapbox.navigationcore:android-ndk27:3.26.0")
    implementation("com.mapbox.navigationcore:copilot-ndk27:3.26.0")
    implementation("com.mapbox.navigationcore:ui-maps-ndk27:3.26.0")
    implementation("com.mapbox.navigationcore:voice-ndk27:3.26.0")
    implementation("com.mapbox.navigationcore:tripdata-ndk27:3.26.0")
    implementation("com.mapbox.navigationcore:android-ndk27:3.26.0")
    implementation("com.mapbox.navigationcore:ui-components-ndk27:3.26.0")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

}