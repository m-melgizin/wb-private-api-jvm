package com.wb.privateapi.model

/**
 * Вопрос к товару. Перенос `WBQuestion` из `WBQuestion.js`.
 *
 * JS использует `Object.assign(this, question)`. Здесь храним исходный `raw: Map`
 * и предоставляем доступ к произвольным полям через [get].
 */
class Question(val raw: Map<String, Any?>) {

    /** Произвольное поле ответа. */
    operator fun get(key: String): Any? = raw[key]

    /** Идентификатор вопроса. */
    val id: Long? by lazy { (raw["id"] as? Number)?.toLong() }

    /** Текст вопроса. */
    val questionText: String? get() = raw["question"] as? String

    /** Имя автора. */
    val userName: String? get() = raw["name"] as? String

    override fun toString(): String = "Question(id=$id, question=$questionText)"
}
