package com.meow.ledger.shared.stats

import java.util.Calendar

internal actual fun todayAsDay(): Long {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.YEAR) * 10000L +
        (calendar.get(Calendar.MONTH) + 1) * 100L +
        calendar.get(Calendar.DAY_OF_MONTH)
}
