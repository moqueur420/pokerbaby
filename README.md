# PokerBaby

Детерминированный движок хедз-ап безлимитного техасского холдема на Java 21.

Выполнены первые пять этапов: проект, базовые типы и колода, оценщик комбинаций, полный розыгрыш раздачи и матч. Движок проводит торговлю, обрабатывает олл-ины, фолды, вскрытие и выплаты. Матч переносит стеки, меняет баттон, нумерует раздачи и завершается при нулевом стеке. Демо показывает последовательность связанных раздач. Воспроизведение, возобновление и JSON-сериализация — следующие этапы.

Этапы 6–8 пока отложены. Отдельный модуль `poker-engine-web` добавляет простой браузерный интерфейс для игры против бота поверх существующего движка.

## Игра в браузере

```powershell
.\gradlew.bat :poker-engine-web:run
```

Откройте [http://localhost:8080](http://localhost:8080). В Linux / macOS используйте `./gradlew :poker-engine-web:run`. Другой порт можно передать через `--args=8081`.

За столом доступны карты, банк, стеки, история текущей раздачи, фолд, чек, колл, бет и рейз. Размер ставки «до» включает уже поставленные на текущей улице фишки; границы присылает движок. Есть ползунок, быстрые размеры ставок, следующая раздача, новый матч и краткие правила. Интерфейс адаптирован для телефона.

Начальные стеки — по 10 000, блайнды — 50/100. Бот использует простую стратегию и получает только свой `PlayerView`. Браузер получает карты соперника только при вскрытии, включая историю событий. Раздачи переносят стеки и меняют баттон; при нулевом стеке матч завершается.

Сервер слушает только локальный адрес `127.0.0.1`. Сессии разделены по cookie: обновление страницы сохраняет матч, перезапуск сервера сбрасывает его. Это локальное демо без аккаунтов и базы данных. HTML/CSS/JavaScript и HTTP-сервер JDK не требуют Node.js, npm или внешних зависимостей времени выполнения.

Проверка веб-модуля: `.\gradlew.bat :poker-engine-web:check`.

## Требования

Для запуска Gradle нужен JDK 17 или новее. Gradle 9.9.1 закреплён в Wrapper, глобальная установка Gradle не требуется. Для компиляции, тестов и запуска приложения используется Eclipse Temurin JDK 21: если он не установлен, Gradle автоматически загрузит его через [Foojay Toolchains Resolver](https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories). Установленный JDK 25 также подходит для запуска Wrapper по [матрице совместимости Gradle](https://docs.gradle.org/current/userguide/compatibility.html).

Первый запуск требует доступа в интернет для загрузки Gradle, JDK и зависимостей.

## Сборка и проверка

Windows PowerShell:

```powershell
.\gradlew.bat build
```

Linux / macOS:

```sh
./gradlew build
```

`build` собирает все модули, запускает тесты, генерирует Javadoc и отчёты JaCoCo, проверяет покрытие ядра. Для полной проверки без сборки дистрибутивов можно выполнить `check` вместо `build`.

Минимальное покрытие строк ядра — 90 %; проверка включена в `check` и `build`. Проходят 236 тестов, покрытие строк текущего ядра — 100 %. В обычную сборку включены полный перебор 2 598 960 пятикарточных рук, независимая проверка 60 000 рук из 6–7 карт, 2,4 млн тасовок четырёх карт, все сценарии Т-1—Т-15, 20 000 раздач с независимым подсчётом фишек по событиям и 100 матчей до завершения с повторным прогоном для проверки детерминированности.

## Полезные команды

Команды ниже приведены для Windows; в Linux / macOS замените `.\gradlew.bat` на `./gradlew`.

```powershell
# Только тесты ядра
.\gradlew.bat :poker-engine-core:test

# Проверка доступных Java toolchains
.\gradlew.bat -q javaToolchains

# Запуск демонстрационной точки входа
.\gradlew.bat :poker-engine-demo:run

# Проверка отсутствия внешних зависимостей времени выполнения в ядре
.\gradlew.bat :poker-engine-core:dependencies --configuration runtimeClasspath

# Отдельный предварительный замер скорости оценщика после прогрева JVM
.\gradlew.bat :poker-engine-core:benchmarkEvaluator

# Отдельный предварительный замер полной раздачи с историей и PlayerView
.\gradlew.bat :poker-engine-core:benchmarkHands
```

Демонстрационная программа по умолчанию показывает две раздачи одного матча с seed 42, блайндами 50/100 и начальными стеками по 10 000. Фиксированная учебная последовательность: рейз до 250 и колл на префлопе, бет 300 и колл на флопе, два чека на тёрне, бет 500 и колл на ривере. Размеры ограничиваются допустимыми ставками, а при олл-ине борд досдаётся автоматически. Функция выбора действия получает только `PlayerView`; карты показываются при вскрытии.

Первая раздача при seed 42:

```text
Вскрытие human: [2s, Jh]
Вскрытие bot: [4d, Th]
Общие карты: [8h, 3c, Td, 6c, Ts]
Комбинация human: Пара — [Td, Ts, Jh, 8h, 6c]
Комбинация bot: Тройка — [Td, Th, Ts, 8h, 6c]
Победитель: bot.
Сыгранный банк: 2100
Выплаты: [0, 2100]
Итоговые стеки: [8950, 11050]
Банк после выплаты: 0
```

Во второй раздаче баттон переходит к `bot`, стартовые стеки — `[8950, 11050]`. Её выигрывает `human`, и стеки становятся `[10000, 10000]`. Демо сообщает о достижении лимита показа; сам матч ещё продолжается. При нулевом стеке матч заканчивается сразу, независимо от лимита. Случайная политика выбора действий будет добавлена на этапе 7.

Другой seed можно передать аргументом:

```powershell
.\gradlew.bat :poker-engine-demo:run --args=43

# Seed 42, до 10 раздач одного матча
.\gradlew.bat :poker-engine-demo:run --args="42 10"

# Seed 42, до 1000 раздач; текущая политика завершает этот матч за 77 раздач
.\gradlew.bat :poker-engine-demo:run --args="42 1000"
```

Формат аргументов — `[seed] [maxHands]`, по умолчанию `42 2`; лимит должен быть положительным целым числом. Один `Random(seed)` используется на весь матч: каждая новая раздача получает следующую перетасованную колоду из этой последовательности. Первая колода совпадает с `Deck.shuffled(seed)`.

## Базовый API

Все публичные типы ядра находятся в `io.pokerbaby.engine`:

```java
Card ace = Card.parse("As");
Deck original = Deck.shuffled(42);
Deck.Draw draw = original.draw();
Card dealt = draw.card();
Deck advanced = draw.deck();
// original.remaining() == 52; advanced.remaining() == 51

SeatChips stacks = SeatChips.of(10_000, 10_000);
stacks.validateMatchStart();
GameConfig config = GameConfig.DEFAULT; // 50/100
HandMeta meta = new HandMeta("m1-h1", List.of("human", "bot"));
Action raise = Action.raiseTo(250); // полная сумма на улице после повышения
HandHistory history = HandHistory.empty(config, meta, stacks, 0)
        .append(new HandEvent.PostBlind(0, 50));
```

`Deck.ordered(List<Card>)` создаёт полную или частичную колоду для тестов. `Deck.shuffled(RandomGenerator)` принимает внешний источник случайности, включая `SecureRandom`. Объекты и возвращаемые списки неизменяемы. Тип `Action` проверяет структуру команды; допустимость действия в конкретной раздаче проверяет `HandEngine.apply`.

`Deck.shuffled(List<Card>, seed)` и перегрузка с `RandomGenerator` перемешивают заданный набор карт. Тасовка выполняется обратным алгоритмом Фишера — Йетса; выбор индекса использует явную отбраковку для исключения смещения. Помимо граничных проверок, в сборку включён тест 2,4 млн тасовок четырёх карт: каждая из 24 перестановок должна встретиться 99 000—101 000 раз. Старый порядок полной колоды для seed 42 сохранён.

Подробности воспроизводимости, ограничений и порядка событий описаны в [контрактах базовых типов](docs/domain-model.md).

## Розыгрыш раздачи

```java
HandState state = HandEngine.start(GameConfig.DEFAULT,
        new HandMeta("m1-h1", List.of("human", "bot")),
        SeatChips.of(10_000, 10_000), 0, Deck.shuffled(42));

state = HandEngine.apply(state, 0, Action.raiseTo(250));
// Новый state: ход большого блайнда. Прежний объект остаётся действительным.
while (!state.isComplete()) {
    PlayerView view = state.viewFor(state.actingSeat().orElseThrow());
    Action action = view.legalActions().canCheck() ? Action.check() : Action.call();
    state = HandEngine.apply(state, view.seat(), action);
}
HandResult result = state.result().orElseThrow();
// result.pot() == 500; result.finalStacks() == [9750, 10250] по значению
// state.pot() == 0; вклады и ставки очищены после выплаты
HandHistory fullHistory = state.history();
HandHistory playerHistory = state.viewFor(0).history();
```

`LegalActions` содержит доступность фолда и чека, стоимость колла, `Optional<BetRange>` для бета и рейза с включительными `minTo`/`maxTo`. Размер «до» включает уже поставленные фишки. Если игрок в олл-ине, сопернику остаются фолд и колл; когда доплачивать нечего, досдача и выплата происходят автоматически.

`PlayerView` скрывает колоду и приватное событие сдачи сопернику. Карты соперника доступны только после `showdown`. Полное состояние и полная история предназначены для кода, управляющего раздачей. [Контракты движка, сценарии и предварительный замер](docs/hand-engine.md).

## Матч

```java
MatchState match = MatchState.start("m1", List.of("human", "bot"),
        GameConfig.DEFAULT, SeatChips.of(10_000, 10_000), 0);
HandState first = match.nextHand(Deck.shuffled(42));
HandResult firstResult = HandEngine.apply(first, 0, Action.fold()).result().orElseThrow();
MatchState afterFirst = match.afterHand(firstResult);
// afterFirst.stacks() == [9950, 10050] по значению
// afterFirst.buttonSeat() == 1; afterFirst.handNumber() == 2
// afterFirst.nextHandMeta().handId().equals("m1-h2")

HandState second = afterFirst.nextHand(Deck.shuffled(43));
HandResult secondResult = HandEngine.apply(second, 1, Action.fold()).result().orElseThrow();
MatchState afterSecond = afterFirst.afterHand(secondResult);
// afterSecond.stacks() == [10000, 10000] по значению
// afterSecond.buttonSeat() == 0; afterSecond.completedHands() == 2
```

`handNumber()` — номер следующей раздачи, `completedHands()` — число уже применённых результатов. `nextHandMeta()` и `nextHand(deck)` оставляют матч неизменным; переход выполняет `afterHand(result)`. Он сверяет идентификатор раздачи, игроков, блайнды, баттон и начальные стеки. После завершения `isOver()` возвращает `true`, `winnerSeat()` содержит победителя, запуск следующей раздачи запрещён. [Контракты матча и проверки](docs/match.md).

## Оценка и сравнение комбинаций

```java
HandRank result = HandEvaluator.evaluate(List.of(
        Card.parse("As"), Card.parse("Ks"), Card.parse("Qs"),
        Card.parse("Js"), Card.parse("Ts"), Card.parse("2c"), Card.parse("3d")));
// result.category() == HandCategory.STRAIGHT_FLUSH
// result.cards() == [As, Ks, Qs, Js, Ts] по значению
```

Оценщик принимает 5–7 различных карт. Поддерживаются девять категорий, кикеры и младший стрит A-2-3-4-5. `first.compareTo(second)` возвращает положительное число, если первая рука сильнее, и ноль при ничьей. Масти силу комбинации не меняют; при выборе равнозначных физических карт используется стабильный порядок мастей.

Первый локальный замер полного API: около **2,86 млн семикарточных оценок/с** на одном рабочем потоке. Методика, оборудование и границы этого предварительного результата описаны в [документации оценщика](docs/hand-evaluator.md). Замер запускается отдельно от тестов и JaCoCo.

## Кириллица в терминале Windows

Исходники и вывод Java/Gradle используют UTF-8. Если вместо русских букв видны символы рамок или искажённый текст, настройте ту же кодировку в терминале. В текущем терминале PowerShell выполните:

```powershell
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
.\gradlew.bat :poker-engine-demo:run
```

В VS Code можно сохранить эти команды инициализации в пользовательском профиле PowerShell через `terminal.integrated.profiles.windows`. После изменения профиля закройте старый терминал и создайте новый. Настройки `.vscode` локальные и не входят в Git.

## Структура проекта

| Модуль | Назначение |
| --- | --- |
| `poker-engine-core` | Карты, оценщик, правила, состояние, матч, история и воспроизведение; без внешних зависимостей времени выполнения |
| `poker-engine-json` | JSON-формат истории; Jackson будет подключён на этапе сериализации |
| `poker-engine-demo` | Консольный пример использования библиотеки |
| `poker-engine-web` | Локальный HTTP-сервер и браузерный стол для игры против бота |

Публичный API ядра размещается в пакете `io.pokerbaby.engine`.

## Отчёты и CI

- Тесты: `<модуль>/build/reports/tests/test/index.html`.
- Покрытие: `<модуль>/build/reports/jacoco/test/html/index.html` и XML рядом с каталогом `html`.
- Javadoc: `<модуль>/build/docs/javadoc/index.html`.
- Конфигурация GitHub Actions выполняет сборку на Windows и Linux с Java 21 при push, pull request и ручном запуске. Отчёты сохраняются как артефакты; результаты доступны во вкладке Actions репозитория.

Отчёты появляются для задач, у которых есть соответствующие исходники и данные выполнения. Задачи `benchmarkEvaluator` и `benchmarkHands` запускаются вручную и не входят в обязательные проверки каждого коммита. Плановые замеры в CI, миллион раздач и замер со случайными ботами относятся к этапу 8.

## Документы

- [Актуальное техническое задание](docs/specification.md).
- [План реализации](docs/implementation-plan.md).
- [Контракты базовых типов и колоды](docs/domain-model.md).
- [Оценка комбинаций, проверки и предварительный замер](docs/hand-evaluator.md).
- [Полная раздача: API, торговля, выплаты и проверки](docs/hand-engine.md).
- [Матч: перенос стеков, смена баттона и завершение](docs/match.md).

Тесты связываем с идентификаторами требований ТЗ. Основные решения: неизменяемые состояния, целочисленные фишки `long`, внешние метаданные и случайность, отдельные типы игрового состояния и снимка воспроизведения.
