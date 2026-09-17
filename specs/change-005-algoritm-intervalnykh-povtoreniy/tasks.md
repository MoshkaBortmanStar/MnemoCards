# Tasks: SM-2 — планирование повторений и интервалы на кнопках оценки

> **Change ID:** `add-sm2-scheduling`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера.

Тестовая инфраструктура: чистая логика (планировщик, форматирование, ViewModel с fake-зависимостями) — JUnit + kotlinx-coroutines-test в `app/src/test`; всё, что трогает Room (миграция, сидинг, DAO, репозиторий) — androidTest в `app/src/androidTest`. Текущее время передаётся параметром `now` (инъекция), прямые вызовы `System.currentTimeMillis()` в логике не используются.

## Реализация

- [ ] **T1. Падающий тест SM-2 планировщика** — создать параметризованный unit-тест `Sm2SchedulerTest`: для каждой из четырёх оценок на новой карточке (№0, EF=2.5) и повторяемой (№2, EF=2.5, интервал 6 дней) проверить новые EF / номер повторения / интервал / due (S1–S8); клампы EF=1.3 и EF=2.8 (S9, S10); нормализацию EF=0.9 → 1.3 до расчёта, без падения (S11); минимальный интервал graduating-оценок = 1 день (S12); карточка №1 + «Хорошо» → 6 дней (S23); функция превью для четырёх кнопок возвращает ровно те интервалы, которые применяются при оценке (S15). Запустить тест и убедиться, что он падает (класс отсутствует).
      Сценарии: S1–S12, S15, S23. Файлы: `app/src/test/java/com/mnemocards/domain/scheduler/Sm2SchedulerTest.kt`

- [ ] **T2. Реализация SM-2 планировщика** — создать `ReviewGrade` (Forgot/Hard/Good/Easy), `SchedulingState` (easinessFactor, repetitionNumber, intervalDays, dueDate) и объект `Sm2Scheduler` с методами `next(state, grade, now): SchedulingState` и `preview(state, now)` для четырёх оценок — превью и применение используют один расчёт. Правила по спеке: EF′ с клампом [1.3, 2.8]; EF < 1.3 на входе нормализуется к 1.3; «Забыл» — №0, intervalDays=0, due=now+10 мин (10-минутный сдвиг живёт только в dueDate); «Трудно»/«Хорошо»/«Легко» — №+1, интервал ≥ 1 день с округлением до целых дней; фиксированные ветки «Хорошо» №1 → 6 дней, «Легко» №0 → 4 дня. Тест T1 зелёный.
      Сценарии: S1–S12, S15, S23. Файлы: `app/src/main/java/com/mnemocards/domain/scheduler/ReviewGrade.kt`, `app/src/main/java/com/mnemocards/domain/scheduler/SchedulingState.kt`, `app/src/main/java/com/mnemocards/domain/scheduler/Sm2Scheduler.kt`

- [ ] **T3. Падающий тест форматирования интервалов** — unit-тест русской плюрализации: 1 → «1 день», 3 → «3 дня», 5 → «5 дней», 11 → «11 дней», 21 → «21 день», 10 минут → «10 мин». Убедиться, что тест падает.
      Сценарии: S13. Файлы: `app/src/test/java/com/mnemocards/ui/review/IntervalLabelTest.kt`

- [ ] **T4. Форматирование интервалов** — чистая функция `formatInterval(duration): String`: дни — с формой день/дня/дней по числительному, минуты — сокращённо «N мин». Тест T3 зелёный.
      Сценарии: S13. Файлы: `app/src/main/java/com/mnemocards/ui/review/IntervalLabel.kt`

- [ ] **T5. Падающий тест миграции Room 1→2** — androidTest на `MigrationTestHelper`: создать БД версии 1 с одной колодой и 5 карточками без полей планирования, применить `MIGRATION_1_2`, проверить: все 5 карточек сохранены; каждой присвоены EF=2.5, №0, intervalDays=0, dueDate≈now; `fallbackToDestructiveMigration` не используется. Включить экспорт схемы (`room.schemaLocation` в KSP-аргументах Gradle, JSON схемы версии 1 закоммитить). Убедиться, что тест падает.
      Сценарии: S28. Файлы: `app/src/androidTest/java/com/mnemocards/data/Migration1to2Test.kt`, `app/build.gradle.kts`, `app/schemas/com.mnemocards.data.MnemoCardsDatabase/1.json`

