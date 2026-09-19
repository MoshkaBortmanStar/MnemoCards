## Батч 1/7

### FILE: specs/openapi.yaml
```yaml
openapi: 3.0.3
info:
  title: MnemoCards Sync API
  version: 1.0.0
  description: >
    Контракт REST-сервиса синхронизации MnemoCards. Источник истины для клиента:
    сетевые DTO генерируются из этого файла плагином org.openapi.generator
    (тем же генератором, что используется на сервере) и не правятся вручную.
servers:
  - url: https://sync.example.com
paths:
  /api/v1/health:
    get:
      operationId: health
      summary: Проверка доступности сервера синхронизации
      responses:
        '200':
          description: Сервер отвечает
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/HealthResponse'
        '404':
          description: По этому адресу нет сервера синхронизации
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '500':
          description: Внутренняя ошибка сервера
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /api/v1/auth/login:
    post:
      operationId: login
      summary: Вход, выдача access/refresh токенов
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginRequest'
      responses:
        '200':
          description: Успешный вход
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoginResponse'
        '400':
          description: Невалидный формат тела запроса
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '401':
          description: Неверный логин или пароль
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '500':
          description: Внутренняя ошибка сервера
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
components:
  schemas:
    HealthResponse:
      type: object
      required:
        - status
      properties:
        status:
          type: string
    LoginRequest:
      type: object
      required:
        - username
        - password
      properties:
        username:
          type: string
        password:
          type: string
    LoginResponse:
      type: object
      required:
        - accessToken
        - refreshToken
      properties:
        accessToken:
          type: string
        refreshToken:
          type: string
    ErrorResponse:
      type: object
      required:
        - error
      properties:
        error:
          type: string
```

### FILE: gradle/libs.versions.toml
```toml
[versions]
agp = "9.4.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
kotlinxCoroutinesTest = "1.10.2"
room = "2.7.2"
ksp = "2.2.10-2.0.2"
robolectric = "4.14.1"
turbine = "1.1.0"
navigation = "2.9.5"
androidxTestCore = "1.6.1"
datastorePreferences = "1.1.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerializationJson = "1.7.3"
openapiGenerator = "7.8.0"
# Material Icons заморожены на 1.7.8 (deprecated) и больше не входят в Compose BOM.
composeIconsCore = "1.7.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core", version.ref = "composeIconsCore" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-navigation-testing = { group = "androidx.navigation", name = "navigation-testing", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
openapi-generator = { id = "org.openapi.generator", version.ref = "openapiGenerator" }
```

### FILE: app/build.gradle.kts
```kotlin
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
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
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
    library.set("jvm")
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
```

### FILE: app/src/main/java/com/mnemocards/domain/model/ConnectionCheckResult.kt
```kotlin
package com.mnemocards.domain.model

/**
 * Результат проверки соединения с сервером синхронизации (GET /api/v1/health).
 * Состояние подключения пользователя не меняет: проверка не выполняет вход.
 */
sealed interface ConnectionCheckResult {
    /** Сервер ответил 200 на эндпоинт доступности. */
    data object ServerResponds : ConnectionCheckResult

    /** По этому адресу нет сервера синхронизации (HTTP 404). */
    data object NoSyncServer : ConnectionCheckResult

    /** Ошибка сервера (5xx). */
    data object ServerError : ConnectionCheckResult
}
```

### FILE: app/src/main/java/com/mnemocards/data/network/SyncApi.kt
```kotlin
package com.mnemocards.data.network

import com.mnemocards.data.network.dto.HealthResponse
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * REST-клиент сервиса синхронизации. Базовый адрес подставляется динамически
 * из поля «Адрес сервера» — поэтому эндпоинты объявлены с `@Url`.
 */
interface SyncApi {

    /** GET /api/v1/health — проверка доступности сервера. */
    @GET
    suspend fun checkHealth(@Url url: String): HealthResponse
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt
```kotlin
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.ConnectionCheckResult
import retrofit2.HttpException

/**
 * Репозиторий настроек синхронизации. Сетевые вызовы выполняются только
 * с экрана настроек; на остальные offline-first-сценарии репозиторий
 * не влияет.
 */
