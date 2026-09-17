# Tasks: Управление карточками внутри колоды

> **Change ID:** `add-card-management`
> **Дата:** 2026-09-17

План реализации. Каждая задача — атомарный шаг для ИИ-кодера.
Тестовая база: unit-тесты — JUnit + Robolectric (Room) + kotlinx-coroutines-test + Turbine;
UI-тесты — Compose UI Test в `androidTest`. Сеть не затрагивается.
Если реальные имена файлов отличаются от указанных — находить их по классам из MAIN.md.

## Реализация

- [ ] **T1. Падающий тест репозитория** — добавить тест-зависимости (Robolectric,
      kotlinx-coroutines-test, Turbine — только в test/androidTest, версии из каталога
      библиотек). Создать интеграционный тест репозитория карточек (Robolectric, Room
      с файлом БД во временной папке): (1) карточки колоды читаются с лицевой и обратной
      стороной; (2) добавленная карточка появляется в потоке первой; (3) update меняет
      значения, счётчик не растёт; (4) delete удаляет запись; (5) счётчик карточек колоды
      эмитит 3 → 4 → 3; (6) закрыть и переоткрыть БД — данные на месте; (7) два одинаковых
      addCard → две независимые записи. Запустить тест, убедиться: он падает
      (не компилируется — API репозитория не существует = RED).
      Сценарии: S1, S3, S5, S9, S12, S16, S19, S20, S21.
      Файлы: `app/build.gradle.kts`, `gradle/libs.versions.toml`,
      `app/src/test/java/com/mnemocards/data/CardRepositoryTest.kt`

- [ ] **T2. Поле `createdAt` + миграция БД** — в `CardEntity` добавить `createdAt: Long`
      (миллисекунды, основа порядка «новые сверху»); поднять версию
      `MnemoCardsDatabase`, написать миграцию (ALTER TABLE + заполнить существующие
      строки различными значениями, например по id); в сидинге проставлять возрастающие
      `createdAt`, сохранив идемпотентность. Тест: миграция не теряет данные посева;
      сортировка `createdAt DESC` ставит новую карточку первой.
      Сценарии: S3, S19.
      Файлы: `app/src/main/java/com/mnemocards/data/db/CardEntity.kt`,
      `app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt`,
      `app/src/main/java/com/mnemocards/data/db/Migrations.kt` (новый),
      файл сидинга БД, `app/src/test/java/com/mnemocards/data/db/MigrationTest.kt`

- [ ] **T3. CRUD в `CardDao`** — методы: `insertCard(CardEntity): Long`,
      `updateCard(CardEntity): Int` (0 при отсутствии записи — основа 404-аналога),
      `deleteCard(...)`, `observeCardsByDeck(deckId): Flow<List<CardEntity>>`
      с `ORDER BY createdAt DESC`, `getCardById(id)`. Тесты DAO: insert возвращает id;
      update меняет только целевую запись; delete удаляет; порядок убывающий; дубликаты
      сохраняются обеими записями; текст 200+ символов сохраняется целиком.
      Сценарии: S3, S5, S9, S10, S12, S16, S25.
      Файлы: `app/src/main/java/com/mnemocards/data/db/CardDao.kt`,
      `app/src/test/java/com/mnemocards/data/db/CardDaoTest.kt`

- [ ] **T4. `CardRepository`** — интерфейс + реализация на Room:
      `observeDeck(deckId): Flow<Deck?>`, `observeCards(deckId)`,
      `observeCardCount(deckId)`, `addCard(deckId, front, back)`,
      `updateCard(cardId, front, back)`, `deleteCard(cardId)`; зарегистрировать в точке
      DI/фабрике ViewModel. Задача готова, когда тест T1 зелёный.
      Сценарии: S1, S3, S5, S12, S16, S19, S20, S21.
      Файлы: `app/src/main/java/com/mnemocards/data/repository/CardRepository.kt`,
      точка DI (AppContainer / фабрики ViewModel)

- [ ] **T5. Репозиторий: 404/500-аналоги** — `updateCard`/`deleteCard` возвращают
      sealed-результат; update несуществующей записи → `NotFound`, insert
      НЕ выполняется; исключения Room в любой операции ловятся и возвращаются
      как `Error`. Тесты: update по удалённому id; фейковый DAO, бросающий исключение.
      Сценарии: S25, S26.
      Файлы: `app/src/main/java/com/mnemocards/data/repository/CardRepository.kt`,
      `app/src/test/java/com/mnemocards/data/CardRepositoryTest.kt` (добавить кейсы)

