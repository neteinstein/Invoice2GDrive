package org.neteinstein.snap2sheet

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.neteinstein.snap2sheet.domain.format.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class FormattingTest {

    private val utc = TimeZone.UTC
    private val now = Instant.parse("2026-09-18T14:32:00Z").toEpochMilliseconds()
    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    @Test
    fun amounts() {
        assertEquals("18.42", Formatting.amount(18.42))
        assertEquals("5.00", Formatting.amount(4.999))
        assertEquals("-€3.00", Formatting.euros(-3.0))
    }

    @Test
    fun parseAmount_acceptsPortugueseAndEnglishNotation() {
        assertEquals(18.42, Formatting.parseAmount("18,42"))
        assertEquals(18.42, Formatting.parseAmount("€ 18.42"))
        assertEquals(1234.5, Formatting.parseAmount("1.234,50"))
        assertEquals(1234.5, Formatting.parseAmount("1,234.50"))
        assertNull(Formatting.parseAmount("abc"))
        assertNull(Formatting.parseAmount(""))
    }

    @Test
    fun dates() {
        assertEquals("18/09/2026", Formatting.date(LocalDate(2026, 9, 18)))
        assertEquals(LocalDate(2026, 9, 18), Formatting.parseDate("18/09/2026"))
        assertEquals(LocalDate(2026, 9, 18), Formatting.parseDate("2026-09-18"))
        assertNull(Formatting.parseDate("31/02/2026"))
        assertNull(Formatting.parseDate("18/09"))
    }

    @Test
    fun scannedAt_isRelativeForTheLastWeek() {
        assertEquals("Today · 09:14", Formatting.scannedAt(at("2026-09-18T09:14:00Z"), now, utc))
        assertEquals("Yesterday · 18:05", Formatting.scannedAt(at("2026-09-17T18:05:00Z"), now, utc))
        assertEquals("Mon · 10:00", Formatting.scannedAt(at("2026-09-14T10:00:00Z"), now, utc))
        assertEquals("01/09/2026", Formatting.scannedAt(at("2026-09-01T10:00:00Z"), now, utc))
    }

    @Test
    fun scanGroup() {
        assertEquals("Today", Formatting.scanGroup(at("2026-09-18T00:01:00Z"), now, utc))
        assertEquals("This week", Formatting.scanGroup(at("2026-09-13T10:00:00Z"), now, utc))
        assertEquals("This month", Formatting.scanGroup(at("2026-09-02T10:00:00Z"), now, utc))
        assertEquals("August 2026", Formatting.scanGroup(at("2026-08-30T10:00:00Z"), now, utc))
    }
}
