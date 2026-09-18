# Tasks: Глобальные идентификаторы и отслеживание локальных изменений

> **Change ID:** `prepare-sync-data-model`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера.
Стек: Kotlin, Room, DataStore, Compose. Тесты — JUnit + Robolectric
(in-memory Room); MockWebServer не используется — сетевого слоя в фиче нет.
Имена существующих классов сверять с картой проекта; параллельные сущности
не заводить (инварианты MAIN.md).

## Реализация

- [ ] **T1. Падающий тест: создание записи с UUID и признаками** — сгенерировать
      интеграционный тест репозитория (JUnit + Robolectric, in-memory Room),
      воспроизводящий сценарии S6, S10, S13 из spec-bdd.md: создание колоды
      и карточки через repository даёт `id` = валидный UUID (уникальный среди
      записей того же типа), `deckId` карточки = UUID колоды, `isChanged = true`,
      `revision = 1`, `isDeleted = false`. Убедиться, что тест падает (на этом
      этапе — ошибкой компиляции: полей и типов ещё нет). Добавить тест-зависимости,
      если отсутствуют (robolectric, kotlinx-coroutines-test).
      Сценарии: S6, S10, S13.
      Файлы: `app/src/test/java/com/mnemocards/data/repository/DeckRepositoryTest.kt`,
      `app/build.gradle.kts`

- [ ] **T2. Сущности на UUID + новые поля** — перевести `DeckEntity` и `CardEntity`
      на `id: String` (UUID, PK), у карточки `deckId: String` (FK, индекс),
      добавить поля `isChanged: Boolean`, `revision: Int`, `isDeleted: Boolean`.
      Поднять версию `MnemoCardsDatabase` до 2, включить `exportSchema`
      (миграция появится в T6 — до неё старые данные не запускать, только тесты
      и чистая установка). В repository при создании генерировать
      `UUID.randomUUID().toString()` и проставлять `isChanged = true`,
      `revision = 1`, `isDeleted = false`. Обновить сигнатуры DAO, сидинг и все
      существующие тесты под новые типы (механически, без нового поведения).
      Тест T1 зелёный.
      Сценарии: S6, S10, S13.
      Файлы: `app/src/main/java/com/mnemocards/data/db/DeckEntity.kt`,
      `app/src/main/java/com/mnemocards/data/db/CardEntity.kt`,
      `app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt`,
      `app/src/main/java/com/mnemocards/data/db/DeckDao.kt`,
      `app/src/main/java/com/mnemocards/data/db/CardDao.kt`,
      `app/src/main/java/com/mnemocards/data/repository/` (repository + сидинг,
      по карте проекта), существующие тесты

- [ ] **T3. Падающий тест: изменение записи** — тест: карточка с `isChanged = false`,
      `revision = 3` (установлено напрямую) после редактирования через repository —
      `isChanged = true`, `revision = 4`; второе редактирование — `revision = 5`;
      редактирование колоды — аналогично. Переоткрыть БД (новое соединение) —
      признаки не сброшены, ни одна операция их не снимает.
      Сценарии: S11, S12, S13.
      Файлы: `app/src/test/java/com/mnemocards/data/repository/DeckRepositoryTest.kt`

- [ ] **T4. Реализация update-методов** — DAO-запросы обновления всегда выполняют
      `isChanged = 1, revision = revision + 1` (SQL-инкремент или read-modify-write
      в `withTransaction`). Редактирование колоды и карточки идёт только через
      единственную точку — repository. Операции снятия `isChanged` в коде
      не появляется.
      Сценарии: S11, S12, S13.
      Файлы: `app/src/main/java/com/mnemocards/data/db/DeckDao.kt`,
      `app/src/main/java/com/mnemocards/data/db/CardDao.kt`,
      `app/src/main/java/com/mnemocards/data/repository/` (repository)

- [ ] **T5. Падающий тест миграции 1→2** — тест через `MigrationTestHelper`
      (room-testing): создать БД v1 с 2 колодами и 7 карточками (числовые
      автоинкрементные PK, заполненная статистика повторений), прогнать
      `MIGRATION_1_2`, проверить: без сбоев; у каждой записи уникальный UUID,
      дубликатов и потерь нет; данные и статистика сохранены; `deckId` карточки
      равен UUID её колоды; `isChanged = true`, `revision = 1`, `isDeleted = false`;
      пустая v1-БД мигрирует без ошибок и остаётся пустой. Если
      `app/schemas/.../1.json` не экспортирован — восстановить по git-истории
      старых сущностей и зафиксировать в схемах.
      Сценарии: S1, S2, S3, S4.
      Файлы: `app/src/test/java/com/mnemocards/data/db/MigrationFrom1To2Test.kt`,
      `app/schemas/com.mnemocards.data.db.MnemoCardsDatabase/1.json`,
      `app/build.gradle.kts`

