# Tasks: Управление колодами

> **Change ID:** `add-deck-management`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера. Тестовая база: JUnit + Robolectric (Room на JVM) + kotlinx-coroutines-test + Compose UI Test; MockWebServer не используется — изменение полностью локальное, сетевой слой не затрагивается. Существующие классы (`DeckEntity`, `CardEntity`, `DeckDao`, `MnemoCardsDatabase`, сидер, `DeckRepository`, `DeckListViewModel`, `DeckListScreen`) переиспользуются — параллельные дубликаты не создаются. Ритм работы: красный тест → реализация → зелёный тест.

## Реализация

- [ ] **T1. Падающий тест — мутации DeckDao** — подключить в `app/build.gradle.kts` тест-инфраструктуру, если её нет (Robolectric, room-testing, kotlinx-coroutines-test, compose ui-test + ui-test-manifest). Написать `DeckDaoTest` на in-memory Room: (а) вставка колоды → появилась строка в таблице, возвращён id; (б) переименование по id — обновлена та же строка, новых строк нет, карточки сохранили привязку; (в) удаление колоды удаляет все её карточки, карточки других колод не затронуты; (г) две колоды с одинаковым названием сосуществуют, удаление одной не влияет на другую. Запустить `./gradlew testDebugUnitTest` — падает: методов мутаций в DAO нет.
      Сценарии: S1, S4, S8, S12, S20. Файлы: `app/build.gradle.kts`, `app/src/test/java/com/mnemocards/data/DeckDaoTest.kt`

- [ ] **T2. Реализация мутаций DeckDao** — добавить в `DeckDao`: `suspend insertDeck(deck: DeckEntity): Long`; `suspend renameDeck(id: Long, name: String)` (UPDATE по id); `@Transaction suspend deleteDeckWithCards(id: Long)` — внутри два `@Query`-удаления (карточки по deckId, затем колода по id). Имена таблиц и колонок взять из текущих `DeckEntity`/`CardEntity`; схему и версию `MnemoCardsDatabase` не менять. Прогнать тесты T1 — зелёные.
      Сценарии: S1, S4, S8, S12, S20. Файлы: `app/src/main/java/com/mnemocards/data/DeckDao.kt`

- [ ] **T3. Падающий тест — одноразовый сидинг** — написать `DeckSeederTest`: первый запуск сидинга создаёт базовую колоду с 5 карточками; после удаления этой колоды повторный запуск сидинга (эмуляция перезапуска приложения) не создаёт её заново; при 0 колод в базе сидинг не восстанавливает список. Запустить — падает: текущий сидинг идемпотентен по названию и пересоздаёт колоду.
      Сценарии: S14, S15, S19. Файлы: `app/src/test/java/com/mnemocards/data/DeckSeederTest.kt`

- [ ] **T4. Одноразовый сидинг** — заменить в существующем сидере проверку «есть ли колода с таким названием» на одноразовый флаг (SharedPreferences/DataStore, например `seeding_done`), выставляемый после первого выполнения. Базовая колода остаётся обычной записью без спецпризнаков и спецзащиты — с ней работают те же операции. Тесты T3 зелёные.
      Сценарии: S14, S15, S19. Файлы: `app/src/main/java/com/mnemocards/data/DeckSeeder.kt`, при необходимости `app/src/main/java/com/mnemocards/MnemoCardsApplication.kt`

- [ ] **T5. Падающий тест — операции DeckRepository** — написать `RoomDeckRepositoryTest` (in-memory Room; для сбоя — фейковый DAO, бросающий RuntimeException): `createDeck("Английские глаголы")` пишет строку и возвращает id; `renameDeck(id, name)` обновляет существующую запись; `deleteDeck(id)` удаляет колоду и её карточки; `renameDeck`/`deleteDeck` с несуществующим id — безопасный no-op без исключений и «призрачных» записей; исключение DAO пробрасывается наружу. Падает — методов в репозитории нет.
      Сценарии: S1, S8, S12, S21, S22. Файлы: `app/src/test/java/com/mnemocards/data/RoomDeckRepositoryTest.kt`

