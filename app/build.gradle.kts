plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Read GEMINI_API_KEY from (priority) Gradle property -> local.properties -> environment
val localPropsFile = rootProject.file("local.properties")
val geminiKey: String = (
    providers.gradleProperty("GEMINI_API_KEY").orNull
        ?: (if (localPropsFile.exists()) {
            localPropsFile.readLines()
                .firstOrNull { it.trim().startsWith("GEMINI_API_KEY=") }
                ?.substringAfter("=")
                ?.trim()
        } else null)
        ?: System.getenv("GEMINI_API_KEY")
        ?: ""
).trim()

if (geminiKey.isBlank()) {
    logger.warn("GEMINI_API_KEY not provided; keyword generation will be skipped.")
}

val mapsKey: String = (
    providers.gradleProperty("MAPS_API_KEY").orNull
        ?: (if (localPropsFile.exists()) {
            localPropsFile.readLines()
                .firstOrNull { it.trim().startsWith("MAPS_API_KEY=") }
                ?.substringAfter("=")
                ?.trim()
        } else null)
        ?: System.getenv("MAPS_API_KEY")
        ?: ""
).trim()

if (mapsKey.isBlank()) {
    logger.warn("MAPS_API_KEY not provided; Places Autocomplete will not work.")
}

android {
    namespace = "com.fillit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fillit.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    kotlin {
        jvmToolchain(17)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("com.google.accompanist:accompanist-flowlayout:0.34.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")


    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore:25.1.0")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.places:places:3.5.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