- [ ] **T6. Миграция 1→2 и регистрация в билдере** — реализовать `MigrationFrom1To2`:
      внутри миграции создать новые таблицы (String PK) → прочитать старые строки →
      каждой колоде и карточке сгенерировать UUID (карта oldId→UUID для колод) →
      вставить с `isChanged = true`, `revision = 1`, `isDeleted = false`,
      пересчитанным `deckId` и сохранёнными остальными колонками (статистика) →
      удалить старые таблицы, переименовать новые. Зарегистрировать
      `.addMigrations(MIGRATION_1_2)` в месте сборки БД (AppContainer/Application).
      Тесты T5 зелёные.
      Сценарии: S1, S2, S3, S4.
      Файлы: `app/src/main/java/com/mnemocards/data/db/MigrationFrom1To2.kt`,
      `app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt`,
      `app/src/main/java/com/mnemocards/di/AppContainer.kt` (по карте проекта)

- [ ] **T7. Падающий тест флага синхронизации (DataStore)** — тест (Robolectric,
      tmp-каталог): чистый DataStore — чтение флага возвращает `false`; запись
      `true` — новый экземпляр хранилища читает `true` (персистентность);
      повреждённый файл (мусорные байты) — чтение возвращает `false` без
      исключения.
      Сценарии: S14, S15, S16.
      Файлы: `app/src/test/java/com/mnemocards/data/sync/SyncFlagDataStoreTest.kt`

- [ ] **T8. Реализация SyncFlagDataStore** — Preferences DataStore
      (`preferencesDataStore(name = "sync_settings")`, ключ `sync_enabled`),
      API: `Flow<Boolean>` + suspend-сеттер; ошибки чтения (IOException)
      транслируются в `false`. Единственный экземпляр — в AppContainer.
      UI-переключателей не добавлять. Добавить зависимость
      `androidx.datastore:datastore-preferences`.
      Сценарии: S14, S15, S16.
      Файлы: `app/src/main/java/com/mnemocards/data/sync/SyncFlagDataStore.kt`,
      `app/src/main/java/com/mnemocards/di/AppContainer.kt`, `app/build.gradle.kts`

- [ ] **T9. Падающий тест физического удаления** — тесты при выключенном флаге:
      удаление карточки — прямой DAO-запрос по UUID не находит строку, счётчик
      колоды уменьшился; удаление колоды с 5 карточками — все 6 строк физически
      отсутствуют; при ошибке чтения DataStore удаление выполняется как
      физическое.
      Сценарии: S16, S17, S18.
      Файлы: `app/src/test/java/com/mnemocards/data/repository/DeletionRepositoryTest.kt`

- [ ] **T10. Реализация ветки физического удаления** — `repository.deleteCard` /
      `deleteDeck`: читать флаг (`first()`, дефолт `false` при ошибке); при `false` —
      hard delete карточки, для колоды — транзакция удаления колоды и каскадного
      удаления её карточек (FK CASCADE или явные delete-запросы). Каркас ветвления
      «мягкое/жёсткое» заложить сразу; мягкая ветка реализуется в T12.
      Сценарии: S16, S17, S18.
      Файлы: `app/src/main/java/com/mnemocards/data/db/DeckDao.kt`,
      `app/src/main/java/com/mnemocards/data/db/CardDao.kt`,
      `app/src/main/java/com/mnemocards/data/repository/` (repository)

- [ ] **T11. Падающий тест мягкого удаления** — тесты при включённом флаге:
      карточка (`isChanged = true`, `revision = 2`) после удаления — строка в БД
      с `isDeleted = true`, `isChanged = true`, `revision = 3`, данные сохранены,
      прямой DAO-запрос без фильтра находит запись; колода с 4 карточками —
      все 5 строк на месте, у каждой `isDeleted = true`, `isChanged = true`,
      `revision +1`; после переключения флага в `false` напрямую в DataStore
      и «перезапуска» мягко удалённые записи не изменяются и не восстанавливаются.
      Сценарии: S19, S20, S23, S24.
      Файлы: `app/src/test/java/com/mnemocards/data/repository/DeletionRepositoryTest.kt`

- [ ] **T12. Реализация мягкого удаления** — DAO-запросы
      `UPDATE ... SET isDeleted = 1, isChanged = 1, revision = revision + 1
      WHERE id = :id` для карточки и колоды; удаление колоды — `withTransaction`:
      пометить колоду, затем пометить все карточки `WHERE deckId = :deckId`.
      Repository при флаге `true` идёт в мягкую ветку, при `false`/ошибке —
      в физическую (T10).
      Сценарии: S19, S20, S23, S24.
      Файлы: `app/src/main/java/com/mnemocards/data/db/DeckDao.kt`,
      `app/src/main/java/com/mnemocards/data/db/CardDao.kt`,
      `app/src/main/java/com/mnemocards/data/repository/` (repository)

