plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.vm2st.patternhunter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vm2st.patternhunter"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1100m"
            it.maxParallelForks = 1
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
    lint {
        abortOnError = true
        lintConfig = file("lint.xml")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
