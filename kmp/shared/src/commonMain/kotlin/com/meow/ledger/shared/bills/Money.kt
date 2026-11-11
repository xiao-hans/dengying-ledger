package com.meow.ledger.shared.bills

/**
 * 元转分。用字符串直接拆，不走 double，避免 0.1 这类值累加出错。
 * 空串、非数字、负数、超过两位小数都返回 null，由调用方提示用户，不静默改值。
 */
fun toCents(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty() || text.startsWith("-") || text.startsWith("+")) return null
    val dot = text.indexOf('.')
    val whole = if (dot < 0) text else text.substring(0, dot)
    val fraction = if (dot < 0) "" else text.substring(dot + 1)
    if (fraction.length > 2 || fraction.contains('.')) return null
    if (whole.isEmpty() || !whole.all { it.isDigit() }) return null
    if (!fraction.all { it.isDigit() }) return null
    val yuan = whole.toLongOrNull() ?: return null
    val cents = fraction.padEnd(2, '0').toLongOrNull() ?: return null
    return yuan * 100 + cents
}

/** 分转回输入框文本：3500 显示 35，3550 显示 35.5，3556 显示 35.56。 */
fun centsToYuan(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val whole = abs / 100
    val frac = abs % 100
    val tail = when {
        frac == 0L -> ""
        frac % 10 == 0L -> ".${frac / 10}"
        else -> ".${frac.toString().padStart(2, '0')}"
    }
    return "$sign$whole$tail"
}
