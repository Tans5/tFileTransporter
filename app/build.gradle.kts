plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("com.google.devtools.ksp")
    id("kotlin-kapt")
}

android {

    namespace = "com.tans.tfiletransporter"
    compileSdk = properties["ANDROID_COMPILE_SDK"].toString().toInt()

    defaultConfig {
        applicationId = "com.tans.tfiletransporter"
        minSdk = properties["ANDROID_MIN_SDK"].toString().toInt()
        targetSdk = properties["ANDROID_TARGET_SDK"].toString().toInt()
        versionCode = properties["VERSION_CODE"].toString().toInt()
        versionName = properties["VERSION_NAME"].toString()

        setProperty("archivesBaseName", "tfiletransfer-${properties["VERSION_NAME"].toString()}")
    }

    packaging {
        resources {
            excludes.addAll(listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties"))
        }
    }


    signingConfigs {

        val debugConfig = this.getByName("debug")
        with(debugConfig) {
            storeFile = File(projectDir, "debugkey${File.separator}debug.jks")
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
    }

    buildTypes {
        debug {
            multiDexEnabled = true
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("debug")
        }
        release {
            multiDexEnabled = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        viewBinding {
            enable = true
        }
        dataBinding {
            enable = true
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity)

    // rxjava3
    implementation(libs.rxjava3)
    implementation(libs.rxkotlin3)
    implementation(libs.rxandroid3)

    // rxbinding
    implementation(libs.rxbinding.core)
    implementation(libs.rxbinding.appcompat)
    implementation(libs.rxbinding.swiperefreshlayout)

    // coroutine
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.core.jvm)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.rx3)

    // kodein
    implementation(libs.kodein.core)
    implementation(libs.kodein.androidx)

    // moshi
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.kotlin.codegen)

    // lifecycle
    implementation(libs.androidx.lifecycle.rumtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    kapt(libs.androidx.lifecycle.compiler)

    // camerax
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)
    implementation(libs.camerax.extensions)

    // barcode scan
    implementation(libs.barcodescan)

    // qrcode gen
    implementation(libs.qrcodegen)

    // tans5
    implementation(libs.tadapter)
    implementation(libs.rxutils)
    implementation(libs.tuiutils)

    // glide
    implementation(libs.glide)

    //keyboard hide
    implementation(libs.keyboard)

    implementation(project(":net"))

}