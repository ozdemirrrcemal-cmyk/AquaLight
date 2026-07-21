package com.aqua.aqualight.platform.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface BoundedImageProcessor {
    suspend fun process(uri: Uri): ImageProcessingResult

    suspend fun delete(path: String?)

    suspend fun cleanupExpired()
}

data class ProcessedImage(
    val path: String,
    val width: Int,
    val height: Int,
    val byteCount: Long
)

sealed interface ImageProcessingResult {
    data class Success(
        val media: ProcessedImage
    ) : ImageProcessingResult

    data class Failure(
        val kind: ImageProcessingFailureKind,
        val cause: Throwable? = null
    ) : ImageProcessingResult
}

enum class ImageProcessingFailureKind {
    UNSUPPORTED_TYPE,
    SOURCE_TOO_LARGE,
    TOO_MANY_PIXELS,
    INVALID_IMAGE,
    OUTPUT_TOO_LARGE,
    OUT_OF_MEMORY,
    IO
}

/**
 * Narrow source boundary so URI ownership and bounded-copy behavior can be tested without
 * coupling the processor to a particular ContentProvider implementation.
 */
internal interface ImageSourceAccess {
    fun mimeType(uri: Uri): String?
    fun declaredLength(uri: Uri): Long?
    fun open(uri: Uri): InputStream?
}

private class ContentResolverImageSourceAccess(
    private val resolver: ContentResolver
) : ImageSourceAccess {

    override fun mimeType(uri: Uri): String? = runCatching {
        resolver.getType(uri)?.trim()?.lowercase()
    }.getOrNull()

    override fun declaredLength(uri: Uri): Long? = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it >= 0L }
        }
    }.getOrNull()

    override fun open(uri: Uri): InputStream? = resolver.openInputStream(uri)
}