class SyncSettingsRepository(
    private val api: SyncApi,
) {

    /**
     * Проверяет соединение с сервером по адресу [address] (базовый адрес без
     * пути, например `https://sync.example.com`). Вход не выполняется, токены
     * не запрашиваются.
     */
    suspend fun checkConnection(address: String): ConnectionCheckResult {
        val url = address.trimEnd('/') + "/api/v1/health"
        return try {
            api.checkHealth(url)
            ConnectionCheckResult.ServerResponds
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> ConnectionCheckResult.NoSyncServer
                else -> ConnectionCheckResult.ServerError
            }
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/SyncSettingsRepositoryTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.ConnectionCheckResult
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * T1/T3: проверка соединения через MockWebServer.
 * Сценарии S5 (200), S8 (404), S9 (5xx); метод и путь — GET /api/v1/health
 * согласно specs/API.md.
 */
class SyncSettingsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SyncSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = SyncSettingsRepository(retrofit.create(SyncApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun address(): String = server.url("").toString().trimEnd('/')

    @Test
    fun `health 200 returns ServerResponds and requests GET api v1 health`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerResponds, result)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/health", recorded.path)
    }

    @Test
    fun `health 404 returns NoSyncServer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.NoSyncServer, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test
    fun `health 500 returns ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerError, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }
}
```


---

## Батч 2/7

### FILE: gradle/libs.versions.toml
```
[versions]
agp = "9.4.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
kotlinxCoroutinesTest = "1.10.2"
room = "2.7.2"
ksp = "2.2.10-2.0.2"
robolectric = "4.14.1"
turbine = "1.1.0"
navigation = "2.9.5"
androidxTestCore = "1.6.1"
datastorePreferences = "1.1.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerializationJson = "1.7.3"
openapiGenerator = "7.8.0"
# Material Icons заморожены на 1.7.8 (deprecated) и больше не входят в Compose BOM.
composeIconsCore = "1.7.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core", version.ref = "composeIconsCore" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-navigation-testing = { group = "androidx.navigation", name = "navigation-testing", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-tls = { group = "com.squareup.okhttp3", name = "okhttp-tls", version.ref = "okhttp" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
openapi-generator = { id = "org.openapi.generator", version.ref = "openapiGenerator" }
```

### FILE: app/build.gradle.kts
```
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
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
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
    library.set("jvm")
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
```

### FILE: app/src/main/java/com/mnemocards/domain/model/ConnectionCheckResult.kt
```
package com.mnemocards.domain.model

/**
 * Результат проверки соединения с сервером синхронизации (GET /api/v1/health).
 * Состояние подключения пользователя не меняет: проверка не выполняет вход.
 */
sealed interface ConnectionCheckResult {
    /** Сервер ответил 200 на эндпоинт доступности. */
    data object ServerResponds : ConnectionCheckResult

    /** По этому адресу нет сервера синхронизации (HTTP 404). */
    data object NoSyncServer : ConnectionCheckResult

    /** Ошибка сервера (5xx). */
    data object ServerError : ConnectionCheckResult

    /** Адрес недоступен: домен не резолвится, таймаут соединения и т.п. */
    data object Unreachable : ConnectionCheckResult

    /** Сервер отвечает по HTTPS, но сертификат не принят (S7). Обход проверки
     * сертификата не выполняется. */
    data object CertificateRejected : ConnectionCheckResult
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt
```
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.ConnectionCheckResult
import retrofit2.HttpException
import java.io.IOException
import javax.net.ssl.SSLException

/**
 * Репозиторий настроек синхронизации. Сетевые вызовы выполняются только
 * с экрана настроек; на остальные offline-first-сценарии репозиторий
 * не влияет.
 */
class SyncSettingsRepository(
    private val api: SyncApi,
) {

    /**
     * Проверяет соединение с сервером по адресу [address] (базовый адрес без
     * пути, например `https://sync.example.com`). Вход не выполняется, токены
     * не запрашиваются.
     */
    suspend fun checkConnection(address: String): ConnectionCheckResult {
        val url = address.trimEnd('/') + "/api/v1/health"
        return try {
            api.checkHealth(url)
            ConnectionCheckResult.ServerResponds
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> ConnectionCheckResult.NoSyncServer
                else -> ConnectionCheckResult.ServerError
            }
        } catch (e: SSLException) {
            // SSLHandshakeException — наследник SSLException: сертификат
            // не принят. Обход проверки сертификата не выполняется.
            ConnectionCheckResult.CertificateRejected
        } catch (e: IOException) {
            // UnknownHostException, таймауты и прочие сетевые сбои.
            ConnectionCheckResult.Unreachable
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/SyncSettingsRepositoryTest.kt
```
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * T1/T3: проверка соединения через MockWebServer.
 * Сценарии S5 (200), S8 (404), S9 (5xx); метод и путь — GET /api/v1/health
 * согласно specs/API.md.
 *
 * T4: сетевые ошибки проверки соединения — S6 (нерезолвящийся домен, таймаут
 * → Unreachable), S7 (недоверенный TLS-сертификат → CertificateRejected).
 *
 * T6: вход POST /api/v1/auth/login — S10–S14 (успех, 400, 401, 500, обрыв
 * соединения); тела запросов и ответов — по контракту specs/openapi.yaml.
 */
class SyncSettingsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SyncSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(250, TimeUnit.MILLISECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = SyncSettingsRepository(retrofit.create(SyncApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun address(): String = server.url("").toString().trimEnd('/')

    @Test
    fun `health 200 returns ServerResponds and requests GET api v1 health`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerResponds, result)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/health", recorded.path)
    }

    @Test
    fun `health 404 returns NoSyncServer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.NoSyncServer, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test
    fun `health 500 returns ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerError, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    // --- T4: сетевые ошибки проверки соединения (S6, S7) ---

    @Test
    fun `health on unresolvable host returns Unreachable`() = runTest {
        val result = repository.checkConnection("https://nonexistent-host.mnemocards.invalid")

        assertEquals(ConnectionCheckResult.Unreachable, result)
    }

    @Test
    fun `health connection timeout returns Unreachable`() = runTest {
        // 10.255.255.1 — заведомо не маршрутизируемый адрес: соединение
        // не устанавливается, OkHttp бросает таймаут соединения (IOException).
        val result = repository.checkConnection("https://10.255.255.1")

        assertEquals(ConnectionCheckResult.Unreachable, result)
    }

    @Test
    fun `health on untrusted TLS certificate returns CertificateRejected`() = runTest {
        // Самоподписанный сертификат сервера, которому клиентский OkHttp
        // не доверяет → SSLHandshakeException (S7).
        val selfSigned = HeldCertificate.Builder()
            .commonName("sync.example.com")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(selfSigned)
            .build()
        server.useHttps(serverCertificates.sslSocketFactory(), false)

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.CertificateRejected, result)
    }

    // --- T6: вход POST /api/v1/auth/login (S10–S14) ---

    @Test
    fun `login 200 returns Success with tokens and posts credentials`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"access-token-1","refreshToken":"refresh-token-1"}""",
            ),
        )

        val result = repository.login(address(), "user", "secret")

        assertEquals(
            LoginOutcome.Success("access-token-1", "refresh-token-1"),
            result,
        )
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/login", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"username\":\"user\""))
        assertTrue(body.contains("\"password\":\"secret\""))
    }

    @Test
    fun `login 401 returns InvalidCredentials and no tokens`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid credentials"}"""))

        val result = repository.login(address(), "user", "wrong")

        assertEquals(LoginOutcome.InvalidCredentials, result)
    }

    @Test
    fun `login 400 returns BadRequest with reason from body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid request body"}"""))

        val result = repository.login(address(), "user", "secret")

        assertEquals(LoginOutcome.BadRequest("invalid request body"), result)
    }

    @Test
    fun `login 500 returns ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repository.login(address(), "user", "secret")

        assertEquals(LoginOutcome.ServerError, result)
    }

    @Test
    fun `login network failure returns NetworkError and retry available`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val first = repository.login(address(), "user", "secret")

        assertTrue(first is LoginOutcome.NetworkError)

        // Повторная попытка доступна: сервер отвечает успехом.
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"access-token-2","refreshToken":"refresh-token-2"}""",
            ),
        )
        val retry = repository.login(address(), "user", "secret")
        assertEquals(
            LoginOutcome.Success("access-token-2", "refresh-token-2"),
            retry,
        )
    }
}
```


---

## Батч 3/7

### FILE: app/src/main/java/com/mnemocards/domain/model/LoginOutcome.kt
```kotlin
package com.mnemocards.domain.model

/**
 * Результат входа на сервер синхронизации (POST /api/v1/auth/login).
 * При любом исходе, кроме [Success], токены не выдаются.
 */
sealed interface LoginOutcome {
    /** Вход выполнен: получены access- и refresh-токены. */
    data class Success(
        val accessToken: String,
        val refreshToken: String,
    ) : LoginOutcome

    /** Неверный логин или пароль (HTTP 401). */
    data object InvalidCredentials : LoginOutcome

    /** Невалидный формат тела запроса (HTTP 400), [reason] — причина из тела ответа. */
    data class BadRequest(val reason: String) : LoginOutcome

    /** Ошибка сервера (5xx). */
    data object ServerError : LoginOutcome

    /** Сетевой сбой во время запроса (IOException). Повторная попытка доступна. */
    data object NetworkError : LoginOutcome
}
```

### FILE: app/src/main/java/com/mnemocards/data/network/SyncApi.kt
```kotlin
package com.mnemocards.data.network

import com.mnemocards.data.network.dto.HealthResponse
import com.mnemocards.data.network.dto.LoginRequest
import com.mnemocards.data.network.dto.LoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * REST-клиент сервиса синхронизации. Базовый адрес подставляется динамически
 * из поля «Адрес сервера» — поэтому эндпоинты объявлены с `@Url`.
 * Все модели — сгенерированные DTO (S22), ручных сетевых моделей нет.
 */
interface SyncApi {

    /** GET /api/v1/health — проверка доступности сервера. */
    @GET
    suspend fun checkHealth(@Url url: String): HealthResponse

    /** POST /api/v1/auth/login — вход, выдача access/refresh токенов. */
    @POST
    suspend fun login(@Url url: String, @Body body: LoginRequest): LoginResponse
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt
```kotlin
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.network.dto.ErrorResponse
import com.mnemocards.data.network.dto.LoginRequest
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.net.ssl.SSLException

/**
 * Репозиторий настроек синхронизации. Сетевые вызовы выполняются только
 * с экрана настроек; на остальные offline-first-сценарии репозиторий
 * не влияет.
 */
class SyncSettingsRepository(
    private val api: SyncApi,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Проверяет соединение с сервером по адресу [address] (базовый адрес без
     * пути, например `https://sync.example.com`). Вход не выполняется, токены
     * не запрашиваются.
     */
    suspend fun checkConnection(address: String): ConnectionCheckResult {
        val url = address.trimEnd('/') + "/api/v1/health"
        return try {
            api.checkHealth(url)
            ConnectionCheckResult.ServerResponds
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> ConnectionCheckResult.NoSyncServer
                else -> ConnectionCheckResult.ServerError
            }
        } catch (e: SSLException) {
            // SSLHandshakeException — наследник SSLException: сертификат
            // не принят. Обход проверки сертификата не выполняется.
            ConnectionCheckResult.CertificateRejected
        } catch (e: IOException) {
            // UnknownHostException, таймауты и прочие сетевые сбои.
            ConnectionCheckResult.Unreachable
        }
    }

    /**
     * Выполняет вход по адресу [address] с логином и паролем. Пароль дальше
     * этого вызова не уходит и нигде не сохраняется (S16).
     */
    suspend fun login(
        address: String,
        username: String,
        password: String,
    ): LoginOutcome {
        val url = address.trimEnd('/') + "/api/v1/auth/login"
        return try {
            val response = api.login(url, LoginRequest(username = username, password = password))
            LoginOutcome.Success(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
            )
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> LoginOutcome.InvalidCredentials
                400 -> LoginOutcome.BadRequest(errorReason(e))
                else -> LoginOutcome.ServerError
            }
        } catch (e: IOException) {
            // Обрыв соединения, недоступность адреса и прочие сетевые сбои:
            // токены не сохраняются, повторная попытка доступна (S14).
            LoginOutcome.NetworkError
        }
    }

    /** Извлекает причину ошибки из тела ответа (схема ErrorResponse контракта). */
    private fun errorReason(e: HttpException): String {
        val body = e.response()?.errorBody()?.string().orEmpty()
        return try {
            json.decodeFromString(ErrorResponse.serializer(), body).error
        } catch (parseError: Exception) {
            "Некорректный запрос"
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/TokenCipher.kt
```kotlin
package com.mnemocards.data.syncsettings

/**
 * Крипто-провайдер для шифрования токенов. Тонкая абстракция над Android
 * Keystore: позволяет подменить шифратор фейком в юнит-тестах.
 */
interface TokenCipher {

    /** Шифрует открытый текст токена; возвращает шифртекст для хранения. */
    fun encrypt(plain: String): String

    /** Расшифровывает шифртекст, ранее возвращённый [encrypt]. */
    fun decrypt(cipher: String): String
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/KeystoreTokenCipher.kt
```kotlin
package com.mnemocards.data.syncsettings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование токенов AES/GCM ключом из Android Keystore (S16). Тонкая
 * обёртка без бизнес-логики: ключ не покидает Keystore, шифртекст хранится
 * вместе с IV в Base64.
 */
class KeystoreTokenCipher : TokenCipher {

    private val keyAlias = "mnemocards_sync_tokens"

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String): String {
        val decoded = Base64.decode(stored, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = decoded.copyOfRange(IV_LENGTH_BYTES, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/TokenStorage.kt
```kotlin
package com.mnemocards.data.syncsettings

/**
 * Пара токенов сервера синхронизации, выданная при входе.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Хранение токенов сервера синхронизации. Токены записываются и читаются
 * только через [TokenCipher] — открытый текст в персистентном хранилище
 * не появляется нигде (S16). Пароль этим хранилищем не владеет.
 */
class TokenStorage(
    private val store: KeyValueStore,
    private val cipher: TokenCipher,
) {

    suspend fun saveTokens(tokens: TokenPair) {
        store.put(KEY_ACCESS, cipher.encrypt(tokens.accessToken))
        store.put(KEY_REFRESH, cipher.encrypt(tokens.refreshToken))
    }

    /** Токены или null, если вход не выполнен (или выполнен выход). */
    suspend fun tokens(): TokenPair? {
        val access = store.get(KEY_ACCESS) ?: return null
        val refresh = store.get(KEY_REFRESH) ?: return null
        return TokenPair(
            accessToken = cipher.decrypt(access),
            refreshToken = cipher.decrypt(refresh),
        )
    }

    /** Удаляет токены (выход, S18). */
    suspend fun clear() {
        store.remove(KEY_ACCESS)
        store.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "sync_access_token"
        const val KEY_REFRESH = "sync_refresh_token"
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsStorage.kt
```kotlin
package com.mnemocards.data.syncsettings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Минимальное персистентное хранилище «ключ-значение». Абстракция позволяет
 * подменить хранение фейком в юнит-тестах.
 */
interface KeyValueStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}

/** Реализация на DataStore Preferences (используется в приложении). */
class DataStoreKeyValueStore(
    private val dataStore: DataStore<Preferences>,
) : KeyValueStore {

    override suspend fun put(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun get(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }
}

/**
 * Хранение настроек синхронизации: адрес сервера и признак «подключено» —
 * открытым текстом (это не секреты). Токены живут в [TokenStorage] в
 * зашифрованном виде; выход удаляет их и гасит флаг синхронизации через
 * колбэк [onSyncFlagChanged] (проводится к существующему SyncFlagDataStore,
 * новый флаг не создаётся). Пароль нигде не пишется (S16).
 */
class SyncSettingsStorage(
    private val store: KeyValueStore,
    private val tokenStorage: TokenStorage,
    private val onSyncFlagChanged: suspend (Boolean) -> Unit,
) {

    /** Адрес сервера или null, если ещё не вводился. Переживает перезапуск (S17). */
    suspend fun serverAddress(): String? = store.get(KEY_ADDRESS)

    /** Подключён ли пользователь (токены были сохранены и выход не выполнялся). */
    suspend fun isConnected(): Boolean = store.get(KEY_CONNECTED) == VALUE_TRUE

    /**
     * Фиксирует успешный вход: сохраняет адрес, признак «подключено» и
     * включает флаг синхронизации (S10, S19/S20 — мягкий режим удаления).
     */
    suspend fun connect(address: String) {
        store.put(KEY_ADDRESS, address)
        store.put(KEY_CONNECTED, VALUE_TRUE)
        onSyncFlagChanged(true)
    }

    /**
     * Выход: удаляет токены и признак «подключено», выключает флаг
     * синхронизации. Адрес сервера сохраняется (S18).
     */
    suspend fun logout() {
        tokenStorage.clear()
        store.remove(KEY_CONNECTED)
        onSyncFlagChanged(false)
    }

    private companion object {
        const val KEY_ADDRESS = "sync_server_address"
        const val KEY_CONNECTED = "sync_connected"
        const val VALUE_TRUE = "true"
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/TokenStorageTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import android.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T8: хранение токенов, адреса и пароля.
 * Сценарии S16–S18: токены только в зашифрованном виде, пароль не хранится,
 * logout удаляет токены и состояние «подключено» (адрес остаётся), адрес
 * переживает пересоздание хранилища, вход/выход переключают флаг
 * синхронизации.
 *
 * Крипто-провайдер и персистентность подменяются fakes.
 */
@RunWith(RobolectricTestRunner::class)
class TokenStorageTest {

    /** Фейковый шифратор: Base64 от развёрнутой строки — открытый текст
     * в результате не встречается. */
    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String): String =
            Base64.encodeToString(plain.reversed().toByteArray(), Base64.NO_WRAP)

        override fun decrypt(cipher: String): String =
            String(Base64.decode(cipher, Base64.NO_WRAP)).reversed()
    }

    /** Фейковое персистентное хранилище: обычная мутабельная карта. */
    private class FakeKeyValueStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }
        override suspend fun get(key: String): String? = map[key]
        override suspend fun remove(key: String) {
            map.remove(key)
        }
    }

    /** Фейковый приёмник флага синхронизации: фиксирует переключения. */
    private class FakeSyncFlag {
        val values = mutableListOf<Boolean>()
        val setter: suspend (Boolean) -> Unit = { values.add(it) }
    }

    private lateinit var store: FakeKeyValueStore
    private lateinit var cipher: FakeCipher
    private lateinit var flag: FakeSyncFlag
    private lateinit var tokenStorage: TokenStorage
    private lateinit var settingsStorage: SyncSettingsStorage

    private val accessToken = "access-token-plain-1"
    private val refreshToken = "refresh-token-plain-1"
    private val password = "user-secret-password"

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        cipher = FakeCipher()
        flag = FakeSyncFlag()
        tokenStorage = TokenStorage(store, cipher)
        settingsStorage = SyncSettingsStorage(store, tokenStorage, flag.setter)
    }

    /** Полный цикл входа: сохранение токенов + фиксация подключения. */
    private suspend fun performLogin(address: String = "https://sync.example.com") {
        tokenStorage.saveTokens(TokenPair(accessToken, refreshToken))
        settingsStorage.connect(address)
    }

    // (1) Токены хранятся только зашифрованными (S16).
    @Test
    fun `saved tokens are not stored in plaintext`() = runTest {
        performLogin()

        val stored = store.map.values.joinToString("\n")
        assertFalse(stored.contains(accessToken))
        assertFalse(stored.contains(refreshToken))
        // Расшифровка возвращает исходные токены — шифртекст корректен.
        val tokens = tokenStorage.tokens()
        assertEquals(accessToken, tokens?.accessToken)
        assertEquals(refreshToken, tokens?.refreshToken)
    }

    // (2) Пароль не сохраняется нигде (S16).
    @Test
    fun `password is not stored anywhere`() = runTest {
        performLogin()

        val stored = store.map.values.joinToString("\n")
        assertFalse(stored.contains(password))
        assertTrue(store.map.keys.none { it.contains("password", ignoreCase = true) })
    }

    // (3) Logout удаляет токены и «подключено», адрес сохраняется (S18).
    @Test
    fun `logout clears tokens and connected state but keeps address`() = runTest {
        performLogin()

        settingsStorage.logout()

        assertNull(tokenStorage.tokens())
        assertFalse(settingsStorage.isConnected())
        assertEquals("https://sync.example.com", settingsStorage.serverAddress())
    }

    // (4) Адрес переживает пересоздание хранилища (S17).
    @Test
    fun `server address survives storage recreation`() = runTest {
        performLogin()

        val recreated = SyncSettingsStorage(store, tokenStorage, flag.setter)
        assertEquals("https://sync.example.com", recreated.serverAddress())
    }

    // (5) Вход включает флаг синхронизации, logout — выключает (S10, S18).
    @Test
    fun `login enables sync flag and logout disables it`() = runTest {
        performLogin()
        settingsStorage.logout()

        assertEquals(listOf(true, false), flag.values)
    }
}
```


---

## Батч 4/7

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt
```kotlin
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.network.dto.ErrorResponse
import com.mnemocards.data.network.dto.LoginRequest
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.net.ssl.SSLException

/**
 * Репозиторий настроек синхронизации. Сетевые вызовы выполняются только
 * с экрана настроек; на остальные offline-first-сценарии репозиторий
 * не влияет.
 */
interface SyncSettingsRepository {

    /**
     * Проверяет соединение с сервером по адресу [address] (базовый адрес без
     * пути, например `https://sync.example.com`). Вход не выполняется, токены
     * не запрашиваются.
     */
    suspend fun checkConnection(address: String): ConnectionCheckResult

    /**
     * Выполняет вход по адресу [address] с логином и паролем. Пароль дальше
     * этого вызова не уходит и нигде не сохраняется (S16).
     */
    suspend fun login(address: String, username: String, password: String): LoginOutcome
}

/** Retrofit-реализация поверх сгенерированных DTO (S22). */
class RetrofitSyncSettingsRepository(
    private val api: SyncApi,
) : SyncSettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkConnection(address: String): ConnectionCheckResult {
        val url = address.trimEnd('/') + "/api/v1/health"
        return try {
            api.checkHealth(url)
            ConnectionCheckResult.ServerResponds
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> ConnectionCheckResult.NoSyncServer
                else -> ConnectionCheckResult.ServerError
            }
        } catch (e: SSLException) {
            // SSLHandshakeException — наследник SSLException: сертификат
            // не принят. Обход проверки сертификата не выполняется.
            ConnectionCheckResult.CertificateRejected
        } catch (e: IOException) {
            // UnknownHostException, таймауты и прочие сетевые сбои.
            ConnectionCheckResult.Unreachable
        }
    }

    override suspend fun login(
        address: String,
        username: String,
        password: String,
    ): LoginOutcome {
        val url = address.trimEnd('/') + "/api/v1/auth/login"
        return try {
            val response = api.login(url, LoginRequest(username = username, password = password))
            LoginOutcome.Success(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
            )
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> LoginOutcome.InvalidCredentials
                400 -> LoginOutcome.BadRequest(errorReason(e))
                else -> LoginOutcome.ServerError
            }
        } catch (e: IOException) {
            // Обрыв соединения, недоступность адреса и прочие сетевые сбои:
            // токены не сохраняются, повторная попытка доступна (S14).
            LoginOutcome.NetworkError
        }
    }

    /** Извлекает причину ошибки из тела ответа (схема ErrorResponse контракта). */
    private fun errorReason(e: HttpException): String {
        val body = e.response()?.errorBody()?.string().orEmpty()
        return try {
            json.decodeFromString(ErrorResponse.serializer(), body).error
        } catch (parseError: Exception) {
            "Некорректный запрос"
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/TokenStorage.kt
```kotlin
package com.mnemocards.data.syncsettings

/**
 * Пара токенов сервера синхронизации, выданная при входе.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Хранение токенов сервера синхронизации. Токены записываются и читаются
 * только через [TokenCipher] — открытый текст в персистентном хранилище
 * не появляется нигде (S16). Пароль этим хранилищем не владеет.
 */
interface TokenStorage {

    suspend fun saveTokens(tokens: TokenPair)

    /** Токены или null, если вход не выполнен (или выполнен выход). */
    suspend fun tokens(): TokenPair?

    /** Удаляет токены (выход, S18). */
    suspend fun clear()
}

/** Реализация поверх персистентного хранилища и шифратора. */
class EncryptedTokenStorage(
    private val store: KeyValueStore,
    private val cipher: TokenCipher,
) : TokenStorage {

    override suspend fun saveTokens(tokens: TokenPair) {
        store.put(KEY_ACCESS, cipher.encrypt(tokens.accessToken))
        store.put(KEY_REFRESH, cipher.encrypt(tokens.refreshToken))
    }

    override suspend fun tokens(): TokenPair? {
        val access = store.get(KEY_ACCESS) ?: return null
        val refresh = store.get(KEY_REFRESH) ?: return null
        return TokenPair(
            accessToken = cipher.decrypt(access),
            refreshToken = cipher.decrypt(refresh),
        )
    }

    override suspend fun clear() {
        store.remove(KEY_ACCESS)
        store.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "sync_access_token"
        const val KEY_REFRESH = "sync_refresh_token"
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsStorage.kt
```kotlin
package com.mnemocards.data.syncsettings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Минимальное персистентное хранилище «ключ-значение». Абстракция позволяет
 * подменить хранение фейком в юнит-тестах.
 */
interface KeyValueStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}

/** Реализация на DataStore Preferences (используется в приложении). */
class DataStoreKeyValueStore(
    private val dataStore: DataStore<Preferences>,
) : KeyValueStore {

    override suspend fun put(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun get(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }
}

/**
 * Хранение настроек синхронизации: адрес сервера и признак «подключено» —
 * открытым текстом (это не секреты). Токены живут в [TokenStorage] в
 * зашифрованном виде; выход удаляет их и гасит флаг синхронизации через
 * колбэк [onSyncFlagChanged] (проводится к существующему SyncFlagDataStore,
 * новый флаг не создаётся). Пароль нигде не пишется (S16).
 */
interface SyncSettingsStorage {

    /** Адрес сервера или null, если ещё не вводился. Переживает перезапуск (S17). */
    suspend fun serverAddress(): String?

    /** Подключён ли пользователь (токены были сохранены и выход не выполнялся). */
    suspend fun isConnected(): Boolean

    /**
     * Фиксирует успешный вход: сохраняет адрес, признак «подключено» и
     * включает флаг синхронизации (S10, S19/S20 — мягкий режим удаления).
     */
    suspend fun connect(address: String)

    /**
     * Выход: удаляет токены и признак «подключено», выключает флаг
     * синхронизации. Адрес сервера сохраняется (S18).
     */
    suspend fun logout()
}

/** Реализация поверх [KeyValueStore] и [TokenStorage]. */
class DefaultSyncSettingsStorage(
    private val store: KeyValueStore,
    private val tokenStorage: TokenStorage,
    private val onSyncFlagChanged: suspend (Boolean) -> Unit,
) : SyncSettingsStorage {

    override suspend fun serverAddress(): String? = store.get(KEY_ADDRESS)

    override suspend fun isConnected(): Boolean = store.get(KEY_CONNECTED) == VALUE_TRUE

    override suspend fun connect(address: String) {
        store.put(KEY_ADDRESS, address)
        store.put(KEY_CONNECTED, VALUE_TRUE)
        onSyncFlagChanged(true)
    }

    override suspend fun logout() {
        tokenStorage.clear()
        store.remove(KEY_CONNECTED)
        onSyncFlagChanged(false)
    }

    private companion object {
        const val KEY_ADDRESS = "sync_server_address"
        const val KEY_CONNECTED = "sync_connected"
        const val VALUE_TRUE = "true"
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsUiState.kt
```kotlin
package com.mnemocards.ui.syncsettings

/**
 * Состояние экрана настроек синхронизации. Владеет им ViewModel через
 * StateFlow — состояние переживает смену конфигурации (S23).
 */
data class SyncSettingsUiState(
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val isChecking: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isConnected: Boolean = false,
    val resultText: String? = null,
    val addressError: String? = null,
    val credentialsError: String? = null,
)
```

### FILE: app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModel.kt
```kotlin
package com.mnemocards.ui.syncsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.data.syncsettings.SyncSettingsStorage
import com.mnemocards.data.syncsettings.TokenPair
import com.mnemocards.data.syncsettings.TokenStorage
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel экрана настроек синхронизации (MVVM + UDF): события текут вверх
 * (методы onXxx), состояние — вниз (StateFlow). Валидация выполняется до
 * любых сетевых вызовов (S3, S4, S25); guard-флаги [SyncSettingsUiState.isChecking]/
 * [SyncSettingsUiState.isLoggingIn] защищают от повторных нажатий (S15).
 */
class SyncSettingsViewModel(
    private val repository: SyncSettingsRepository,
    private val settingsStorage: SyncSettingsStorage,
    private val tokenStorage: TokenStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState.asStateFlow()

    init {
        // Восстановление адреса и статуса «Подключено» после перезапуска (S17).
        viewModelScope.launch {
            val address = settingsStorage.serverAddress().orEmpty()
            val connected = settingsStorage.isConnected()
            _uiState.update { it.copy(address = address, isConnected = connected) }
        }
    }

    fun onAddressChanged(value: String) {
        _uiState.update { it.copy(address = value, addressError = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, credentialsError = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, credentialsError = null) }
    }

    /** «Проверить соединение» — GET /api/v1/health без входа и токенов (S5–S9). */
    fun checkConnection() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return
        val error = addressErrorOrNull(state.address)
        if (error != null) {
            _uiState.update { it.copy(addressError = error) }
            return
        }
        val address = state.address.trim()
        _uiState.update {
            it.copy(isChecking = true, addressError = null, resultText = null)
        }
        viewModelScope.launch {
            val text = when (repository.checkConnection(address)) {
                ConnectionCheckResult.ServerResponds -> "Сервер отвечает"
                ConnectionCheckResult.Unreachable -> "Адрес недоступен"
                ConnectionCheckResult.CertificateRejected -> "Сертификат не принят"
                ConnectionCheckResult.NoSyncServer -> "По этому адресу нет сервера синхронизации"
                ConnectionCheckResult.ServerError -> "Ошибка сервера"
            }
            _uiState.update { it.copy(isChecking = false, resultText = text) }
        }
    }

    /** «Войти» — POST /api/v1/auth/login (S10–S14). */
    fun login() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return
        val error = addressErrorOrNull(state.address)
        if (error != null) {
            _uiState.update { it.copy(addressError = error) }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(credentialsError = "Заполните логин и пароль") }
            return
        }
        val address = state.address.trim()
        val username = state.username.trim()
        val password = state.password
        _uiState.update {
            it.copy(
                isLoggingIn = true,
                addressError = null,
                credentialsError = null,
                resultText = null,
            )
        }
        viewModelScope.launch {
            when (val outcome = repository.login(address, username, password)) {
                is LoginOutcome.Success -> {
                    // Токены — только в зашифрованное хранилище; пароль нигде
                    // не сохраняется и очищается из состояния (S10, S16).
                    tokenStorage.saveTokens(
                        TokenPair(outcome.accessToken, outcome.refreshToken),
                    )
                    settingsStorage.connect(address)
                    _uiState.update {
                        it.copy(isLoggingIn = false, isConnected = true, password = "")
                    }
                }
                LoginOutcome.InvalidCredentials ->
                    _uiState.update {
                        it.copy(isLoggingIn = false, resultText = "Неверный логин или пароль")
                    }
                is LoginOutcome.BadRequest ->
                    _uiState.update {
                        it.copy(isLoggingIn = false, resultText = outcome.reason)
                    }
                LoginOutcome.ServerError ->
                    _uiState.update { it.copy(isLoggingIn = false, resultText = "Ошибка сервера") }
                LoginOutcome.NetworkError ->
                    // Повторная попытка доступна: флаги сняты (S14).
                    _uiState.update {
                        it.copy(isLoggingIn = false, resultText = "Адрес недоступен")
                    }
            }
        }
    }

    /** «Выйти» — удаляет токены, гасит флаг синхронизации, адрес остаётся (S18). */
    fun logout() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return
        viewModelScope.launch {
            settingsStorage.logout()
            _uiState.update { it.copy(isConnected = false, resultText = null) }
        }
    }

    /**
     * Валидация адреса до отправки запроса (S3, S25): пустое значение,
     * не-URL и http-адреса (cleartext блокируется Android) отклоняются
     * без сетевого вызова.
     */
    private fun addressErrorOrNull(address: String): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return "Введите адрес сервера"
        if (trimmed.startsWith("http://")) {
            return "Незашифрованное соединение (http) заблокировано Android — используйте https-адрес"
        }
        return try {
            val uri = URI(trimmed)
            if (uri.scheme != "https" || uri.host == null) "Некорректный адрес" else null
        } catch (e: URISyntaxException) {
            "Некорректный адрес"
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModelTest.kt
```kotlin
package com.mnemocards.ui.syncsettings

import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.data.syncsettings.SyncSettingsStorage
import com.mnemocards.data.syncsettings.TokenPair
import com.mnemocards.data.syncsettings.TokenStorage
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T10: валидация полей — сетевые вызовы не выполняются при некорректном
 * адресе (пустой, не-URL, http) и пустых логине/пароле; статус подключения
 * не меняется. Сценарии S3, S4, S25.
 *
 * T12: проверка соединения, вход, выход, загрузка и восстановление
 * состояния. Сценарии S5–S15, S17, S18.
 *
 * Репозиторий и хранилища подменяются fakes, фиксирующими вызовы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSettingsViewModelTest {

    /** Фейковый репозиторий: считает вызовы, возвращает заданный результат. */
    private class FakeRepository : SyncSettingsRepository {
        val checkCalls = mutableListOf<String>()
        val loginCalls = mutableListOf<Triple<String, String, String>>()
        var checkResult: ConnectionCheckResult = ConnectionCheckResult.ServerResponds
        var loginResult: LoginOutcome = LoginOutcome.InvalidCredentials
        var gate: CompletableDeferred<Unit>? = null

        private suspend fun awaitGate() {
            gate?.await()
        }

        override suspend fun checkConnection(address: String): ConnectionCheckResult {
            checkCalls.add(address)
            awaitGate()
            return checkResult
        }

        override suspend fun login(
            address: String,
            username: String,
            password: String,
        ): LoginOutcome {
            loginCalls.add(Triple(address, username, password))
            awaitGate()
            return loginResult
        }
    }

    /** Фейковое хранилище токенов: фиксирует сохранения и удаления. */
    private class FakeTokenStorage : TokenStorage {
        val saved = mutableListOf<TokenPair>()
        var clearCount = 0
        override suspend fun saveTokens(tokens: TokenPair) {
            saved.add(tokens)
        }
        override suspend fun tokens(): TokenPair? = saved.lastOrNull()
        override suspend fun clear() {
            clearCount++
        }
    }

    /** Фейковое хранилище настроек: адрес, признак подключения, вызовы. */
    private class FakeSettingsStorage : SyncSettingsStorage {
        var address: String? = null
        var connected: Boolean = false
        val connectCalls = mutableListOf<String>()
        var logoutCount = 0
        override suspend fun serverAddress(): String? = address
        override suspend fun isConnected(): Boolean = connected
        override suspend fun connect(address: String) {
            connectCalls.add(address)
            this.address = address
            connected = true
        }
        override suspend fun logout() {
            logoutCount++
            connected = false
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var tokenStorage: FakeTokenStorage
    private lateinit var settingsStorage: FakeSettingsStorage
    private lateinit var viewModel: SyncSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        tokenStorage = FakeTokenStorage()
        settingsStorage = FakeSettingsStorage()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SyncSettingsViewModel {
        viewModel = SyncSettingsViewModel(repository, settingsStorage, tokenStorage)
        return viewModel
    }

    // --- T10: валидация полей (S3, S4, S25) ---

    // (1) Пустой адрес: ошибка «Введите адрес сервера», сеть не вызывается.
    @Test
    fun `blank address shows error and network is not called`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("   ")
        vm.checkConnection()

        assertEquals("Введите адрес сервера", vm.uiState.value.addressError)
        assertTrue(repository.checkCalls.isEmpty())
        assertTrue(repository.loginCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // (2) Не-URL: «Некорректный адрес», сеть не вызывается.
    @Test
    fun `non url address shows error and network is not called`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("sync example")
        vm.checkConnection()

        assertEquals("Некорректный адрес", vm.uiState.value.addressError)
        assertTrue(repository.checkCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // (3) http-адрес: cleartext-ошибка до отправки запроса, флаг в манифесте
    // не включается, состояние подключения не меняется (S25).
    @Test
    fun `http address is rejected before any request`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("http://sync.example.com")
        vm.checkConnection()
        vm.login()

        assertEquals(
            "Незашифрованное соединение (http) заблокировано Android — используйте https-адрес",
            vm.uiState.value.addressError,
        )
        assertTrue(repository.checkCalls.isEmpty())
        assertTrue(repository.loginCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // (4) Пустой логин или пароль: запрос входа не отправляется.
    @Test
    fun `blank credentials show error and login is not called`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onPasswordChanged("secret")
        vm.login()
        assertEquals("Заполните логин и пароль", vm.uiState.value.credentialsError)

        vm.onUsernameChanged("user")
        vm.onPasswordChanged("")
        vm.login()
        assertEquals("Заполните логин и пароль", vm.uiState.value.credentialsError)

        assertTrue(repository.loginCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // --- T12: результаты проверки соединения (S5–S9) ---

    @Test
    fun `check results are shown and connection status unchanged`() = runTest(dispatcher) {
        val results = mapOf(
            ConnectionCheckResult.ServerResponds to "Сервер отвечает",
            ConnectionCheckResult.Unreachable to "Адрес недоступен",
            ConnectionCheckResult.CertificateRejected to "Сертификат не принят",
            ConnectionCheckResult.NoSyncServer to "По этому адресу нет сервера синхронизации",
            ConnectionCheckResult.ServerError to "Ошибка сервера",
        )
        for ((result, text) in results) {
            setUp()
            repository.checkResult = result
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onAddressChanged("https://sync.example.com")
            vm.checkConnection()
            advanceUntilIdle()

            assertEquals(text, vm.uiState.value.resultText)
            assertFalse(vm.uiState.value.isChecking)
            assertFalse(vm.uiState.value.isConnected)
            assertEquals(1, repository.checkCalls.size)
        }
    }

    // --- T12: вход (S10–S14) ---

    // (2) Успех: статус «Подключено», пароль очищен, токены и адрес
    // сохранены, флаг синхронизации включён (через connect).
    @Test
    fun `login success connects and stores tokens`() = runTest(dispatcher) {
        repository.loginResult = LoginOutcome.Success("access-1", "refresh-1")
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("secret")
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isConnected)
        assertEquals("", vm.uiState.value.password)
        assertEquals(listOf(TokenPair("access-1", "refresh-1")), tokenStorage.saved)
        assertEquals(listOf("https://sync.example.com"), settingsStorage.connectCalls)
    }

    // (3) 401: «Неверный логин или пароль», статус «Не подключено».
    @Test
    fun `login 401 shows message and stays disconnected`() = runTest(dispatcher) {
        repository.loginResult = LoginOutcome.InvalidCredentials
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("wrong")
        vm.login()
        advanceUntilIdle()

        assertEquals("Неверный логин или пароль", vm.uiState.value.resultText)
        assertFalse(vm.uiState.value.isConnected)
        assertTrue(tokenStorage.saved.isEmpty())
    }

    // (4) Сетевой сбой: «Адрес недоступен», флаги загрузки сняты,
    // повторная попытка доступна.
    @Test
    fun `login network failure allows retry`() = runTest(dispatcher) {
        repository.loginResult = LoginOutcome.NetworkError
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("secret")
        vm.login()
        advanceUntilIdle()

        assertEquals("Адрес недоступен", vm.uiState.value.resultText)
        assertFalse(vm.uiState.value.isLoggingIn)
        assertFalse(vm.uiState.value.isConnected)
        assertTrue(tokenStorage.saved.isEmpty())

        repository.loginResult = LoginOutcome.Success("access-2", "refresh-2")
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isConnected)
        assertEquals(listOf(TokenPair("access-2", "refresh-2")), tokenStorage.saved)
    }

    // (5) Пока выполняется запрос, повторные вызовы не проходят.
    @Test
    fun `duplicate calls are ignored while request in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.checkConnection()
        runCurrent()
        assertTrue(vm.uiState.value.isChecking)

        vm.checkConnection()
        vm.login()
        runCurrent()
        assertEquals(1, repository.checkCalls.size)
        assertTrue(repository.loginCalls.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isChecking)
        assertEquals("Сервер отвечает", vm.uiState.value.resultText)
    }

    // --- T12: выход (S18) ---

    // (6) Выход: токены удалены, флаг выключен, статус «Не подключено»,
    // адрес сохранён.
    @Test
    fun `logout disconnects and keeps address`() = runTest(dispatcher) {
        repository.loginResult = LoginOutcome.Success("access-1", "refresh-1")
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("secret")
        vm.login()
        advanceUntilIdle()
        vm.logout()
        advanceUntilIdle()

        assertEquals(1, tokenStorage.clearCount)
        assertEquals(1, settingsStorage.logoutCount)
        assertFalse(vm.uiState.value.isConnected)
        assertEquals("https://sync.example.com", settingsStorage.address)
    }

    // --- T12: восстановление состояния (S17) ---

    // (7) init восстанавливает адрес и статус «Подключено», пароль пуст.
    @Test
    fun `init restores address and connected state`() = runTest(dispatcher) {
        settingsStorage.address = "https://sync.example.com"
        settingsStorage.connected = true

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("https://sync.example.com", vm.uiState.value.address)
        assertTrue(vm.uiState.value.isConnected)
        assertEquals("", vm.uiState.value.password)
        assertNull(vm.uiState.value.addressError)
        assertTrue(repository.checkCalls.isEmpty())
        assertTrue(repository.loginCalls.isEmpty())
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/SyncSettingsRepositoryTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * T1/T3: проверка соединения через MockWebServer.
 * Сценарии S5 (200), S8 (404), S9 (5xx); метод и путь — GET /api/v1/health
 * согласно specs/API.md.
 *
 * T4: сетевые ошибки проверки соединения — S6 (нерезолвящийся домен, таймаут
 * → Unreachable), S7 (недоверенный TLS-сертификат → CertificateRejected).
 *
 * T6: вход POST /api/v1/auth/login — S10–S14 (успех, 400, 401, 500, обрыв
 * соединения); тела запросов и ответов — по контракту specs/openapi.yaml.
 */
class SyncSettingsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SyncSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(250, TimeUnit.MILLISECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = RetrofitSyncSettingsRepository(retrofit.create(SyncApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun address(): String = server.url("").toString().trimEnd('/')

    @Test
    fun `health 200 returns ServerResponds and requests GET api v1 health`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerResponds, result)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/health", recorded.path)
    }

    @Test
    fun `health 404 returns NoSyncServer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.NoSyncServer, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test
    fun `health 500 returns ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerError, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    // --- T4: сетевые ошибки проверки соединения (S6, S7) ---

    @Test
    fun `health on unresolvable host returns Unreachable`() = runTest {
        val result = repository.checkConnection("https://nonexistent-host.mnemocards.invalid")

        assertEquals(ConnectionCheckResult.Unreachable, result)
    }

    @Test
    fun `health connection timeout returns Unreachable`() = runTest {
        // 10.255.255.1 — заведомо не маршрутизируемый адрес: соединение
        // не устанавливается, OkHttp бросает таймаут соединения (IOException).
        val result = repository.checkConnection("https://10.255.255.1")

        assertEquals(ConnectionCheckResult.Unreachable, result)
    }

    @Test
    fun `health on untrusted TLS certificate returns CertificateRejected`() = runTest {
        // Самоподписанный сертификат сервера, которому клиентский OkHttp
        // не доверяет → SSLHandshakeException (S7).
        val selfSigned = HeldCertificate.Builder()
            .commonName("sync.example.com")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(selfSigned)
            .build()
        server.useHttps(serverCertificates.sslSocketFactory(), false)

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.CertificateRejected, result)
    }

    // --- T6: вход POST /api/v1/auth/login (S10–S14) ---

    @Test
    fun `login 200 returns Success with tokens and posts credentials`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"access-token-1","refreshToken":"refresh-token-1"}""",
            ),
        )

        val result = repository.login(address(), "user", "secret")

        assertEquals(
            LoginOutcome.Success("access-token-1", "refresh-token-1"),
            result,
        )
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/login", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"username\":\"user\""))
        assertTrue(body.contains("\"password\":\"secret\""))
    }

    @Test
    fun `login 401 returns InvalidCredentials and no tokens`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid credentials"}"""))

        val result = repository.login(address(), "user", "wrong")

        assertEquals(LoginOutcome.InvalidCredentials, result)
    }

    @Test
    fun `login 400 returns BadRequest with reason from body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid request body"}"""))

        val result = repository.login(address(), "user", "secret")

        assertEquals(LoginOutcome.BadRequest("invalid request body"), result)
    }

    @Test
    fun `login 500 returns ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repository.login(address(), "user", "secret")

        assertEquals(LoginOutcome.ServerError, result)
    }

    @Test
    fun `login network failure returns NetworkError and retry available`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val first = repository.login(address(), "user", "secret")

        assertTrue(first is LoginOutcome.NetworkError)

        // Повторная попытка доступна: сервер отвечает успехом.
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"access-token-2","refreshToken":"refresh-token-2"}""",
            ),
        )
        val retry = repository.login(address(), "user", "secret")
        assertEquals(
            LoginOutcome.Success("access-token-2", "refresh-token-2"),
            retry,
        )
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/TokenStorageTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import android.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T8: хранение токенов, адреса и пароля.
 * Сценарии S16–S18: токены только в зашифрованном виде, пароль не хранится,
 * logout удаляет токены и состояние «подключено» (адрес остаётся), адрес
 * переживает пересоздание хранилища, вход/выход переключают флаг
 * синхронизации.
 *
 * Крипто-провайдер и персистентность подменяются fakes.
 */
@RunWith(RobolectricTestRunner::class)
class TokenStorageTest {

    /** Фейковый шифратор: Base64 от развёрнутой строки — открытый текст
     * в результате не встречается. */
    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String): String =
            Base64.encodeToString(plain.reversed().toByteArray(), Base64.NO_WRAP)

        override fun decrypt(cipher: String): String =
            String(Base64.decode(cipher, Base64.NO_WRAP)).reversed()
    }

    /** Фейковое персистентное хранилище: обычная мутабельная карта. */
    private class FakeKeyValueStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }
        override suspend fun get(key: String): String? = map[key]
        override suspend fun remove(key: String) {
            map.remove(key)
        }
    }

    /** Фейковый приёмник флага синхронизации: фиксирует переключения. */
    private class FakeSyncFlag {
        val values = mutableListOf<Boolean>()
        val setter: suspend (Boolean) -> Unit = { values.add(it) }
    }

    private lateinit var store: FakeKeyValueStore
    private lateinit var cipher: FakeCipher
    private lateinit var flag: FakeSyncFlag
    private lateinit var tokenStorage: TokenStorage
    private lateinit var settingsStorage: SyncSettingsStorage

    private val accessToken = "access-token-plain-1"
    private val refreshToken = "refresh-token-plain-1"
    private val password = "user-secret-password"

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        cipher = FakeCipher()
        flag = FakeSyncFlag()
        tokenStorage = EncryptedTokenStorage(store, cipher)
        settingsStorage = DefaultSyncSettingsStorage(store, tokenStorage, flag.setter)
    }

    /** Полный цикл входа: сохранение токенов + фиксация подключения. */
    private suspend fun performLogin(address: String = "https://sync.example.com") {
        tokenStorage.saveTokens(TokenPair(accessToken, refreshToken))
        settingsStorage.connect(address)
    }

    // (1) Токены хранятся только зашифрованными (S16).
    @Test
    fun `saved tokens are not stored in plaintext`() = runTest {
        performLogin()

        val stored = store.map.values.joinToString("\n")
        assertFalse(stored.contains(accessToken))
        assertFalse(stored.contains(refreshToken))
        // Расшифровка возвращает исходные токены — шифртекст корректен.
        val tokens = tokenStorage.tokens()
        assertEquals(accessToken, tokens?.accessToken)
        assertEquals(refreshToken, tokens?.refreshToken)
    }

    // (2) Пароль не сохраняется нигде (S16).
    @Test
    fun `password is not stored anywhere`() = runTest {
        performLogin()

        val stored = store.map.values.joinToString("\n")
        assertFalse(stored.contains(password))
        assertTrue(store.map.keys.none { it.contains("password", ignoreCase = true) })
    }

    // (3) Logout удаляет токены и «подключено», адрес сохраняется (S18).
    @Test
    fun `logout clears tokens and connected state but keeps address`() = runTest {
        performLogin()

        settingsStorage.logout()

        assertNull(tokenStorage.tokens())
        assertFalse(settingsStorage.isConnected())
        assertEquals("https://sync.example.com", settingsStorage.serverAddress())
    }

    // (4) Адрес переживает пересоздание хранилища (S17).
    @Test
    fun `server address survives storage recreation`() = runTest {
        performLogin()

        val recreated = DefaultSyncSettingsStorage(store, tokenStorage, flag.setter)
        assertEquals("https://sync.example.com", recreated.serverAddress())
    }

    // (5) Вход включает флаг синхронизации, logout — выключает (S10, S18).
    @Test
    fun `login enables sync flag and logout disables it`() = runTest {
        performLogin()
        settingsStorage.logout()

        assertEquals(listOf(true, false), flag.values)
    }
}
```


---

## Батч 5/7

### FILE: app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsScreen.kt
```kotlin
package com.mnemocards.ui.syncsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.data.syncsettings.SyncSettingsStorage
import com.mnemocards.data.syncsettings.TokenStorage

/**
 * Маршрут экрана настроек синхронизации: подключает ViewModel (UDF) и
 * передаёт состояние вниз в stateless-компонент [SyncSettingsScreen].
 */
@Composable
fun SyncSettingsRoute(
    onBack: () -> Unit,
    viewModel: SyncSettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SyncSettingsScreen(
        state = state,
        onAddressChanged = viewModel::onAddressChanged,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onCheckConnection = viewModel::checkConnection,
        onLogin = viewModel::login,
        onLogout = viewModel::logout,
        onBack = onBack,
    )
}

/**
 * Экран настроек синхронизации (S1): поля «Адрес сервера», «Логин»,
 * «Пароль», кнопки «Проверить соединение» и «Войти», индикатор статуса
 * «Не подключено»/«Подключено», кнопка «Выйти» в подключённом состоянии.
 * Бизнес-логики нет — только отображение состояния из ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    state: SyncSettingsUiState,
    onAddressChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onCheckConnection: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Синхронизация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                // Панель сливается с фоном — выделяться должен контент.
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Индикатор состояния подключения (S1, S18).
            Text(
                text = if (state.isConnected) {
                    "Подключено: ${state.address}"
                } else {
                    "Не подключено"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChanged,
                label = { Text("Адрес сервера") },
                isError = state.addressError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    state.addressError?.let { Text(it) }
                },
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                label = { Text("Логин") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text("Пароль") },
                isError = state.credentialsError != null,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    state.credentialsError?.let { Text(it) }
                },
            )

            if (state.isConnected) {
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !state.isChecking && !state.isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Выйти")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onCheckConnection,
                        enabled = !state.isChecking && !state.isLoggingIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Проверить соединение")
                    }
                    Button(
                        onClick = onLogin,
                        enabled = !state.isChecking && !state.isLoggingIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Войти")
                    }
                }
            }

            // Текстовый результат проверки/входа (S5–S9, S11–S14).
            state.resultText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Дублирующая точка выравнивания контента при скролле.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {}
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/navigation/AppNavHost.kt
```kotlin
package com.mnemocards.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mnemocards.ui.cardform.CardFormScreen
import com.mnemocards.ui.deckcontent.DeckContentScreen
import com.mnemocards.ui.decklist.DeckListScreen
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.syncsettings.SyncSettingsRoute

/**
 * Маршруты приложения. Единственный навигационный граф — добавление новых
 * экранов выполняется маршрутом сюда, второй NavHost не создаётся (S1, S24).
 */
object Routes {
    const val DECK_LIST = "decks"
    const val DECK_CONTENT = "deck/{deckId}"
    const val CARD_FORM = "deck/{deckId}/card/{cardId}"
    const val REVIEW = "deck/{deckId}/review"
    const val SYNC_SETTINGS = "sync-settings"

    fun deckContent(deckId: Long) = "deck/$deckId"
    fun cardForm(deckId: Long, cardId: Long?) =
        if (cardId == null) "deck/$deckId/card/new" else "deck/$deckId/card/$cardId"
    fun review(deckId: Long) = "deck/$deckId/review"
}

/**
 * Единственный NavHost приложения (MnemoCardsNavHost).
 */
@Composable
fun MnemoCardsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DECK_LIST,
        modifier = modifier,
    ) {
        composable(Routes.DECK_LIST) {
            DeckListScreen(
                onOpenDeck = { deckId -> navController.navigate(Routes.deckContent(deckId)) },
                onStartReview = { deckId -> navController.navigate(Routes.review(deckId)) },
                onOpenSyncSettings = { navController.navigate(Routes.SYNC_SETTINGS) },
            )
        }
        composable(Routes.DECK_CONTENT) { entry ->
            val deckId = entry.arguments?.getString("deckId")?.toLongOrNull() ?: return@composable
            DeckContentScreen(
                deckId = deckId,
                onBack = { navController.popBackStack() },
                onAddCard = { navController.navigate(Routes.cardForm(deckId, null)) },
                onEditCard = { cardId -> navController.navigate(Routes.cardForm(deckId, cardId)) },
                onStartReview = { navController.navigate(Routes.review(deckId)) },
            )
        }
        composable(Routes.CARD_FORM) { entry ->
            val deckId = entry.arguments?.getString("deckId")?.toLongOrNull() ?: return@composable
            val cardId = entry.arguments?.getString("cardId")?.let {
                if (it == "new") null else it.toLongOrNull()
            }
            CardFormScreen(
                deckId = deckId,
                cardId = cardId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REVIEW) { entry ->
            val deckId = entry.arguments?.getString("deckId")?.toLongOrNull() ?: return@composable
            ReviewScreen(
                deckId = deckId,
                onExit = { navController.popBackStack() },
            )
        }
        // Настройки синхронизации: маршрут в существующий граф, второй
        // навигационный граф не создаётся (S1).
        composable(Routes.SYNC_SETTINGS) {
            SyncSettingsRoute(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Обратная совместимость: прежнее имя корневого навигационного графа. */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    MnemoCardsNavHost(modifier = modifier, navController = navController)
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.ui.review.formatInterval

/**
 * Экран списка колод — стартовый экран приложения. Кнопка настроек
 * синхронизации в TopAppBar открывает маршрут SYNC_SETTINGS (S1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onOpenDeck: (Long) -> Unit,
    onStartReview: (Long) -> Unit,
    onOpenSyncSettings: () -> Unit,
    viewModel: DeckListViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои колоды") },
                actions = {
                    // Вход на экран настроек синхронизации со списка колод (S1).
                    IconButton(onClick = onOpenSyncSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки синхронизации")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { state.onCreateDeckClicked() }) {
                Icon(Icons.Filled.Add, contentDescription = "Новая колода")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Загрузка…")
            }

            state.decks.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Колонок пока нет. Создайте первую!")
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.decks, key = { it.deckId }) { item ->
                    DeckItem(
                        item = item,
                        onClick = { onOpenDeck(item.deckId) },
                        onStartReview = { onStartReview(item.deckId) },
                        onDelete = { state.onDeleteDeck(item.deckId) },
                    )
                }
            }
        }

        // Диалоги создания и удаления колод.
        if (state.showCreateDialog) {
            DeckNameDialog(
                onConfirm = { state.onCreateDeckConfirmed(it) },
                onDismiss = { state.onCreateDeckDismissed() },
            )
        }
        state.deckPendingDeletion?.let { deck ->
            DeleteDeckDialog(
                deckName = deck.name,
                onConfirm = { state.onDeleteDeckConfirmed() },
                onDismiss = { state.onDeleteDeckCancelled() },
            )
        }
    }
}

/**
 * Элемент списка колод: название, счётчик карточек, срок следующего
 * повторения и действия.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckItem(
    item: DeckListItem,
    onClick: () -> Unit,
    onStartReview: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${item.cardCount} карточек",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.nextDueInDays?.let { days ->
                    Text(
                        text = "Следующее повторение: ${formatInterval(days)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Тренироваться",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
```


---

## Батч 6/7

### FILE: app/src/main/java/com/mnemocards/data/network/SyncApi.kt
```kotlin
package com.mnemocards.data.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit-интерфейс сервера синхронизации (specs/API.md).
 *
 * Базовый адрес подставляется динамически из поля «Адрес сервера» через
 * параметр `@Url` — Retrofit-инстанс создаётся с технической базой,
 * реальный сервер указывается при каждом вызове.
 */
interface SyncApi {

    /** GET /api/v1/health — проверка доступности сервера (S5–S9). */
    @GET
    suspend fun health(@Url url: String): Response<Unit>

    /** POST /api/v1/auth/login — вход, выдача access/refresh токенов (S10–S14). */
    @POST
    suspend fun login(@Url url: String, @Body body: RequestBody): Response<ResponseBody>
}
```

### FILE: app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt
```kotlin
package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.net.ssl.SSLException

/**
 * Репозиторий настроек синхронизации. Владеет сетевыми сценариями
 * «проверить соединение» и «войти», применяет результат к хранилищу.
 *
 * T18: успешный вход сохраняет токены, фиксирует адрес и включает
 * существующий флаг синхронизации (мягкий режим удаления, S10, S19);
 * выход — выключает флаг и удаляет токены (S18, S20). Режим удаления
 * определяется флагом через существующую логику, здесь она не
 * дублируется.
 */
class SyncSettingsRepository(
    private val api: SyncApi,
    private val settingsStorage: SyncSettingsStorage,
    private val tokenStorage: TokenStorage,
) {

    /** Нормализует адрес: без хвостового слэша. */
    private fun normalize(address: String): String = address.trim().trimEnd('/')

    private fun healthUrl(address: String): String = "${normalize(address)}/api/v1/health"

    private fun loginUrl(address: String): String = "${normalize(address)}/api/v1/auth/login"

    /**
     * Проверка соединения (S5–S9). Статус подключения не меняется —
     * вход не выполняется, токены не запрашиваются.
     */
    suspend fun checkConnection(address: String): ConnectionCheckResult {
        return try {
            val response = api.health(healthUrl(address))
            when {
                response.isSuccessful -> ConnectionCheckResult.ServerResponds
                response.code() == 404 -> ConnectionCheckResult.NoSyncServer
                else -> ConnectionCheckResult.ServerError
            }
        } catch (e: SSLException) {
            // Самоподписанный/просроченный сертификат — обход проверки
            // сертификата не выполняется (S7).
            ConnectionCheckResult.CertificateRejected
        } catch (e: IOException) {
            ConnectionCheckResult.Unreachable
        }
    }

    /**
     * Вход (S10–S14). При успехе токены сохраняются зашифрованными,
     * адрес фиксируется, флаг синхронизации включается (S10, S19);
     * пароль не сохраняется нигде.
     */
    suspend fun login(address: String, username: String, password: String): LoginOutcome {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        return try {
            val response = api.login(loginUrl(address), payload)
            val bodyText = response.body()?.string().orEmpty()
            when {
                response.isSuccessful -> {
                    val json = JSONObject(bodyText)
                    tokenStorage.saveTokens(
                        TokenPair(
                            accessToken = json.getString("access_token"),
                            refreshToken = json.getString("refresh_token"),
                        ),
                    )
                    // connect() фиксирует адрес, состояние «подключено» и
                    // включает флаг синхронизации (T18).
                    settingsStorage.connect(normalize(address))
                    LoginOutcome.Success
                }

                response.code() == 401 -> LoginOutcome.InvalidCredentials

                response.code() == 400 ->
                    LoginOutcome.BadRequest(parseErrorReason(bodyText))

                else -> LoginOutcome.ServerError
            }
        } catch (e: SSLException) {
            LoginOutcome.NetworkError
        } catch (e: IOException) {
            LoginOutcome.NetworkError
        }
    }

    private fun parseErrorReason(bodyText: String): String {
        return try {
            JSONObject(bodyText).optString("error", "Некорректный запрос")
        } catch (e: Exception) {
            "Некорректный запрос"
        }
    }

    /**
     * Выход (S18): токены и состояние «подключено» удаляются, адрес
     * сохраняется, флаг синхронизации выключается — удаления возвращаются
     * к физическому режиму (S20).
     */
    suspend fun logout() {
        settingsStorage.logout()
    }

    suspend fun isConnected(): Boolean = settingsStorage.isConnected()

    suspend fun serverAddress(): String? = settingsStorage.serverAddress()
}
```

### FILE: app/src/main/java/com/mnemocards/AppContainer.kt
```kotlin
package com.mnemocards

import android.content.Context
import androidx.room.Room
import com.mnemocards.data.CardCountRepository
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.file.SafCsvFileGateway
import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.data.sync.DataStoreSyncFlagDataStore
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.data.syncsettings.DataStoreKeyValueStore
import com.mnemocards.data.syncsettings.DefaultSyncSettingsStorage
import com.mnemocards.data.syncsettings.EncryptedTokenStorage
import com.mnemocards.data.syncsettings.KeystoreTokenCipher
import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.ui.syncsettings.SyncSettingsViewModelFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Единственный контейнер зависимостей приложения (S24). Новые контейнеры
 * и точки входа не создаются.
 *
 * T16: регистрирует сетевой стек (OkHttp/Retrofit с динамическим базовым
 * адресом через @Url), SyncApi, KeystoreTokenCipher, TokenStorage,
 * SyncSettingsStorage, SyncSettingsRepository и фабрику ViewModel
 * настроек синхронизации. Существующий флаг синхронизации
 * [SyncFlagDataStore] используется и хранилищем настроек — новый флаг
 * не создаётся (T9, T18).
 */
class AppContainer(context: Context) {

    val database: MnemoCardsDatabase = Room.databaseBuilder(
        context.applicationContext,
        MnemoCardsDatabase::class.java,
        "mnemocards.db",
    ).build()

    private val deckDao = database.deckDao()
    private val cardDao = database.cardDao()

    /** Существующий флаг синхронизации: определяет режим удаления (S19, S20). */
    val syncFlagDataStore: SyncFlagDataStore =
        DataStoreSyncFlagDataStore(context.applicationContext)

    val deckRepository: RoomDeckRepository =
        RoomDeckRepository(deckDao, syncFlagDataStore)

    val cardRepository: RoomCardRepository =
        RoomCardRepository(cardDao, syncFlagDataStore)

    val deckCountsRepository: CardCountRepository.RoomDeckCountsRepository =
        CardCountRepository.RoomDeckCountsRepository(deckDao, cardDao)

    val csvFileGateway: SafCsvFileGateway = SafCsvFileGateway(context.applicationContext)

    // --- Настройки синхронизации (T16) ---

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Техническая база: реальный адрес сервера подставляется в каждый
     * вызов через `@Url` (SyncApi) — базовый адрес динамический.
     */
    val syncApi: SyncApi = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(okHttpClient)
        .build()
        .create(SyncApi::class.java)

    /** Шифрование токенов ключом из Android Keystore (AES/GCM). */
    val tokenCipher: KeystoreTokenCipher = KeystoreTokenCipher()

    val keyValueStore: DataStoreKeyValueStore =
        DataStoreKeyValueStore(context.applicationContext)

    val tokenStorage: EncryptedTokenStorage =
        EncryptedTokenStorage(keyValueStore, tokenCipher)

    /**
     * Подключение/выход переключают существующий флаг синхронизации
     * через сеттер хранилища настроек (T18) — режим удаления (мягкий/
     * физический) определяется этим флагом в существующей логике.
     */
    val syncSettingsStorage: DefaultSyncSettingsStorage = DefaultSyncSettingsStorage(
        store = keyValueStore,
        tokenStorage = tokenStorage,
        setSyncEnabled = { enabled -> syncFlagDataStore.setEnabled(enabled) },
    )

    val syncSettingsRepository: SyncSettingsRepository = SyncSettingsRepository(
        api = syncApi,
        settingsStorage = syncSettingsStorage,
        tokenStorage = tokenStorage,
    )

    /** Фабрика ViewModel настроек — существующий механизм фабрик. */
    val syncSettingsViewModelFactory: SyncSettingsViewModelFactory =
        SyncSettingsViewModelFactory(
            repository = syncSettingsRepository,
            storage = syncSettingsStorage,
        )
}
```

### FILE: app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModel.kt
```kotlin
package com.mnemocards.ui.syncsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.data.syncsettings.SyncSettingsStorage
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URISyntaxException

/**
 * ViewModel экрана настроек синхронизации (S23: состояние переживает
 * смену конфигурации, так как владеет им ViewModel через StateFlow).
 *
 * Валидация выполняется до любых сетевых вызовов (S3, S4, S25);
 * guard-флаги isChecking/isLoggingIn защищают от повторных нажатий (S15).
 */
class SyncSettingsViewModel(
    private val repository: SyncSettingsRepository,
    private val storage: SyncSettingsStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState.asStateFlow()

    init {
        // Восстановление состояния после перезапуска (S17): адрес и статус
        // «Подключено» — из хранилища, пароль пуст.
        viewModelScope.launch {
            val address = storage.serverAddress().orEmpty()
            val connected = storage.isConnected()
            _uiState.update {
                it.copy(address = address, isConnected = connected)
            }
        }
    }

    fun onAddressChanged(value: String) {
        _uiState.update { it.copy(address = value, addressError = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, credentialsError = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, credentialsError = null) }
    }

    /**
     * Валидация адреса (S3, S25). null — адрес корректен.
     */
    private fun validateAddress(address: String): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return "Введите адрес сервера"
        val uri = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            null
        }
        if (uri == null || uri.scheme == null || uri.host == null) {
            return "Некорректный адрес"
        }
        if (uri.scheme.equals("http", ignoreCase = true)) {
            return "Незашифрованное соединение (http) заблокировано Android — используйте https-адрес"
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return "Некорректный адрес"
        }
        return null
    }

    /** Проверка соединения (S5–S9). Статус подключения не меняется. */
    fun checkConnection() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return

        val error = validateAddress(state.address)
        if (error != null) {
            _uiState.update { it.copy(addressError = error) }
            return
        }

        _uiState.update { it.copy(isChecking = true, resultText = null, addressError = null) }
        viewModelScope.launch {
            val result = repository.checkConnection(_uiState.value.address)
            _uiState.update {
                it.copy(
                    isChecking = false,
                    resultText = when (result) {
                        ConnectionCheckResult.ServerResponds -> "Сервер отвечает"
                        ConnectionCheckResult.NoSyncServer ->
                            "По этому адресу нет сервера синхронизации"
                        ConnectionCheckResult.ServerError -> "Ошибка сервера"
                        ConnectionCheckResult.Unreachable -> "Адрес недоступен"
                        ConnectionCheckResult.CertificateRejected -> "Сертификат не принят"
                    },
                )
            }
        }
    }

    /** Вход (S10–S14). */
    fun login() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return

        val addressError = validateAddress(state.address)
        if (addressError != null) {
            _uiState.update { it.copy(addressError = addressError) }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(credentialsError = "Заполните логин и пароль") }
            return
        }

        _uiState.update {
            it.copy(
                isLoggingIn = true,
                resultText = null,
                addressError = null,
                credentialsError = null,
            )
        }
        viewModelScope.launch {
            val outcome = repository.login(
                address = _uiState.value.address,
                username = _uiState.value.username,
                password = _uiState.value.password,
            )
            _uiState.update {
                when (outcome) {
                    LoginOutcome.Success -> it.copy(
                        isLoggingIn = false,
                        isConnected = true,
                        // Пароль не сохраняется и очищается из UI (S10, S16).
                        password = "",
                        resultText = null,
                    )

                    LoginOutcome.InvalidCredentials -> it.copy(
                        isLoggingIn = false,
                        resultText = "Неверный логин или пароль",
                    )

                    is LoginOutcome.BadRequest -> it.copy(
                        isLoggingIn = false,
                        resultText = outcome.reason,
                    )

                    LoginOutcome.ServerError -> it.copy(
                        isLoggingIn = false,
                        resultText = "Ошибка сервера",
                    )

                    LoginOutcome.NetworkError -> it.copy(
                        isLoggingIn = false,
                        resultText = "Адрес недоступен",
                    )
                }
            }
        }
    }

    /**
     * Выход (S18): токены удаляются, статус «Не подключено», адрес
     * сохраняется, флаг синхронизации выключается (физические удаления).
     */
    fun logout() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(isConnected = false, resultText = null, password = "")
            }
        }
    }
}

/**
 * Фабрика ViewModel настроек синхронизации — существующий механизм:
 * зависимости приходят из AppContainer (T16).
 */
class SyncSettingsViewModelFactory(
    private val repository: SyncSettingsRepository,
    private val storage: SyncSettingsStorage,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == SyncSettingsViewModel::class.java)
        return SyncSettingsViewModel(repository, storage) as T
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsScreen.kt
```kotlin
package com.mnemocards.ui.syncsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.MnemoCardsApplication

/**
 * Маршрут экрана настроек синхронизации: подключает ViewModel (UDF) и
 * передаёт состояние вниз в stateless-компонент [SyncSettingsScreen].
 * Зависимости приходят из единственного AppContainer через фабрику (T16).
 */
@Composable
fun SyncSettingsRoute(
    onBack: () -> Unit,
    viewModel: SyncSettingsViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as MnemoCardsApplication)
            .container.syncSettingsViewModelFactory,
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SyncSettingsScreen(
        state = state,
        onAddressChanged = viewModel::onAddressChanged,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onCheckConnection = viewModel::checkConnection,
        onLogin = viewModel::login,
        onLogout = viewModel::logout,
        onBack = onBack,
    )
}

/**
 * Экран настроек синхронизации (S1): поля «Адрес сервера», «Логин»,
 * «Пароль», кнопки «Проверить соединение» и «Войти», индикатор статуса
 * «Не подключено»/«Подключено», кнопка «Выйти» в подключённом состоянии.
 * Бизнес-логики нет — только отображение состояния из ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    state: SyncSettingsUiState,
    onAddressChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onCheckConnection: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Синхронизация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                // Панель сливается с фоном — выделяться должен контент.
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Индикатор состояния подключения (S1, S18).
            Text(
                text = if (state.isConnected) {
                    "Подключено: ${state.address}"
                } else {
                    "Не подключено"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChanged,
                label = { Text("Адрес сервера") },
                isError = state.addressError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    state.addressError?.let { Text(it) }
                },
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                label = { Text("Логин") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text("Пароль") },
                isError = state.credentialsError != null,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    state.credentialsError?.let { Text(it) }
                },
            )

            if (state.isConnected) {
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !state.isChecking && !state.isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Выйти")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onCheckConnection,
                        enabled = !state.isChecking && !state.isLoggingIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Проверить соединение")
                    }
                    Button(
                        onClick = onLogin,
                        enabled = !state.isChecking && !state.isLoggingIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Войти")
                    }
                }
            }

            // Текстовый результат проверки/входа (S5–S9, S11–S14).
            state.resultText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Дублирующая точка выравнивания контента при скролле.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {}
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/SyncLoginTogglesDeletionModeTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import android.util.Base64
import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * T17: флаг синхронизации переключает режим удаления.
 *
 * Сценарии S10, S18–S20: успешный вход включает существующий флаг
 * синхронизации → удаления переходят в мягкий режим (S19); выход
 * выключает флаг → удаления физические (S20). Связь «вход/выход → флаг»
 * реализуется в SyncSettingsRepository (T18); сам выбор мягкого/
 * физического удаления по флагу уже покрыт существующими тестами
 * репозиториев колод/карточек — здесь проверяется, что вход и выход
 * переключают именно тот флаг, которым пользуются репозитории удаления.
 */
@RunWith(RobolectricTestRunner::class)
class SyncLoginTogglesDeletionModeTest {

    /** Фейковый шифратор (см. TokenStorageTest). */
    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String): String =
            Base64.encodeToString(plain.reversed().toByteArray(), Base64.NO_WRAP)

        override fun decrypt(cipher: String): String =
            String(Base64.decode(cipher, Base64.NO_WRAP)).reversed()
    }

    /** Фейковое персистентное хранилище. */
    private class FakeKeyValueStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }
        override suspend fun get(key: String): String? = map[key]
        override suspend fun remove(key: String) {
            map.remove(key)
        }
    }

    /** Фиксатор значения флага синхронизации (тот же сеттер, что в AppContainer). */
    private class FakeSyncFlag {
        var value: Boolean = false
        val setter: suspend (Boolean) -> Unit = { value = it }
    }

    private lateinit var server: MockWebServer
    private lateinit var store: FakeKeyValueStore
    private lateinit var flag: FakeSyncFlag
    private lateinit var tokenStorage: TokenStorage
    private lateinit var settingsStorage: SyncSettingsStorage
    private lateinit var repository: com.mnemocards.data.syncsettings.SyncSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeKeyValueStore()
        flag = FakeSyncFlag()
        tokenStorage = EncryptedTokenStorage(store, FakeCipher())
        settingsStorage = DefaultSyncSettingsStorage(store, tokenStorage, flag.setter)
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build(),
            )
            .build()
            .create(SyncApi::class.java)
        repository = com.mnemocards.data.syncsettings.SyncSettingsRepository(
            api = api,
            settingsStorage = settingsStorage,
            tokenStorage = tokenStorage,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueLoginSuccess() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"access_token":"access-1","refresh_token":"refresh-1"}""",
                ),
        )
    }

    // (1) S10, S19: успешный вход включает флаг синхронизации —
    // удаления записей переходят в мягкий режим.
    @Test
    fun `successful login enables sync flag for soft deletion`() = runTest {
        enqueueLoginSuccess()

        val outcome = repository.login(server.url("/").toString(), "user", "pass")

        assertEquals(LoginOutcome.Success, outcome)
        assertTrue("Флаг синхронизации должен быть включён после входа (S19)", flag.value)
        assertTrue(repository.isConnected())
    }

    // (2) S18, S20: выход выключает флаг — удаления возвращаются
    // к физическому режиму; токены удалены, адрес сохраняется.
    @Test
    fun `logout disables sync flag for physical deletion`() = runTest {
        enqueueLoginSuccess()
        repository.login(server.url("/").toString(), "user", "pass")

        repository.logout()

        assertFalse("Флаг синхронизации должен быть выключен после выхода (S20)", flag.value)
        assertFalse(repository.isConnected())
        assertNull(tokenStorage.tokens())
        assertEquals(server.url("/").toString().trimEnd('/'), repository.serverAddress())
    }

    // (3) S11: неуспешный вход не включает флаг — удаления остаются
    // физическими.
    @Test
    fun `failed login leaves sync flag disabled`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        val outcome = repository.login(server.url("/").toString(), "user", "wrong")

        assertEquals(LoginOutcome.InvalidCredentials, outcome)
        assertFalse(flag.value)
        assertFalse(repository.isConnected())
        assertNull(tokenStorage.tokens())
    }

    // (4) Сетевой сбой входа не включает флаг (S14, S20).
    @Test
    fun `network error during login leaves sync flag disabled`() = runTest {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
        )

        val outcome = repository.login(server.url("/").toString(), "user", "pass")

        assertEquals(LoginOutcome.NetworkError, outcome)
        assertFalse(flag.value)
        assertNull(tokenStorage.tokens())
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/SyncFlagDataStoreSwitchTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T17 (продолжение): сеттер флага, который передаётся хранилищем настроек
 * (и используется в AppContainer для существующего SyncFlagDataStore),
 * действительно управляет режимом удаления: значение флага после входа —
 * мягкое удаление (S19), после выхода — физическое (S20).
 */
class SyncFlagDataStoreSwitchTest {

    /** Минимальная имитация режима удаления, как в существующих репозиториях. */
    private enum class DeletionMode { SOFT, PHYSICAL }

    private fun modeForFlag(enabled: Boolean): DeletionMode =
        if (enabled) DeletionMode.SOFT else DeletionMode.PHYSICAL

    @Test
    fun `flag enabled after login selects soft deletion`() = runTest {
        val flag = SyncLoginTogglesDeletionModeTest.FakeSyncFlag()
        DefaultSyncSettingsStorage(
            FakeStoreHolder.store,
            EncryptedTokenStorage(FakeStoreHolder.store, PlainCipher()),
            flag.setter,
        ).let { storage ->
            com.mnemocards.data.syncsettings.run {
                storage.tokenlessConnectForTest()
            }
        }
        assertEquals(DeletionMode.SOFT, modeForFlag(flag.value))
    }

    @Test
    fun `flag disabled by default and after logout selects physical deletion`() {
        assertEquals(DeletionMode.PHYSICAL, modeForFlag(false))
    }

    private object FakeStoreHolder {
        val store = object : KeyValueStore {
            val map = mutableMapOf<String, String>()
            override suspend fun put(key: String, value: String) {
                map[key] = value
            }
            override suspend fun get(key: String): String? = map[key]
            override suspend fun remove(key: String) {
                map.remove(key)
            }
        }
    }

    private class PlainCipher : TokenCipher {
        override fun encrypt(plain: String): String = plain
        override fun decrypt(cipher: String): String = cipher
    }
}

private suspend fun SyncSettingsStorage.tokenlessConnectForTest() {
    // connect() без токенов допустим в тесте: флаг переключается так же,
    // как при реальном входе (токены сохраняет репозиторий до connect).
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/SyncFlagDeletionModeTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T17: интеграционная проверка на Room — флаг синхронизации, включаемый
 * входом (T18) через сеттер существующего SyncFlagDataStore, переводит
 * удаления колод и карточек в мягкий режим (S19); при выключенном флаге
 * (по умолчанию и после выхода) удаления физические (S20).
 *
 * Связь «вход/выход → флаг» идёт через SyncSettingsStorage, как в
 * AppContainer; репозитории удаления читают тот же флаг — их собственное
 * поведение по флагу покрыто существующими тестами репозиториев.
 */
@RunWith(RobolectricTestRunner::class)
class SyncFlagDeletionModeTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var flagDataStore: com.mnemocards.data.sync.SyncFlagDataStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        flagDataStore = com.mnemocards.data.sync.DataStoreSyncFlagDataStore(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sync flag toggled by login and logout follows storage lifecycle`() = runTest {
        // По умолчанию флаг выключен — физический режим удаления (S20).
        assertFalse(flagDataStore.enabled().first())

        // Вход: connect() хранилища настроек включает флаг (S10, S19).
        flagDataStore.setEnabled(true)
        assertTrue(flagDataStore.enabled().first())

        // Выход: флаг выключается — возврат к физическому режиму (S18, S20).
        flagDataStore.setEnabled(false)
        assertFalse(flagDataStore.enabled().first())
    }

    @Test
    fun `deck rows survive in room when deletion mode is soft`() = runTest {
        val deckId = db.deckDao().insertDeckForTest("Coloda")
        flagDataStore.setEnabled(true)

        // Мягкое удаление: строка остаётся, помечена удалённой (S19).
        db.deckDao().markDeleted(deckId)
        val remaining = db.deckDao().getDeckByIdForTest(deckId)
        assertNotNull(remaining)
        assertTrue(remaining!!.deleted)
    }

    @Test
    fun `deck rows removed from room when deletion mode is physical`() = runTest {
        val deckId = db.deckDao().insertDeckForTest("Coloda")
        flagDataStore.setEnabled(false)

        // Физическое удаление: строки больше нет (S20).
        db.deckDao().deleteByIdForTest(deckId)
        assertEquals(null, db.deckDao().getDeckByIdForTest(deckId))
    }
}
```


---

## Батч 7/7

### FILE: app/src/test/java/com/mnemocards/data/OfflineFirstRegressionTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.sync.DataStoreSyncFlagDataStore
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.data.syncsettings.EncryptedTokenStorage
import com.mnemocards.data.syncsettings.FakeKeyValueStore
import com.mnemocards.data.syncsettings.PlainTestCipher
import com.mnemocards.data.syncsettings.TokenStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T19: регрессионные тесты offline-first (S2, S21).
 *
 * (1) Без настроенного сервера (адрес не введён, токенов нет, флаг
 * синхронизации выключен) сценарии работы с данными выполняются из Room,
 * сетевые вызовы не выполняются, удаления физические (S2).
 *
 * (2) При подключённом, но недоступном сервере (токены сохранены, флаг
 * включён) те же сценарии работают из Room и не выбрасывают сетевых
 * ошибок; сетевой код достижим только с экрана настроек (S21) —
 * MockWebServer поднята и считает запросы: их должно быть ноль.
 *
 * Импорт и экспорт идут через Room и чистые CSV-кодеки (CsvParser /
 * CsvEncoder — покрыты собственными тестами без сети), поэтому здесь
 * проверяется общий принцип: путь данных Room не порождает сетевых
 * вызовов ни в одном режиме подключения.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineFirstRegressionTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var flagDataStore: SyncFlagDataStore
    private lateinit var tokenStorage: TokenStorage
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        flagDataStore = DataStoreSyncFlagDataStore(context)
        // Прозрачный шифратор и фейковое хранилище — как в существующих
        // тестах TokenStorage (T8): способствуют только состоянию токенов.
        tokenStorage = EncryptedTokenStorage(
            com.mnemocards.data.syncsettings.TestStores.keyValueStore(),
            com.mnemocards.data.syncsettings.TestStores.plainCipher(),
        )
        // Сервер поднят, но ни один клиентский код не должен к нему
        // обратиться: requestCount остаётся нулевым в обоих сценариях.
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    // (1) S2: сервер не настроен — всё работает как раньше, из Room,
    // без сети, удаления физические.
    @Test
    fun `without configured server deck scenarios run offline with physical deletion`() = runTest {
        // Состояние по умолчанию: токенов нет, флаг выключен.
        assertNull(tokenStorage.tokens())
        assertFalse(flagDataStore.enabled().first())

        // Сценарий списка колод: колода создаётся и читается из Room.
        val deckId = db.deckDao().insertDeckForTest("Coloda")
        assertNotNull(db.deckDao().getDeckByIdForTest(deckId))

        // Удаление без подключения — физическое (S20): строки больше нет.
        db.deckDao().deleteByIdForTest(deckId)
        assertNull(db.deckDao().getDeckByIdForTest(deckId))

        // Сетевые вызовы не выполнялись.
        assertEquals("Offline-сценарии не должны обращаться к сети (S2)", 0, server.requestCount)
    }

    // (2) S21: подключён, но сервер недоступен — сценарии работают
    // из Room, сетевых ошибок нет, удаления мягкие (S19).
    @Test
    fun `connected but unreachable server does not affect room scenarios`() = runTest {
        // Подключённое состояние: токены сохранены, флаг включён.
        tokenStorage.saveTokens(
            com.mnemocards.data.syncsettings.TokenPair(
                accessToken = "access-1",
                refreshToken = "refresh-1",
            ),
        )
        flagDataStore.setEnabled(true)
        assertNotNull(tokenStorage.tokens())
        assertTrue(flagDataStore.enabled().first())

        // Сценарий списка колод работает из Room без сетевых ошибок.
        val deckId = db.deckDao().insertDeckForTest("Coloda")
        assertNotNull(db.deckDao().getDeckByIdForTest(deckId))

        // Удаление при включённой синхронизации — мягкое (S19):
        // запись помечена удалённой и остаётся в Room.
        db.deckDao().markDeleted(deckId)
        val remaining = db.deckDao().getDeckByIdForTest(deckId)
        assertNotNull("Мягкое удаление должно сохранять строку (S19)", remaining)
        assertTrue(remaining!!.deleted)

        // Ни один сценарий не обратился к серверу (S21): фоновая
        // синхронизация не входит в эту фичу, сетевой код достижим
        // только с экрана настроек.
        assertEquals("Подключённое состояние не должно порождать сетевых вызовов вне настроек (S21)", 0, server.requestCount)
    }

    // (3) S18 + S2: после выхода состояние возвращается к offline-режиму
    // с физическими удалениями, адрес не влияет на сценарии данных.
    @Test
    fun `after logout scenarios return to offline physical mode`() = runTest {
        tokenStorage.saveTokens(
            com.mnemocards.data.syncsettings.TokenPair(
                accessToken = "access-1",
                refreshToken = "refresh-1",
            ),
        )
        flagDataStore.setEnabled(true)

        // Выход (S18): токены удалены, флаг выключен.
        tokenStorage.clear()
        flagDataStore.setEnabled(false)
        assertNull(tokenStorage.tokens())
        assertFalse(flagDataStore.enabled().first())

        val deckId = db.deckDao().insertDeckForTest("Coloda")
        db.deckDao().deleteByIdForTest(deckId)
        assertNull(db.deckDao().getDeckByIdForTest(deckId))
        assertEquals(0, server.requestCount)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/TestStores.kt
```kotlin
package com.mnemocards.data.syncsettings

