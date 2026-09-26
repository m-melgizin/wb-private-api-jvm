package com.wb.privateapi.constant

object Urls {
    const val MAIN_MENU = "https://static-basket-01.wbbasket.ru/vol0/data/main-menu-ru-ru-v3.json"

    object Brand {
        const val INFO_BY_NAME = "https://static-basket-01.wbbasket.ru/vol0/data/brands/{0}.json"
        const val INFO = "https://static-basket-01.wbbasket.ru/vol0/data/brands-by-id/{0}.json"
        const val IMAGE = "https://static-basket-01.wbbasket.ru/vol0/brand-flow-logos/by-id/{0}.webp"
        const val CATALOG = "https://catalog.wb.ru/brands/v4/catalog"
        const val CATALOG_INTERNAL = "https://www.wildberries.ru/__internal/catalog/brands/v4/catalog"
        const val ENRICH = "https://user-data-syncer.wb.ru/sellers/api/v1/enrich_brands"
        const val FILTERS = "https://catalog.wb.ru/brands/v8/filters"
        const val FILTERS_INTERNAL = "https://www.wildberries.ru/__internal/catalog/brands/v8/filters"
    }

    object Supplier {
        const val INFO = "https://static-basket-01.wbbasket.ru/vol0/data/supplier-by-id/{0}.json"
        const val STOREFRONT = "https://static-basket-01.wbbasket.ru/vol0/constructor-api/shops/v3/{0}.json"
        const val CATALOG = "https://catalog.wb.ru/sellers/v4/catalog"
        const val CATALOG_INTERNAL = "https://www.wildberries.ru/__internal/catalog/sellers/v4/catalog"
        const val FILTERS = "https://catalog.wb.ru/sellers/v8/filters"
        const val FILTERS_INTERNAL = "https://www.wildberries.ru/__internal/catalog/sellers/v8/filters"
        const val SHIPMENT = "https://suppliers-shipment-2.wildberries.ru/api/v1/suppliers/{0}"
    }

    const val PROMOS = "https://www.wildberries.ru/webapi/settings/promo/get"

    object Product {
        const val CONTENT = "https://wbx-content-v2.wbstatic.net/ru/{0}.json"
        const val CARD = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/info/ru/card.json"
        const val SELLERS = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/info/sellers.json"
        const val PRICE_HISTORY = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/info/price-history.json"
        const val CERTIFICATE = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/info/certificate.json"
        const val EXTRADATA = "https://www.wildberries.ru/webapi/product/{0}/data"
        const val DETAILS = "https://card.wb.ru/cards/v4/detail"
        const val DETAILS_INTERNAL = "https://www.wildberries.ru/__internal/card/cards/v4/detail"
        const val FEEDBACKS = "https://feedbacks{0}.wb.ru/feedbacks/v2/{1}"
        const val QUESTIONS = "https://questions.wildberries.ru/api/v1/questions"
        const val DELIVERYDATA = "https://card.wb.ru/cards/list"
    }

    object Search {
        const val SIMILAR_BY_NM = "https://in-similar.wildberries.ru/"
        const val EXACTMATCH = "https://search.wb.ru/exactmatch/ru/common/v18/search"
        const val EXACTMATCH_INTERNAL = "https://www.wildberries.ru/__internal/search/exactmatch/ru/common/v18/search"
        const val CATALOG = "https://wbxcatalog-ru.wildberries.ru/{}/catalog"
        const val HINT = "https://suggests.wb.ru/suggests/api/v7/hint"
        const val HINT_INTERNAL = "https://www.wildberries.ru/__internal/suggests/suggests/api/v7/hint"
        const val LIST = "https://card.wb.ru/cards/v4/list"
        const val LIST_INTERNAL = "https://www.wildberries.ru/__internal/card/cards/v4/list"
    }

    object Images {
        const val TINY = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/images/tm/{4}.webp"
        const val BIG = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/images/big/{4}.webp"
        const val SMALL = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/images/c246x328/{4}.webp"
        const val MEDIUM = "https://basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/images/c516x688/{4}.webp"
    }

    object Video {
        const val HLS = "https://videonme-basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/hls/{4}/index.m3u8"
        const val MP4 = "https://videonme-basket-{0}.wbbasket.ru/vol{1}/part{2}/{3}/mp4/{4}/1.mp4"
    }

    /**
     * CDN отзывов (фото и видео). Домен `feedbackphotos.wbstatic.net` выведен
     * из эксплуатации; актуальный CDN — `geobasket.ru`, шардированный по
     * номеру, зашитому в поле `key`/`video.id` (`"{shard}/{uuid}"`).
     *
     * `{0}` — номер шарда (2 цифры, `05`, `10`, …), `{1}` — uuid файла,
     * `{2}` — размер фото (`ms` — миниатюра, `fs` — полный размер).
     */
    object Feedback {
        const val PHOTO = "https://mow-feedback-uuid-{0}-cdn-{0}.geobasket.ru/{1}/{2}.webp"
        const val VIDEO_PREVIEW = "https://mow-videofeedback-{0}-cdn-{0}.geobasket.ru/{1}/preview.webp"
        const val VIDEO_PLAYLIST = "https://mow-videofeedback-{0}-cdn-{0}.geobasket.ru/{1}/index.m3u8"
    }
}