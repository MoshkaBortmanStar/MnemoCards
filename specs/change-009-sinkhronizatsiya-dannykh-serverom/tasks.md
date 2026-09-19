# Tasks: Фоновая синхронизация колод с сервером

> **Change ID:** `add-background-sync`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера.

Общие правила для всех задач: контракт запросов, ответов и кодов ошибок — `specs/API.md` и `specs/openapi.yaml` (источник истины, своего формата не вводить); сетевые модели только сгенерированные; тестовая задача выполняется раньше парной задачи реализации и обязана падать до неё; каждую задачу завершать отдельным коммитом (Conventional Commits). Точные имена существующих файлов сверять с картой проекта — сущности не дублировать, новые пакеты верхнего уровня не создавать.

## Реализация

### Блок 1 — Синх-метаданные в Room

- [ ] **T1. Падающий тест: DAO синх-полей и счётчика правок** — тест Room (in-memory БД): пометка записи `pendingChanges=true` с инкрементом `editCounter`; выборка pending-записей потока, включая tombstone (`deleted=true`); снятие pending только при совпадении текущего `editCounter` с зафиксированным снапшотом; физическое удаление tombstone. Убедиться, что тест падает.
      Сценарии: S5, S7, S9, S10, S11. Файлы: `app/src/test/java/com/mnemocards/data/local/SyncFieldsDaoTest.kt`

- [ ] **T2. Синх-колонки, миграция, DAO-методы** — в существующие `DeckEntity` и `CardEntity` добавить `pendingChanges: Boolean`, `editCounter: Long`, `deleted: Boolean` (tombstone); поднять версию `MnemoCardsDatabase` и написать миграцию (`ALTER TABLE ... ADD COLUMN` с дефолтами, данные не теряются); в `DeckDao`/`CardDao` добавить методы: pending-выборка по потоку, `markPending`, `clearPendingIfUnchanged(снапшот счётчиков)`, физическое удаление.
      Сценарии: S5, S7, S9, S10, S11, S24. Файлы: `app/src/main/java/com/mnemocards/data/local/DeckEntity.kt`, `app/src/main/java/com/mnemocards/data/local/CardEntity.kt`, `app/src/main/java/com/mnemocards/data/local/DeckDao.kt`, `app/src/main/java/com/mnemocards/data/local/CardDao.kt`, `app/src/main/java/com/mnemocards/data/local/MnemoCardsDatabase.kt`, `app/src/main/java/com/mnemocards/data/local/Migrations.kt`

### Блок 2 — Состояние потоков, токены, итоги синхронизации

- [ ] **T3. Падающий тест: состояние потоков, токены, результат** — тест DAO: чтение/запись ревизии по ключу потока (`decks` и `cards:{deckUuid}`), отсутствие записи трактуется как ревизия 0; сохранение и обновление пары access/refresh токенов; запись итога последней синхронизации (время, статус, текст ошибки). Убедиться, что тест падает.
      Сценарии: S4, S12, S13, S16, S24. Файлы: `app/src/test/java/com/mnemocards/data/local/SyncStateDaoTest.kt`

- [ ] **T4. Реализация state-сущностей** — новые `SyncStateEntity`, `AuthTokensEntity`, `LastSyncResultEntity` + `SyncStateDao` (или аналоги по карте проекта); зарегистрировать в `MnemoCardsDatabase`, добавить в миграцию; признак «синхронизация настроена» = наличие токенов.
      Сценарии: S1, S4, S12, S13, S14, S24. Файлы: `app/src/main/java/com/mnemocards/data/local/SyncStateEntity.kt`, `app/src/main/java/com/mnemocards/data/local/AuthTokensEntity.kt`, `app/src/main/java/com/mnemocards/data/local/LastSyncResultEntity.kt`, `app/src/main/java/com/mnemocards/data/local/MnemoCardsDatabase.kt`, `app/src/main/java/com/mnemocards/data/local/Migrations.kt`

### Блок 3 — Сетевой слой по контракту

