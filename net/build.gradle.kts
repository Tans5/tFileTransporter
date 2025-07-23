plugins {
    id("com.google.devtools.ksp")
    alias(libs.plugins.jetbrainsKotlinJvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(libs.coroutines.core)

    // Moshi
    api(libs.moshi)
    api(libs.moshi.kotlin)
    api(libs.moshi.adapters)
    ksp(libs.moshi.kotlin.codegen)

    api(libs.netty)
    api(libs.okio)
    api(libs.androidx.annotaion)
    api(libs.tlrucache)
}