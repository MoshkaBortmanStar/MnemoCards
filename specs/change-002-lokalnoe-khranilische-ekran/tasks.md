# Tasks: Локальное хранилище и список колод

> **Change ID:** `add-offline-storage`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера.

Контекст: стек Kotlin + Jetpack Compose + Room + Coroutines/Flow, MVVM + UDF, базовый пакет `com.mnemocards` со слоями `ui` / `domain` / `data`. Тесты — JUnit + Robolectric (Room и Compose тестируются в `app/src/test`), Turbine для Flow. Сеть (Retrofit) в этой фече не используется. Существующий код `cards.review` (`ui/review/ReviewScreen.kt`, `ui/review/ReviewViewModel.kt`) содержит захардкоженную колоду из 5 карточек — рефакторится в T10–T11.

## Реализация

- [ ] **T1. Падающий тест — сидинг БД и персистентность данных** — создать Robolectric-тест с in-memory Room: (а) после первого вызова сидинга в БД ровно одна колода с 5 карточками; (б) повторный вызов сидинга не создаёт дублей — колода одна, карточек по-прежнему 5; (в) запрос списка колод отдаёт название колоды и счётчик карточек «5». Добавить test-зависимости (robolectric, room-testing, turbine, coroutines-test, compose-ui-test). Запустить тест, убедиться, что он падает (Room-классов ещё нет).
      Сценарии: S1, S2, S3. Файлы: `app/src/test/java/com/mnemocards/data/db/DatabaseSeederTest.kt`, `app/build.gradle.kts`

- [ ] **T2. Room-схема: сущности, DAO, база данных** — реализовать `DeckEntity(id, name)`, `CardEntity(id, deckId, front, back)` с FK на колоду и индексом по `deckId`; `MnemoCarsDatabase` (version 1, `exportSchema = false`, имя файла `mnemocards.db`); `DeckDao`: транзакционная вставка колоды с карточками, `observeDecksWithCount` (LEFT JOIN + COUNT, группировка по колоде), `getDeckById`, `observeCardsByDeckId(deckId)`, `cardCountForDeck(deckId)`. Подключить Room-компилятор (KSP/kapt) в `app/build.gradle.kts`. Пункт (в) теста T1 зеленеет.
      Сценарии: S1, S2, S3, S5, S8, S12. Файлы: `app/src/main/java/com/mnemocards/data/db/DeckEntity.kt`, `app/src/main/java/com/mnemocards/data/db/CardEntity.kt`, `app/src/main/java/com/mnemocards/data/db/DeckDao.kt`, `app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt`, `app/build.gradle.kts`

- [ ] **T3. Сидинг при первом запуске** — реализовать `DatabaseSeeder`: если таблица колод пуста, транзакционно вставить базовую колоду с теми же 5 парами «слово + перевод», что в текущей захардкоженной колоде; сидинг идемпотентен (guard по пустоте таблицы). Создать `MnemoCardsApplication` и `AppContainer` (ручной DI: singleton БД → seeder → repository), вызвать сидинг в `Application.onCreate` в фоновом корутине. Зарегистрировать Application в манифесте. Тест T1 зелёный полностью.
      Сценарии: S1, S2, S11. Файлы: `app/src/main/java/com/mnemocards/data/db/DatabaseSeeder.kt`, `app/src/main/java/com/mnemocards/MnemoCardsApplication.kt`, `app/src/main/java/com/mnemocards/AppContainer.kt`, `app/src/main/AndroidManifest.xml`

- [ ] **T4. Падающий тест — DeckRepository** — тест репозитория на in-memory Room: `observeDecks()` эмитит список колод (id, название, счётчик карточек); `observeCards(deckId)` возвращает карточки только выбранной колоды; при исключении БД (например, закрытая БД) наружу отдаётся ошибочный результат, а не необработанный крах. Запустить, убедиться в падении.
      Сценарии: S3, S5, S9. Файлы: `app/src/test/java/com/mnemocards/data/repository/DeckRepositoryImplTest.kt`

