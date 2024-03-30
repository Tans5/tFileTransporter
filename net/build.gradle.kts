plugins {
    id("com.google.devtools.ksp")
    alias(libs.plugins.jetbrainsKotlinJvm)
}

//java {
//    sourceCompatibility = JavaVersion.VERSION_1_8
//    targetCompatibility = JavaVersion.VERSION_1_8
//}

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
}