# Tasks: Настройки синхронизации и вход на сервер

> **Change ID:** `add-sync-settings`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера. Задачи выполняются по порядку; каждая пара «падающий тест → реализация» — цикл TDD: тест пишется до кода. Формат запросов, ответов и коды ошибок берутся только из `specs/API.md` и `specs/openapi.yaml`. Существующие файлы (NavHost, AppContainer, флаг синхронизации, экран списка колод) не дублируются — пути уточняются по карте проекта.

## Реализация

- [ ] **T1. Падающий тест: проверка соединения (успех, 404, 500)** — создать тест репозитория с MockWebServer: (1) `GET /api/v1/health` отвечает 200 → `checkConnection` возвращает `ConnectionCheckResult.ServerResponds`; (2) 404 → `NoSyncServer`; (3) 5xx → `ServerError`. Проверить метод и путь запроса (`GET /api/v1/health`) согласно specs/API.md. Запустить тест: падение или ошибка компиляции — ожидаемое красное состояние.
      Сценарии: S5, S8, S9, S22. Файлы: `app/src/test/java/com/mnemocards/data/syncsettings/SyncSettingsRepositoryTest.kt`

- [ ] **T2. Генерация DTO из specs/openapi.yaml** — подключить в `app/build.gradle.kts` тот же openapi-генератор, что используется на сервере; вход — `specs/openapi.yaml`; выход — DTO для `GET /api/v1/health` и `POST /api/v1/auth/login` в пакете внутри `com.mnemocards.data`; сгенерированная директория подключена в source sets. Ручные сетевые модели не создаются, сгенерированные файлы не правятся.
      Сценарии: S22. Файлы: `app/build.gradle.kts`, `settings.gradle.kts` (при необходимости), `app/src/main/java/com/mnemocards/data/network/dto/` (генерируется)

- [ ] **T3. Retrofit API + проверка соединения** — реализовать `SyncApi` (Retrofit; запрос здоровья через `@Url` — базовый адрес подставляется из поля ввода), `SyncSettingsRepository.checkConnection(address)` и sealed-класс `ConnectionCheckResult` в `domain.model` (`ServerResponds`, `NoSyncServer`, `ServerError`). OkHttp-клиент с явными таймаутами connect/read; ответ маппится по HTTP-коду. Использовать только сгенерированные DTO. Добиться зелёного T1.
      Сценарии: S5, S8, S9, S22. Файлы: `app/src/main/java/com/mnemocards/data/network/SyncApi.kt`, `app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt`, `app/src/main/java/com/mnemocards/domain/model/ConnectionCheckResult.kt`

- [ ] **T4. Падающий тест: сетевые ошибки проверки соединения** — дополнить тесты: (1) нерезолвящийся домен (`UnknownHostException`) и таймаут соединения → `ConnectionCheckResult.Unreachable`; (2) сервер поверх TLS с сертификатом, которому клиентский OkHttp не доверяет → `SSLHandshakeException` → `CertificateRejected`. Маппинг ещё не реализован — тест красный.
      Сценарии: S6, S7. Файлы: `app/src/test/java/com/mnemocards/data/syncsettings/SyncSettingsRepositoryTest.kt`

- [ ] **T5. Маппинг сетевых ошибок** — в `SyncSettingsRepository` различить исключения: `SSLException`/`SSLHandshakeException` → `CertificateRejected`; прочие `IOException` → `Unreachable`. Обхода проверки сертификата (доверие всем, `hostnameVerifier`-заглушки) не добавлять. Добиться зелёного T4.
      Сценарии: S6, S7. Файлы: `app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt`, `app/src/main/java/com/mnemocards/domain/model/ConnectionCheckResult.kt`

- [ ] **T6. Падающий тест: вход (успех, 400, 401, 500, сетевой сбой)** — тесты с MockWebServer для `POST /api/v1/auth/login`: (1) 200 + тело с access/refresh-токенами по контракту openapi.yaml → `LoginOutcome.Success`, проверить тело запроса согласно specs/API.md; (2) 401 → `InvalidCredentials`; (3) 400 → `BadRequest` с причиной из тела ответа; (4) 500 → `ServerError`; (5) обрыв соединения (`SocketPolicy.DISCONNECT_AT_START`, `IOException`) → `NetworkError`, повторный вызов доступен. При любой ошибке токены не возвращаются.
      Сценарии: S10–S14, S22. Файлы: `app/src/test/java/com/mnemocards/data/syncsettings/SyncSettingsRepositoryTest.kt`