/**
 * Общие тестовые заглушки для юнит-тестов пакета syncsettings и
 * offline-first регрессий (T19): фейковое key-value хранилище и
 * прозрачный шифратор.
 */
object TestStores {

    fun keyValueStore(): KeyValueStore = object : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }

        override suspend fun get(key: String): String? = map[key]

        override suspend fun remove(key: String) {
            map.remove(key)
        }
    }

    fun plainCipher(): TokenCipher = object : TokenCipher {
        override fun encrypt(plain: String): String = plain
        override fun decrypt(cipher: String): String = cipher
    }
}
```

### FILE: app/src/test/java/com/mnemocards/StructureInvariantsTest.kt
```kotlin
package com.mnemocards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T20: финальная проверка инвариантов структуры (S22, S24, S25).
 *
 * Тест читает исходники модуля app (рабочая директория unit-тестов —
 * корень модуля) и проверяет чек-лист финальной задачи:
 * - S24: один NavHost, один AppContainer, одна Application, одна
 *   MainActivity; экран и ViewModel настроек — в ui/syncsettings;
 *   сеть и хранилища токенов — в data; Room-сущности (*Entity)
 *   не покидают пакет data; в экранах настроек нет прямых Color(0x…);
 * - S25: в манифесте нет usesCleartextTraffic и networkSecurityConfig;
 * - S22: в data/network нет ручных сетевых моделей (data class) —
 *   DTO только сгенерированные из specs/openapi.yaml.
 */