- [ ] **T13. Падающий тест скрытия мягко удалённых из запросов UI** — тесты DAO:
      список колод со счётчиками — мягко удалённые колоды и карточки не входят
      (колода с 5 карточками, 2 удалены → счётчик 3); запрос карточек для сессии
      тренировки не возвращает `isDeleted = true`; агрегирующие/экспортные запросы —
      без мягко удалённых; при этом `getById` без фильтра по-прежнему находит
      мягко удалённую запись.
      Сценарии: S21, S22, S23.
      Файлы: `app/src/test/java/com/mnemocards/data/db/DaoFilterTest.kt`

- [ ] **T14. Фильтры isDeleted = 0 в запросах для UI** — добавить
      `AND isDeleted = 0` во все запросы, питающие экраны, счётчики и экспорт
      (список колод + counts, карточки сессии); прямой `getById` оставить без
      фильтра. Тесты T13 зелёные.
      Сценарии: S21, S22, S23.
      Файлы: `app/src/main/java/com/mnemocards/data/db/DeckDao.kt`,
      `app/src/main/java/com/mnemocards/data/db/CardDao.kt`

- [ ] **T15. Падающий тест сидинга чистой установки** — тест на новой БД:
      сидинг создаёт 1 колоду и 5 карточек; id записей — сгенерированные UUID;
      `isChanged = true`, `revision = 1`, `isDeleted = false`; повторный прогон
      сидинга не создаёт дубликатов; в списке колод счётчик 5.
      Сценарии: S5.
      Файлы: `app/src/test/java/com/mnemocards/data/db/SeedingTest.kt`

- [ ] **T16. Доработка сидинга под новую схему** — фиксированный UUID базовой
      колоды (константа — основа идемпотентности), `UUID.randomUUID()` для
      карточек, заполнение `isChanged`/`revision` при вставке; идемпотентный
      upsert. Тесты T15 зелёные.
      Сценарии: S5.
      Файлы: `app/src/main/java/com/mnemocards/data/` (сидинг, по карте проекта)

- [ ] **T17. Падающий тест ViewModel и навигации** — тесты ReviewViewModel
      (fake repository): валидный UUID-аргумент — колода загружается, сессия
      работает как раньше; невалидная строка («1», «abc») — исключение не
      пробрасывается, терминальное/пустое состояние; несуществующий UUID —
      ведёт себя как отсутствующая колода; во время открытой сессии Flow
      эмитит список без карточек (колоду мягко удалили) — сессия корректно
      завершается терминальным состоянием без краха. Тест DecksViewModel:
      repository кидает ошибку БД — состояние ошибки, повтор попытки доступен.
      Сценарии: S7, S8, S9, S25, S26.
      Файлы: `app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt`,
      `app/src/test/java/com/mnemocards/ui/decks/DecksViewModelTest.kt`

- [ ] **T18. Строковые аргументы навигации и ViewModel** — маршрут тренировки
      `review/{deckId}` с аргументом типа String в существующем
      `MnemoCardsNavHost` (второй NavHost не заводить); ViewModel принимает
      String, валидирует формат UUID — невалид ведёт к терминальному состоянию,
      не к исключению; при пустом списке из Flow сессия завершается
      терминально. Внешний вид экранов не меняется: оформление `ReviewScreen.kt`
      переносить целиком по конвенциям MAIN.md, компоненты не выносить;
      элементов управления флагом синхронизации в UI нет. Тесты T17 зелёные.
      Сценарии: S7, S8, S9, S15, S25, S26.
      Файлы: `app/src/main/java/com/mnemocards/ui/MnemoCardsNavHost.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt`,
      `app/src/main/java/com/mnemocards/ui/decks/DecksViewModel.kt`

- [ ] **T19. Финальная проверка и регрессия** — прогнать весь тестовый набор;
      ручной чек-лист: обновление поверх версии с данными (миграция S1),
      чистая установка (S5), авиарежим, тёмная тема (`MnemoCardsTheme`
      сохранена), вёрстка экрана тренировки не изменена; проверить инварианты
      MAIN.md (один NavHost, один AppContainer, нет дублей сущностей, Room
      не покидает пакет `data`); обновить заметку в capabilities MAIN.md
      (groundwork `sync.background`).
      Сценарии: S1–S26.
      Файлы: `MAIN.md`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
