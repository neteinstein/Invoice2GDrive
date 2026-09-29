package org.neteinstein.snap2sheet.platform

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
actual fun systemPrefersDark(): Boolean = isSystemInDarkTheme()

actual val showsThemeToggle: Boolean = false