- [ ] **T6. Валидация полей (домен)** — сначала красный юнит-тест
      `validateCardInput(front, back): CardFieldErrors`: пустое одно поле → ошибка только
      у него; оба пусты → две ошибки; только пробелы/переводы строк → ошибки после trim;
      валидные значения → без ошибок; текст 200+ символов — валиден. Затем реализовать
      (trim, сообщение «Заполните поле»). Тест зелёный.
      Сценарии: S6, S7, S8, S10, S13.
      Файлы: `app/src/main/java/com/mnemocards/domain/CardFormValidator.kt`,
      `app/src/test/java/com/mnemocards/domain/CardFormValidatorTest.kt`

- [ ] **T7. Падающий тест `DeckContentViewModel`** — fake-репозиторий
      (coroutines-test + Turbine): загрузка → `Content(deckTitle, cards)`; пустой список →
      `Empty`; ошибка репозитория → `Error` с повтором; `observeDeck == null` →
      `NotFound`; `deleteCard(id)` делегируется в репозиторий. Запустить, убедиться:
      падает (класс не существует).
      Сценарии: S1, S2, S16, S24, S26.
      Файлы: `app/src/test/java/com/mnemocards/ui/deckcontent/DeckContentViewModelTest.kt`

- [ ] **T8. Реализация `DeckContentViewModel`** — `UiState`: Loading / Content(title,
      cards) / Empty / NotFound / Error(retry); combine потоков колоды и карточек;
      методы `deleteCard`, `retry`; переживающий смену конфигурации. Тест T7 зелёный.
      Сценарии: S1, S2, S16, S24, S26.
      Файлы: `app/src/main/java/com/mnemocards/ui/deckcontent/DeckContentViewModel.kt`
      (+фабрика/DI)

- [ ] **T9. Падающий тест `CardFormViewModel`** — fake-репозиторий: режим добавления —
      поля пусты; режим редактирования — предзаполнение по cardId; «Сохранить»
      с валидными данными → ровно один вызов add/update и событие закрытия; невалидные
      данные → ошибки полей, репозиторий не вызывается, введённые значения сохраняются;
      ошибка записи → форма открыта, ввод не потерян; update удалённой карточки →
      `NotFound`, insert не происходит; закрытие без сохранения → ноль вызовов
      репозитория. Запустить, убедиться: падает.
      Сценарии: S4, S5, S6, S7, S8, S11, S12, S13, S14, S25, S26.
      Файлы: `app/src/test/java/com/mnemocards/ui/cardform/CardFormViewModelTest.kt`

- [ ] **T10. Реализация `CardFormViewModel`** — состояние: front, back, ошибки полей,
      isSaving, one-shot события (Saved / Closed / NotFound / Error); загрузка карточки
      при наличии cardId; при сохранении: validate → trim → репозиторий → маппинг
      результата. Тест T9 зелёный.
      Сценарии: S4, S5, S6, S7, S8, S11, S12, S13, S14, S25, S26.
      Файлы: `app/src/main/java/com/mnemocards/ui/cardform/CardFormViewModel.kt`
      (+фабрика/DI)

- [ ] **T11. Падающий Compose-тест экрана содержимого** — androidTest на стабах
      состояния: элементы списка показывают обе стороны («hello — привет»); видна кнопка
      добавления; пустое состояние с текстом «В колоде пока нет карточек»; две
      одинаковые карточки отображаются обе; текст 200+ символов не ломает макет
      (перенос строк или многоточие). Запустить, убедиться: падает (экран не существует).
      Сценарии: S1, S2, S9, S10.
      Файлы: `app/src/androidTest/java/com/mnemocards/ui/deckcontent/DeckContentScreenTest.kt`

- [ ] **T12. Реализация `DeckContentScreen`** — Scaffold + TopAppBar (название колоды,
      стрелка назад, `containerColor = colorScheme.background`); LazyColumn, элемент
      показывает обе стороны, клик по элементу → коллбек редактирования; кнопка
      добавления карточки; состояния Empty / Error с «Повторить» / NotFound
      с «К списку колод»; список обновляется из state VM без перезагрузки (новая
      карточка — первой строкой). Только цвета из `MaterialTheme.colorScheme`,
      без прямых `Color(0xFF...)` — см. Conventions в MAIN.md. Тест T11 зелёный.
      Сценарии: S1, S2, S3, S9, S10, S24, S26.
      Файлы: `app/src/main/java/com/mnemocards/ui/deckcontent/DeckContentScreen.kt`

