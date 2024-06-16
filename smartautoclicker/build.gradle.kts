
plugins {
    alias(libs.plugins.buzbuz.androidApplication)
    alias(libs.plugins.buzbuz.buildParameters)
    alias(libs.plugins.buzbuz.hilt)
}

android {
    namespace = "com.gpt40.smartautoclicker"
    buildFeatures.viewBinding = true

    defaultConfig {
        applicationId = "com.gpt40.smartautoclicker"
        versionCode = 44
        versionName = "3.0.0-beta03"
    }

    flavorDimensions += listOf("version")
    productFlavors {
        create("fDroid") {
            dimension = "version"
        }
        create("playStore") {
            dimension = "version"
        }
    }
//
//    if (buildParameters.isBuildForVariant("fDroidDebug")) {
//        buildTypes {
//            debug {
//                applicationIdSuffix = ".debug"
//            }
//        }
//    }

    signingConfigs {
        create("release") {
            storeFile = file("./smartautoclicker.jks")
            storePassword = buildParameters["signingStorePassword"].asString()
            keyAlias = buildParameters["signingKeyAlias"].asString()
            keyPassword = buildParameters["signingKeyPassword"].asString()
        }
    }
}

// Apply signature convention after declaring the signingConfigs
apply { plugin(libs.plugins.buzbuz.androidSigning.get().pluginId) }

// Only apply gms/firebase plugins if we are building for the play store
//if (buildParameters.isBuildForVariant("playStoreRelease")) {
//    apply { plugin(libs.plugins.buzbuz.crashlytics.get().pluginId) }
//}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.recyclerView)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.common.java8)

    implementation(libs.airbnb.lottie)
    implementation(libs.google.material)

    implementation(libs.nertc)
    implementation(libs.basesdk)
    implementation(project(":core:common:base"))
    implementation(project(":core:common:bitmaps"))
    implementation(project(":core:common:display"))
    implementation(project(":core:common:quality"))
    implementation(project(":core:common:overlays"))
    implementation(project(":core:common:ui"))
    implementation(project(":core:dumb"))
    implementation(project(":core:smart:detection"))
    implementation(project(":core:smart:domain"))
    implementation(project(":core:smart:processing"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:permissions"))
    implementation(project(":feature:revenue"))
    implementation(project(":feature:smart-config"))
    implementation(project(":feature:smart-debugging"))
    implementation(project(":feature:dumb-config"))
}
