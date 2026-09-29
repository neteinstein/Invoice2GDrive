package org.neteinstein.snap2sheet.platform

private fun browserLanguage(): String = js("(navigator.languages && navigator.languages[0]) || navigator.language || 'en'")

actual fun deviceLanguageTag(): String = browserLanguage()
