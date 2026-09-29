package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private fun readPrefersDark(): Boolean =
    js("window.matchMedia('(prefers-color-scheme: dark)').matches")

private fun listenPrefersDark(onChange: () -> Unit): () -> Unit = js(
    """(function () {
        var mq = window.matchMedia('(prefers-color-scheme: dark)');
        mq.addEventListener('change', onChange);
        return function () { mq.removeEventListener('change', onChange); };
    })()"""
)

// Read straight from the browser's media query rather than relying on the Compose runtime's own
// system-theme plumbing, which doesn't reliably report dark mode on the web target.
@Composable
actual fun systemPrefersDark(): Boolean {
    var dark by remember { mutableStateOf(readPrefersDark()) }
    DisposableEffect(Unit) {
        val stop = listenPrefersDark { dark = readPrefersDark() }
        onDispose { stop() }
    }
    return dark
}

actual val showsThemeToggle: Boolean = true