- [ ] **T7. Реализация входа** — `SyncApi`: POST входа на сгенерированных DTO; `SyncSettingsRepository.login(address, username, password)` → sealed `LoginOutcome` в `domain.model` (`Success`, `InvalidCredentials`, `BadRequest(reason)`, `ServerError`, `NetworkError`). Коды и тела ошибок — строго из specs/API.md. Добиться зелёного T6.
      Сценарии: S10–S14, S22. Файлы: `app/src/main/java/com/mnemocards/data/network/SyncApi.kt`, `app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt`, `app/src/main/java/com/mnemocards/domain/model/LoginOutcome.kt`

- [ ] **T8. Падающий тест: хранение токенов, адреса, пароля** — тесты `TokenStorage` и `SyncSettingsStorage` (крипто-провайдер и персистентность подменяются fakes): (1) после сохранения токенов в хранилище нет их открытого текста — только шифртекст; (2) пароль не сохраняется нигде; (3) `logout` удаляет токены и состояние «подключено», адрес сохраняется; (4) адрес переживает пересоздание хранилища; (5) сохранение токенов включает флаг синхронизации, logout — выключает.
      Сценарии: S16–S18. Файлы: `app/src/test/java/com/mnemocards/data/syncsettings/TokenStorageTest.kt`

- [ ] **T9. Реализация хранилища** — интерфейс `TokenCipher` + `KeystoreTokenCipher` (AES-ключ в Android Keystore, AES/GCM) — тонкая обёртка без логики; `TokenStorage` — запись/чтение/удаление токенов только через шифратор; `SyncSettingsStorage` — адрес сервера и признак «подключено» открытым текстом. Используется существующее хранилище флага синхронизации (карта проекта), новый флаг не создаётся. Пароль нигде не пишется. Добиться зелёного T8.
      Сценарии: S16–S18. Файлы: `app/src/main/java/com/mnemocards/data/syncsettings/TokenCipher.kt`, `app/src/main/java/com/mnemocards/data/syncsettings/KeystoreTokenCipher.kt`, `app/src/main/java/com/mnemocards/data/syncsettings/TokenStorage.kt`, `app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsStorage.kt`

- [ ] **T10. Падающий тест: ViewModel — валидация полей** — `SyncSettingsViewModelTest` (репозиторий и хранилища — fakes, фиксирующие вызовы): (1) пустой адрес → ошибка «Введите адрес сервера», сеть не вызывается; (2) не-URL («sync example») → «Некорректный адрес»; (3) `http://…` → «Незашифрованное соединение (http) заблокировано Android — используйте https-адрес»; (4) при пустом логине или пароле → «Заполните логин и пароль», запрос входа не отправляется; (5) статус подключения не меняется во всех случаях.
      Сценарии: S3, S4, S25. Файлы: `app/src/test/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModelTest.kt`

- [ ] **T11. ViewModel — состояние и валидация** — `SyncSettingsViewModel` с `StateFlow<SyncSettingsUiState>` (адрес, логин, пароль, `isChecking`, `isLoggingIn`, статус «Не подключено»/«Подключено», текст результата, ошибки полей); состояние переживает смену конфигурации, так как владеет им ViewModel. Валидация выполняется до любых сетевых вызовов. Добиться зелёного T10.
      Сценарии: S3, S4, S23, S25. Файлы: `app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModel.kt`, `app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsUiState.kt`

- [ ] **T12. Падающий тест: ViewModel — проверка, вход, выход, загрузка** — дополнить тесты: (1) результаты `checkConnection` отображаются текстами «Сервер отвечает» / «Адрес недоступен» / «Сертификат не принят» / «По этому адресу нет сервера синхронизации» / сообщение об ошибке сервера; статус подключения не меняется; (2) успех входа → статус «Подключено» с адресом, поле пароля очищено, токены сохранены, адрес сохранён, флаг синхронизации включён; (3) 401 → «Неверный логин или пароль», статус «Не подключено»; (4) сетевой сбой → «Адрес недоступен», флаги загрузки сняты, повторная попытка доступна; (5) пока `isChecking`/`isLoggingIn` — повторные вызовы не проходят (fake считает вызовы); (6) `logout` → токены удалены, флаг выключен, статус «Не подключено», адрес сохранён; (7) `init` восстанавливает адрес и статус «Подключено» из хранилища, пароль пуст.
      Сценарии: S5–S15, S17, S18, S23. Файлы: `app/src/test/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModelTest.kt`

- [ ] **T13. ViewModel — сценарии** — подключить `SyncSettingsRepository` и хранилища: `checkConnection`, `login`, `logout`, обновление полей; guard-флаги против повторных нажатий; маппинг `ConnectionCheckResult`/`LoginOutcome` в тексты; восстановление состояния в `init`. Добиться зелёного T12.
      Сценарии: S5–S15, S17, S18, S23. Файлы: `app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModel.kt`