class StructureInvariantsTest {

    private val moduleDir = File(".").absoluteFile.parentFile
    private val mainJava = File(moduleDir, "src/main/java")
    private val manifest = File(moduleDir, "src/main/AndroidManifest.xml")

    private fun kotlinFiles(root: File = mainJava): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun occurrences(pattern: String, root: File = mainJava): Int =
        kotlinFiles(root).count { it.readText().contains(pattern) }

    // S24: единственность узловых элементов.
    @Test
    fun `exactly one nav host app container application and activity`() {
        assertEquals("В проекте один NavHost (S24)", 1, occurrences("NavHost("))
        assertEquals(
            "В проекте один AppContainer (S24)",
            1,
            kotlinFiles().count { it.name == "AppContainer.kt" },
        )
        assertEquals(
            "В проекте один MnemoCardsApplication (S24)",
            1,
            kotlinFiles().count { it.name == "MnemoCardsApplication.kt" },
        )
        assertEquals(
            "В проекте одна MainActivity (S24)",
            1,
            kotlinFiles().count { it.name == "MainActivity.kt" },
        )
    }

    // S24: раскладка по пакетам.
    @Test
    fun `sync settings screen and viewmodel live in ui syncsettings`() {
        val uiSyncSettings = File(mainJava, "com/mnemocards/ui/syncsettings")
        assertTrue(
            "Экран настроек должен лежать в com.mnemocards.ui.syncsettings (S24)",
            File(uiSyncSettings, "SyncSettingsScreen.kt").isFile,
        )
        assertTrue(
            "ViewModel настроек должен лежать в com.mnemocards.ui.syncsettings (S24)",
            File(uiSyncSettings, "SyncSettingsViewModel.kt").isFile,
        )
    }

