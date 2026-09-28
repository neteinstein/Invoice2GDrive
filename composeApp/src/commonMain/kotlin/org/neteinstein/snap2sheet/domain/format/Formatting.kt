package org.neteinstein.snap2sheet.domain.format

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Instant

/** Euro amounts and Portuguese-style dates, formatted without platform locale APIs. */
object Formatting {

    /** 1234.5 → "1234.50". */
    fun amount(value: Double): String {
        val cents = round(value * 100).toLong()
        val sign = if (cents < 0) "-" else ""
        val abs = if (cents < 0) -cents else cents
        return sign + (abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    }

    /** "€18.42", "-€3.00". */
    fun euros(value: Double): String = if (value < 0) "-€" + amount(-value) else "€" + amount(value)

    /** Parses "18,42", "€ 18.42" or "1 234,50" into euros; null when it isn't a number. */
    fun parseAmount(text: String): Double? {
        val cleaned = text.replace("€", "").filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return null
        // "1.234,50" → thousands dot + decimal comma; otherwise a lone comma is the decimal mark.
        val normalized = if (cleaned.contains(',') && cleaned.contains('.')) {
            if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) cleaned.replace(".", "").replace(',', '.') else cleaned.replace(",", "")
        } else {
            cleaned.replace(',', '.')
        }
        return normalized.toDoubleOrNull()?.let { round(it * 100) / 100 }
    }

    /** 2026-09-18 → "18/09/2026". */
    fun date(value: LocalDate): String =
        value.day.toString().padStart(2, '0') + "/" + value.month.number.toString().padStart(2, '0') + "/" + value.year

    /** "18/09/2026" (also "18-09-2026", "2026-09-18") → LocalDate; null when invalid. */
    fun parseDate(text: String): LocalDate? {
        val parts = text.trim().split('/', '-', '.').map { it.trim() }
        if (parts.size != 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        val (a, b, c) = parts.map { it.toInt() }
        return runCatching { if (parts[0].length == 4) LocalDate(a, b, c) else LocalDate(c, b, a) }.getOrNull()
    }

    private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "Today · 14:32", "Yesterday · 18:05", "Mon · 09:14", or "14/09/2026" for anything older than a week. */
    fun scannedAt(epochMillis: Long, nowEpochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val scanned = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
        val today = Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(timeZone).date
        val time = scanned.hour.toString().padStart(2, '0') + ":" + scanned.minute.toString().padStart(2, '0')
        return when {
            scanned.date == today -> "Today · $time"
            scanned.date == today.minus(DatePeriod(days = 1)) -> "Yesterday · $time"
            scanned.date > today.minus(DatePeriod(days = 7)) -> "${weekdays[scanned.date.dayOfWeek.ordinal]} · $time"
            else -> date(scanned.date)
        }
    }

    /** History section header for a scan: "Today", "Yesterday", "This week", "This month" or "September 2026". */
    fun scanGroup(epochMillis: Long, nowEpochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val scanned = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        val today = Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(timeZone).date
        return when {
            scanned == today -> "Today"
            scanned == today.minus(DatePeriod(days = 1)) -> "Yesterday"
            scanned > today.minus(DatePeriod(days = 7)) -> "This week"
            scanned.year == today.year && scanned.month == today.month -> "This month"
            else -> monthName(scanned.month.number) + " " + scanned.year
        }
    }

    /** "Edited today", "Edited 3 days ago", "Edited 14/09/2026". */
    fun lastEdited(epochMillis: Long?, nowEpochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        if (epochMillis == null) return "Google Sheets"
        val edited = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        val today = Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(timeZone).date
        val days = today.toEpochDays() - edited.toEpochDays()
        return when {
            days <= 0L -> "Edited today"
            days == 1L -> "Edited yesterday"
            days < 7L -> "Edited $days days ago"
            days < 14L -> "Edited last week"
            else -> "Edited ${date(edited)}"
        }
    }

    fun monthName(month: Int): String = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[month - 1]
}
