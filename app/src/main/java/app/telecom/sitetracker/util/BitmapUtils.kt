package app.telecom.sitetracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object BitmapUtils {

    fun getPhotosDir(context: Context): File {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Prepares a file in filesDir/photos/ and returns a content URI for camera capture.
     */
    fun createImageCaptureUri(context: Context): Pair<Uri, File> {
        val photosDir = getPhotosDir(context)
        val fileName = "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val file = File(photosDir, fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(uri, file)
    }

    /**
     * Copies a user-selected image URI (from gallery/SAF) into the app's internal photos/ directory.
     * Returns the relative path e.g. "photos/photo_xyz.jpg".
     */
    fun copyUriToPhotos(context: Context, sourceUri: Uri): String? {
        return try {
            val photosDir = getPhotosDir(context)
            val fileName = "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(photosDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            "photos/$fileName"
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Safely loads and scales down a bitmap thumbnail to prevent OOM on 2GB RAM devices.
     */
    fun loadThumbnailBitmap(file: File, maxDim: Int = 256): Bitmap? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val width = options.outWidth
            val height = options.outHeight
            var inSampleSize = 1

            if (height > maxDim || width > maxDim) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= maxDim && halfWidth / inSampleSize >= maxDim) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Low memory consumption
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: Throwable) {
            null
        }
    }
}
