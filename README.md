# PokerBaby

Детерминированный движок хедз-ап безлимитного техасского холдема на Java 21.

Выполнены первые три этапа: настроен проект, реализованы базовые типы и неизменяемая колода, добавлена оценка комбинаций из 5–7 карт. Демо раздаёт закрытые и общие карты, показывает лучшие пятёрки и сравнивает руки. Торговля, выплаты, воспроизведение и JSON-сериализация реализуются следующими этапами.

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

Минимальное покрытие строк ядра — 90 %; проверка включена в `check` и `build`. Проходят 181 тест, покрытие строк текущего ядра — 100 %. В обычную сборку включены полный перебор 2 598 960 пятикарточных рук, независимая проверка 60 000 рук из 6–7 карт и тест 2,4 млн тасовок четырёх карт.

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
```

Демонстрационная программа перемешивает колоду с seed 42, раздаёт по две карты начиная с игрока без баттона, затем пять общих карт без сжигания. Для каждого игрока выбирается лучшая пятёрка из семи карт. После сдачи остаётся 43 карты; исходная колода по-прежнему содержит 52 карты.

При seed 42:

```text
Карты human: [2s, Jh]
Карты bot: [4d, Th]
Общие карты: [8h, 3c, Td, 6c, Ts]
Комбинация human: Пара — [Td, Ts, Jh, 8h, 6c]
Комбинация bot: Тройка — [Td, Th, Ts, 8h, 6c]
Сильнее рука bot.
```

Блайнды и стеки пока только выводятся как конфигурация: ставок и выплат в этом демо нет.

Другой seed можно передать аргументом:

```powershell
.\gradlew.bat :poker-engine-demo:run --args=43
```

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

`Deck.ordered(List<Card>)` создаёт полную или частичную колоду для тестов. `Deck.shuffled(RandomGenerator)` принимает внешний источник случайности, включая `SecureRandom`. Объекты и возвращаемые списки неизменяемы. Тип `Action` проверяет структуру команды; допустимость действия в конкретной раздаче будет проверять движок торговли.

`Deck.shuffled(List<Card>, seed)` и перегрузка с `RandomGenerator` перемешивают заданный набор карт. Тасовка выполняется обратным алгоритмом Фишера — Йетса; выбор индекса использует явную отбраковку для исключения смещения. Помимо граничных проверок, в сборку включён тест 2,4 млн тасовок четырёх карт: каждая из 24 перестановок должна встретиться 99 000—101 000 раз. Старый порядок полной колоды для seed 42 сохранён.

Подробности воспроизводимости, ограничений и порядка событий описаны в [контрактах базовых типов](docs/domain-model.md).

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

Публичный API ядра размещается в пакете `io.pokerbaby.engine`.

## Отчёты и CI

- Тесты: `<модуль>/build/reports/tests/test/index.html`.
- Покрытие: `<модуль>/build/reports/jacoco/test/html/index.html` и XML рядом с каталогом `html`.
- Javadoc: `<модуль>/build/docs/javadoc/index.html`.
- Конфигурация GitHub Actions выполняет сборку на Windows и Linux с Java 21 при push, pull request и ручном запуске. Отчёты сохраняются как артефакты; результаты доступны во вкладке Actions репозитория.

Отчёты появляются для задач, у которых есть соответствующие исходники и данные выполнения. Задача `benchmarkEvaluator` запускается вручную и не входит в обязательные проверки каждого коммита. Плановые замеры в CI и скорость полной симуляции будут добавлены на следующих этапах.

## Документы

- [Актуальное техническое задание](docs/specification.md).
- [План реализации](docs/implementation-plan.md).
- [Контракты базовых типов и колоды](docs/domain-model.md).
- [Оценка комбинаций, проверки и предварительный замер](docs/hand-evaluator.md).

Тесты связываем с идентификаторами требований ТЗ. Основные решения: неизменяемые состояния, целочисленные фишки `long`, внешние метаданные и случайность, отдельные типы игрового состояния и снимка воспроизведения.
