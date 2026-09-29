package org.neteinstein.snap2sheet.platform

import java.util.Locale

actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()
