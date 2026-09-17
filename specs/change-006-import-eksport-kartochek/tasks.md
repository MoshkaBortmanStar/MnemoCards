# Tasks: Импорт и экспорт карточек колоды через CSV

> **Change ID:** `add-csv-import-export`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера. Порядок соответствует зависимостям: чистая логика CSV (JUnit) → Room/репозиторий → файловый шлюз SAF → ViewModel → UI/навигация. Перед созданием новых классов сверяться с картой проекта: дубликаты сущностей и второй NavHost запрещены.

## Реализация

- [ ] **T1. Падающий тест: формат CSV-экспорта (красная фаза)** — создать чистый
      JUnit-тест `CsvEncoder` с API `encode(rows: List<Pair<String, String>>): ByteArray`.
      Кейсы: 3 карточки → байты начинаются с `EF BB BF`, первая строка — заголовок
      `front,back`, далее 3 строки с разделителем `,` и завершением CRLF; кириллица
      кодируется в UTF-8; значения с `,`, `"` и переносом строки обёрнуты в кавычки,
      внутренние `"` удвоены; каждая строка данных содержит ровно 2 колонки (без id и
      SRS-полей); пустой список → файл из одного заголовка с BOM. Запустить
      `./gradlew test` — падение (класс не существует) — ожидаемое состояние.
      Сценарии: S2, S3, S4, S5, S6. Файлы: `app/src/test/java/com/mnemocards/data/csv/CsvEncoderTest.kt`

- [ ] **T2. Реализация CsvEncoder (зелёная фаза)** — реализовать кодировщик:
      заголовок `front,back`, разделитель `,`, CRLF, экранирование по RFC 4180
      (поле с `,`/`"`/переводом строки — в кавычках, `"` удваивается), результат —
      UTF-8 байты с BOM. Запустить тест T1, добиться зелёного.
      Сценарии: S2, S3, S4, S5, S6. Файлы: `app/src/main/java/com/mnemocards/data/csv/CsvEncoder.kt`

- [ ] **T3. Падающий тест: парсер CSV (красная фаза)** — тест `CsvParser` с API
      `parse(bytes: ByteArray): CsvParseResult`, где `CsvParseResult` — sealed:
      `Parsed(cards: List<Pair<String, String>>, skipped: Int)` | `NotCsv`.
      Кейсы: BOM + заголовок `FRONT,BACK` (любой регистр) пропускается и не попадает
      в итог; файл без заголовка — первая строка становится карточкой; строки с пустой
      лицевой стороной / пустым переводом / из пробелов — пропущены и посчитаны;
      `"hello, world","скажи ""привет"""` и значение с переносом строки внутри кавычек
      парсятся как одно поле; файл с `;` (`front;back`, `"hello, world";привет`) —
      разделитель определён по первой строке, при равенстве счёта `,` и `;` побеждает
      запятая; строка из 1 колонки — пропущена, из 3 колонок — взяты первые две;
      бинарные данные и битая UTF-8 → `NotCsv`; файл 0 байт и файл из одного BOM →
      `NotCsv`; строки с LF принимаются. Запустить — падение.
      Сценарии: S10, S11, S12, S13, S14, S17, S18, S19. Файлы: `app/src/test/java/com/mnemocards/data/csv/CsvParserTest.kt`

- [ ] **T4. Реализация CsvParser (зелёная фаза)** — конечный автомат по RFC 4180:
      строгая UTF-8-декодация (`CodingErrorAction.REPORT`, ошибка → `NotCsv`), пропуск
      BOM, автодетект разделителя подсчётом `,` и `;` в первой строке (после снятия
      BOM, ничья → запятая), пропуск заголовка при совпадении колонок с `front`/`back`
      без учёта регистра, обрезка пробелов, пропуск строк с пустой лицевой стороной или
      переводом (учёт в `skipped`), лишние колонки игнорируются. Добиться зелёного.
      Сценарии: S10, S11, S12, S13, S14, S17, S18, S19.
      Файлы: `app/src/main/java/com/mnemocards/data/csv/CsvParser.kt`, `app/src/main/java/com/mnemocards/data/csv/CsvParseResult.kt`

- [ ] **T5. Раунд-трип тест кодека** — тест: `encode` списка пар (кириллица, запятые,
      кавычки, переносы строк, `;` внутри значений) → `parse` → исходные пары без
      потерь; то же для варианта с заменой CRLF на LF; кодек не переносит идентификаторы
      и SRS-данные. При падении — исправить кодек в этой же задаче.
      Сценарии: S25, S5, S13. Файлы: `app/src/test/java/com/mnemocards/data/csv/CsvRoundTripTest.kt`