- [ ] **T5. Реализация DeckRepository и доменных моделей** — модели `Deck(id, name, cardCount)` и `ReviewCard(id, front, back)` в `domain/model`; интерфейс `DeckRepository` в `domain/repository`; имплементация над `DeckDao`: маппинг entity → domain, оборачивание исключений Room (включая ошибку открытия БД) в ошибочное состояние (`runCatching` / catch на Flow). Зарегистрировать в `AppContainer`. Тест T4 зелёный.
      Сценарии: S3, S5, S8, S9. Файлы: `app/src/main/java/com/mnemocards/domain/model/Deck.kt`, `app/src/main/java/com/mnemocards/domain/model/ReviewCard.kt`, `app/src/main/java/com/mnemocards/domain/repository/DeckRepository.kt`, `app/src/main/java/com/mnemocards/data/repository/DeckRepositoryImpl.kt`, `app/src/main/java/com/mnemocards/AppContainer.kt`

- [ ] **T6. Падающий тест — DeckListViewModel (состояния экрана)** — тест с фейковым `DeckRepository` (Turbine): старт → `Loading`; появление данных → `Content` со списком «название + счётчик»; пустая БД → `Empty`; исключение репозитория → `Error` с доступным действием «повторить»; повтор после ошибки → `Content`. Запустить, убедиться в падении.
      Сценарии: S3, S7, S9, S11. Файлы: `app/src/test/java/com/mnemocards/ui/decks/DeckListViewModelTest.kt`

- [ ] **T7. Реализация DeckListViewModel** — sealed interface `DeckListUiState { Loading, Content(decks), Empty, Error }` в `StateFlow`; подписка на `repository.observeDecks()`; `retry()` переподписывается на Flow; `onDeckClick(deckId)` формирует событие навигации. Состояние живёт во ViewModel — переживает смену конфигурации без повторного сидинга. Тест T6 зелёный.
      Сценарии: S3, S7, S9, S11. Файлы: `app/src/main/java/com/mnemocards/ui/decks/DeckListUiState.kt`, `app/src/main/java/com/mnemocards/ui/decks/DeckListViewModel.kt`

- [ ] **T8. Падающий UI-тест — DeckListScreen** — Compose-тест (Robolectric, createComposeRule) с фейковыми состояниями: в `Content` отрисованы название колоды и счётчик карточек; в `Empty` — заглушка «пока нет колод»; в `Error` — сообщение об ошибке и кнопка «Повторить», тап по которой вызывает retry. Запустить, убедиться в падении.
      Сценарии: S3, S7, S9. Файлы: `app/src/test/java/com/mnemocards/ui/decks/DeckListScreenTest.kt`

- [ ] **T9. Реализация DeckListScreen (Compose)** — `LazyColumn`; элемент списка: название колоды и «N карточек», clickable → `onDeckClick(deckId)`; пустое состояние — заглушка «пока нет колод»; ошибка — сообщение и кнопка «Повторить»; загрузка — индикатор. Экран stateless: состояние и колбэки приходят параметрами, бизнес-логики в Compose нет. Тест T8 зелёный.
      Сценарии: S3, S7, S9. Файлы: `app/src/main/java/com/mnemocards/ui/decks/DeckListScreen.kt`

- [ ] **T10. Падающий тест — ReviewViewModel читает колоду из БД** — переписать тест `ReviewViewModel`: сессия получает `deckId`; карточки загружаются из фейкового `DeckRepository` и только выбранной колоды; колода/карточки отсутствуют → состояние «не найдено»; полный проход сессии (перевороты, оценки, терминальный экран) не изменяет данные репозитория. Запустить, убедиться в падении.
      Сценарии: S4, S5, S8, S12. Файлы: `app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt`

- [ ] **T11. Рефакторинг ReviewViewModel и ReviewScreen на данные Room** — удалить захардкоженную колоду; `deckId` читается из `SavedStateHandle`; загрузка карточек через `DeckRepository.observeCards(deckId)`; при пустой/отсутствующей колоде — состояние `NotFound` и событие возврата на список; логика переворота, четырёх оценок и терминального экрана «на сегодня всё» не меняется. Тест T10 зелёный.
      Сценарии: S4, S5, S8, S12. Файлы: `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`, `app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt`

