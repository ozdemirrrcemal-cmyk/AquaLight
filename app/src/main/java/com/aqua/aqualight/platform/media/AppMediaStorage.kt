package com.aqua.aqualight.platform.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Canonical app-owned media storage for profile and aquarium photo flows. */
object AppMediaStorage {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val MAX_PENDING_AGE_MILLIS = 24L * 60L * 60L * 1000L

    fun createCameraCaptureUri(
        context: Context,
        scope: AppMediaScope,
        ownerToken: String
    ): Uri? {
        return createTemporaryFile(
            context = context,
            scope = scope,
            role = MediaFileRole.CAMERA,
            ownerToken = ownerToken
        )?.let { file -> toContentUri(context, file) }
    }

    fun createCropOutputUri(
        context: Context,
        scope: AppMediaScope,
        ownerToken: String
    ): Uri? {
        return createTemporaryFile(
            context = context,
            scope = scope,
            role = MediaFileRole.CROP,
            ownerToken = ownerToken
        )?.let(Uri::fromFile)
    }

    fun promoteCropOutput(
        context: Context,
        scope: AppMediaScope,
        ownerToken: String,
        outputUri: Uri
    ): Uri? {
        val source = resolveInternalMediaFile(
            context = context,
            uriString = outputUri.toString(),
            expectedScope = scope
        ) ?: return null
        if (!source.isFile || source.length() <= 0L) return null

        return runCatching {
            val target = File(
                mediaDirectory(context, scope),
                buildFileName(
                    scope = scope,
                    role = MediaFileRole.SAVED,
                    ownerToken = ownerToken
                )
            )
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
            toContentUri(context, target)
        }.getOrNull()
    }

    fun toContentUriIfInternalFile(
        context: Context,
        uri: Uri
    ): Uri? {
        val file = resolveInternalMediaFile(
            context = context,
            uriString = uri.toString()
        ) ?: return null
        return toContentUri(context, file)
    }

    fun copyInternalMedia(
        context: Context,
        sourceUriString: String?,
        targetScope: AppMediaScope,
        ownerToken: String
    ): String? {
        val sourceFile = resolveInternalMediaFile(
            context = context,
            uriString = sourceUriString
        ) ?: return sourceUriString

        return runCatching {
            val targetFile = File(
                mediaDirectory(context, targetScope),
                buildFileName(
                    scope = targetScope,
                    role = MediaFileRole.SAVED,
                    ownerToken = ownerToken
                )
            )
            sourceFile.copyTo(targetFile, overwrite = true)
            toContentUri(context, targetFile).toString()
        }.getOrElse { sourceUriString }
    }

    fun deleteInternalMedia(
        context: Context,
        uriString: String?
    ): Boolean {
        val file = resolveInternalMediaFile(
            context = context,
            uriString = uriString
        ) ?: return false
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    fun deleteInternalMedia(
        context: Context,
        uriStrings: Collection<String?>
    ) {
        uriStrings.forEach { deleteInternalMedia(context, it) }
    }

    fun deleteOwnerTemporaryFiles(
        context: Context,
        scope: AppMediaScope,
        ownerToken: String
    ) {
        val safeToken = safeOwnerToken(ownerToken)
        mediaDirectory(context, scope).listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.contains("_${safeToken}_") &&
                    (file.name.contains("_camera_") || file.name.contains("_crop_"))
            }
            ?.forEach(File::delete)
    }

    fun cleanupStaleTemporaryFiles(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val cutoff = nowMillis - MAX_PENDING_AGE_MILLIS
        AppMediaScope.entries.forEach { scope ->
            mediaDirectory(context, scope).listFiles()
                ?.filter { file ->
                    file.isFile &&
                        file.lastModified() < cutoff &&
                        (file.name.contains("_camera_") || file.name.contains("_crop_"))
                }
                ?.forEach(File::delete)
        }
    }

    fun isAppOwned(
        context: Context,
        uriString: String?
    ): Boolean {
        return resolveInternalMediaFile(context, uriString)?.exists() == true
    }