- [ ] **T6. Реализация DeckRepository** — расширить интерфейс и реализацию: `suspend createDeck(name: String): Long` (название обрезается trim), `suspend renameDeck(id: Long, name: String)`, `suspend deleteDeck(id: Long)`; ошибки БД пробрасываются наружу; отсутствующий id — no-op. Обновить DI в `AppContainer`, если сигнатуры изменились. Тесты T5 зелёные.
      Сценарии: S1, S8, S12, S21, S22. Файлы: `app/src/main/java/com/mnemocards/domain/DeckRepository.kt`, `app/src/main/java/com/mnemocards/data/RoomDeckRepository.kt`, `app/src/main/java/com/mnemocards/AppContainer.kt`

- [ ] **T7. Интеграционный тест персистентности** — написать `PersistenceIntegrationTest` на файловой Room (Robolectric, временный каталог): создать колоду «A», переименовать «B» → «B2», удалить «C»; закрыть БД, переоткрыть — состояние идентично (A есть, B2 есть, C нет, счётчики совпадают); повторный сидинг после переоткрытия не воскрешает удалённую базовую колоду; новая колода с названием удалённой создаётся пустой (0 карточек). Если тест красный — исправить слой данных; если зелёный — зафиксировать как регрессионный.
      Сценарии: S18, S19, S20. Файлы: `app/src/test/java/com/mnemocards/data/PersistenceIntegrationTest.kt`

- [ ] **T8. Падающий тест — ViewModel: создание и валидация** — написать `DeckListViewModelTest` (coroutines-test, фейковый `DeckRepository`): валидное название → вызван `createDeck`, диалог закрыт; пустое и только из пробелов название → в состоянии ошибка «Название не может быть пустым», `createDeck` НЕ вызван, диалог открыт; дубликат существующего названия → создание разрешено; название 100+ символов → валидацию проходит. Падает — состояний и методов нет.
      Сценарии: S1, S2, S3, S4, S5. Файлы: `app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt`

- [ ] **T9. ViewModel: создание колоды** — в `DeckListViewModel` добавить состояние диалога создания (флаг открытия, текст поля, флаг ошибки) и методы `openCreateDialog / dismissCreateDialog / createDeck`; валидация: `name.trim().isBlank()` → ошибка, иначе `repository.createDeck(обрезанное название)`; ошибки репозитория ловятся в состояние ошибки, приложение не падает. Тесты T8 зелёные.
      Сценарии: S1, S2, S3, S4, S5. Файлы: `app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt`

- [ ] **T10. Падающий тест — ViewModel: переименование, удаление, отмена, ошибки** — дополнить `DeckListViewModelTest`: долгое нажатие → состояние «выбранная колода», закрытие меню без выбора ничего не меняет; переименование → `renameDeck(id, название)`, счётчик не меняется; пустое название при переименовании → ошибка, репозиторий не вызван; «Отмена» → вызовов нет; сохранение без изменений → без ошибки; подтверждение удаления → вызван `deleteDeck`, отмена → не вызван; пустой список → флаг пустого состояния; ошибка репозитория → сообщение об ошибке, состояние консистентно; действие над уже отсутствующим id → без исключения. Падает.
      Сценарии: S6, S7, S8, S9, S10, S11, S12, S13, S16, S21, S22. Файлы: `app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt`

- [ ] **T11. ViewModel: переименование и удаление** — реализовать: состояние выбранной колоды для меню и диалогов (long-press → выбранная, закрытие меню → сброс); `openRenameDialog / dismissRenameDialog / renameDeck` с той же валидацией и предзаполнением текущим названием; `openDeleteDialog / dismissDeleteDialog / deleteDeck`; catch ошибок репозитория → `errorMessage`; список колод остаётся Flow из репозитория, поэтому устаревший элемент исчезает автоматически, no-op по отсутствующему id безопасен. Никаких специальных веток для базовой колоды. Тесты T10 зелёные.
      Сценарии: S6, S7, S8, S9, S10, S11, S12, S13, S14, S15, S16, S21, S22. Файлы: `app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt`

