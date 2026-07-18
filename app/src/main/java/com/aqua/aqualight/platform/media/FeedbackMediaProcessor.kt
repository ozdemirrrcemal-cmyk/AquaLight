package com.aqua.aqualight.platform.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FeedbackMediaProcessor {
    suspend fun process(uri: Uri): FeedbackMediaProcessingResult

    fun restore(
        path: String?,
        displayName: String?,
        width: Int?,
        height: Int?,
        byteCount: Long?
    ): ProcessedFeedbackMedia?

    suspend fun delete(path: String?)

    suspend fun cleanupExpired()
}

data class ProcessedFeedbackMedia(
    val path: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val byteCount: Long
) {
    val file: File
        get() = File(path)
}

sealed interface FeedbackMediaProcessingResult {
    data class Success(
        val media: ProcessedFeedbackMedia
    ) : FeedbackMediaProcessingResult

    data class Failure(
        val kind: FeedbackMediaFailureKind,
        val cause: Throwable? = null
    ) : FeedbackMediaProcessingResult
}

enum class FeedbackMediaFailureKind {
    UNSUPPORTED_TYPE,
    SOURCE_TOO_LARGE,
    TOO_MANY_PIXELS,
    INVALID_IMAGE,
    OUTPUT_TOO_LARGE,
    OUT_OF_MEMORY,
    IO
}

