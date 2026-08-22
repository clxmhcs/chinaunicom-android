plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clxmhcs.chinaunicom.data.account"
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
    implementation(project(":core:storage"))
    testImplementation(libs.junit4)
}