class AndroidBoundedImageProcessor internal constructor(
    context: Context,
    private val dispatcher: CoroutineDispatcher,
    private val clockMillis: () -> Long,
    private val sourceAccess: ImageSourceAccess
) : BoundedImageProcessor {

    constructor(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        clockMillis: () -> Long = System::currentTimeMillis
    ) : this(
        context = context,
        dispatcher = dispatcher,
        clockMillis = clockMillis,
        sourceAccess = ContentResolverImageSourceAccess(
            context.applicationContext.contentResolver
        )
    )

    private val appContext = context.applicationContext
    private val outputDirectory = File(appContext.cacheDir, DIRECTORY_NAME)

    override suspend fun process(uri: Uri): ImageProcessingResult = withContext(dispatcher) {
        cleanupExpiredInternal()
        currentCoroutineContext().ensureActive()

        val mimeType = sourceAccess.mimeType(uri)
        if (isExplicitlyUnsupportedMimeType(mimeType)) {
            return@withContext ImageProcessingResult.Failure(
                ImageProcessingFailureKind.UNSUPPORTED_TYPE
            )
        }

        val declaredBytes = sourceAccess.declaredLength(uri)
        if (declaredBytes != null && declaredBytes > BoundedImagePolicy.MAX_SOURCE_BYTES) {
            return@withContext ImageProcessingResult.Failure(
                ImageProcessingFailureKind.SOURCE_TOO_LARGE
            )
        }

        var stagedSource: File? = null
        var outputFile: File? = null
        var completedSuccessfully = false
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null

        try {
            stagedSource = createOwnedFile(SOURCE_PREFIX, SOURCE_SUFFIX)
                ?: return@withContext ImageProcessingResult.Failure(
                    ImageProcessingFailureKind.IO
                )

            when (stageSource(uri, stagedSource)) {
                SourceStageResult.Success -> Unit
                SourceStageResult.Unavailable -> {
                    return@withContext ImageProcessingResult.Failure(
                        ImageProcessingFailureKind.INVALID_IMAGE
                    )
                }
            }

            currentCoroutineContext().ensureActive()
            val bounds = decodeBounds(stagedSource)
                ?: return@withContext ImageProcessingResult.Failure(
                    ImageProcessingFailureKind.INVALID_IMAGE
                )

            when (
                BoundedImagePolicy.validateSource(
                    width = bounds.first,
                    height = bounds.second,
                    sourceBytes = stagedSource.length()
                )
            ) {
                BoundedImagePolicyResult.Accepted -> Unit
                BoundedImagePolicyResult.SourceTooLarge -> {
                    return@withContext ImageProcessingResult.Failure(
                        ImageProcessingFailureKind.SOURCE_TOO_LARGE
                    )
                }
                BoundedImagePolicyResult.TooManyPixels -> {
                    return@withContext ImageProcessingResult.Failure(
                        ImageProcessingFailureKind.TOO_MANY_PIXELS
                    )
                }
                BoundedImagePolicyResult.InvalidDimensions -> {
                    return@withContext ImageProcessingResult.Failure(
                        ImageProcessingFailureKind.INVALID_IMAGE
                    )
                }
            }

            outputFile = createOwnedFile(OUTPUT_PREFIX, OUTPUT_SUFFIX)
                ?: return@withContext ImageProcessingResult.Failure(
                    ImageProcessingFailureKind.IO
                )

            val options = BitmapFactory.Options().apply {
                inSampleSize = BoundedImagePolicy.calculateInSampleSize(
                    width = bounds.first,
                    height = bounds.second
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            decoded = FileInputStream(stagedSource).buffered().use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: throw InvalidImageException()

            currentCoroutineContext().ensureActive()
            oriented = applyExifOrientation(stagedSource, decoded)
            val target = BoundedImagePolicy.targetSize(
                width = oriented.width,
                height = oriented.height
            )
            scaled = if (target.first == oriented.width && target.second == oriented.height) {
                oriented
            } else {
                Bitmap.createScaledBitmap(
                    oriented,
                    target.first,
                    target.second,
                    true
                )
            }

            currentCoroutineContext().ensureActive()
            if (!compressWithinLimit(scaled, outputFile)) {
                return@withContext ImageProcessingResult.Failure(
                    ImageProcessingFailureKind.OUTPUT_TOO_LARGE
                )
            }

            completedSuccessfully = true
            ImageProcessingResult.Success(
                ProcessedImage(
                    path = outputFile.canonicalPath,
                    width = scaled.width,
                    height = scaled.height,
                    byteCount = outputFile.length()
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: OutOfMemoryError) {
            ImageProcessingResult.Failure(
                kind = ImageProcessingFailureKind.OUT_OF_MEMORY,
                cause = error
            )
        } catch (error: SourceTooLargeException) {
            ImageProcessingResult.Failure(
                kind = ImageProcessingFailureKind.SOURCE_TOO_LARGE,
                cause = error
            )
        } catch (error: InvalidImageException) {
            ImageProcessingResult.Failure(
                kind = ImageProcessingFailureKind.INVALID_IMAGE,
                cause = error
            )
        } catch (error: Throwable) {
            ImageProcessingResult.Failure(
                kind = ImageProcessingFailureKind.IO,
                cause = error
            )
        } finally {
            stagedSource?.delete()
            if (!completedSuccessfully) outputFile?.delete()
            recycleDistinct(scaled, oriented, decoded)
        }
    }

    override suspend fun delete(path: String?) = withContext(dispatcher) {
        resolveOwnedOutputFile(path)?.delete()
        Unit
    }

    override suspend fun cleanupExpired() = withContext(dispatcher) {
        cleanupExpiredInternal()
    }

    private suspend fun stageSource(uri: Uri, destination: File): SourceStageResult {
        val input = sourceAccess.open(uri) ?: return SourceStageResult.Unavailable
        val coroutineContext = currentCoroutineContext()
        var totalBytes = 0L

        input.buffered().use { source ->
            FileOutputStream(destination, false).buffered().use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    totalBytes += read.toLong()
                    if (totalBytes > BoundedImagePolicy.MAX_SOURCE_BYTES) {
                        throw SourceTooLargeException()
                    }
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

        return if (totalBytes > 0L) SourceStageResult.Success else SourceStageResult.Unavailable
    }

    private fun decodeBounds(source: File): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(source).buffered().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

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

    private fun compressWithinLimit(bitmap: Bitmap, outputFile: File): Boolean {
        for (quality in COMPRESSION_QUALITIES) {
            FileOutputStream(outputFile, false).buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw IllegalStateException("Image compression failed.")
                }
                output.flush()
            }

            if (outputFile.length() in 1..BoundedImagePolicy.MAX_OUTPUT_BYTES) {
                return true
            }
        }
        return false
    }

    private fun createOwnedFile(prefix: String, suffix: String): File? {
        return runCatching {
            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                error("Image processing directory could not be created.")
            }
            File(
                outputDirectory,
                "${prefix}${clockMillis()}_${UUID.randomUUID()}$suffix"
            ).apply {
                if (!createNewFile()) error("Image processing file could not be created.")
            }
        }.getOrNull()
    }

    private fun resolveOwnedOutputFile(path: String?): File? {
        val candidate = resolveOwnedFile(path) ?: return null
        return candidate.takeIf { file ->
            file.name.startsWith(OUTPUT_PREFIX) && file.name.endsWith(OUTPUT_SUFFIX)
        }
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

    private fun isExplicitlyUnsupportedMimeType(mimeType: String?): Boolean {
        val normalized = mimeType?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return false
        return !normalized.startsWith("image/") && normalized !in GENERIC_BINARY_MIME_TYPES
    }

    private sealed interface SourceStageResult {
        data object Success : SourceStageResult
        data object Unavailable : SourceStageResult
    }

    private class SourceTooLargeException : IllegalArgumentException("Image source exceeds limit")
    private class InvalidImageException : IllegalArgumentException("Invalid image")

    private companion object {
        const val DIRECTORY_NAME = "bounded_images"
        const val SOURCE_PREFIX = "image_source_"
        const val SOURCE_SUFFIX = ".source"
        const val OUTPUT_PREFIX = "image_output_"
        const val OUTPUT_SUFFIX = ".jpg"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MAX_TEMP_AGE_MILLIS = 24L * 60L * 60L * 1000L
        val COMPRESSION_QUALITIES = intArrayOf(86, 76, 66, 56, 46)
        val GENERIC_BINARY_MIME_TYPES = setOf(
            "application/octet-stream",
            "binary/octet-stream",
            "application/x-unknown"
        )
    }
}
