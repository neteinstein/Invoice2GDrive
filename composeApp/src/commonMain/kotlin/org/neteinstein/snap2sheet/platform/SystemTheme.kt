package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable

/** Whether the OS/browser currently asks for a dark appearance; recomposes when that changes. */
@Composable
expect fun systemPrefersDark(): Boolean

/** Only the web build shows an on-screen light/dark toggle; phones use the system setting. */
expect val showsThemeToggle: Boolean
