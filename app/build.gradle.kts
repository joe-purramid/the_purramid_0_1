plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.purramid.thepurramid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.purramid.thepurramid"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }

    // If using Room with KSP, you might need to configure sourcesets
    // sourceSets.configureEach { // <- Potentially needed for KSP + Room
    //     kotlin.srcDir("build/generated/ksp/$name/kotlin")
    // }
}

dependencies {

    // annotationProcessor(libs.glide.compiler)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:...") // Version from your TOML or define one
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:...") // Version from your TOML or define one
    implementation(libs.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidsvg)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.window)
    implementation(libs.appcompat)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.cardview)
    implementation(libs.constraintlayout)
    implementation(libs.glide.core)
    implementation(libs.gson)
    testImplementation(libs.junit)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    ksp(libs.glide.compiler)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}