- [ ] **T6. Падающий тест: репозиторий импорта/экспорта (красная фаза)** — добавить
      тестовые зависимости (`testImplementation`: `org.robolectric:robolectric`,
      `androidx.test:core-ktx`, `kotlinx-coroutines-test`). Написать тест
      репозитория на Robolectric + in-memory `MnemoCardsDatabase`: вставка 4 черновиков
      в колоду из 5 карточек → счётчик через Flow меняется 5 → 9, существующие карточки
      не изменены; дубликат «cat»/«кошка» добавляется как новая карточка без
      дедупликации; импортированные карточки попадают в существующую выборку карточек
      для тренировки и имеют состояние новых (как при ручном создании); несуществующий
      `deckId` → исход `DeckNotFound`, ничего не вставлено; DAO-фейк, бросающий
      исключение на вставке → ошибка наружу, колода без изменений (атомарность);
      `exportCardTexts` возвращает только тексты сторон. Запустить — падение.
      Сценарии: S5, S9, S15, S16, S21, S22.
      Файлы: `app/src/test/java/com/mnemocards/data/repository/DeckContentRepositoryTest.kt`, `app/build.gradle.kts`

- [ ] **T7. Реализация: DAO и репозиторий колоды (зелёная фаза)** — расширить
      существующие `CardDao` и `DeckDao` (новые сущности не создавать):
      `CardDao` — `insertCards` и `@Transaction importCards(deckId, cards)` с проверкой
      существования колоды, `observeCardsByDeck(deckId): Flow<List<CardEntity>>`;
      `DeckDao` — `observeDeck(deckId): Flow<DeckEntity?>`, `getDeckById(deckId)`.
      Создать доменные модели `CardDraft(front, back)` и `CsvImportSummary(added, skipped)`
      в `domain/model`. Создать `DeckContentRepository` (или расширить существующий
      репозиторий колод, если роль совпадает — дубликат не заводить):
      `observeDeckContent(deckId): Flow<DeckContent?>`, `exportCardTexts(deckId):
      List<Pair<String, String>>?` (null → колода не найдена), `importCards(deckId,
      List<CardDraft>): ImportOutcome` (`Imported(summary)` | `DeckNotFound`), ошибка БД
      пробрасывается. Новые `CardEntity` создаются с дефолтами новых карточек.
      Сценарии: S5, S9, S15, S16, S21, S22.
      Файлы: `app/src/main/java/com/mnemocards/data/db/CardDao.kt` (сущ.), `app/src/main/java/com/mnemocards/data/db/DeckDao.kt` (сущ.), `app/src/main/java/com/mnemocards/domain/model/CardDraft.kt`, `app/src/main/java/com/mnemocards/domain/model/CsvImportSummary.kt`, `app/src/main/java/com/mnemocards/data/repository/DeckContentRepository.kt`

- [ ] **T8. Падающий тест: файловый шлюз SAF (красная фаза)** — Robolectric-тест
      (`ShadowContentResolver`): `write(uri, bytes)` открывает поток по Uri и пишет
      байты; `read(uri)` возвращает записанные байты; при ошибке потока записи
      выбрасывается `IOException`, шлюз пытается удалить частично созданный документ.
      Запустить — падение.
      Сценарии: S2, S8, S9. Файлы: `app/src/test/java/com/mnemocards/data/file/SafCsvFileGatewayTest.kt`

- [ ] **T9. Реализация CsvFileGateway (зелёная фаза)** — интерфейс `CsvFileGateway`
      (`suspend write(uri: Uri, bytes: ByteArray)`, `suspend read(uri: Uri):
      ByteArray`) и реализация `SafCsvFileGateway` через `ContentResolver`
      (`openOutputStream(uri, "wt")` / `openInputStream`), выполнение на
      `Dispatchers.IO`; при сбое записи — best-effort удаление Uri через
      `DocumentsContract.deleteDocument` и проброс исключения (частичный файл не
      остаётся). Разрешения хранилища не запрашиваются.
      Сценарии: S2, S8, S9, S24.
      Файлы: `app/src/main/java/com/mnemocards/data/file/CsvFileGateway.kt`, `app/src/main/java/com/mnemocards/data/file/SafCsvFileGateway.kt`

