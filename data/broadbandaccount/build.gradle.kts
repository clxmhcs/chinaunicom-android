plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clxmhcs.chinaunicom.data.broadbandaccount"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
}