- [ ] **T12. Падающий навигационный тест** — Compose-тест с `TestNavHostController`: приложение открывается на экране списка колод; тап по колоде ведёт на маршрут `review/{deckId}` с корректным аргументом; системный «назад» (в том числе с терминального экрана «на сегодня всё») возвращает на список, список отрисован с теми же колодами и счётчиками. Запустить, убедиться в падении.
      Сценарии: S4, S6, S12. Файлы: `app/src/test/java/com/mnemocards/NavigationTest.kt`

- [ ] **T13. Навигация и MainActivity** — реализовать `MnemoCardsNavHost`: маршруты `decks` (стартовый) и `review/{deckId}`; `MainActivity` рендерит NavHost; ViewModel-фабрики получают зависимости из `AppContainer`, `ReviewViewModel` создаётся с `deckId` из аргументов маршрута; системная кнопка «назад» обрабатывается NavController; список колод подписан на Flow Room и актуализируется при возврате. Тест T12 зелёный.
      Сценарии: S3, S4, S6. Файлы: `app/src/main/java/com/mnemocards/ui/MnemoCardsNavHost.kt`, `app/src/main/java/com/mnemocards/MainActivity.kt`

- [ ] **T14. Краевой случай — тап по устаревшей колоде (аналог 404)** — дополнить навигационный тест: элемент списка, чья колода к моменту тапа отсутствует в БД, не открывает тренировку на пустом наборе — пользователь остаётся на списке, список перечитывается из Room, крах исключён. Реализовать обработку: событие `NotFound` в `ReviewViewModel` → `popBackStack` в `ReviewScreen`.
      Сценарии: S8. Файлы: `app/src/test/java/com/mnemocards/NavigationTest.kt`, `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`, `app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt`

- [ ] **T15. Краевой случай — ошибка БД сквозным сценарием (аналог 500)** — тест: провайдер БД/репозиторий бросает исключение при открытии → стартовый экран показывает состояние ошибки с сообщением и кнопкой «Повторить», пустые или частичные данные не отображаются как валидные; повторная попытка после восстановления → `Content`. При необходимости усилить обработку ошибок в `DeckRepositoryImpl` / `DeckListViewModel`.
      Сценарии: S9. Файлы: `app/src/test/java/com/mnemocards/ui/decks/DeckListViewModelTest.kt`, `app/src/main/java/com/mnemocards/data/repository/DeckRepositoryImpl.kt`, `app/src/main/java/com/mnemocards/ui/decks/DeckListViewModel.kt`

- [ ] **T16. Верификация офлайн-работы, конфигурационных изменений и консистентности счётчика** — тесты и проверки: (S10) поиском по коду убедиться, что фича не обращается к Retrofit/сети — все сценарии работают без сетевых вызовов и сообщений о сети; (S11) повторная инициализация (пересоздание Activity/повторный вызов сидинга при существующих данных) не дублирует колоду — данные перечитываются из Room; (S12) после полной сессии из 5 карточек запрос счётчика возвращает 5, список показывает «5».
      Сценарии: S10, S11, S12. Файлы: `app/src/test/java/com/mnemocards/data/db/DatabaseSeederTest.kt`, `app/src/test/java/com/mnemocards/data/repository/DeckRepositoryImplTest.kt`, `app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt`

- [ ] **T17. Финализация — полный прогон и обновление документации** — запустить `./gradlew test`, убедиться, что все тесты зелёные и покрывают S1–S12; обновить MAIN.md: пометить capability `offline.storage` как реализованную (change `add-offline-storage`, дата), дополнить описание `cards.review` (данные читаются из Room, стартовый экран — список колод); коммиты — по Conventional Commits (`feat:`, `test:`, `chore:`, `docs:`).
      Сценарии: S1–S12. Файлы: `MAIN.md`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