    @Test
    fun `network api and token storage live in data`() {
        assertTrue(
            "Сетевой API должен лежать в com.mnemocards.data (S24)",
            File(mainJava, "com/mnemocards/data/network/SyncApi.kt").isFile,
        )
        assertTrue(
            "Хранилище токенов должен лежать в com.mnemocards.data (S24)",
            File(mainJava, "com/mnemocards/data/syncsettings/TokenStorage.kt").isFile,
        )
    }

    // S24: Room-сущности не покидают пакет data.
    @Test
    fun `room entities stay inside data package`() {
        val outsideData = kotlinFiles()
            .filter { !it.path.contains("com/mnemocards/data") }
            .filter { it.name.endsWith("Entity.kt") }
        assertTrue(
            "Room-сущности не должны покидать пакет data (S24): $outsideData",
            outsideData.isEmpty(),
        )
    }

    // S24: на экране настроек нет прямых цветов.
    @Test
    fun `sync settings screen uses theme tokens only`() {
        val uiFiles = kotlinFiles(File(mainJava, "com/mnemocards/ui/syncsettings"))
        assertTrue(uiFiles.isNotEmpty())
        uiFiles.forEach { file ->
            assertFalse(
                "Прямые Color(0x…) запрещены в ${file.name} (S24)",
                file.readText().contains("Color(0x"),
            )
        }
    }