- [ ] **T5. Падающий тест: SyncApi соответствует openapi** — тест MockWebServer: `POST /api/v1/sync/decks` и `POST /api/v1/sync/decks/{deckUuid}/cards` с телом запроса `{sinceRevision, changed[]}` и ответом `{revision, items[], fullSnapshot}` строго по схеме; `POST /api/v1/auth/refresh`; проверка метода, пути, сериализации полей. Убедиться, что тест падает.
      Сценарии: S1, S3, S4, S12. Файлы: `app/src/test/java/com/mnemocards/data/remote/SyncApiTest.kt`

- [ ] **T6. Сгенерированные DTO и Retrofit-интерфейс** — сгенерировать модели из `specs/openapi.yaml` (без ручных правок, конвенция MAIN), объявить `SyncApi` (Retrofit), OkHttp-клиент и Retrofit зарегистрировать в существующем `AppContainer`.
      Сценарии: S1, S3, S4, S12. Файлы: `app/src/main/java/com/mnemocards/data/remote/SyncApi.kt`, `app/src/main/java/com/mnemocards/data/remote/dto/` (сгенерированные), `app/src/main/java/com/mnemocards/di/AppContainer.kt`

### Блок 4 — Применение ответа к Room

- [ ] **T7. Падающий тест: SyncApplier** — тест с in-memory Room: перезапись локальных записей серверными (S6); запись с признаком удаления — физическое удаление без tombstone (S7); `fullSync=true` — полная замена состояния потока, локальные записи вне ответа удаляются (S8); запись ответа не применяется, если её локальная копия помечена изменённой после снапшота (S25); снятие pending при совпадении `editCounter` и сохранение при несовпадении (S9, S10); подтверждение tombstone — полное удаление (S11); повторное применение того же ответа не создаёт дублей (S24). Убедиться, что тест падает.
      Сценарии: S6, S7, S8, S9, S10, S11, S24, S25. Файлы: `app/src/test/java/com/mnemocards/data/sync/SyncApplierTest.kt`

- [ ] **T8. Реализация SyncApplier** — класс применения ответа к Room в транзакциях: upsert серверных записей (перезапись полей + ревизия), удаление по признаку удаления, полная замена потока при `fullSync`, `clearPending` со сравнением снапшота счётчиков отправки; использует DAO из T2.
      Сценарии: S6, S7, S8, S9, S10, S11, S24, S25. Файлы: `app/src/main/java/com/mnemocards/data/sync/SyncApplier.kt`, `app/src/main/java/com/mnemocards/data/local/DeckDao.kt`, `app/src/main/java/com/mnemocards/data/local/CardDao.kt`

### Блок 5 — Движок сессии

- [ ] **T9. Падающий тест: порядок сессии и чанкирование** — интеграционный тест MockWebServer + in-memory Room: сначала `POST /api/v1/sync/decks`, затем `POST /api/v1/sync/decks/{deckUuid}/cards` только для живых колод, каждая со своей сохранённой ревизией (S3); новая колода — `since=0` (S4); возобновление после обрыва — колоды `since=42`, карточки колоды 5 `since=7` (S24); в отправку попадают только pending-записи, включая tombstone (S5); снапшоты `editCounter` фиксируются в момент отправки; 1200 изменений отправляются чанками ≤1000, ревизия — из последнего ответа (S26). Убедиться, что тест падает.
      Сценарии: S1, S3, S4, S5, S24, S26. Файлы: `app/src/test/java/com/mnemocards/data/sync/SyncEngineSessionTest.kt`

- [ ] **T10. Реализация SyncEngine (успешный путь)** — класс сессии на coroutines: последовательность «колоды → карточки живых колод», сбор pending и чанки ≤1000, фиксация снапшотов счётчиков при отправке, после 200 — вызов `SyncApplier` и сохранение ревизии потока из ответа; выполняется на фоновом диспетчере, UI не блокирует.
      Сценарии: S1, S3, S4, S5, S6, S9, S24, S26. Файлы: `app/src/main/java/com/mnemocards/data/sync/SyncEngine.kt`

