package com.wb.privateapi.util

/**
 * Замена JS `string-format` для шаблонов вида `"https://...{0}...{1}..."`.
 *
 * Подставляет значения по позиционному индексу (`{0}`, `{1}`, …),
 * повторяя поведение `format(template, a, b, …)` из библиотеки `string-format`.
 *
 * Важно: при отсутствии аргумента для плейсхолдера (например,
 * `formatUrl("https://x/{0}")` без аргументов) плейсхолдер заменяется
 * на пустую строку — это поведение `string-format` v3, подтверждено
 * напрямую (`format('https://x/{0}')` → `"https://x/"`).
 *
 * Не использует `String.format`, чтобы не требовать экранирования `%`.
 */
fun formatUrl(template: String, vararg args: Any?): String {
    val builder = StringBuilder(template.length + args.size * 8)
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c == '{' && i + 2 < template.length && template[i + 2] == '}') {
            val digit = template[i + 1]
            if (digit in '0'..'9') {
                val idx = digit - '0'
                // Поведение string-format: отсутствующий аргумент → пустая строка
                if (idx < args.size) {
                    builder.append(args[idx]?.toString().orEmpty())
                }
                i += 3
                continue
            }
        }
        builder.append(c)
        i++
    }
    return builder.toString()
}