- [ ] **T14. Экран настроек** — `SyncSettingsScreen`: поля «Адрес сервера», «Логин», «Пароль», кнопки «Проверить соединение» и «Войти», индикатор статуса «Не подключено»/«Подключено» (+ адрес), кнопка «Выйти» в подключённом состоянии, инлайн-ошибки под полями, текстовый результат проверки, отключение кнопок + индикатор загрузки при запросе. Оформление: `MnemoCardsTheme`, цвета только из `MaterialTheme.colorScheme` (новые оттенки — в `ui/theme/Color.kt`), прямые `Color(0xFF…)` запрещены. Бизнес-логики в composable нет — только состояние из ViewModel.
      Сценарии: S1, S3–S5, S8–S15, S18, S24. Файлы: `app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsScreen.kt`, `app/src/main/java/com/mnemocards/ui/theme/Color.kt` (при необходимости)

- [ ] **T15. Маршрут и вход с списка колод** — добавить маршрут экрана настроек в существующий единственный `MnemoCardsNavHost` (второй навигационный граф не создаётся); на экране списка колод добавить кнопку/иконку настроек (в TopAppBar). Проверить переход «список колод → настройки → назад».
      Сценарии: S1, S24. Файлы: `app/src/main/java/com/mnemocards/ui/…/MnemoCardsNavHost.kt` (существующий, путь по карте проекта), экран списка колод (существующий)

- [ ] **T16. Сборка зависимостей в AppContainer** — зарегистрировать в единственном `AppContainer`: OkHttp/Retrofit (динамический базовый адрес), `SyncApi`, `KeystoreTokenCipher`, `TokenStorage`, `SyncSettingsStorage`, `SyncSettingsRepository`; ViewModel получает зависимости через существующий механизм фабрик. Новые контейнеры и точки входа не создаются.
      Сценарии: S24. Файлы: `app/src/main/java/com/mnemocards/…/AppContainer.kt` (существующий), `app/src/main/java/com/mnemocards/ui/syncsettings/SyncSettingsViewModel.kt`

- [ ] **T17. Падающий тест: флаг синхронизации переключает режим удаления** — тест существующего пути удаления колоды и карточки (репозиторий/DAO, карта проекта): (1) при включённом флаге (после входа) удаление помечает запись удалённой, строка остаётся в Room; (2) при выключенном (после выхода или по умолчанию) — физическое удаление. Красное состояние ожидается: связь «вход/выход → флаг» ещё не реализована.
      Сценарии: S19, S20. Файлы: `app/src/test/java/com/mnemocards/data/…` (существующие тесты репозитория колод/карточек, дополнение)

- [ ] **T18. Связывание входа с флагом синхронизации** — успешный вход включает существующий флаг синхронизации, выход — выключает; режим удаления (мягкий/физический) определяется флагом через существующую логику, без её дублирования. Добиться зелёного T17.
      Сценарии: S10, S18–S20. Файлы: `app/src/main/java/com/mnemocards/data/syncsettings/SyncSettingsRepository.kt`, код включения флага (существующий, карта проекта)

- [ ] **T19. Регрессионные тесты offline-first** — дополнить существующие тесты: (1) без настроенного сервера (нет адреса и токенов) сценарии списка колод, тренировки, импорта и экспорта не выполняют сетевых вызовов, удаления физические; (2) при подключённом, но недоступном сервере те же сценарии работают из Room и не выбрасывают сетевых ошибок; сетевой код достижим только с экрана настроек. Тесты должны быть зелёными; падение — нарушение offline-first, а не ожидание.
      Сценарии: S2, S21. Файлы: `app/src/test/java/com/mnemocards/data/…` (существующие тесты репозиториев колод/карточек)

- [ ] **T20. Финальная проверка инвариантов** — полная сборка и прогон всех тестов; чек-лист: (S24) код экрана и ViewModel в `com.mnemocards.ui.syncsettings`, сеть и хранилища в `com.mnemocards.data`, Room-сущности не покидают `data`, в проекте один NavHost и один AppContainer, экран в тёмной теме без прямых цветов; (S25) в манифесте нет `usesCleartextTraffic` и network security config; (S22) сетевые модели только сгенерированные, ручных DTO нет, сгенерированные файлы не изменялись. Коммит — по Conventional Commits.
      Сценарии: S22, S24, S25. Файлы: проверка всего проекта; при необходимости — точечные правки без изменения контракта

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