- [ ] **T11. Падающий тест: обработка ошибок сессии** — тест MockWebServer: ошибка соединения без HTTP-ответа — сессия прекращена, данные/ревизии/признаки не изменены (S15); 500 на поток карточек — применённые потоки остаются, ревизия упавшего потока не меняется, сессия прерывается (S16); 400 — тело не применяется, pending не снимается, ревизия не обновляется (S17); 404 на поток карточек — колода и её карточки полностью удаляются локально, сессия продолжается (S18); 200 с повреждённым JSON — ошибка десериализации, БД цела (S19); 413 TOO_MANY_CHANGES — сессия прерывается, pending сохранён (S26). Убедиться, что тест падает.
      Сценарии: S15, S16, S17, S18, S19, S26. Файлы: `app/src/test/java/com/mnemocards/data/sync/SyncEngineErrorTest.kt`

- [ ] **T12. Реализация обработки ошибок** — sealed-иерархия `SyncError` (сеть / сервер 5xx / валидация 400 / поток удалён 404 / формат / требуется вход / слишком много изменений); прерывание сессии с сохранением уже применённых потоков; запись итога (время, статус, человекочитаемая ошибка) в `LastSyncResultEntity`.
      Сценарии: S15, S16, S17, S18, S19, S26. Файлы: `app/src/main/java/com/mnemocards/data/sync/SyncEngine.kt`, `app/src/main/java/com/mnemocards/data/sync/SyncError.kt`

### Блок 6 — Авторизация и прозрачный refresh

- [ ] **T13. Падающий тест: refresh и состояние «требуется вход»** — тест MockWebServer: 401 на sync-запрос → `POST /api/v1/auth/refresh` → новая пара токенов сохраняется → исходный запрос повторяется с новым access, сессия продолжается штатно (S12); refresh отвечает 401 → состояние «требуется вход», сессия останавливается, токены/данные/ревизии/признаки не теряются (S13); защита от бесконечного цикла refresh. Убедиться, что тест падает.
      Сценарии: S12, S13. Файлы: `app/src/test/java/com/mnemocards/data/remote/AuthRefreshTest.kt`

- [ ] **T14. Реализация AuthAuthenticator** — OkHttp `Authenticator` (или перехватчик 401) с одним refresh на вызов, обновление `AuthTokensEntity`, проброс сигнала «требуется вход» в `SyncEngine`/`SyncManager`; подключить к клиенту из T6.
      Сценарии: S12, S13. Файлы: `app/src/main/java/com/mnemocards/data/remote/AuthAuthenticator.kt`, `app/src/main/java/com/mnemocards/di/AppContainer.kt`

### Блок 7 — Запуск и одиночное выполнение

- [ ] **T15. Падающий тест: SyncManager (single-flight, настройка)** — тест: повторный вызов запуска при выполняющейся сессии не создаёт вторую параллельную (по логам MockWebServer — одна последовательность), статус «выполняется» (S23); синхронизация не настроена (нет токенов) — сетевые вызовы не выполняются (S14); `StateFlow` статуса: idle/running/успех/ошибка/требуется вход. Убедиться, что тест падает.
      Сценарии: S2, S14, S23. Файлы: `app/src/test/java/com/mnemocards/data/sync/SyncManagerTest.kt`

- [ ] **T16. Реализация SyncManager и автозапуск** — Mutex single-flight, `StateFlow<SyncStatus>`, публичный `requestSync()`; автозапуск при открытии приложения, если есть токены — хук в `MnemoCardsApplication` (application-scope корутина, Dispatchers.IO), UI не блокируется; регистрация в `AppContainer`.
      Сценарии: S1, S2, S14, S23. Файлы: `app/src/main/java/com/mnemocards/data/sync/SyncManager.kt`, `app/src/main/java/com/mnemocards/MnemoCardsApplication.kt`, `app/src/main/java/com/mnemocards/di/AppContainer.kt`

### Блок 8 — Пометка локальных правок

