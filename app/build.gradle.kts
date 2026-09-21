plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "com.mnemocards"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mnemocards"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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
        compose = true
        // BuildConfig.DEBUG — признак debug-сборки для локальных http-адресов
        // (см. SyncSettingsViewModel.validateAddress).
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // android.util.Log в main-коде (SyncSettingsRepository) становится
            // no-op в JVM-тестах вместо "not mocked" исключения.
            isReturnDefaultValues = true
        }
    }
    sourceSets {
        // JSON схем Room — как assets androidTest: их читает MigrationTestHelper (S28).
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        // DTO сети генерируются из specs/openapi.yaml (S22) тем же генератором,
        // что на сервере; ручные сетевые модели в проекте не заводятся.
        getByName("main").kotlin.srcDir("$buildDir/generated/openapi/src/main/kotlin")
    }
}

kotlin {
    // Тесты (Robolectric/ASM) запускаются на тулчейне JDK 17: класс-файлы
    // JDK 25 (major 69) не читаются ASM-инструментацией Robolectric.
    jvmToolchain(17)
}

ksp {
    // Экспорт схем Room для MigrationTestHelper (T5).
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Генерация сетевых DTO из контракта (specs/openapi.yaml — источник истины).
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/specs/openapi.yaml")
    outputDir.set("$buildDir/generated/openapi")
    packageName.set("com.mnemocards.data.network.dto")
    library.set("jvm-okhttp4")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
        ),
    )
}

tasks.named("preBuild") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.withType<Test>().configureEach {
    dependsOn(tasks.named("openApiGenerate"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
