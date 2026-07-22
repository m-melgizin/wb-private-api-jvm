# wb-private-api-jvm — Wiki

Kotlin/JVM-порт библиотеки [`wb-private-api`](https://github.com/Gitlawb/wb-private-api)
(обёртка над приватным API Wildberries). Полный перенос JS-версии на Kotlin
с корутинами, OkHttp и kotlinx.serialization.

- **Статус:** все 8 этапов плана выполнены. Этапы 1 (Gradle-scaffold) и 2
  (Constants) существовали до начала портирования.
- **Сборка:** `./gradlew clean build` — успешно, 0 предупреждений, 0 ошибок.
- **Тесты:** **113 кейсов** (67 unit + 46 integration), все проходят.
  Независимая верификация — **PASS**.
- **Дополнительно:** подпроект `token-fetcher` для автоматического получения
  `x_wbaas_token` через Playwright (Chromium headless).

---

## Содержание

1. [Быстрый старт](#быстрый-старт)
2. [Структура проекта](#структура-проекта)
3. [Архитектура](#архитектура)
4. [Стек технологий](#стек-технологий)
5. [API фасада `WbPrivateApi`](#api-фасада-wbprivateapi)
6. [Модель `Product`](#модель-product)
7. [Модели `Catalog`, `Feedback`, `Question`](#модели-catalog-feedback-question)
8. [Сессия и HTTP-клиент](#сессия-и-http-клиент)
9. [Утилиты](#утилиты)
10. [Константы](#константы)
11. [Обработка ошибок](#обработка-ошибок)
12. [Сравнение с JS-версией](#сравнение-с-js-версией)
13. [Unit-тесты](#unit-тесты)
14. [Интеграционные тесты](#интеграционные-тесты)
15. [Сборка и запуск](#сборка-и-запуск)
16. [Подпроект `token-fetcher` (авто-получение токена)](#подпроект-token-fetcher)
17. [Известные ограничения](#известные-ограничения)

---

## Быстрый старт

### Зависимость (Gradle Kotlin DSL)

```kotlin
dependencies {
    implementation("com.wb:wb-private-api-jvm:1.0.0")
}
```

### Поиск товаров

```kotlin
import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Destinations
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Токен читается автоматически из .wbaas_token (если есть).
    // Без токена запросы идут напрямую к *.wb.ru.
    val api = WbPrivateApi(destination = Destinations.MOSCOW)

    val catalog = api.search("ноутбук", pageCount = 3)
    println("Найдено: ${catalog.totalProducts}, страниц: ${catalog.pages}")

    val firstPage = catalog.page(1)
    println("Товаров на 1-й странице: ${firstPage.size}")
}
```

### Полная загрузка товара

```kotlin
import com.wb.privateapi.model.Product

val product = Product.create(177899980L, session = api.session())
// Параллельно загружены card.json, details, sellers + кол-во вопросов
println("Бренд: ${product.brand}")
println("Остатки: ${product.getStocks()}")
println("Отзывы: ${product.getFeedbacks().size}")
println("Видео: ${product.getVideo().playlistUrl}")
```

### Поиск с фильтрами

```kotlin
import com.wb.privateapi.CatalogFilter

val catalog = api.search(
    keyword = "кроссовки",
    pageCount = 5,
    filters = listOf(CatalogFilter("fbrand", "1531"))
)
```

### Каталог поставщика

```kotlin
val supplierCatalog = api.getSupplierCatalogAll(supplierId = 12345L)
println("Всего: ${supplierCatalog.totalProducts}")
```

---

## Структура проекта

```
wb-private-api-jvm/
├── build.gradle.kts
├── settings.gradle.kts                # include(":token-fetcher")
├── gradle.properties
├── gradle/wrapper/                    # Gradle 9.3.0
├── gradlew, gradlew.bat
├── src/                               # основная библиотека
│   ├── main/kotlin/com/wb/privateapi/
│   │   ├── WbPrivateApi.kt            # Фасад (21 метод API)
│   │   ├── session/
│   │   │   ├── Session.kt             # HTTP-сессия: retry/backoff, query-сборка
│   │   │   ├── SessionBuilder.kt      # Фабрика OkHttp-клиента, токен, deviceID, proxy
│   │   │   └── SessionJson.kt         # JSON-кодек на kotlinx.serialization
│   │   ├── model/
│   │   │   ├── Product.kt             # Товар + suspend-методы (стоки, отзывы, видео…)
│   │   │   ├── Catalog.kt             # Обёртка результатов поиска/каталога
│   │   │   ├── Feedback.kt            # Отзыв + getPhotos(size)
│   │   │   └── Question.kt            # Вопрос
│   │   ├── constant/
│   │   │   ├── Constants.kt           # PRODUCTS_PER_PAGE, USER_AGENT, enum'ы
│   │   │   ├── Urls.kt                # Все URL-шаблоны WB
│   │   │   ├── Warehouse.kt           # 131 склад WB
│   │   │   ├── Destination.kt         # Направления доставки (MOSCOW и др.)
│   │   │   └── HttpStatus.kt          # HTTP-статусы
│   │   ├── util/
│   │   │   ├── UrlFormat.kt           # formatUrl() — аналог string-format
│   │   │   ├── BasketCalculator.kt    # CDN-корзины (изображения + видео)
│   │   │   ├── ImageUrlBuilder.kt     # URL изображений/видео/брендов
│   │   │   ├── QueryIdGenerator.kt    # x-queryid
│   │   │   └── Crc16.kt               # CRC16-ARC (партиции feedbacks)
│   │   └── error/
│   │       └── WbException.kt         # Sealed-иерархия ошибок WB
│   └── test/kotlin/com/wb/privateapi/  # 67 тестов в 11 классах
└── token-fetcher/                     # ОТДЕЛЬНЫЙ подпроект: авто-получение токена
    ├── build.gradle.kts               # application + Playwright
    └── src/main/kotlin/com/wb/privateapi/token/
        └── FetchToken.kt              # CLI: ./gradlew :token-fetcher:run
```

---

## Архитектура

```
WbPrivateApi (WbPrivateApi.kt)
 ├─ Session (session/Session.kt)                — suspend GET/POST, retry/backoff
 │   └─ SessionBuilder.create() (SessionBuilder.kt) — OkHttpClient + токен + deviceID + proxy
 ├─ Constants.Urls (constant/Urls.kt)           — все URL-шаблоны ({0},{1}…)
 ├─ utils (util/*)                              — basket, image/video URL, queryId, CRC16
 ├─ возвращает Catalog (model/Catalog.kt) с List<Map> товаров
 │   └─ Catalog.page(n) / getPosition(id)
 └─ Product.create() (model/Product.kt)
     └─ getStocks() / getPromo() / getFeedbacks() / getQuestions() / getVideo()
         └─ Feedback (model/Feedback.kt) / Question (model/Question.kt)
```

**Поток данных повторяет JS-версию 1-в-1:**

1. `WbPrivateApi.search()` → `searchTotalProducts()` → `getQueryMetadata()` →
   параллельная загрузка страниц через `mapConcurrent(MAX_CONCURRENCY=5)`.
2. Каждая страница — `getCatalogPage()`, который шлёт `Search.EXACTMATCH` с
   заголовком `x-queryid` и `Referer`.
3. Результаты склеиваются в плоский `List<Map<String, Any?>>` (как в JS).
4. `Product.create()` параллельно (`coroutineScope { awaitAll(...) }`) грузит
   card.json, details и sellers, затем `getQuestionsCount()`.

---

## Стек технологий

| Компонент | Выбор | Обоснование |
|-----------|-------|-------------|
| Язык | Kotlin 1.9.22 | Data-классы, null-safety, корутины, sealed classes |
| HTTP-клиент | OkHttp 4.12.0 | Connection pooling, интерцепторы, проверенный |
| Асинхронность | kotlinx.coroutines 1.7.3 | `suspend fun`, structured concurrency |
| JSON | kotlinx.serialization-json 1.6.2 | Ленивый парсинг произвольных ответов WB |
| Логирование | SLF4J + Logback 1.4.14 | SLF4J-API, реализация подключается опционально |
| Сборка | Gradle 9.3.0 (Kotlin DSL) | Стандарт JVM-экосистемы |
| Тесты | JUnit 5.10.1 + MockWebServer 4.12.0 | Unit + интеграционные через mock-сервер |
| Java | 21 (sourceCompatibility/targetCompatibility) | Современный LTS |

---

## API фасада `WbPrivateApi`

```kotlin
class WbPrivateApi(
    val destination: DestinationInfo = Destinations.MOSCOW,
    wbaasToken: String? = null
)
```

Все методы — `suspend`. Возвращает `Catalog` с плоскими `Map`-товарами
(как JS-версия); обернуть товар в `Product` можно через
`Product(map, session, destination)` или `Product.create(nmId, ...)`.

| Метод | Назначение |
|-------|-----------|
| `search(keyword, pageCount=0, retries=0, filters=[])` | Поиск товаров по ключевому слову |
| `getQueryMetadata(keyword, limit=0, withProducts=false, page=1, retries=0, suppressSpellcheck=true)` | Метаданные запроса (`catalog_type`, `catalog_value`) |
| `searchTotalProducts(keyword)` | Общее число товаров по запросу |
| `getSupplierProductCount(supplierId)` | Число товаров поставщика |
| `getBrandProductCount(brandId)` | Число товаров бренда |
| `getBrandCatalog(brandId, page=1)` | Сырой ответ страницы каталога бренда |
| `getBrandCatalogPage(brandId, page=1, retries=0)` | Товары со страницы каталога бренда |
| `searchCustomFilters(keyword, filters)` | Поиск с произвольными фильтрами |
| `getCatalogPage(catalogConfig, page=1, retries=0, filters=[])` | Товары со страницы поискового каталога |
| `keyHint(query)` | Подсказки по запросу |
| `searchSimilarByNm(productId)` | Похожие товары по `nm_id` |
| `getDeliveryDataByNms(productIds, retries=0)` | Данные о доставке для списка `nm_id` |
| `getPromos()` | Активные промо |
| `getListOfProducts(productIds)` | Товары по списку `nm_id` |
| `getSupplierInfo(sellerId)` | Информация о поставщике |
| `getSupplierShipment(sellerId)` | Отгрузки поставщика |
| `getSupplierCatalog(supplierId, page=1)` | Сырой ответ страницы каталога поставщика |
| `getSupplierCatalogAll(supplierId, pageCount=0, retries=0)` | Весь каталог поставщика с пагинацией |
| `getSupplierCatalogPage(supplierId, page=1, retries=0)` | Товары со страницы каталога поставщика |
| `getPageCount(totalProducts)` | Число страниц по общему количеству (cap `PAGES_PER_CATALOG=100`) |
| `session()` | Возвращает `Session` (для передачи в `Product`) |
| `setToken(token)` | **Выбрасывает `UnsupportedOperationException`** — см. [Ограничения](#известные-ограничения) |

### Параллельная загрузка

Внутренний `mapConcurrent(items, concurrency, mapper)` повторяет JS
`mapWithConcurrency(5)`: ограничивает параллельность через `Semaphore`,
сохраняет порядок результатов по индексам.

```kotlin
val parsedPages = mapConcurrent((1..totalPages).toList(), Constants.MAX_CONCURRENCY) { page ->
    getCatalogPage(catalogConfig, page, retries, filters)
}
```

---

## Модель `Product`

```kotlin
// По nm_id (с авто-загрузкой card/details/seller + кол-во вопросов):
val product = Product.create(177899980L, session = api.session())

// Из готовой мапы (как в каталоге):
val product = Product(map, session = api.session())
```

### Свойства

| Свойство | Тип | Источник |
|----------|-----|----------|
| `id` | `Long` | `raw.id` или `rawCard.nm` |
| `name` | `String?` | `raw.name` |
| `brand` | `String?` | `raw.brand` или `rawSellers.brand` |
| `salePriceU`, `priceU` | `Long?` | `raw.*` |
| `imtId` | `Long?` | `raw.imt_id` / `rawCard` / `rawDetails` |
| `dest` | `Int` | `destination.lastId` |
| `currentPrice` | `Long?` | `rawDetails.sizes[0].price.product` |
| `totalStocks` | `Int` | сумма `qty` из `rawDetails.sizes[0].stocks` |
| `stocks` | `MutableList<Map>` | заполняется `getProductData()` |
| `promo` | `Map<String,Any?>` | заполняется `getPromo()` |
| `feedbacks` | `MutableList<Feedback>` | заполняется `getFeedbacks()` |
| `totalQuestions` | `Int` | заполняется `getQuestionsCount()` |
| `raw`, `rawCard`, `rawDetails`, `rawSellers` | `Map<String,Any?>` | сырые ответы API |

Доступ к произвольному полю: `product["customField"]` (оператор `get`).

### Suspend-методы

| Метод | Возвращает | Что делает |
|-------|-----------|-----------|
| `getProductData()` | `Product` | Загружает `card.json` (basket) → `rawCard`, `stocks` |
| `getDetailsData()` | `Product` | Загружает `cards/v4/detail` → `rawDetails` |
| `getSellerData()` | `Product` | Загружает `sellers.json` → `rawSellers` |
| `getStocks()` | `List<Map>` | `details.sizes[0].stocks` (без доп. запроса) |
| `getPromo()` | `Map<String,Any?>` | `card.panelPromoId` (с авто-retry через `getProductData`) |
| `getFeedbacks()` | `List<Feedback>` | Все отзывы (CRC16-партиция от `imt_id`) |
| `getQuestionsCount()` | `Int` | Число вопросов |
| `getQuestions()` | `QuestionsResult` | Все вопросы (cap WB: `skip < 510`) |
| `getVideo()` | `VideoInfo` | HLS-плейлист, длительность, `.ts`-чанки, mp4-preview |

### URL-помощники

```kotlin
product.imageUrl()                          // SMALL изображение, order=1
product.imageUrl(ImageType.BIG, 3)          // BIG, 3-е фото
product.videoUrl()                          // HLS 1440p
product.videoUrl("mp4", "360p")             // mp4-preview
```

---

## Модели `Catalog`, `Feedback`, `Question`

### `Catalog`

```kotlin
val catalog = Catalog(
    catalogType = "search",
    catalogValue = "test",
    pages = 3,
    products = listOf(mapOf("id" to 1L)),
    totalProducts = 250
)

catalog.page(1)          // List<Map> — срез PRODUCTS_PER_PAGE=100 товаров
catalog.page(3)          // последний срез (50 товаров)
catalog.getPosition(10L) // 0-based индекс, -1 если не найден
```

Конструктор из ответа API: `Catalog(data: Map)` — парсит `catalog_type`,
`catalog_value`, `pages`, `products`, `totalProducts`.

### `Feedback`

```kotlin
val feedback = Feedback(mapOf("id" to 42L, "productValuation" to 5, ...))
feedback.id                    // 42L
feedback.text                  // "отлично"
feedback.getPhotos("min")      // List<String> — полные URL (FEEDBACK_BASE + minSizeUri)
feedback.getPhotos("c516x516") // крупные фото
feedback["customField"]        // произвольное поле
```

### `Question`

```kotlin
val question = Question(mapOf("id" to 1L, "question" to "размер?"))
question.id            // 1L
question.questionText  // "размер?"
question["customField"] // произвольное поле
```

### `QuestionsResult` / `VideoInfo`

```kotlin
data class QuestionsResult(
    val items: List<Question>,
    val totalQuestions: Int,
    val fetchedQuestions: Int,
    val truncated: Boolean          // true, если skip >= 510 (WB hard-limit)
)

data class VideoInfo(
    val hasVideo: Boolean,
    val quality: String,            // "1440p"
    val playlistUrl: String,        // HLS m3u8
    val duration: Int,              // сек (сумма #EXTINF)
    val chunks: Int,
    val hls: List<String>,          // .ts-файлы
    val mp4Preview: String?,        // 360p mp4
    val error: String?
)
```

---

## Сессия и HTTP-клиент

### `SessionBuilder.create()`

```kotlin
val session = SessionBuilder.create(
    wbaasToken = null,      // null → читается из .wbaas_token
    timeout = 30.seconds,
    retries = 3,
    maxSockets = 10,
    userAgent = Constants.USER_AGENT,
    requestLogger = null    // RequestLogger (диагностика запросов)
)
```

**Возможности (повторяют `SessionBuilder.js`):**

- **Connection pooling:** OkHttp `ConnectionPool(maxIdle=10, keepAlive=30s)`.
- **Общие заголовки:** `User-Agent`, `Accept`, `Accept-Language`, `Origin`,
  `Referer`, `Cache-Control`. При наличии токена — `Cookie: x_wbaas_token=...`.
- **Токен:** читается из `.wbaas_token` (JSON `{"token","expires_at"}`) —
  текущая директория, затем `~`. Истёкший/отсутствующий → `null` (запросы идут напрямую).
- **deviceID:** `site_<uuid-v4-без-дефисов>`, кешируется в `.deviceid`.
  Добавляется только для запросов к `www.wildberries.ru` (после proxy-переписывания).
- **Proxy-переписывание:** `toProxyUrl()` — `https://<subdomain>.wb.ru/...`
  → `https://www.wildberries.ru/__internal/<subdomain>/...` для 15 известных subdomain'ов
  (`catalog`, `search`, `card`, `suggests`, …). Применяется только при наличии токена.
- **Retry:** экспоненциальный backoff `min(2^attempt * 1000 + rand*1000, 10000)` ms
  на 429 / 5xx / сетевые ошибки. Финальная неудача → `WbException.byStatus()`.
- **Query-параметры:** `qs.stringify({arrayFormat:'comma', encode:false})` —
  массивы через запятую, **без URL-кодирования**, `null` → `a=` (не пропускается).
  Сверено напрямую с `npm i qs`.

### `Session.get()` / `Session.post()`

```kotlin
suspend fun get(url, params=emptyMap(), headers=emptyMap(),
                retryOptions=RetryOptions.DEFAULT, responseType=AUTO): ResponseData
suspend fun post(url, body, headers=emptyMap(), retryOptions=RetryOptions.DEFAULT): ResponseData
```

- `get()` автоматически переписывает URL через proxy при наличии токена и
  добавляет `deviceid` для `__internal`-запросов.
- `post()` шлёт в исходный URL (как в JS) с `Content-Type: application/json`.
- `responseType`: `AUTO` (по Content-Type + эвристика), `JSON`, `TEXT`.
- Возвращает `ResponseData(status, data)` где `data` — `Map`/`List`/`String`/`null`.

### JSON-кодек (`SessionJson`)

Использует `kotlinx.serialization.json.Json` с `ignoreUnknownKeys`,
`isLenient`, `coerceInputValues`, `explicitNulls=false`. Произвольный ответ WB
парсится в доменно-нейтральные типы (`Map`/`List`/примитивы).

---

## Утилиты

### `BasketCalculator`

CDN-корзины WB для раздачи изображений/видео.

```kotlin
BasketCalculator.getBasketNumber(14381552)   // "01"
BasketCalculator.getBasketNumber(165879870)  // "12"
BasketCalculator.getBasketVolume(177899980)  // 1778
BasketCalculator.getBasketPart(177899980)    // 177899
BasketCalculator.getVideoVol(177899980)      // nm % 144
BasketCalculator.getVideoBasket(vol)         // 2-значный номер видео-корзины
```

`BASKETS` (47 порогов) и `VIDEO_BASKETS` (13 порогов) перенесены из `Utils.js`.
Все 11 эталонных SKU из `tests/Utils.test.js` совпадают.

### `ImageUrlBuilder`

```kotlin
ImageUrlBuilder.imageUrl(177899980, ImageType.BIG, 3)
// → https://basket-12.wbbasket.ru/vol1778/part177899/177899980/images/big/3.webp?r=<ts>

ImageUrlBuilder.brandImageUrl(87238)
// → https://static-basket-01.wbbasket.ru/vol0/brand-flow-logos/by-id/87238.webp

ImageUrlBuilder.videoUrl(177899980, "hls", "1440p")  // m3u8
ImageUrlBuilder.videoUrl(177899980, "mp4", "360p")   // mp4-preview
```

`ImageType`: `TINY`, `BIG`, `SMALL`, `MEDIUM` (`fromName()` регистронезависим,
fallback → `SMALL`).

### `Crc16`

CRC16-ARC (полином `0xA001`, reflected) для выбора партиции feedbacks.

```kotlin
Crc16.crc16Arc(12345678)  // 34543
Crc16.crc16Arc(27334676)  // 20412
// partition: crc % 100 >= 50 ? "2" : "1"
```

Значения сверены с JS-алгоритмом через `node`.

### `QueryIdGenerator`

```kotlin
QueryIdGenerator.getQueryIdForSearch()  // "qid<random><unix><yyyyMMddHHmmss>"
QueryIdGenerator.genNewUserId()         // "<random 2^30><unix-sec>"
QueryIdGenerator.formatDateForQueryId() // "20260718120000" (14 цифр)
```

### `formatUrl`

```kotlin
formatUrl("https://basket-{0}.wb.ru/vol{1}/part{2}/{3}", basket, vol, part, id)
// Повтор string-format: отсутствующий плейсхолдер → пустая строка
formatUrl("https://x/{0}")  // → "https://x/" (как JS string-format)
```

---

## Константы

### `Constants.kt`

```kotlin
object Constants {
    const val PRODUCTS_PER_PAGE = 100
    const val PAGES_PER_CATALOG = 100
    const val FEEDBACKS_PER_PAGE = 20
    const val QUESTIONS_PER_PAGE = 30
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ..."
    const val MAX_CONCURRENCY = 5   // для mapConcurrent
}
```

### Enum'ы

```kotlin
enum class AppType(val value: Int) { DESKTOP(1), ANDROID(32), IOS(64) }
enum class Locale(val value: String) { RU("ru") }
enum class Currency(val value: String) { RUB("rub") }
enum class Sex(val value: String) { FEMALE("female"), MALE("male"), COMMON("common") }
```

### `Destinations`

```kotlin
object Destinations {
    val MOSCOW: DestinationInfo     // ids = [-1029256, -102269, -2162196, -1257786], lastId = -1257786
    val SAINT_PETERSBURG: DestinationInfo
    val KRASNODAR: DestinationInfo
    val MINSK: DestinationInfo
    val ALMATY: DestinationInfo
    val YEREVAN: DestinationInfo
    object GLOBAL { /* 7 регионов */ }
}
```

`DestinationInfo`: `name`, `ids: List<Int>`, `lastId: Int` (аналог JS `ids.at(-1)`).

### `Warehouse.kt`

131 склад WB — данные-классы `Warehouse(id, name, ...)`. Перенесено 1-в-1
(верификатор подтвердил совпадение всех 131 записей).

### `Urls.kt`

Все URL-шаблоны WB с плейсхолдерами `{0}`..`{4}`:
`Brand`, `Supplier`, `Product`, `Search`, `Images`, `Video`.
Байт-идентичны `Constants.js` (подтверждено верификатором).

---

## Обработка ошибок

Sealed-иерархия `WbException` (перенос `WB_ERRORS` из `Constants.js`):

```
WbException (sealed)
 ├─ BadRequestException        (400)
 ├─ UnauthorizedException      (401)
 ├─ ForbiddenException         (403)
 ├─ NotFoundException          (404)
 ├─ MethodNotAllowedException  (405)
 ├─ ConflictException          (409)
 ├─ RateLimitException         (429)
 ├─ InvalidTokenException      (498)
 ├─ ServerErrorException       (500)
 ├─ NotImplementedException    (501)
 ├─ ServiceUnavailableException(503)
 └─ UnknownException           (любой другой статус)
```

```kotlin
try {
    api.search("телефон")
} catch (e: WbException) {
    when (e) {
        is WbException.RateLimitException -> retry()
        is WbException.InvalidTokenException -> refreshToken()
        else -> throw e
    }
}

// Подбор по статусу:
WbException.byStatus(429)  // → RateLimitException
```

Дополнительно: `WbCatalogException(detail, status, data)` — выбрасывается
`getCatalogPage()` при `error`/`code` в ответе без массива товаров.

---

## Сравнение с JS-версией

### Полный паритет

| Аспект | Статус |
|--------|--------|
| Методы фасада (21) | ✅ Все перенесены |
| `Product` suspend-методы (8) | ✅ Все перенесены + `totalStocks` |
| `Catalog` / `Feedback` / `Question` | ✅ Полная эквивалентность |
| URL-шаблоны | ✅ Байт-идентичны |
| `BASKETS` / `VIDEO_BASKETS` | ✅ 11/11 эталонных SKU совпадают |
| CRC16-ARC | ✅ Значения совпадают (сверено через `node`) |
| Query-string (`qs encode:false`) | ✅ Сверено с `npm i qs` |
| Proxy-переписывание (15 доменов) | ✅ Идентично |
| Retry/backoff (429/5xx) | ✅ Формула совпадает |
| deviceID, токен из `.wbaas_token` | ✅ Идентично |
| Warehouses (131) / Destinations | ✅ Совпадают |

### Намеренные отличия

| Отличие | Причина |
|--------|---------|
| `setToken()` выбрасывает `UnsupportedOperationException` | Токен иммутабелен в Kotlin-сборке (см. [Ограничения](#известные-ограничения)). Для смены токена — новый `WbPrivateApi(wbaasToken=...)` |
| `Product` хранит `raw: Map` вместо `Object.assign(this, …)` | Идемпотентность и явная типизация; доступ через `product["field"]` |
| Корутины (`suspend fun`) вместо `async/await` | Идиоматичный Kotlin, structured concurrency |
| Sealed class `WbException` вместо `err.name === "WBRateLimitError"` | Типобезопасный `when(err)` |

### Тесты: JS → JVM

| Проект | Тест-файлов | Кейсов |
|--------|------------|--------|
| JS (`tests/`) | 9 (incl. 6 интеграционных с сетью) | ~45 (`test`/`it`) |
| JVM (`src/test/`) | 11 (все unit, без сети) | 67 |

JVM-тесты **не требуют сети и токена** — все через MockWebServer и чистые
функции. JS-интеграционные тесты (6 файлов) требуют `.wbaas_token` и живой WB API.

**Перенесённые проверки:**
- `Utils.test.js` (basket-номера) → `BasketCalculatorTest` ✅
- `Constants.URLs.utils.test.js` (imageURL, brandURL, CRC) → `ImageUrlBuilderTest`, `Crc16Test` ✅
- Логика сессии → `SessionMockWebServerTest` (13 кейсов: retry, proxy, query, headers, POST) ✅
- `appendQuery` (qs-поведение) → `AppendQueryTest` (11 кейсов, сверка с `npm i qs`) ✅
- `Catalog.page`/`getPosition` → `CatalogTest` ✅
- `Feedback.getPhotos` → `FeedbackTest` ✅
- `WbException` mapping → `WbExceptionTest` ✅

> **Примечание о CRC16-комментариях:** JS-тест `Constants.URLs.utils.test.js`
> содержит **вводящие в заблуждение комментарии** (утверждается, что
> `12345678`→партиция `"2"`, `27334676`→`"1"`). Реальные CRC-значения
> (`34543%100=43`, `20412%100=12`, оба <50) дают партицию `"1"` для обоих.
> JS-тест проходит только потому, что он проверяет включение вычисленного
> значения в URL (тавтология), а не хардкод партиции. JVM-тесты
> (`Crc16Test`) корректно утверждают реальные значения `"1"`/`"1"`.

---

## Unit-тесты

```bash
# Все unit-тесты (без сети, без токена)
./gradlew test --tests "*Test"

# Конкретный класс
./gradlew test --tests "com.wb.privateapi.util.Crc16Test"

# Конкретный кейс
./gradlew test --tests "com.wb.privateapi.session.AppendQueryTest"

# С покрытием
./gradlew test jacocoTestReport
```

### Классы unit-тестов (67 кейсов)

| Класс | Кейсов | Покрытие |
|-------|--------|----------|
| `AppendQueryTest` | 11 | qs-stringify (comma, null, no-encoding, order, existing-query) |
| `SessionMockWebServerTest` | 13 | retry 429/5xx, no-retry 404, headers, query, POST JSON, proxy-rewrite, text/empty response |
| `SessionBuilderTokenTest` | 5 | parseTokenJson (valid/expired/missing), getDeviceId format |
| `BasketCalculatorTest` | 4 | 11 эталонных SKU, vol/part, video basket |
| `Crc16Test` | 5 | эталонные значения, детерминированность, 0-вход, диапазон |
| `ImageUrlBuilderTest` | 5 | imageURL/brandImage/video/mp4, ImageType.fromName |
| `QueryIdGeneratorTest` | 4 | qid-префикс, timestamp, numeric userID, date format |
| `UrlFormatTest` | 7 | позиционные плейсхолдеры, missing→empty, percent, literal braces |
| `CatalogTest` | 5 | page-срезы, out-of-range, getPosition, конструктор из Map |
| `FeedbackTest` | 4 | getPhotos(min/c516x516), typed accessors, operator get |
| `WbExceptionTest` | 4 | byStatus mapping, sealed `when`, статус 429 |

Все кейсы — unit (без сети, MockWebServer для HTTP).

---

## Интеграционные тесты

Портированы все 4 JS интеграционных тест-сьюта из оригинального проекта.
Требуют `.wbaas_token` в корне проекта (см. [token-fetcher](#подпроект-token-fetcher)).
При отсутствии токена тесты корректно пропускаются (без падений).

```bash
# Все интеграционные тесты
./gradlew test --tests "*Integration*"

# Все тесты (unit + integration)
./gradlew build
```

### Классы интеграционных тестов (46 кейсов)

| Класс | Кейсов | Что тестирует | JS-оригинал |
|-------|--------|---------------|-------------|
| `WbPrivateApiIntegrationTest` | 17 | search, filters, supplier, metadata, keyHint, similarByNm, getListOfProducts | `WBPrivateAPI.test.js` |
| `WbProductIntegrationTest` | 4 | Product.create, getFeedbacks, getQuestionsCount, getQuestions | `WBProduct.test.js` |
| `WbCatalogIntegrationTest` | 3 | page(), getPosition(), non-existent SKU | `WBCatalog.test.js` |
| `ConstantsUrlsIntegrationTest` | 17 | домены (*.wb.ru, wildberries.ru, wbbasket.ru…), версии API (v18, v8, v4…), константы, URL-паттерны | `Constants.URLs.integration.test.js` |
| `WbProductStocksIntegrationTest` | 3 | getStocks для найденного товара, множественные товары, несуществующий товар | `WBProduct.getStocks.test.js` |

**Итого: 113 тестов** (67 unit + 46 integration) — все проходят, `./gradlew clean build` — BUILD SUCCESSFUL.

---

## Сборка и запуск

### Требования

- JDK 21+
- Gradle 9.3.0 (через `./gradlew`, wrapper включён)

### Команды

```bash
./gradlew clean build         # Полная сборка + тесты
./gradlew compileKotlin       # Только компиляция main
./gradlew test                # Только тесты
./gradlew build               # Без clean (инкрементально)
```

### Токен (опционально)

Для доступа к `__internal`-эндпойнтам WB нужен токен `x_wbaas_token`.
Получается скриптом `scripts/get-wb-token.js` из JS-проекта, сохраняется
в `.wbaas_token` (JSON `{"token":"...","expires_at":<ms>}`):

```bash
node ../wb-private-api/scripts/get-wb-token.js   # записывает .wbaas_token
```

Без токена запросы идут напрямую к `*.wb.ru` (ограниченный функционал).
С токеном — переписываются на `www.wildberries.ru/__internal/<subdomain>/`
с Cookie `x_wbaas_token`.

### Передача токена явно

```kotlin
val api = WbPrivateApi(
    destination = Destinations.MOSCOW,
    wbaasToken = "ваш_токен"
)
```

---

## Подпроект `token-fetcher`

Отдельный Gradle-подпроект для **автоматического** получения `x_wbaas_token`
из реального браузера через [Playwright for Java](https://playwright.dev/java/).
Вынесен в подпроект, чтобы основная библиотека оставалась лёгкой (Playwright
тянет ~150MB бинарников Chromium).

### Почему так

JS-скрипт `scripts/get-wb-token.js` — ручной: открыть DevTools на залогиненном
`wildberries.ru`, вставить сниппет, скопировать JSON в `.wbaas_token`. Токен
нельзя получить HTTP-запросом из бэкенда: антибот WB проверяет fingerprint
браузера и выполняет JS-челлендж. Нужен **реальный браузер** — Playwright
запускает настоящий Chromium, проходит челлендж, и `context.cookies()` читает
cookie без ручного копирования.

### Запуск

```bash
# Headless (по умолчанию — для VPS без рабочего стола)
./gradlew :token-fetcher:run

# Headed (видимое окно — если антибот подставляет капчу в headless)
./gradlew :token-fetcher:run --args="--headed"

# Свои путь и таймаут
./gradlew :token-fetcher:run --args="--out ~/.wbaas_token --timeout 60"

# Предзагрузка браузера (подготовка VPS заранее)
./gradlew :token-fetcher:run --args="--install"

# Справка
./gradlew :token-fetcher:run --args="--help"
```

Первый запуск скачает Chromium (~150MB) в кеш Playwright. Результат пишется
в `.wbaas_token` (JSON `{token, expires_at}`) — тот же формат, что читает
`SessionBuilder.readToken()`.

### Аргументы CLI

| Аргумент | По умолчанию | Назначение |
|----------|-------------|-----------|
| `--headed` | (headless) | Видимое окно браузера. Полезно, если антибот подставляет капчу — пользователь проходит её вручную, скрипт продолжает ждать cookie |
| `--out <path>` | `./.wbaas_token` | Путь к файлу токена |
| `--timeout <sec>` | `30` | Таймаут ожидания cookie после загрузки страницы |
| `--browser <type>` | `chromium` | `chromium` / `firefox` / `webkit` |
| `--install` | — | Только предзагрузка браузера (без получения токена) |
| `-h`, `--help` | — | Справка |

### Алгоритм

1. Запуск Chromium (`--disable-blink-features=AutomationControlled`, UA/viewport/locale как у десктопа).
2. Открытие `https://www.wildberries.ru`, ожидание `networkidle` (с таймаутом).
3. Poll `context.cookies()` каждые 500мс до появления `x_wbaas_token` или истечения `--timeout`.
4. В headed-режиме при капче пользователь проходит её вручную — опрос продолжается весь таймаут.
5. Запись `{token, expires_at}` (expires из cookie, fallback +14 дней) в `--out`.
6. Закрытие браузера.

### Риски и нюансы

- **Антибот может блокировать headless.** Если за `--timeout` cookie не появилась —
  попробуйте `--headed` (на машине с рабочим столом) и пройдите капчу. Токен
  действителен ~14 дней, его можно получить локально и скопировать на VPS.
- **Playwright на VPS** требует системных зависимостей (`libnss3`, `libatk1.0`,
  и т.п.). Для headless Linux обычно достаточно `apt install` пакета из
  [официального списка](https://playwright.dev/java/docs/browsers#install-system-dependencies).
- **Токен ~14 дней.** Refresh по расписанию не реализован (по запросу —
  ответственность пользователя). Перезапустите `:token-fetcher:run` при истечении.
- **Stealth.** Ванильный Playwright в большинстве случаев проходит челлендж WB.
  При агрессивном детекте можно добавить `playwright-extra`+`stealth` (TODO).

### Использование в коде

`token-fetcher` — отдельный CLI, **не зависит** от основной библиотеки и не
тянет её в runtime. Основная либа читает результат через `SessionBuilder.readToken()`
из `.wbaas_token` — никаких изменений в API не требуется.

---

## Известные ограничения

1. **`setToken()` не реализован** — выбрасывает `UnsupportedOperationException`.
   Токен фиксируется при создании `Session`. Для смены токена создайте новый
   `WbPrivateApi(wbaasToken = ...)`. Это задокументированное отличие от JS,
   где `Session.defaults.headers.common.Cookie` мутабелен. Если нужна
   рантайм-смена токена — это TODO (требует рефакторинга `SessionConfig`).

2. **Двузначные плейсхолдеры `{10}+`** в `formatUrl` возвращаются как литерал.
   JS `string-format` подставил бы 11-й аргумент. **Недостижимо** в текущих
   шаблонах WB (максимум `{4}`). Фикс — тривиален при появлении таких шаблонов.

3. ~~**Интеграционные тесты не перенесены.**~~ ✅ **Перенесены.** Все 4 JS
   интеграционных тест-сьюта портированы в 5 JVM-классов (46 кейсов).
   Запускаются через `./gradlew test --tests "*Integration*"`.
   При отсутствии `.wbaas_token` корректно пропускаются.

4. **URL-кодирование пробелов/кириллицы на транспортном уровне.** `appendQuery`
   эмитит сырые символы (как JS `qs encode:false`), но OkHttp-транспорт
   перекодирует пробелы/кириллицу в `%XX` при `Request.Builder().url()`.
   Это совпадает с поведением JS `undici`/`fetch` (браузерный HTTP-клиент
   также кодирует). Итоговые wire-запросы JS и Kotlin эквивалентны.

5. **`Product.getStocks()` не делает отдельный сетевой запрос** — возвращает
   `details.sizes[0].stocks` (как JS). При пустых `rawDetails` подгружает
   их через `getDetailsData()`.

---

## Вердикт верификации

Независимый verification-агент (этап initial port) проверил: сборка, 67 тестов,
алгоритмическая точность (CRC16, basket, imageURL, qs), покрытие API-методами,
поведение сессии.

**VERDICT: PASS** — все проверки пройдены, единственное наблюдение
(`{10}`-плейсхолдер) недостижимо в текущих шаблонах WB.

После верификации дополнительно:
- Добавлен подпроект `token-fetcher` (авто-получение токена через Playwright)
- Исправлены 3 бага live-запроса (gzip декодинг, `isInternal` для `__internal` URL,
  селектор `EXACTMATCH_INTERNAL`/`EXACTMATCH`)
- Портированы все JS интеграционные тесты (46 кейсов, live WB API)
- **Итог: 113 тестов (67 unit + 46 integration), BUILD SUCCESSFUL, 0 предупреждений**
- Создан `README.md` + обновлён `WIKI.md`
