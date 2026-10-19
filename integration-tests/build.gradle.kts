plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.dcorp.licensecore.it"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64())

    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
