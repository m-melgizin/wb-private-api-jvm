# wb-private-api-jvm

> Kotlin/JVM порт библиотеки [`wb-private-api`](https://github.com/Gitlawb/wb-private-api) —
> обёртка над приватным API Wildberries для поиска товаров, получения отзывов,
> вопросов, остатков, данных о поставщиках и многого другого.

## Быстрый старт

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.wb:wb-private-api-jvm:0.1.0")
}
```

```kotlin
import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.model.Product
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val api = WbPrivateApi(destination = Destinations.MOSCOW)

    // Поиск товаров
    val catalog = api.search("ноутбук", pageCount = 3)
    println("Найдено: ${catalog.totalProducts}, страниц: ${catalog.pages}")

    // Полная загрузка товара
    val product = Product.create(177899980L, session = api.session())
    println("${product.name}: ${product.currentPrice} ₽")

    // Отзывы
    val feedbacks = product.getFeedbacks()
    for (fb in feedbacks.take(3)) {
        println("[${fb.productValuation}★] ${fb.text?.take(200)}")
    }
}
```

## Возможности

- **21 метод API** — поиск, каталог поставщиков/брендов, подсказки, похожие товары,
  доставка, промо, информация о поставщиках
- **Модели** — `Product` (с suspend-методами: стоки, отзывы, вопросы, видео),
  `Catalog` (с page() / getPosition()), `Feedback`, `Question`
- **HTTP-сессия** — OkHttp, retry с экспоненциальным backoff, proxy-переписывание
  (`*.wb.ru` → `__internal/*`), deviceID, `qs`-стиль query-параметров
- **Авто-получение токена** — отдельный Gradle-подпроект `token-fetcher` на Playwright
  (Chromium headless)
- **Обработка ошибок** — sealed-иерархия `WbException` (400..503 + WB 498)

## Требования

- JDK 21+
- Gradle 9.3 (wrapper включён)

## Тесты

```bash
# Все тесты
./gradlew build

# Unit-тесты (67 кейсов, без сети)
./gradlew test --tests "*Test"

# Интеграционные тесты (46 кейсов, нужен .wbaas_token)
./gradlew test --tests "*Integration*"
```

**113 тестов, 0 ошибок** — 67 unit + 46 integration.

## Получение токена

```bash
# Автоматически (Playwright, headless — для VPS)
./gradlew :token-fetcher:run

# С видимым окном (для прохождения капчи)
./gradlew :token-fetcher:run --args="--headed"

# Только предзагрузка Chromium
./gradlew :token-fetcher:run --args="--install"
```

Без токена библиотека работает в ограниченном режиме (запросы напрямую к `*.wb.ru`).
Подробнее — [WIKI.md](WIKI.md).

## Структура проекта

```
src/main/kotlin/com/wb/privateapi/
├── WbPrivateApi.kt            # Фасад (21 метод API)
├── session/                   # OkHttp-клиент, proxy, retry, deviceID, token
├── model/                     # Product, Catalog, Feedback, Question
├── constant/                  # Urls, Warehouse (131), Destinations, enums
├── util/                      # Basket, ImageUrl, QueryId, CRC16, formatUrl
└── error/                     # Sealed WbException

token-fetcher/                 # Отдельный подпроект: авто-получение токена
```

## Статус

| Компонент | Статус |
|-----------|--------|
| Фасад API | ✅ 21 метод |
| Модели | ✅ 4 класса |
| HTTP-сессия | ✅ OkHttp + retry + proxy + deviceID + token |
| Утилиты | ✅ 5 модулей (сверены с JS) |
| Ошибки | ✅ Sealed hierarchy |
| Unit-тесты | ✅ 67 кейсов, MockWebServer |
| Интеграционные тесты | ✅ 46 кейсов, live WB API |
| Алгоритмическая точность | ✅ Неизнвисимая верификация PASS |
| Авто-получение токена | ✅ Playwright (отдельный подпроект) |
| Документация | [WIKI.md](WIKI.md) — полное описание |

## Связь с JS-проектом

Исходный проект: [rrrublev/wb-private-api](https://github.com/rrrublev/wb-private-api)

Порт выполнен методом сравнения (JS-тесты, независимая верификация).
Все URL-шаблоны байт-идентичны, алгоритмы (CRC16, basket, qs) сверены через `node`.

## Лицензия

MIT