- [ ] **T12. Падающий тест — UI: создание колоды** — написать `DeckListScreenTest` (Robolectric + compose-ui-test, экран с фейковым репозиторием внутри `MnemoCardsTheme`): кнопка добавления открывает форму с полем и кнопками «Сохранить»/«Отмена»; ввод валидного названия и сохранение → элемент появился в списке со счётчиком «0 карточек»; сохранение с пустым полем → показан текст ошибки, форма остаётся открытой; «Отмена» закрывает форму без изменений. Падает — UI-элементов нет.
      Сценарии: S1, S2, S3. Файлы: `app/src/test/java/com/mnemocards/ui/decklist/DeckListScreenTest.kt`

- [ ] **T13. UI: кнопка добавления и диалог названия** — в `DeckListScreen` добавить кнопку добавления колоды (FAB или действие в TopAppBar — по текущему макету экрана); создать composable `DeckNameDialog` (TextField, «Сохранить»/«Отмена», показ ошибки валидации, поддержка предзаполнения для будущего переименования); в пункте списка название выводить с `maxLines = 1` и `overflow = TextOverflow.Ellipsis`. Подключить к `DeckListViewModel`; цвета только из `MaterialTheme.colorScheme`, обёртка `MnemoCardsTheme` сохранена. Тесты T12 зелёные.
      Сценарии: S1, S2, S3, S4, S5. Файлы: `app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt`, `app/src/main/java/com/mnemocards/ui/decklist/DeckNameDialog.kt`

- [ ] **T14. Падающий тест — UI: меню, переименование, удаление** — дополнить `DeckListScreenTest`: `performTouchInput { longClick() }` по элементу списка → меню ровно с двумя пунктами «Переименовать» и «Удалить»; закрытие меню (тап вне / «назад») — состояние без изменений; «Переименовать» → форма с текущим названием в поле; сохранение нового названия → текст в списке обновился, счётчик прежний; пустое название в форме → ошибка; «Удалить» → диалог подтверждения; подтверждение → элемент исчез из списка; отмена → остался. Падает.
      Сценарии: S6, S7, S8, S9, S12, S13. Файлы: `app/src/test/java/com/mnemocards/ui/decklist/DeckListScreenTest.kt`

- [ ] **T15. UI: контекстное меню и диалоги** — реализовать: long press через `combinedClickable(onLongClick = ...)`; `DropdownMenu` ровно с двумя `DropdownMenuItem` («Переименовать», «Удалить»), закрытие по тапу вне и системной кнопке «назад»; переименование — переиспользовать `DeckNameDialog` с предзаполнением; удаление — `AlertDialog`-подтверждение («Удалить»/«Отмена», тап вне диалога = отмена); показ ошибки из состояния ViewModel (Snackbar или текст на экране). Никаких особых веток для базовой колоды. Тесты T14 зелёные.
      Сценарии: S6, S7, S8, S9, S10, S11, S12, S13, S14, S15, S22. Файлы: `app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt`, `app/src/main/java/com/mnemocards/ui/decklist/DeleteDeckDialog.kt`

- [ ] **T16. Падающий тест — UI: пустое состояние** — дополнить `DeckListScreenTest`: пустой список → показаны текст «Пока нет колод» и кнопка «Создать первую колоду»; кнопка открывает форму создания; сохранение непустого названия → колода появилась в списке, пустое состояние исчезло; пустое название по-прежнему отклоняется. Падает.
      Сценарии: S16, S17. Файлы: `app/src/test/java/com/mnemocards/ui/decklist/DeckListScreenTest.kt`

- [ ] **T17. UI: пустое состояние** — в `DeckListScreen` добавить ветку пустого списка (когда Flow из репозитория пуст): сообщение «Пока нет колод» и кнопка «Создать первую колоду» → `openCreateDialog` (та же форма создания, те же правила валидации). Тесты T16 зелёные.
      Сценарии: S16, S17. Файлы: `app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt`

- [ ] **T18. Финальная проверка и документация** — прогнать `./gradlew assembleDebug testDebugUnitTest` — всё зелёное; пройти чек-лист S1–S22 из spec-bdd.md на эмуляторе (включая авиарежим — фича полностью офлайн); обновить `MAIN.md`: в секции Capabilities добавить `deck.management` со статусом «Реализована» (change `add-deck-management`, дата, краткое описание); закоммитить по Conventional Commits (`feat: управление колодами — создание, переименование, удаление`).
      Сценарии: S1–S22. Файлы: `MAIN.md`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