- [ ] **T17. Падающий тест: правки помечаются как локально изменённые** — тест репозиториев: создание/редактирование колоды и карточки → `pendingChanges=true`, `editCounter` +1 (S20, S22); локальное удаление → tombstone + pending (S5, S7); повторная правка во время «полёта» запроса инкрементирует счётчик (S10). Убедиться, что тест падает.
      Сценарии: S5, S7, S10, S20, S22. Файлы: `app/src/test/java/com/mnemocards/data/repository/PendingFlagsTest.kt`

- [ ] **T18. Реализация пометки в write-операциях** — обновить существующие write-методы репозиториев и DAO колод/карточек: установка pending и инкремент `editCounter` атомарно в тех же транзакциях; удаление заменяется на tombstone для синхронизируемых записей.
      Сценарии: S5, S10, S20, S22. Файлы: `app/src/main/java/com/mnemocards/data/repository/` (существующие репозитории), `app/src/main/java/com/mnemocards/data/local/DeckDao.kt`, `app/src/main/java/com/mnemocards/data/local/CardDao.kt`

### Блок 9 — Экран настроек

- [ ] **T19. Падающий тест: SettingsViewModel** — тест ViewModel: отображение «выполняется» при активной сессии, результата/времени/ошибок после завершения (S2); «требуется вход» при отказе refresh (S13); приглашение войти вместо статуса, когда синхронизация не настроена (S14); команда «Синхронизировать сейчас» не запускает вторую сессию при выполнении (S23). Убедиться, что тест падает.
      Сценарии: S2, S13, S14, S23. Файлы: `app/src/test/java/com/mnemocards/ui/settings/SettingsViewModelTest.kt`

- [ ] **T20. Реализация SettingsViewModel** — подписка на `SyncManager` status и `LastSyncResultEntity` через Flow; UDF: состояние вниз в `StateFlow`, событие запуска вверх; ViewModel без бизнес-логики синхронизации.
      Сценарии: S2, S13, S14, S23. Файлы: `app/src/main/java/com/mnemocards/ui/settings/SettingsViewModel.kt`

- [ ] **T21. Экран SettingsScreen** — Compose-экран: кнопка «Синхронизировать сейчас», статус (выполняется/успех/ошибка/требуется вход), время и текст ошибки последней синхронизации, приглашение войти при отсутствии настройки; строго тёмная тема — цвета только из `MaterialTheme.colorScheme`, обёртка `MnemoCardsTheme`, прямые `Color(0xFF...)` запрещены.
      Сценарии: S2, S13, S14, S15, S21. Файлы: `app/src/main/java/com/mnemocards/ui/settings/SettingsScreen.kt`

- [ ] **T22. Маршрут настроек в существующий NavHost** — добавить маршрут settings в единственный `MnemoCardsNavHost` (второй навигационный граф НЕ создавать), точка входа со списка колод, переход «назад»; никаких сообщений синхронизации (снекбары/диалоги) на экранах списка колод и тренировки; оформление `ReviewScreen.kt` не трогать.
      Сценарии: S2, S21. Файлы: `app/src/main/java/com/mnemocards/ui/navigation/MnemoCardsNavHost.kt`, `app/src/main/java/com/mnemocards/ui/decks/DecksScreen.kt`, `app/src/main/java/com/mnemocards/ui/decks/DecksViewModel.kt`

### Блок 10 — Приёмка

- [ ] **T23. Полный прогон и проверка инвариантов** — весь интеграционный тест из T1 зелёный; тест параллельной правки: MockWebServer с задержкой ответа + редактирование карточки во время сессии — UI не блокируется, правка сохраняется, защита счётчика срабатывает (S20, S22); проверка инвариантов specs/MAIN.md: один `MnemoCardsNavHost`, один `AppContainer`, отсутствие дублей сущностей, Room-сущности не покидают `data`, пакеты только `ui`/`domain`/`data`, сетевые модели только сгенерированные, тёмная тема во всех новых экранах; прогон всех тестов проекта.
      Сценарии: S1–S26 (регресс). Файлы: `app/src/test/java/com/mnemocards/` (все тесты), проверка `app/src/main/java/com/mnemocards/`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