    internal fun resolveInternalMediaFile(
        context: Context,
        uriString: String?,
        expectedScope: AppMediaScope? = null
    ): File? {
        if (uriString.isNullOrBlank()) return null
        val appContext = context.applicationContext
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val scopes = expectedScope?.let(::listOf) ?: AppMediaScope.entries

        val candidates = when (uri.scheme) {
            "file" -> listOfNotNull(uri.path?.let(::File))
            "content" -> contentUriCandidates(appContext, uri, scopes)
            null, "" -> listOf(File(uriString))
            else -> return null
        }

        return candidates.firstOrNull { candidate ->
            scopes.any { scope -> candidate.isInside(mediaDirectory(appContext, scope)) }
        }
    }

    private fun contentUriCandidates(
        context: Context,
        uri: Uri,
        scopes: List<AppMediaScope>
    ): List<File> {
        val expectedAuthority = "${context.packageName}$FILE_PROVIDER_SUFFIX"
        if (uri.authority != expectedAuthority) return emptyList()

        val candidates = linkedSetOf<File>()
        val segments = uri.pathSegments

        // FileProvider encodes the configured root name as the first path segment
        // and the relative file path in the remaining segments. Reconstructing the
        // relative path avoids relying on lastPathSegment for nested/encoded paths.
        if (segments.size >= 2) {
            val rootName = segments.first()
            val relativePath = segments.drop(1).joinToString(File.separator)
            scopes.filter { scope -> scope.directoryName == rootName }
                .forEach { scope ->
                    candidates += File(mediaDirectory(context, scope), relativePath)
                }
        }

        // Compatibility fallback for providers that expose only the file name.
        uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?.let { fileName ->
                scopes.forEach { scope ->
                    candidates += File(mediaDirectory(context, scope), fileName)
                }
            }

        // Final round-trip fallback: compare the canonical FileProvider URI for
        // existing app-owned files. This is deterministic and remains fail-closed.
        if (candidates.none(File::exists)) {
            scopes.forEach { scope ->
                mediaDirectory(context, scope).listFiles()
                    ?.asSequence()
                    ?.filter(File::isFile)
                    ?.filter { file ->
                        runCatching { toContentUri(context, file) == uri }.getOrDefault(false)
                    }
                    ?.forEach(candidates::add)
            }
        }

        return candidates.toList()
    }

    private fun createTemporaryFile(
        context: Context,
        scope: AppMediaScope,
        role: MediaFileRole,
        ownerToken: String
    ): File? {
        return runCatching {
            File(
                mediaDirectory(context, scope),
                buildFileName(scope, role, ownerToken)
            ).apply {
                check(createNewFile()) { "Media file could not be created." }
            }
        }.getOrNull()
    }

    private fun buildFileName(
        scope: AppMediaScope,
        role: MediaFileRole,
        ownerToken: String
    ): String {
        return "${scope.prefix}_${role.token}_${safeOwnerToken(ownerToken)}_" +
            "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
    }

    private fun mediaDirectory(
        context: Context,
        scope: AppMediaScope
    ): File {
        return File(context.applicationContext.filesDir, scope.directoryName).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun toContentUri(
        context: Context,
        file: File
    ): Uri {
        val appContext = context.applicationContext
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}$FILE_PROVIDER_SUFFIX",
            file
        )
    }

    private fun File.isInside(root: File): Boolean {
        val candidatePath = runCatching { canonicalPath }.getOrNull() ?: return false
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return false
        return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
    }

    private fun safeOwnerToken(value: String): String {
        return value.ifBlank { "draft" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}

enum class AppMediaScope(
    val directoryName: String,
    val prefix: String
) {
    PROFILE("profile_photos", "profile"),
    TANK("tank_photos", "tank")
}

private enum class MediaFileRole(val token: String) {
    CAMERA("camera"),
    CROP("crop"),
    SAVED("saved")
}
