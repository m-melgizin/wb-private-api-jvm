package com.wb.privateapi.session

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * JSON-кодек для сессии. Гибко парсит произвольные ответы WB
 * (без strict-режима, игнорирует неизвестные ключи).
 *
 * Возвращает доменно-нейтральные типы:
 *  - объект → [Map]<String, Any?>
 *  - массив → [List]<Any?>
 *  - строка/число/boolean → [String]/[Double]/[Long]/[Boolean]
 *  - null → null
 */
@OptIn(ExperimentalSerializationApi::class)
internal object SessionJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Разобрать JSON-строку в `Any?` (Map/List/примитив). */
    fun parse(text: String): Any? {
        val element = json.parseToJsonElement(text)
        return element.toAny()
    }

    /** Сериализовать произвольный объект (Map/List/примитив) в JSON-строку. */
    fun encode(value: Any?): String {
        val element = value.toJsonElement()
        return json.encodeToString(JsonElement.serializer(), element)
    }

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonObject -> entries.associate { (k, v) -> k to v.toAny() }
        is JsonArray -> map { it.toAny() }
        is JsonPrimitive -> when {
            this.isString -> contentOrNull
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> contentOrNull
        }
        else -> null
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonPrimitive(null)
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(
            entries.associate { (k, v) -> k.toString() to v.toJsonElement() }
        )
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
}

/** Точка входа для сессии. */
internal fun defaultJsonParse(text: String): Any? = SessionJson.parse(text)
internal fun defaultJsonEncode(value: Any?): String = SessionJson.encode(value)
