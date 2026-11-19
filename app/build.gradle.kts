plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.example.booking_app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.booking_app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = providers.gradleProperty("adminTestRunner")
            .orElse("androidx.test.runner.AndroidJUnitRunner").get()
    }

    buildTypes {
        debug {
            if (providers.gradleProperty("adminLocalAppId").orNull == "true") {
                applicationIdSuffix = ".adminlocal"
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    if (providers.gradleProperty("adminLocalAppId").orNull == "true") {
        sourceSets.getByName("androidTest").kotlin.srcDir("src/adminLocalAndroidTest/java")
    }
}

// Local instrumentation supplies a named demo Firebase app through test DI.
// The isolated package intentionally has no hosted google-services client registration.
if (providers.gradleProperty("adminLocalAppId").orNull == "true") {
    afterEvaluate {
        tasks.named<com.google.gms.googleservices.GoogleServicesTask>("processDebugGoogleServices") {
            googleServicesJsonFiles.set(listOf(rootProject.file("scripts/admin/google-services.admin-local.json")))
        }
    }
}

// Khóa phía app đọc từ local.properties (không commit); máy chưa có khóa dùng giá trị giả
// trong local.defaults.properties. Mọi giá trị sinh ra trong BuildConfig đều là String.
secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.functions)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.android.image.cropper)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.viewpager2)
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.android.embedded)
    implementation("com.google.zxing:core:3.5.3")
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.junit)
    // Admin interaction tests replace backend DI with Emulator/local clients.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
