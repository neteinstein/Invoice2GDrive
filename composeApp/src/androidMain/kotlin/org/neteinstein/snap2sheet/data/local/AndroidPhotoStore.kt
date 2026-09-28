package org.neteinstein.snap2sheet.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private class AndroidPhotoStore(private val dir: File) : PhotoStore {
    override suspend fun save(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val key = UUID.randomUUID().toString()
        dir.mkdirs()
        File(dir, key).writeBytes(bytes)
        key
    }

    override suspend fun load(key: String): ByteArray? = withContext(Dispatchers.IO) {
        File(dir, key).takeIf { it.exists() }?.readBytes()
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { File(dir, key).delete() }
    }
}

actual fun platformPhotoStore(): PhotoStore = AndroidPhotoStore(File(AndroidAppContext.instance.filesDir, "invoice-photos"))
