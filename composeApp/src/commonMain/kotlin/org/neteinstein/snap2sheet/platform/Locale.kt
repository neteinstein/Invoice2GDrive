package org.neteinstein.snap2sheet.platform

/** The device's preferred language as a lowercase BCP-47 tag, e.g. "pt-pt" or "en-us". */
expect fun deviceLanguageTag(): String

/** English by default; Portuguese when the device's language is Portuguese. Fixed for the process. */
val isPortuguese: Boolean = deviceLanguageTag().lowercase().startsWith("pt")

/** Picks the English or Portuguese text for the device language. */
fun tr(en: String, pt: String): String = if (isPortuguese) pt else en
