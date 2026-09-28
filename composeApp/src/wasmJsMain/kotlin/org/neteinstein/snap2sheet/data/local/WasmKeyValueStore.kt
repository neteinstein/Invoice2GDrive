package org.neteinstein.snap2sheet.data.local

import kotlinx.browser.localStorage

private class WasmKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = localStorage.getItem(key)

    override fun putString(key: String, value: String) {
        localStorage.setItem(key, value)
    }

    override fun remove(key: String) {
        localStorage.removeItem(key)
    }
}

actual fun platformKeyValueStore(): KeyValueStore = WasmKeyValueStore()
