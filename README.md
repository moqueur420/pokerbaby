# PokerBaby

Детерминированный движок хедз-ап безлимитного техасского холдема на Java 21.

Выполнены первые два этапа: настроен проект и реализованы карты, неизменяемая колода, значения фишек, конфигурация, метаданные, действия и типы событий истории. Оценка комбинаций, торговля, воспроизведение и JSON-сериализация реализуются следующими этапами.

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

Минимальное покрытие строк ядра — 90 %; проверка включена в `check` и `build`. На этапе 2 проходят 118 тестов, покрытие строк текущего ядра — 100 %. Это проверки базовых типов и колоды; игровая логика пока не реализована.

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
```

Демонстрационная программа перемешивает колоду с seed 42, раздаёт по две карты по очереди начиная с игрока без баттона и показывает неизменность исходной колоды. Для seed 42 карты `human` — `[2s, Jh]`, карты `bot` — `[4d, Th]`; остаётся 48 карт. Блайнды и стеки пока только выводятся как конфигурация: торговля и выплаты появятся после реализации движка раздачи.

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

Подробности воспроизводимости, ограничений и порядка событий описаны в [контрактах базовых типов](docs/domain-model.md).

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

Отчёты появляются для задач, у которых есть соответствующие исходники и данные выполнения. Замеры производительности будут добавлены отдельно и не войдут в обязательные проверки каждого коммита.

## Документы

- [Актуальное техническое задание](docs/specification.md).
- [План реализации](docs/implementation-plan.md).
- [Контракты базовых типов и колоды](docs/domain-model.md).

Тесты связываем с идентификаторами требований ТЗ. Основные решения: неизменяемые состояния, целочисленные фишки `long`, внешние метаданные и случайность, отдельные типы игрового состояния и снимка воспроизведения.