- [ ] **T10. Падающий тест: DeckDetailViewModel (красная фаза)** — тест на
      `kotlinx-coroutines-test` с фейками репозитория и шлюза: начальное состояние
      (колода, карточки, счётчик) приходит из Flow репозитория; `onExportTarget(uri)` —
      пишет `CsvEncoder.encode(exportCardTexts(...))` через шлюз и показывает
      «Файл сохранён» (в т.ч. для пустой колоды); при ошибке записи — «Не удалось
      сохранить файл», состояние колоды прежнее; `onImportFile(uri)` — читает файл,
      парсит, импортирует, показывает «Добавлено: N, пропущено: M»; `NotCsv` → «Файл
      не является CSV или не читается», вставки нет; `DeckNotFound` → «Колода не
      найдена»; исключение БД → понятная ошибка без изменения колоды; без события
      от SAF состояние не меняется (отмена диалога); все операции уходят с Main через
      внедрённый диспетчер. Запустить — падение.
      Сценарии: S1, S2, S6, S7, S8, S9, S12, S15, S18, S19, S21, S22, S23.
      Файлы: `app/src/test/java/com/mnemocards/ui/deckdetail/DeckDetailViewModelTest.kt`

- [ ] **T11. Реализация DeckDetailViewModel (зелёная фаза)** — `StateFlow<DeckDetailUiState>`:
      имя колоды, список карточек, счётчик, флаг занятости и `transferMessage`
      (`Idle | Saved | Summary(added, skipped) | Error(text)`); методы
      `onExportTarget(uri)` и `onImportFile(uri)`; конвейер импорта
      «чтение → парсинг → вставка» выполняется в `viewModelScope` вне Main-диспетчера,
      сообщение об ошибке парсинга показывается до обращения к Room; тексты сообщений
      вынести в `strings.xml`. Добиться зелёного.
      Сценарии: S2, S6, S8, S9, S12, S18, S19, S21, S22, S23.
      Файлы: `app/src/main/java/com/mnemocards/ui/deckdetail/DeckDetailViewModel.kt`, `app/src/main/res/values/strings.xml`

- [ ] **T12. Экран содержимого колоды** — composable `DeckDetailScreen`: `TopAppBar`
      с названием колоды, счётчик карточек, список карточек «front — back», кнопки
      «Экспорт» и «Импорт», кнопка «Тренировать» → маршрут тренировки. Лаунчеры:
      `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"))`
      с предложенным именем `${deckName}.csv` и `ActivityResultContracts.OpenDocument()`
      с `arrayOf("text/csv")`; результат `null` (отмена диалога) — ничего не делать;
      сообщения из UiState — через Snackbar; только токены `MnemoCardsTheme`, прямые
      `Color(0xFF...)` запрещены; экран без бизнес-логики.
      Сценарии: S1, S2, S7, S9, S20, S23, S24.
      Файлы: `app/src/main/java/com/mnemocards/ui/deckdetail/DeckDetailScreen.kt`, `app/src/main/res/values/strings.xml`

- [ ] **T13. Подключение навигации** — добавить маршрут `deck/{deckId}` в существующий
      `MnemoCardsNavHost` (второй навигационный граф не создавать); клик по колоде
      в списке открывает экран колоды вместо прямого перехода на тренировку; «Тренировать»
      ведёт на существующий маршрут тренировки, системный «назад» работает. Оформление
      `ReviewScreen` и существующих экранов не менять.
      Сценарии: S1, S9, S16. Файлы: `app/src/main/java/com/mnemocards/ui/navigation/MnemoCardsNavHost.kt` (сущ.), `app/src/main/java/com/mnemocards/ui/decks/DecksScreen.kt` (сущ.), `app/src/main/java/com/mnemocards/ui/decks/DecksViewModel.kt` (сущ., при необходимости)

- [ ] **T14. Финальная проверка и QA** — убедиться, что в `AndroidManifest.xml` нет
      разрешений на хранилище; прогнать `./gradlew test` целиком; ручная проверка:
      экспорт/импорт через системные диалоги в авиарежиме, открытие файла с кириллицей
      в Excel, импорт файла на 1000 строк без ANR, экран тренировки визуально не
      изменился, отмена диалогов ничего не меняет.
      Сценарии: S1–S25 (приёмка). Файлы: `app/src/main/AndroidManifest.xml` (только проверка, изменений нет)

- [ ] **T15. Обновление мейн-спеки** — в `MAIN.md` отметить capability
      `deck.csv-transfer` как реализованную (change `add-csv-import-export`,
      дата, краткая сводка: SAF, UTF-8 BOM, RFC 4180, автодетект разделителя,
      add-only импорт).
      Сценарии: S1–S25 (документирование). Файлы: `MAIN.md`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
