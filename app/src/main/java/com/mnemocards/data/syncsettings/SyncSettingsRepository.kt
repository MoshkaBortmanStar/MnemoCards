package com.mnemocards.data.syncsettings

import android.util.Log
import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.network.dto.models.ErrorResponse
import com.mnemocards.data.network.dto.models.LoginRequest
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Репозиторий настроек синхронизации. Владеет сетевыми сценариями
 * «проверить соединение» и «войти», применяет результат к хранилищу.
 *
 * Сетевые модели — DTO из specs/openapi.yaml (S22): запрос входа и
 * тела ответов сериализуются сгенерированными классами, ручного JSON нет.
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
    /**
     * Сброс состояния синхронизации при подключении к ДРУГОМУ серверу:
     * ревизии потоков обнуляются, локальные записи помечаются к отправке.
     *
     * Ревизии выдаёт сервер, и для нового сервера они бессмысленны. Без
     * сброса клиент просит `since` из чужой нумерации, не находит что
     * отправить (всё подтверждено прежним сервером) и получает fullSync
     * с пустым срезом — после чего удаляет локальные записи как
     * «отсутствующие на сервере». Так на эмуляторе пропали все колоды.
     */
    private val onServerChanged: suspend () -> Unit = {},
) {

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val TAG = "SyncCheck"
    }

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
            Log.w(TAG, "health: сертификат отклонён", e)
            ConnectionCheckResult.CertificateRejected
        } catch (e: IOException) {
            Log.w(TAG, "health: сеть недоступна", e)
            ConnectionCheckResult.Unreachable
        }
    }

    /**
     * Вход (S10–S14). При успехе токены сохраняются зашифрованными,
     * адрес фиксируется, флаг синхронизации включается (S10, S19);
     * пароль не сохраняется нигде.
     */
    suspend fun login(address: String, username: String, password: String): LoginOutcome {
        return try {
            val response = api.login(
                url = loginUrl(address),
                body = LoginRequest(username = username, password = password),
            )
            when {
                response.isSuccessful -> {
                    val tokens = response.body()
                        // 200 без валидного тела — деградация сервера,
                        // трактуем как ошибку сервера, токенов нет (S13).
                        ?: return LoginOutcome.ServerError
                    tokenStorage.saveTokens(
                        TokenPair(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                        ),
                    )
                    // Сервер сменился — состояние синхронизации от прежнего
                    // больше не действительно. Сбрасываем ДО connect(),
                    // пока прежний адрес ещё доступен для сравнения.
                    val previous = settingsStorage.serverAddress()
                    if (previous != normalize(address)) {
                        onServerChanged()
                    }
                    // connect() фиксирует адрес, состояние «подключено» и
                    // включает флаг синхронизации (T18).
                    settingsStorage.connect(normalize(address))
                    LoginOutcome.Success
                }

                response.code() == 401 -> LoginOutcome.InvalidCredentials

                response.code() == 400 ->
                    LoginOutcome.BadRequest(parseErrorReason(response.errorBody()?.string()))

                else -> LoginOutcome.ServerError
            }
        } catch (e: SSLException) {
            Log.w(TAG, "login: сертификат отклонён", e)
            LoginOutcome.NetworkError
        } catch (e: IOException) {
            Log.w(TAG, "login: сеть недоступна", e)
            LoginOutcome.NetworkError
        } catch (e: SerializationException) {
            LoginOutcome.ServerError
        }
    }

    /** Причина ошибки из тела ответа: ErrorResponse.error.message (S12). */
    private fun parseErrorReason(bodyText: String?): String {
        if (bodyText.isNullOrBlank()) return "Некорректный запрос"
        return try {
            json.decodeFromString<ErrorResponse>(bodyText).error.message
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
