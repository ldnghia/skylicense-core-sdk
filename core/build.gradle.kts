import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kover)
    `maven-publish`
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    explicitApi()

    android {
        namespace = "com.dcorp.licensecore"
        compileSdk = 36
        minSdk = 24
        withHostTest {}
        // `consumerProguardFiles(...)` is not a recognized property on this AGP 9 KMP
        // `android {}` DSL (confirmed by a real build error in this dev environment - no
        // Android SDK to try alternatives against). core/src/androidMain/consumer-rules.pro
        // exists but is NOT wired into the build yet - find the right API before shipping a
        // release AAR, or R8 will strip kotlinx.serialization's generated serializers.
    }

    val xcf = XCFramework("LicenseCore")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "LicenseCore"
            isStatic = true
            xcf.add(this)
        }
    }

    mingwX64 {
        binaries.sharedLib {
            baseName = "licensecore"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.cryptography.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.androidx.core.ktx)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.cryptography.provider.apple)
        }
        mingwX64Main.dependencies {
            implementation(libs.ktor.client.winhttp)
            implementation(libs.cryptography.provider.openssl3.prebuilt)
        }
    }
}

// Phase 8 success criterion: commonMain line coverage >= 85%, enforced in CI
// (`./gradlew :core:koverVerify`). Only reflects whichever test tasks actually ran on the host
// (e.g. mingwX64Test-only in this dev environment) - CI runs all 3 targets' tests first so the
// merged report is complete.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("kotlin.Deprecated")
            }
        }
        verify {
            rule("commonMain line coverage") {
                bound {
                    minValue = 85
                    coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                }
            }
        }
    }
}

// Phase 9: one Maven publication (root metadata + `-android` AAR + native klibs - the klibs are
// unused by consumers but harmless, see Phase 9 Key Insights) to GitHub Packages. Credentials come
// from env so nothing's in source; `publish` is only ever invoked from the tag-triggered
// `release.yml` workflow (Phase 9), never locally with real creds. `./gradlew publishToMavenLocal`
// works with no credentials for the local-consumption rehearsal in Phase 9's Implementation Steps.
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ldnghia/skylicense-core-sdk")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("LicenseCore SDK")
            description.set(
                "Kotlin Multiplatform client SDK (Android, iOS, Windows) for skyapp-api license " +
                    "verification, token caching, offline grace, and revocation.",
            )
            url.set("https://github.com/ldnghia/skylicense-core-sdk")
            licenses {
                license {
                    // Private/internal distribution only (Phase 9 Unresolved Questions,
                    // confirmed 2026-10-12) - not a public open-source license grant, this POM
                    // field is required by Maven tooling regardless of distribution scope.
                    name.set("Proprietary")
                    url.set("https://github.com/ldnghia/skylicense-core-sdk/blob/main/README.md")
                }
            }
            scm {
                url.set("https://github.com/ldnghia/skylicense-core-sdk")
                connection.set("scm:git:https://github.com/ldnghia/skylicense-core-sdk.git")
            }
        }
    }
}
