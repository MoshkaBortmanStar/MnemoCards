# Tasks: Экран тренировки карточек

> **Change ID:** `add-review-screen`
> **Дата:** 2026-09-16

План реализации. Каждая задача — атомарный шаг для ИИ-кодера.

## Реализация

- [ ] **T1. Падающий тест основного пользовательского флоу** — создать Compose UI-тест
      (androidTest, `createComposeRule()`), воспроизводящий сценарии из spec-bdd.md:
      S1 (первая карточка лицевой стороной, перевод и кнопки скрыты), S2 (тап по карточке →
      перевод + 4 кнопки), S5 (кнопка «Хорошо» → карточка №2 лицевой стороной), S7 (оценка
      5-й карточки → экран «на сегодня всё»). Тест обращается к `ReviewScreen(ReviewViewModel())`
      и testTag-ам: `reviewCard`, `cardSideText`, `btnForgot`, `btnHard`, `btnGood`, `btnEasy`,
      `doneScreen`. При отсутствии — добавить зависимости `androidTestImplementation`
      (`ui-test-junit4`, debug `ui-test-manifest`). Запустить тест — он падает (экран ещё не
      существует, это красное состояние TDD).
      Сценарии: S1, S2, S5, S7.
      Файлы: `app/src/androidTest/java/com/mnemocards/ui/review/ReviewScreenTest.kt`,
      `app/build.gradle.kts`

- [ ] **T2. Модель карточки и захардкоженная колода** — написать unit-тест (JUnit)
      `HardcodedDeckTest`: колода содержит ровно 5 карточек; каждая карточка — пара непустых
      строк; в колоде есть карточки с латиницей, кириллицей и CJK-символами (языковая пара
      не зашита); есть карточка со словом или переводом длиннее 40 символов. Реализовать
      `data class Card(val front: String, val back: String)`, `enum class Grade { FORGOT, HARD, GOOD, EASY }`
      и `object HardcodedDeck` с константой `cards: List<Card>` из 5 пар (смешанные письменности,
      одна пара с длинным текстом). Все тесты зелёные.
      Сценарии: S1, S10, S11.
      Файлы: `app/src/main/java/com/mnemocards/domain/model/Card.kt`,
      `app/src/main/java/com/mnemocards/domain/model/Grade.kt`,
      `app/src/main/java/com/mnemocards/data/HardcodedDeck.kt`,
      `app/src/test/java/com/mnemocards/data/HardcodedDeckTest.kt`

- [ ] **T3. Падающие unit-тесты ReviewViewModel** — написать unit-тесты (JUnit +
      kotlinx-coroutines-test, при отсутствии добавить зависимость): начальное состояние —
      индекс 0, сторона «лицо», кнопки скрыты (S1); `onCardTap` из состояния «лицо» →
      «переворот», кнопки видимы (S2); `onCardTap` из состояния «переворот» → состояние
      не меняется (S4); `onRate(grade)` для каждой из четырёх оценок → индекс +1, сторона
      «лицо», порядок карточек фиксирован (S5, S6); `onRate` на 5-й карточке →
      `isFinished = true` (S7); в состоянии finished события `onCardTap`/`onRate` не меняют
      состояние (S8); двойной быстрый вызов `onRate` для одной карточки → индекс растёт
      ровно на 1 (S12). Тесты падают — ViewModel не существует.
      Сценарии: S1, S2, S4, S5, S6, S7, S8, S12.
      Файлы: `app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt`,
      `app/build.gradle.kts`

- [ ] **T4. Реализация ReviewUiState и ReviewViewModel** — `ReviewViewModel : ViewModel`
      c приватным мутабельным `MutableStateFlow<ReviewUiState>` и публичным
      `val state: StateFlow<ReviewUiState>`. `ReviewUiState(cardIndex, isFlipped,
      isFinished)` + вычисляемое `currentCard: Card` из `HardcodedDeck.cards`. События:
      `onCardTap()` — переворот только при `!isFlipped && !isFinished`; `onRate(grade)` —
      только при `isFlipped && !isFinished`: индекс +1, `isFlipped = false`, после последней
      карточки `isFinished = true` (guard-условия закрывают S4, S8, S12). Состояние хранится
      в поле ViewModel, а не в `remember` — переживает пересоздание Activity (S9). Оценки
      эквивалентны: параметр `grade` ни на что не влияет (S6). Все тесты T3 зелёные.
      Сценарии: S1–S9, S12.
      Файлы: `app/src/main/java/com/mnemocards/ui/review/ReviewUiState.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`

- [ ] **T5. Composable карточки: лицевая сторона и переворот** — `ReviewScreen(viewModel)`
      собирает `state` через `collectAsStateWithLifecycle()` и показывает `ReviewCard`:
      контейнер по центру экрана, `Modifier.testTag("reviewCard").clickable { viewModel.onCardTap() }`
      — clickable только на области карточки, тап вне её не переворачивает (S3); текст
      `currentCard.front` либо `currentCard.back` по флагу `isFlipped` с
      `testTag("cardSideText")`. Частично позеленить тест T1 (S1, S2).
      Сценарии: S1, S2, S3, S4.
      Файлы: `app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewCard.kt`