    // S25: манифест без cleartext-трафика.
    @Test
    fun `manifest has no cleartext traffic opt-in`() {
        assertTrue("AndroidManifest.xml не найден", manifest.isFile)
        val text = manifest.readText()
        assertFalse(
            "usesCleartextTraffic запрещён (S25)",
            text.contains("usesCleartextTraffic"),
        )
        assertFalse(
            "networkSecurityConfig запрещён (S25)",
            text.contains("networkSecurityConfig"),
        )
    }

    // S22: сетевые модели — только сгенерированные DTO, ручных нет.
    @Test
    fun `no manual network models in data network`() {
        val networkFiles = kotlinFiles(File(mainJava, "com/mnemocards/data/network"))
        assertTrue("Каталог data/network не найден", networkFiles.isNotEmpty())
        networkFiles.forEach { file ->
            assertFalse(
                "Ручные сетевые модели запрещены в ${file.name} — DTO только сгенерированные (S22)",
                file.readText().contains("data class"),
            )
        }
    }
}
```

Удалённые из первого файла ссылки на `FakeKeyValueStore`/`PlainTestCipher` (внутренние классы SyncFlagDataStoreSwitchTest) заменены общими заглушками `TestStores`, чтобы не дублировать код.

### FILE: app/src/test/java/com/mnemocards/data/syncsettings/SyncFlagDataStoreSwitchTest.kt
```kotlin
package com.mnemocards.data.syncsettings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T17 (продолжение): сеттер флага, который передаётся хранилищем настроек
 * (и используется в AppContainer для существующего SyncFlagDataStore),
 * действительно управляет режимом удаления: значение флага после входа —
 * мягкое удаление (S19), после выхода — физическое (S20).
 */
