plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "edu.swpu.iot2022.moodlistener"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.swpu.iot2022.moodlistener"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX 核心库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    
    // ViewModel 和 LiveData
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // UI 相关
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // 数据库相关
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    
    // 测试相关
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // Activity
    implementation("androidx.activity:activity-ktx:1.8.2")
}