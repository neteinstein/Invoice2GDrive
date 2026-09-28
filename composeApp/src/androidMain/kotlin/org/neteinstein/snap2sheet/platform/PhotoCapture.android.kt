package org.neteinstein.snap2sheet.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private const val MAX_EDGE = 2000

@Composable
actual fun rememberPhotoPicker(onResult: (CapturedPhoto?) -> Unit): (PhotoSource) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult = rememberUpdatedState(onResult)
    val captureUri = remember { captureFileUri(context) }

    fun deliver(uri: Uri?) {
        if (uri == null) {
            currentOnResult.value(null)
            return
        }
        scope.launch {
            val photo = withContext(Dispatchers.IO) { runCatching { normalize(context, uri) }.getOrNull() }
            currentOnResult.value(photo)
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        deliver(if (saved) captureUri else null)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> deliver(uri) }
    // Because the app declares CAMERA (for the QR scanner), Android only lets it launch the
    // camera app once that permission is granted.
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(captureUri) else currentOnResult.value(null)
    }

    return remember(takePicture, pickImage, requestCamera) {
        { source ->
            when (source) {
                PhotoSource.CAMERA ->
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        takePicture.launch(captureUri)
                    } else {
                        requestCamera.launch(Manifest.permission.CAMERA)
                    }
                PhotoSource.LIBRARY -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

private fun captureFileUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.fatura.photos", File(dir, "invoice.jpg"))
}

/** Decodes at most ~2× the target size, applies the EXIF rotation, scales to [MAX_EDGE] and re-encodes as JPEG. */
private fun normalize(context: Context, uri: Uri): CapturedPhoto? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    val normalized = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val out = ByteArrayOutputStream()
    normalized.compress(Bitmap.CompressFormat.JPEG, 82, out)
    if (normalized !== decoded) normalized.recycle()
    decoded.recycle()
    return CapturedPhoto(out.toByteArray(), "image/jpeg")
}