- [ ] **T6. Поля планирования в CardEntity + миграция** — добавить в `CardEntity` колонки `easinessFactor` (REAL, default 2.5), `repetitionNumber` (INTEGER, default 0), `intervalDays` (INTEGER, default 0), `dueDate` (INTEGER, epochMillis, default = момент миграции); поднять версию `MnemoCardsDatabase` до 2; написать `MIGRATION_1_2` через `ALTER TABLE ... ADD COLUMN`. Тест T5 зелёный.
      Сценарии: S28. Файлы: `app/src/main/java/com/mnemocards/data/CardEntity.kt`, `app/src/main/java/com/mnemocards/data/MnemoCardsDatabase.kt`, `app/src/main/java/com/mnemocards/data/Migrations.kt`

- [ ] **T7. Падающий тест сидинга с параметрами по умолчанию** — androidTest на in-memory БД: первый запуск сеет 5 карточек базовой колоды, у каждой EF=2.5, №0, intervalDays=0, dueDate=now (все 5 готовы сегодня); повторный запуск не дублирует данные (идемпотентность сохранена). Убедиться, что тест падает.
      Сценарии: S24, S29. Файлы: `app/src/androidTest/java/com/mnemocards/data/DatabaseSeederTest.kt`

- [ ] **T8. Сидинг с параметрами планирования** — при вставке карточек (сидинг и любые будущие создания) проставлять дефолты планирования EF=2.5, №0, intervalDays=0, dueDate=now — новая карточка без истории сразу попадает в отбор по сроку. Тест T7 зелёный.
      Сценарии: S24, S29. Файлы: `app/src/main/java/com/mnemocards/data/DatabaseSeeder.kt`, `app/src/main/java/com/mnemocards/data/CardEntity.kt`

- [ ] **T9. Падающий тест DAO отбора и счётчиков** — androidTest на in-memory БД: `getDueCards(deckId, now)` возвращает карточки с dueDate ≤ now (включая due ровно = now) и не возвращает будущие (S16, S22); `countDue(deckId, now): Flow<Int>` = 2 при 2 назревших из 5 (S25) и = 0 при всех будущих (S27); `updateScheduling(...)` записывает новые EF/№/интервал/due (S1). Убедиться, что тест падает.
      Сценарии: S1, S16, S17, S22, S25, S27. Файлы: `app/src/androidTest/java/com/mnemocards/data/CardDaoTest.kt`

- [ ] **T10. DAO-методы отбора, счётчика и записи** — реализовать запросы из T9 (условие `dueDate <= :now`, сортировка по dueDate), `updateScheduling(cardId, ef, repetitionNumber, intervalDays, dueDate)` и агрегат для списка колод: total и dueToday по всем колодам одним запросом (Flow). Тест T9 зелёный.
      Сценарии: S1, S16, S17, S22, S25–S27. Файлы: `app/src/main/java/com/mnemocards/data/CardDao.kt`

- [ ] **T11. Падающий тест репозитория** — androidTest: `dueCards(deckId, now)` возвращает доменные карточки с параметрами планирования; `applyGrade(card, grade, now)` записывает результат `Sm2Scheduler` в Room; `observeDecksWithCounts(now)` эмитит пары (total, dueToday) и автоматически обновляется после оценки. Расширить доменную модель `Card` полями планирования; маппинг entity↔domain внутри `data`, сущности наружу не уходят. Убедиться, что тест падает.
      Сценарии: S1–S8, S16, S22, S25, S26. Файлы: `app/src/androidTest/java/com/mnemocards/data/CardRepositoryTest.kt`

- [ ] **T12. Реализация репозитория** — методы из T11 поверх DAO и `Sm2Scheduler`. Параллельные сущности и репозитории не создаются — расширяется существующий `CardRepository` по текущему пути. Тест T11 зелёный.
      Сценарии: S1–S8, S16, S22, S25, S26. Файлы: `app/src/main/java/com/mnemocards/data/CardRepository.kt`, `app/src/main/java/com/mnemocards/domain/model/Card.kt`

- [ ] **T13. Падающий тест ReviewViewModel — построение сессии** — unit-тест с fake-репозиторием и fake-часами (coroutines-test): сессия собирается только из карточек с due ≤ now (S16); нет назревших или колода пуста → сразу состояние Done, кнопки не показываются, данные не меняются (S17, S18); колода не найдена → состояние NotFound (S32); исключение при чтении → состояние ошибки вместо сессии, без краша (S31). Убедиться, что тест падает.
      Сценарии: S16–S18, S22, S31, S32. Файлы: `app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt`