class AndroidFeedbackMediaProcessor(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : FeedbackMediaProcessor {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val outputDirectory = File(appContext.cacheDir, DIRECTORY_NAME)

    override suspend fun process(uri: Uri): FeedbackMediaProcessingResult = withContext(dispatcher) {
        cleanupExpiredInternal()

        val mimeType = resolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            return@withContext FeedbackMediaProcessingResult.Failure(
                FeedbackMediaFailureKind.UNSUPPORTED_TYPE
            )
        }

        val sourceBytes = sourceLength(uri)
        if (sourceBytes != null && sourceBytes > FeedbackImagePolicy.MAX_SOURCE_BYTES) {
            return@withContext FeedbackMediaProcessingResult.Failure(
                FeedbackMediaFailureKind.SOURCE_TOO_LARGE
            )
        }

        val bounds = decodeBounds(uri)
            ?: return@withContext FeedbackMediaProcessingResult.Failure(
                FeedbackMediaFailureKind.INVALID_IMAGE
            )

        when (
            FeedbackImagePolicy.validateSource(
                width = bounds.first,
                height = bounds.second,
                sourceBytes = sourceBytes
            )
        ) {
            FeedbackImagePolicyResult.Accepted -> Unit
            FeedbackImagePolicyResult.SourceTooLarge -> {
                return@withContext FeedbackMediaProcessingResult.Failure(
                    FeedbackMediaFailureKind.SOURCE_TOO_LARGE
                )
            }
            FeedbackImagePolicyResult.TooManyPixels -> {
                return@withContext FeedbackMediaProcessingResult.Failure(
                    FeedbackMediaFailureKind.TOO_MANY_PIXELS
                )
            }
            FeedbackImagePolicyResult.InvalidDimensions -> {
                return@withContext FeedbackMediaProcessingResult.Failure(
                    FeedbackMediaFailureKind.INVALID_IMAGE
                )
            }
        }

        val outputFile = createOutputFile()
            ?: return@withContext FeedbackMediaProcessingResult.Failure(
                FeedbackMediaFailureKind.IO
            )

        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null

        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = FeedbackImagePolicy.calculateInSampleSize(
                    width = bounds.first,
                    height = bounds.second
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            decoded = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: throw InvalidImageException()

            oriented = applyExifOrientation(uri, decoded!!)
            val target = FeedbackImagePolicy.targetSize(
                width = oriented!!.width,
                height = oriented!!.height
            )
            scaled = if (target.first == oriented!!.width && target.second == oriented!!.height) {
                oriented
            } else {
                Bitmap.createScaledBitmap(
                    oriented!!,
                    target.first,
                    target.second,
                    true
                )
            }

            val compressed = compressWithinLimit(
                bitmap = scaled!!,
                outputFile = outputFile
            )
            if (!compressed) {
                outputFile.delete()
                return@withContext FeedbackMediaProcessingResult.Failure(
                    FeedbackMediaFailureKind.OUTPUT_TOO_LARGE
                )
            }

            FeedbackMediaProcessingResult.Success(
                ProcessedFeedbackMedia(
                    path = outputFile.canonicalPath,
                    displayName = displayName(uri),
                    width = scaled!!.width,
                    height = scaled!!.height,
                    byteCount = outputFile.length()
                )
            )
        } catch (error: OutOfMemoryError) {
            outputFile.delete()
            FeedbackMediaProcessingResult.Failure(
                kind = FeedbackMediaFailureKind.OUT_OF_MEMORY,
                cause = error
            )
        } catch (error: InvalidImageException) {
            outputFile.delete()
            FeedbackMediaProcessingResult.Failure(
                kind = FeedbackMediaFailureKind.INVALID_IMAGE,
                cause = error
            )
        } catch (error: Throwable) {
            outputFile.delete()
            FeedbackMediaProcessingResult.Failure(
                kind = FeedbackMediaFailureKind.IO,
                cause = error
            )
        } finally {
            recycleDistinct(scaled, oriented, decoded)
        }
    }

    override fun restore(
        path: String?,
        displayName: String?,
        width: Int?,
        height: Int?,
        byteCount: Long?
    ): ProcessedFeedbackMedia? {
        val file = resolveOwnedFile(path) ?: return null
        val actualBytes = file.length()
        if (!file.isFile || actualBytes <= 0L || actualBytes > FeedbackImagePolicy.MAX_OUTPUT_BYTES) {
            return null
        }
        val restoredWidth = width?.takeIf { it > 0 } ?: return null
        val restoredHeight = height?.takeIf { it > 0 } ?: return null
        val restoredBytes = byteCount?.takeIf { it == actualBytes } ?: actualBytes

        return ProcessedFeedbackMedia(
            path = file.canonicalPath,
            displayName = displayName?.takeIf(String::isNotBlank) ?: DEFAULT_DISPLAY_NAME,
            width = restoredWidth,
            height = restoredHeight,
            byteCount = restoredBytes
        )
    }

    override suspend fun delete(path: String?) = withContext(dispatcher) {
        resolveOwnedFile(path)?.delete()
        Unit
    }

    override suspend fun cleanupExpired() = withContext(dispatcher) {
        cleanupExpiredInternal()
    }

    private fun sourceLength(uri: Uri): Long? {
        return runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
        }.getOrNull()
    }

    private fun decodeBounds(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private fun applyExifOrientation(
        uri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        val orientation = resolver.openInputStream(uri)?.use { input ->
            runCatching {
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            else -> return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    private fun compressWithinLimit(
        bitmap: Bitmap,
        outputFile: File
    ): Boolean {
        for (quality in COMPRESSION_QUALITIES) {
            FileOutputStream(outputFile, false).buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw IllegalStateException("Feedback screenshot compression failed.")
                }
                output.flush()
            }

            if (outputFile.length() in 1..FeedbackImagePolicy.MAX_OUTPUT_BYTES) {
                return true
            }
        }
        return false
    }

    private fun displayName(uri: Uri): String {
        val queriedName = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

        return queriedName?.takeIf(String::isNotBlank) ?: DEFAULT_DISPLAY_NAME
    }

    private fun createOutputFile(): File? {
        return runCatching {
            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                error("Feedback media directory could not be created.")
            }
            File(
                outputDirectory,
                "feedback_${clockMillis()}_${UUID.randomUUID()}.jpg"
            ).apply {
                if (!createNewFile()) error("Feedback media file could not be created.")
            }
        }.getOrNull()
    }

    private fun resolveOwnedFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val root = runCatching { outputDirectory.canonicalFile }.getOrNull() ?: return null
        val inside = candidate.path == root.path ||
            candidate.path.startsWith(root.path + File.separator)
        return candidate.takeIf { inside }
    }

    private fun cleanupExpiredInternal() {
        if (!outputDirectory.exists()) return
        val cutoff = clockMillis() - MAX_TEMP_AGE_MILLIS
        outputDirectory.listFiles()
            ?.filter { file -> file.isFile && file.lastModified() < cutoff }
            ?.forEach(File::delete)
    }

    private fun recycleDistinct(vararg bitmaps: Bitmap?) {
        val recycled = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Bitmap, Boolean>()
        )
        bitmaps.forEach { bitmap ->
            if (bitmap != null && recycled.add(bitmap) && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private class InvalidImageException : IllegalArgumentException("Invalid feedback image")

    private companion object {
        const val DIRECTORY_NAME = "feedback_media"
        const val DEFAULT_DISPLAY_NAME = "screenshot.jpg"
        const val MAX_TEMP_AGE_MILLIS = 24L * 60L * 60L * 1000L
        val COMPRESSION_QUALITIES = intArrayOf(86, 76, 66, 56, 46)
    }
}
