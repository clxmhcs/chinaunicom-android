plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.clxmhcs.chinaunicom"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.clxmhcs.chinaunicom"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.9.0-m9h1r1-i1r2-m10a1r2-m12b1-m13a1-m13b1-m14a1-m14b1-m14c1-m14d1-m14e1-m14f1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-network"))
    implementation(project(":core-security"))
    implementation(project(":core-storage"))
    implementation(project(":data-account"))
    implementation(project(":data-quota"))
    implementation(project(":data-balance"))
    implementation(project(":data-bill"))
    implementation(project(":data-package"))
    implementation(project(":data-integral"))
    implementation(project(":data-order"))
    implementation(project(":data-receipt"))
    implementation(project(":feature-dashboard"))
    implementation(project(":feature-voice"))
    implementation(project(":feature-comprehensive"))
    implementation(project(":feature-business"))
    implementation(project(":feature-account"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-receipt"))
    implementation(project(":automation"))
    implementation(project(":capture"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
}
