package org.neteinstein.snap2sheet.platform

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun deviceLanguageTag(): String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
