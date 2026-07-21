package com.aqua.aqualight.platform.media

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generic source names for the bounded image pipeline shared by profile and aquarium flows.
 *
 * The legacy Feedback* runtime types remain intact so existing Kotlin/Java call sites and tests keep
 * compiling while new code can use domain-neutral names.
 */
typealias ImageMediaProcessor = FeedbackMediaProcessor
typealias ProcessedImageMedia = ProcessedFeedbackMedia
typealias ImageMediaProcessingResult = FeedbackMediaProcessingResult
typealias ImageMediaFailureKind = FeedbackMediaFailureKind

/**
 * Domain-neutral composition entry point with one-release compatibility for prepared files created
 * under the former feedback-specific cache directory.
 */
class AndroidImageMediaProcessor(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val delegate: FeedbackMediaProcessor = AndroidFeedbackMediaProcessor(
        context = context,
        dispatcher = dispatcher,
        clockMillis = clockMillis
    )
) : ImageMediaProcessor {

    private val appContext = context.applicationContext
    private val legacyDirectory = File(appContext.cacheDir, LEGACY_DIRECTORY_NAME)

    override suspend fun process(uri: Uri): ImageMediaProcessingResult = delegate.process(uri)

    override fun restore(
        path: String?,
        displayName: String?,
        width: Int?,
        height: Int?,
        byteCount: Long?
    ): ProcessedImageMedia? {
        return delegate.restore(path, displayName, width, height, byteCount)
            ?: restoreLegacy(path, displayName, width, height, byteCount)
    }

    override suspend fun delete(path: String?) {
        delegate.delete(path)
        withContext(dispatcher) {
            resolveLegacyOutput(path)?.delete()
            Unit
        }
    }

    override suspend fun cleanupExpired() {
        delegate.cleanupExpired()
        withContext(dispatcher) {
            if (!legacyDirectory.exists()) return@withContext
            val cutoff = clockMillis() - MAX_TEMP_AGE_MILLIS
            legacyDirectory.listFiles()
                ?.filter { file -> file.isFile && file.lastModified() < cutoff }
                ?.forEach(File::delete)
        }
    }

    private fun restoreLegacy(
        path: String?,
        displayName: String?,
        width: Int?,
        height: Int?,
        byteCount: Long?
    ): ProcessedImageMedia? {
        val file = resolveLegacyOutput(path) ?: return null
        val actualBytes = file.length()
        if (actualBytes <= 0L || actualBytes > FeedbackImagePolicy.MAX_OUTPUT_BYTES) return null
        val restoredWidth = width?.takeIf { it > 0 } ?: return null
        val restoredHeight = height?.takeIf { it > 0 } ?: return null
        val restoredBytes = byteCount?.takeIf { it == actualBytes } ?: actualBytes

        return ProcessedImageMedia(
            path = file.canonicalPath,
            displayName = displayName?.takeIf(String::isNotBlank) ?: DEFAULT_DISPLAY_NAME,
            width = restoredWidth,
            height = restoredHeight,
            byteCount = restoredBytes
        )
    }

    private fun resolveLegacyOutput(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val root = runCatching { legacyDirectory.canonicalFile }.getOrNull() ?: return null
        val inside = candidate.path == root.path ||
            candidate.path.startsWith(root.path + File.separator)
        return candidate.takeIf { file ->
            inside &&
                file.isFile &&
                file.name.startsWith(LEGACY_OUTPUT_PREFIX) &&
                file.name.endsWith(OUTPUT_SUFFIX)
        }
    }

    private companion object {
        const val LEGACY_DIRECTORY_NAME = "feedback_media"
        const val LEGACY_OUTPUT_PREFIX = "feedback_output_"
        const val OUTPUT_SUFFIX = ".jpg"
        const val DEFAULT_DISPLAY_NAME = "image.jpg"
        const val MAX_TEMP_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
