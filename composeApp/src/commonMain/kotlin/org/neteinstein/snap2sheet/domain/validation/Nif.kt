package org.neteinstein.snap2sheet.domain.validation

/** Portuguese tax number (NIF / NIPC) helpers. */
object Nif {
    /** The NIF AT uses on invoices issued to an unidentified final consumer. */
    const val FINAL_CONSUMER = "999999990"

    /** Nine digits whose last one is the mod-11 check digit of the first eight. */
    fun isValid(value: String): Boolean {
        val digits = normalize(value)
        if (digits.length != 9 || !digits.all { it.isDigit() }) return false
        val sum = (0 until 8).sumOf { i -> (digits[i] - '0') * (9 - i) }
        val check = (11 - sum % 11).let { if (it >= 10) 0 else it }
        return check == digits[8] - '0'
    }

    fun normalize(value: String): String = value.filterNot { it.isWhitespace() }

    /** "500100200" → "500 100 200"; anything that isn't nine digits is returned unchanged. */
    fun format(value: String): String {
        val digits = normalize(value)
        return if (digits.length == 9 && digits.all { it.isDigit() }) digits.chunked(3).joinToString(" ") else value
    }
}