- [ ] **T14. ReviewViewModel — загрузка сессии и состояния** — `ReviewUiState`: Loading / NotFound / Error / Session(card, isFlipped, previews) / Done; очередь карточек живёт в ViewModel; загрузка через `repository.dueCards(deckId, now)` с инъекцией времени. Тест T13 зелёный.
      Сценарии: S16–S18, S31, S32. Файлы: `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`, `app/src/main/java/com/mnemocards/ui/review/ReviewUiState.kt`

- [ ] **T15. Падающий тест ReviewViewModel — оценки и очередь** — unit-тест: переворот карточки; в Session лежат превью-интервалы четырёх кнопок из `Sm2Scheduler.preview` (S14, S15); graduating-оценки убирают карточку из очереди и вызывают `applyGrade` (S1–S3); «Забыл» ставит карточку в конец очереди (S19), повторный «Забыл» — снова в конец, EF не ниже 1.3 (S21); очередь пуста → Done (S20); повторный показ №1 + «Хорошо» → интервал 6 дней (S23); исключение при записи → состояние ошибки сохранения, карточка остаётся в очереди неоценённой, повторное нажатие применяется (S30). Убедиться, что тест падает.
      Сценарии: S1–S4, S14, S15, S19–S21, S23, S30. Файлы: `app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt`

- [ ] **T16. ReviewViewModel — оценки, превью, повторный показ** — реализовать `onFlip()` и `onGrade(grade)`: превью пересчитываются при показе каждой карточки единой функцией из T2; «Забыл» — `applyGrade` + перенос карточки в конец очереди; graduating-оценки — `applyGrade` + удаление из очереди; пустая очередь → Done; ошибка записи → флаг saveError в состоянии, очередь не меняется. Тест T15 зелёный.
      Сценарии: S1–S4, S14, S15, S19–S21, S23, S30. Файлы: `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`

- [ ] **T17. Падающий тест DeckListViewModel — счётчик готовых** — unit-тест с fake-репозиторием: элемент списка несёт total=5 и dueToday=2 (S25); dueToday=0 при всех будущих due, колода не исчезает (S27); после оценок обеих назревших (due ушёл в будущее) эмитится dueToday=0 при total=5 (S26); новая карточка с due=now увеличивает счётчик на 1 (S24). Убедиться, что тест падает.
      Сценарии: S24–S27. Файлы: `app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt`

- [ ] **T18. DeckListViewModel — данные списка колод** — расширить модель элемента списка до `DeckListItem(total, dueToday)`, источник — `repository.observeDecksWithCounts(now)` (Flow из Room, без ручного обновления). Тест T17 зелёный.
      Сценарии: S24–S27. Файлы: `app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt`, `app/src/main/java/com/mnemocards/data/CardRepository.kt`, `app/src/main/java/com/mnemocards/data/CardDao.kt`

- [ ] **T19. ReviewScreen — подписи интервалов и состояния** — на кнопках оценки подписи вида «Забыл — 10 мин», «Трудно — 7 дней», «Хорошо — 15 дней», «Легко — 20 дней» через `formatInterval(previews[grade])`; отрисовать состояния Done, NotFound (с возвратом к списку), ошибку загрузки и ошибку сохранения. Оформление не менять и не выносить компоненты: кнопки в один `Row` с `weight(1f)` внизу, цвета Red/Orange/Blue/Green со светлыми заливками и текстом `Ink`, карточка `Surface(surfaceVariant, RoundedCornerShape(20.dp))` с отступами и `weight(1f)`, эмблема `akatsuki_cloud` 36dp в `BottomCenter` того же `Box`, `TopAppBar` цвета `background`, тёмная тема `MnemoCardsTheme` — всё в одном файле `ReviewScreen.kt`.
      Сценарии: S14, S17, S18, S30–S32. Файлы: `app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt`

- [ ] **T20. DeckListScreen — счётчик готовых сегодня** — рядом с общим количеством карточек вывести счётчик готовых к повторению (формат вида «5 · готово сегодня: 2»), при нуле показывать 0. Существующий `MnemoCardsNavHost`, `MainActivity` и навигация не меняются.
      Сценарии: S25, S27. Файлы: `app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt`

- [ ] **T21. Регрессия и обновление MAIN.md** — прогнать все тесты (unit + androidTest), вручную проверить S22/S23 (оценка «Хорошо» → перезапуск приложения → карточка не показывается до срока → показывается на следующий день с подписью «6 дней»); обновить MAIN.md: capability `srs.scheduling` — реализована, `cards.review` — дополнена отбором по срокам и интервалами на кнопках.
      Сценарии: S1–S32. Файлы: `MAIN.md`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
