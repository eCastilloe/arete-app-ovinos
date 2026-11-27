package com.arete.app.data.local

import android.content.Context
import androidx.core.content.edit
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/** Keeps selected photos private to this installation until an API upload is implemented. */
class AnimalPhotoStore(private val context: Context) {
    fun cameraUri(): Uri {
        val directory = File(context.cacheDir, "animal-camera").apply { mkdirs() }
        return FileProvider.getUriForFile(context, "${context.packageName}.animalphotos",
            File.createTempFile("capture-", ".jpg", directory))
    }

    fun import(uri: Uri): String {
        val temporary = File.createTempFile("animal-import-", ".img", context.cacheDir)
        var bitmap: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > 25 * 1024 * 1024) throw IOException("La foto supera 25 MB.")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("No se pudo abrir la foto.")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Selecciona una imagen válida.")
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            bitmap = BitmapFactory.decodeFile(temporary.path, options) ?: throw IOException("No se pudo leer la foto.")
            val orientation = runCatching { ExifInterface(temporary.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val directory = File(context.filesDir, "animal-photos").apply { mkdirs() }
            val result = File(directory, "${UUID.randomUUID()}.jpg")
            try {
                result.outputStream().use { if (!oriented.compress(Bitmap.CompressFormat.JPEG, 85, it)) throw IOException("No se pudo guardar la foto.") }
            } catch (e: Exception) { result.delete(); throw e }
            return result.path
        } finally {
            if (oriented !== bitmap) oriented?.recycle()
            bitmap?.recycle()
            temporary.delete()
        }
    }
    private val associations = context.getSharedPreferences("animal_photos", android.content.Context.MODE_PRIVATE)
    fun find(ranchId: String, animalId: String): String? = associations.getString("$ranchId/$animalId", null)?.takeIf { File(it).isFile }
    fun associate(ranchId: String, animalId: String, path: String?) {
        associations.edit {
            if (path == null) remove("$ranchId/$animalId") else putString("$ranchId/$animalId", path)
        }
    }
}