- [ ] **T13. Удаление с подтверждением** — сначала красный Compose-тест: действие
      удаления на элементе → диалог «Удалить карточку?» с кнопками «Удалить»/«Отмена»;
      до выбора карточка в списке; «Отмена» → карточка на месте; «Удалить» → вызов
      VM, диалог закрыт, карточка исчезла без перезагрузки; удаление последней → пустое
      состояние. Затем реализовать: иконка удаления на элементе + AlertDialog.
      Сценарии: S15, S16, S17, S18.
      Файлы: `app/src/main/java/com/mnemocards/ui/deckcontent/DeckContentScreen.kt`,
      `app/src/androidTest/java/com/mnemocards/ui/deckcontent/DeckContentScreenTest.kt`

- [ ] **T14. Падающий Compose-тест формы карточки** — androidTest: два пустых поля
      («Лицевая сторона», «Перевод») и кнопка «Сохранить»; предзаполнение в режиме
      редактирования; сохранение с пустым полем → текст «Заполните поле» у пустого поля,
      форма открыта, введённые значения на месте; только пробелы → то же; очищенное поле
      при редактировании → ошибка; системный «Назад» → возврат без изменений. Запустить,
      убедиться: падает.
      Сценарии: S4, S6, S7, S8, S11, S13, S14.
      Файлы: `app/src/androidTest/java/com/mnemocards/ui/cardform/CardFormScreenTest.kt`

- [ ] **T15. Реализация `CardFormScreen`** — два `OutlinedTextField` с isError
      и supportingText из состояния VM; кнопка «Сохранить» всегда активна, ошибки
      показываются по нажатию (допустимый вариант S6–S8); системный «Назад» — закрытие
      без сохранения; цвет/типографика из темы. Тест T14 зелёный.
      Сценарии: S4, S5, S6, S7, S8, S11, S13, S14.
      Файлы: `app/src/main/java/com/mnemocards/ui/cardform/CardFormScreen.kt`

- [ ] **T16. Навигация** — маршруты: содержимое колоды `deck/{deckId}` и форма
      `deck/{deckId}/edit?cardId={cardId}` (cardId опционален: нет — добавление,
      есть — редактирование); тап по элементу колоды → экран содержимого; на элементе
      колоды сохранить отдельную кнопку «Тренировать» → существующий экран тренировки
      (точка входа для S22/S23); при NotFound — возврат `popBackStack` (S24); корректный
      back-стек: список → содержимое → форма (S14).
      Сценарии: S1, S14, S22, S24.
      Файлы: `app/src/main/java/com/mnemocards/ui/navigation/AppNavHost.kt` (файл
      NavHost), `app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt`,
      `app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt`

- [ ] **T17. Автообновление счётчика колод** — тест: после `addCard` / `deleteCard`
      через репозиторий поток счётчика эмитит 4 (S20) и 2 (S21) без пересоздания экрана
      списка; убедиться, что запрос счётчика — реактивный Flow и `DeckListViewModel`
      подписан на него (Room — single source of truth).
      Сценарии: S20, S21.
      Файлы: `app/src/main/java/com/mnemocards/data/db/CardDao.kt` (счётчик),
      `app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt`,
      тест в `app/src/test/java/com/mnemocards/ui/decklist/`

- [ ] **T18. Интеграция с тренировкой** — тесты: после `addCard` сессия тренировки
      колоды содержит новую карточку; `ReviewViewModel` наблюдает карточки через Flow —
      изменение текста «cat» → «a cat» отражается в открытой сессии без её пересоздания.
      Если сессия сейчас читает снапшот списка — перевести на Flow (текущая карточка
      идентифицируется по id, при эмиссии обновлять текст).
      Сценарии: S22, S23.
      Файлы: `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`
      (+репозиторий тренировки), тесты ReviewViewModel

- [ ] **T19. Регрессия и персистентность** — прогнать все unit- и androidTest; ручная
      проверка S19: добавить / изменить / удалить карточку → убить процесс →
      перезапустить → результат операции сохранён, удалённая не восстанавливается;
      проверить тёмную тему на новых экранах (`MnemoCardsTheme`, отсутствие прямых
      `Color(0xFF...)`); внешний вид экрана тренировки не менялся (Conventions MAIN.md).
      Сценарии: S19 (smoke по всему набору S1–S26).
      Файлы: без новых файлов (при необходимости — точечные фиксы)

- [ ] **T20. Документация и коммиты** — обновить MAIN.md: в секции Capabilities
      у `cards.management` добавить пометку «Реализована» с указанием change
      `add-card-management` и даты; шаги задачи закоммитить по Conventional Commits
      (`feat:`, `test:`, `chore:`, `docs:`).
      Сценарии: все (S1–S26).
      Файлы: `MAIN.md`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
