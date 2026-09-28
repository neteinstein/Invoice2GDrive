package org.neteinstein.snap2sheet.data.local

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

private class IosPhotoStore : PhotoStore {
    private val dir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        "$base/invoice-photos".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    override suspend fun save(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val key = NSUUID().UUIDString
        bytes.toNSData().writeToFile("$dir/$key", atomically = true)
        key
    }

    override suspend fun load(key: String): ByteArray? = withContext(Dispatchers.IO) {
        NSData.dataWithContentsOfFile("$dir/$key")?.toByteArray()
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { NSFileManager.defaultManager.removeItemAtPath("$dir/$key", error = null) }
    }
}

internal fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

actual fun platformPhotoStore(): PhotoStore = IosPhotoStore()
