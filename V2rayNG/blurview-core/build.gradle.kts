/*
 * Vendored copy of com.qmdeve.blurview:core (QmBlurView), originally
 * distributed via JitPack. Vendored locally so WindowBlurUtils can force
 * PixelCopy-based capture (see BaseBlurView#setForcePixelCopy) instead of
 * the default Canvas-redraw path, which silently mis-renders clipToOutline
 * (MaterialCardView corners), hardware-layered Views, and hardware bitmaps.
 *
 * Original source: https://github.com/QmDeve/QmBlurView (MIT License)
 */

plugins {
    id("com.android.library")
}

android {
    namespace = "com.qmdeve.blurview"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 24

        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core:1.13.1")
}
