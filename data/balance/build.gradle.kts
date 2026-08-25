plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clxmhcs.chinaunicom.data.balance"
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
    implementation(project(":core:login"))
    implementation(project(":data:refresh"))
    implementation(project(":data:settings"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
}