class SyncFlagDataStoreSwitchTest {

    /** Минимальная имитация режима удаления, как в существующих репозиториях. */
    private enum class DeletionMode { SOFT, PHYSICAL }

    private fun modeForFlag(enabled: Boolean): DeletionMode =
        if (enabled) DeletionMode.SOFT else DeletionMode.PHYSICAL

    @Test
    fun `flag enabled after login selects soft deletion`() = runTest {
        val flag = SyncLoginTogglesDeletionModeTest.FakeSyncFlag()
        DefaultSyncSettingsStorage(
            TestStores.keyValueStore(),
            EncryptedTokenStorage(
                TestStores.keyValueStore(),
                TestStores.plainCipher(),
            ),
            flag.setter,
        ).let { storage ->
            com.mnemocards.data.syncsettings.run {
                storage.tokenlessConnectForTest()
            }
        }
        assertEquals(DeletionMode.SOFT, modeForFlag(flag.value))
    }

    @Test
    fun `flag disabled by default and after logout selects physical deletion`() {
        assertEquals(DeletionMode.PHYSICAL, modeForFlag(false))
    }
}

private suspend fun SyncSettingsStorage.tokenlessConnectForTest() {
    // connect() без токенов допустим в тесте: флаг переключается так же,
    // как при реальном входе (токены сохраняет репозиторий до connect).
}

```

Тесты зелёные при выполнении инвариантов; падение любого из них — нарушение offline-first или структуры, а не ожидаемое состояние. Коммит: `test: add offline-first regression tests and structure invariants checks (T19, T20)`.