- [ ] **T6. Панель оценки из четырёх кнопок** — composable `RatingBar(onRate)` под карточкой:
      `Row` из четырёх `Button` с точными подписями «Забыл», «Трудно», «Хорошо», «Легко» и
      testTag-ами `btnForgot`, `btnHard`, `btnGood`, `btnEasy`; каждый вызывает
      `viewModel.onRate(grade)`. Показывается только при `isFlipped && !isFinished` — после
      нажатия `isFlipped` сбрасывается, кнопки исчезают из композиции, второй клик double-tap
      некуда попадает (S12). Все четыре кнопки ведут к одинаковому переходу (S6). Тест T1 для
      S5 зелёный.
      Сценарии: S2, S5, S6, S12.
      Файлы: `app/src/main/java/com/mnemocards/ui/review/RatingBar.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt`

- [ ] **T7. Экран завершения «На сегодня всё»** — composable `SessionDone`: сообщение
      «На сегодня всё» с `testTag("doneScreen")`; карточки, слов, перевода и кнопок оценки
      нет (в `ReviewScreen` при `isFinished` показывать только `SessionDone` вместо карточки
      и кнопок). Экран терминален: без clickable-элементов, автоперезапуск сессии отсутствует.
      Тест T1 для S7 зелёный.
      Сценарии: S7, S8.
      Файлы: `app/src/main/java/com/mnemocards/ui/review/SessionDone.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt`

- [ ] **T8. Точка входа MainActivity** — подключить экран: в `MainActivity.setContent`
      получить ViewModel (`viewModel()`) и отрисовать `ReviewScreen(viewModel)`. Запустить
      тест T1 полностью — весь основной флоу S1→S7 зелёный; smoke-запуск на эмуляторе.
      Сценарии: S1, S2, S5, S7.
      Файлы: `app/src/main/java/com/mnemocards/MainActivity.kt`

- [ ] **T9. Длинный текст карточки** — сначала падающий androidTest на `ReviewCard` в
      изоляции (`createComposeRule().setContent { ReviewCard(card = Card(<строка длиннее
      ширины карточки>, ...), isFlipped = true/false, onTap = {}) }`): текст не выходит за
      границы, вёрстка не ломается. Затем реализовать: `Text` с `softWrap = true` и
      `overflow = TextOverflow.Ellipsis`, контейнер карточки фиксированной ширины с
      внутренними отступами; кнопки оценки остаются полностью видимы и кликабельны.
      Тест зелёный.
      Сценарии: S11.
      Файлы: `app/src/androidTest/java/com/mnemocards/ui/review/ReviewCardLongTextTest.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewCard.kt`

- [ ] **T10. Поворот экрана сохраняет сессию** — сначала падающий androidTest
      (`createAndroidComposeRule<MainActivity>()`): пройти оценки до карточки №3, перевернуть
      её (перевод и кнопки на экране), вызвать `rule.activityRule.scenario.recreate()`,
      убедиться, что после пересоздания показана та же карточка №3 перевёрнутой с кнопками
      оценки (состояние из ViewModel, а не из `remember`/`saveable` внутри композиции).
      Убедиться, что поведение обеспечено реализацией T4–T8; при необходимости перенести
      состояние в ViewModel. Тест зелёный.
      Сценарии: S9.
      Файлы: `app/src/androidTest/java/com/mnemocards/ui/review/ReviewScreenRotationTest.kt`,
      `app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt`

- [ ] **T11. Краевые android-тесты взаимодействий** — сначала падающие, затем зелёные тесты:
      S3 — тап по корню экрана вне карточки (`onRoot().performTouchInput { click(Position.topLeft) }`)
      → перевод не появился; S4 — тап по перевёрнутой `reviewCard` → состояние не изменилось,
      кнопки остаются, перехода нет; S12 — `performTouchInput { doubleClick() }` по `btnGood`
      → показана ровно следующая карточка (i+1), пропуск до i+2 не произошёл.
      Сценарии: S3, S4, S12.
      Файлы: `app/src/androidTest/java/com/mnemocards/ui/review/ReviewScreenEdgeTest.kt`

- [ ] **T12. Полная проверка и фиксация capability** — прогнать все тесты: `./gradlew test`
      (unit) и `./gradlew connectedAndroidTest` (androidTest на эмуляторе) — все зелёные;
      smoke-прогон полного пользовательского цикла вручную на эмуляторе (S1→S7). Обновить
      MAIN.md: capability `cards.review` помечена как реализованная (первая версия, статические
      данные). Коммиты по Conventional Commits (`feat:`, `test:`, `docs:`).
      Сценарии: S1–S12.
      Файлы: `MAIN.md`

<!--
ПРАВИЛА (из .agent/schema.yaml):
  - Каждая задача не более 2 часов
  - Первая задача — всегда падающий тест
  - Формат задачи: чек-бокс + ID сценариев + затрагиваемые файлы
-->
