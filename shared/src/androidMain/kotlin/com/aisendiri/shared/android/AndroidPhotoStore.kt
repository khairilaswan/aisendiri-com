package com.aisendiri.shared.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aisendiri.shared.platform.Photo
import com.aisendiri.shared.platform.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Photos the user attaches to a chat. They are copied, shrunk and kept in the app's private
 * storage, next to the chats, and never leave the phone.
 *
 * [picker] opens the system photo picker and [camera] the phone's camera app, writing to the
 * given Uri. Both need an activity, so MainActivity provides them. Neither needs a permission.
 */
class AndroidPhotoStore(
    private val context: Context,
    private val picker: (onResult: (Uri?) -> Unit) -> Unit,
    private val camera: (target: Uri, onResult: (Boolean) -> Unit) -> Unit,
) : PhotoStore {
    private val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_.-]"), "_"))

    override suspend fun pick(): String? {
        val uri = suspendCancellableCoroutine<Uri?> { cont -> picker { cont.resume(it) } } ?: return null
        return import(uri)
    }

    override suspend fun takePhoto(): String? {
        val shot = File(cameraDir, "shot.jpg").apply { delete() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", shot)
        val ok = suspendCancellableCoroutine<Boolean> { cont -> camera(uri) { cont.resume(it) } }
        return try {
            if (ok && shot.length() > 0) import(Uri.fromFile(shot)) else null
        } finally {
            shot.delete()
        }
    }

    /** Copies the photo upright and no larger than [MAX_SIDE], so it stays small on disk. */
    private suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
            val upright = scaleAndRotate(decoded, rotation)
            val id = "${System.currentTimeMillis()}-${Random.nextInt(0, Int.MAX_VALUE)}.jpg"
            file(id).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            upright.recycle()
            id
        }.getOrNull()
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Int): Bitmap {
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(src.width, src.height))
        if (scale == 1f && rotation == 0) return src
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
    }

    override suspend fun thumbnail(id: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(id)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIDE) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }

    override suspend fun rgb(id: String): Photo? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = BitmapFactory.decodeFile(file(id).path, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return@runCatching null
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val rgb = ByteArray(pixels.size * 3)
            for (i in pixels.indices) {
                val p = pixels[i]
                rgb[i * 3] = (p shr 16).toByte()
                rgb[i * 3 + 1] = (p shr 8).toByte()
                rgb[i * 3 + 2] = p.toByte()
            }
            Photo(id, bmp.width, bmp.height, rgb).also { bmp.recycle() }
        }.getOrNull()
    }

    override suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        ids.forEach { file(it).delete() }
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }

    private companion object {
        /** Big enough to read a screenshot's text; image AIs shrink photos further themselves. */
        const val MAX_SIDE = 1280
        const val THUMB_SIDE = 480
    }
}
