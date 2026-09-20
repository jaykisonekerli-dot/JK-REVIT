plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.jk.revitar"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.jk.revitar"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0-no-arcore"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 3D Filament/SceneView SEM ARCore
    implementation("io.github.sceneview:sceneview:4.37.0")

    // Leitura inicial do QR
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Camera normal + rastreamento contínuo do QR impresso
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
