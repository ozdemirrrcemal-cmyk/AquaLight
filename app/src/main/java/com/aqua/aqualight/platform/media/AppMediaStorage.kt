package com.aqua.aqualight.platform.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.aqua.aqualight.data.user.UserDataScope
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Canonical app-owned media storage for profile and aquarium photo flows. */
object AppMediaStorage {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val MAX_PENDING_AGE_MILLIS = 24L * 60L * 60L * 1000L
    private const val PENDING_PREFERENCES = "app_media_pending_v1"
    private const val PENDING_PREFIX = "pending."
    private const val DELETION_PREFERENCES = "app_media_deletion_v1"
    private const val DELETION_PREFIX = "deletion."
    private const val JSON_URI = "uri"
    private const val JSON_OWNER_UID = "ownerUid"
    private const val JSON_CREATED_AT = "createdAt"
    private const val BOUNDED_IMAGE_DIRECTORY = "bounded_images"
    private val pendingLock = Any()
    private val deletionLock = Any()

    fun createCameraCaptureUri(
        context: Context,
        scope: AppMediaScope,
        ownerToken: String
    ): Uri? = createTemporaryFile(
        context = context,
        scope = scope,
        role = MediaFileRole.CAMERA,
        ownerToken = ownerToken
    )?.let { file -> toContentUri(context, file) }

    fun createCropOutputUri(
        context: Context,
        scope: AppMediaScope,
        ownerToken: String
    ): Uri? = createTemporaryFile(
        context = context,
        scope = scope,
        role = MediaFileRole.CROP,
        ownerToken = ownerToken
    )?.let(Uri::fromFile)

    /**
     * Promotes a crop output to an app-owned candidate. The candidate remains journaled until the
     * owning repository confirms that its URI was durably committed to the domain store.
     */
    fun promoteCropOutput(
        context: Context,
        scope: AppMediaScope,
        ownerToken: String,
        ownerUid: String,
        outputUri: Uri
    ): Uri? {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        val source = resolveInternalMediaFile(
            context = context,
            uriString = outputUri.toString(),
            expectedScope = scope
        ) ?: return null
        if (!source.isFile || source.length() <= 0L) return null

        var target: File? = null
        return try {
            target = File(
                mediaDirectory(context, scope),
                buildSavedFileName(scope, ownerUid, ownerToken)
            )
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = false)
                check(source.delete()) { "Crop source could not be removed after promotion." }
            }
            val promoted = toContentUri(context, target)
            registerPending(context, promoted.toString(), ownerUid)
            promoted
        } catch (_: Throwable) {
            target?.delete()
            null
        }
    }

    fun toContentUriIfInternalFile(context: Context, uri: Uri): Uri? {
        val file = resolveInternalMediaFile(context, uri.toString()) ?: return null
        return toContentUri(context, file)
    }

    /** Returns a FileProvider URI only for known app-owned media roots. */
    fun toContentUriForOwnedPath(context: Context, path: String?): Uri? {
        if (path.isNullOrBlank()) return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val appContext = context.applicationContext
        val allowedRoots = AppMediaScope.entries.map { mediaDirectory(appContext, it).canonicalFile } +
            File(appContext.cacheDir, BOUNDED_IMAGE_DIRECTORY).canonicalFile
        if (allowedRoots.none { root -> candidate.isInside(root) }) return null
        if (!candidate.isFile || candidate.length() <= 0L) return null
        return runCatching { toContentUri(appContext, candidate) }.getOrNull()
    }

    /** Compatibility entry for the owner-scoped tank store; identity is captured at call time. */
    fun copyInternalMedia(
        context: Context,
        sourceUriString: String?,
        targetScope: AppMediaScope,
        ownerToken: String
    ): String? = copyInternalMedia(
        context = context,
        sourceUriString = sourceUriString,
        targetScope = targetScope,
        ownerToken = ownerToken,
        ownerUid = UserDataScope.requireCurrentUid()
    )

    /**
     * Copies app-owned media into an independently owned target. A copy failure never falls back to
     * the source URI because that would make two domain records own the same physical file.
     */
    fun copyInternalMedia(
        context: Context,
        sourceUriString: String?,
        targetScope: AppMediaScope,
        ownerToken: String,
        ownerUid: String
    ): String? {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        if (sourceUriString.isNullOrBlank()) return sourceUriString
        val sourceFile = resolveInternalMediaFile(context, sourceUriString)
            ?: return sourceUriString

        var targetFile: File? = null
        return try {
            targetFile = File(
                mediaDirectory(context, targetScope),
                buildSavedFileName(targetScope, ownerUid, ownerToken)
            )
            sourceFile.copyTo(targetFile, overwrite = false)
            val targetUri = toContentUri(context, targetFile).toString()
            registerPending(context, targetUri, ownerUid)
            targetUri
        } catch (_: Throwable) {
            targetFile?.delete()
            null
        }
    }

    /** Marks a pending media candidate as owned by a successfully committed domain record. */
    fun commitPendingMedia(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        removePendingEntries(context, uriString)
    }

    /**
     * Deletes a candidate only while it is still journaled as uncommitted.
     * The recovery record is retained when deletion fails, so process-start recovery can retry.
     */
    fun rollbackPendingMedia(context: Context, uriString: String?): Boolean =
        rollbackPendingMedia(context, uriString) { file -> !file.exists() || file.delete() }

    internal fun rollbackPendingMedia(
        context: Context,
        uriString: String?,
        deleteFile: (File) -> Boolean
    ): Boolean {
        if (uriString.isNullOrBlank() || !isPending(context, uriString)) return false
        val file = resolveInternalMediaFile(context, uriString) ?: return false
        val deleted = runCatching { !file.exists() || deleteFile(file) }.getOrDefault(false)
        if (deleted) removePendingEntries(context, uriString)
        return deleted
    }

    /**
     * Journals a superseded committed file before deletion. A journal failure never risks deleting
     * a still-referenced file; the owner-scoped committed-file sweep is the durable fallback.
     */
    fun deleteAfterCommit(
        context: Context,
        ownerUid: String,
        uriString: String?
    ): Boolean = deleteAfterCommit(context, ownerUid, uriString) { file ->
        !file.exists() || file.delete()
    }

    internal fun deleteAfterCommit(
        context: Context,
        ownerUid: String,
        uriString: String?,
        deleteFile: (File) -> Boolean
    ): Boolean {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        if (uriString.isNullOrBlank()) return true
        val file = resolveInternalMediaFile(context, uriString) ?: return true
        if (!file.exists()) return true
        val entry = registerDeletion(context, uriString, ownerUid)
        return reconcileDeletionEntry(context, entry, deleteFile)
    }

    /**
     * Reconciles process-death leftovers for one immutable owner against authoritative domain
     * references. Referenced media is committed; unreferenced candidates are deleted after grace.
     */
    fun reconcilePendingMedia(
        context: Context,
        ownerUid: String,
        referencedUris: Collection<String>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        val appContext = context.applicationContext
        val referencedFiles = referencedUris.mapNotNull { value ->
            resolveInternalMediaFile(appContext, value)?.canonicalPath
        }.toSet()
        val cutoff = nowMillis - MAX_PENDING_AGE_MILLIS

        pendingEntries(appContext)
            .filter { entry -> entry.ownerUid == ownerUid }
            .forEach { entry ->
                val file = resolveInternalMediaFile(appContext, entry.uri)
                when {
                    file == null || !file.exists() -> removePendingKey(appContext, entry.key)
                    file.canonicalPath in referencedFiles -> removePendingKey(appContext, entry.key)
                    entry.createdAtMillis < cutoff -> {
                        if (!file.exists() || file.delete()) {
                            removePendingKey(appContext, entry.key)
                        }
                    }
                }
            }
    }

    /** Retries committed-file deletions without ever deleting a currently referenced URI. */
    fun reconcilePendingDeletions(
        context: Context,
        ownerUid: String,
        referencedUris: Collection<String>
    ) {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        val appContext = context.applicationContext
        val referencedFiles = referencedUris.mapNotNull { value ->
            resolveInternalMediaFile(appContext, value)?.canonicalPath
        }.toSet()

        deletionEntries(appContext)
            .filter { entry -> entry.ownerUid == ownerUid }
            .forEach { entry ->
                val file = resolveInternalMediaFile(appContext, entry.uri)
                when {
                    file == null || !file.exists() -> removeDeletionKey(appContext, entry.key)
                    file.canonicalPath in referencedFiles -> Unit
                    else -> reconcileDeletionEntry(appContext, entry) { candidate ->
                        !candidate.exists() || candidate.delete()
                    }
                }
            }
    }

    /**
     * Owner-prefixed saved files provide a second recovery layer when deletion-journal persistence
     * itself fails. Only unreferenced files older than the grace window are removed.
     */
    fun reconcileUnreferencedCommittedMedia(
        context: Context,
        ownerUid: String,
        referencedUris: Collection<String>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        val appContext = context.applicationContext
        val referencedFiles = referencedUris.mapNotNull { value ->
            resolveInternalMediaFile(appContext, value)?.canonicalPath
        }.toSet()
        val cutoff = nowMillis - MAX_PENDING_AGE_MILLIS

        AppMediaScope.entries.forEach { scope ->
            val ownerPrefix = savedOwnerPrefix(scope, ownerUid)
            mediaDirectory(appContext, scope).listFiles()
                ?.asSequence()
                ?.filter(File::isFile)
                ?.filter { file -> file.name.startsWith(ownerPrefix) }
                ?.filter { file -> file.canonicalPath !in referencedFiles }
                ?.filter { file -> file.lastModified() < cutoff }
                ?.forEach { file ->
                    val uriString = runCatching {
                        toContentUri(appContext, file).toString()
                    }.getOrNull()
                    if (!file.exists() || file.delete()) {
                        removeDeletionEntries(appContext, uriString)
                        removePendingEntries(appContext, uriString)
                    }
                }
        }
    }

    /** Used only after an owner account has been durably cleared. */
    fun discardPendingMediaForOwner(context: Context, ownerUid: String) {
        if (ownerUid.isBlank()) return
        pendingEntries(context)
            .filter { entry -> entry.ownerUid == ownerUid }
            .forEach { entry ->
                val file = resolveInternalMediaFile(context, entry.uri)
                if (file == null || !file.exists() || file.delete()) {
                    removePendingKey(context, entry.key)
                }
            }
        deletionEntries(context)
            .filter { entry -> entry.ownerUid == ownerUid }
            .forEach { entry ->
                val file = resolveInternalMediaFile(context, entry.uri)
                if (file == null || !file.exists() || file.delete()) {
                    removeDeletionKey(context, entry.key)
                }
            }
    }

    fun deleteInternalMedia(context: Context, uriString: String?): Boolean {
        val file = resolveInternalMediaFile(context, uriString) ?: return false
        val deleted = runCatching { !file.exists() || file.delete() }.getOrDefault(false)
        if (deleted) {
            removePendingEntries(context, uriString)
            removeDeletionEntries(context, uriString)
        }
        return deleted
    }

    fun deleteInternalMedia(context: Context, uriStrings: Collection<String?>) {
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

    fun isAppOwned(context: Context, uriString: String?): Boolean =
        resolveInternalMediaFile(context, uriString)?.exists() == true

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
        if (segments.size >= 2) {
            val rootName = segments.first()
            val relativePath = segments.drop(1).joinToString(File.separator)
            scopes.filter { scope -> scope.directoryName == rootName }
                .forEach { scope ->
                    candidates += File(mediaDirectory(context, scope), relativePath)
                }
        }

        uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?.let { fileName ->
                scopes.forEach { scope ->
                    candidates += File(mediaDirectory(context, scope), fileName)
                }
            }

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
    ): File? = runCatching {
        File(
            mediaDirectory(context, scope),
            buildFileName(scope, role, ownerToken)
        ).apply {
            check(createNewFile()) { "Media file could not be created." }
        }
    }.getOrNull()

    private fun buildFileName(
        scope: AppMediaScope,
        role: MediaFileRole,
        ownerToken: String
    ): String = "${scope.prefix}_${role.token}_${safeOwnerToken(ownerToken)}_" +
        "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"

    private fun buildSavedFileName(
        scope: AppMediaScope,
        ownerUid: String,
        ownerToken: String
    ): String = savedOwnerPrefix(scope, ownerUid) +
        "${safeOwnerToken(ownerToken)}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"

    private fun savedOwnerPrefix(scope: AppMediaScope, ownerUid: String): String =
        "${scope.prefix}_${MediaFileRole.SAVED.token}_${safeOwnerToken(ownerUid)}_"

    private fun mediaDirectory(context: Context, scope: AppMediaScope): File {
        return File(context.applicationContext.filesDir, scope.directoryName).apply {
            if (!exists() && !mkdirs() && !exists()) {
                error("Media directory could not be created.")
            }
        }
    }

    private fun toContentUri(context: Context, file: File): Uri {
        val appContext = context.applicationContext
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}$FILE_PROVIDER_SUFFIX",
            file
        )
    }

    private fun registerPending(context: Context, uriString: String, ownerUid: String) {
        val payload = JSONObject()
            .put(JSON_URI, uriString)
            .put(JSON_OWNER_UID, ownerUid)
            .put(JSON_CREATED_AT, System.currentTimeMillis())
            .toString()
        synchronized(pendingLock) {
            check(
                pendingPreferences(context).edit()
                    .putString(PENDING_PREFIX + UUID.randomUUID(), payload)
                    .commit()
            ) { "Pending media journal could not be committed." }
        }
    }

    private fun registerDeletion(
        context: Context,
        uriString: String,
        ownerUid: String
    ): PendingDeletionEntry = synchronized(deletionLock) {
        deletionEntriesLocked(context).firstOrNull { entry ->
            entry.uri == uriString && entry.ownerUid == ownerUid
        } ?: run {
            val key = DELETION_PREFIX + UUID.randomUUID()
            val payload = JSONObject()
                .put(JSON_URI, uriString)
                .put(JSON_OWNER_UID, ownerUid)
                .put(JSON_CREATED_AT, System.currentTimeMillis())
                .toString()
            check(deletionPreferences(context).edit().putString(key, payload).commit()) {
                "Committed media deletion journal could not be committed."
            }
            PendingDeletionEntry(key, uriString, ownerUid)
        }
    }

    private fun reconcileDeletionEntry(
        context: Context,
        entry: PendingDeletionEntry,
        deleteFile: (File) -> Boolean
    ): Boolean {
        val file = resolveInternalMediaFile(context, entry.uri)
        val deleted = file == null || runCatching {
            !file.exists() || deleteFile(file)
        }.getOrDefault(false)
        if (deleted) {
            removeDeletionKey(context, entry.key)
            removePendingEntries(context, entry.uri)
        }
        return deleted
    }

    private fun pendingEntries(context: Context): List<PendingMediaEntry> =
        synchronized(pendingLock) {
            pendingPreferences(context).all.mapNotNull { (key, value) ->
                if (!key.startsWith(PENDING_PREFIX) || value !is String) return@mapNotNull null
                runCatching {
                    val json = JSONObject(value)
                    PendingMediaEntry(
                        key = key,
                        uri = json.getString(JSON_URI),
                        ownerUid = json.getString(JSON_OWNER_UID),
                        createdAtMillis = json.getLong(JSON_CREATED_AT)
                    )
                }.getOrNull()
            }
        }

    private fun deletionEntries(context: Context): List<PendingDeletionEntry> =
        synchronized(deletionLock) { deletionEntriesLocked(context) }

    private fun deletionEntriesLocked(context: Context): List<PendingDeletionEntry> =
        deletionPreferences(context).all.mapNotNull { (key, value) ->
            if (!key.startsWith(DELETION_PREFIX) || value !is String) return@mapNotNull null
            runCatching {
                val json = JSONObject(value)
                PendingDeletionEntry(
                    key = key,
                    uri = json.getString(JSON_URI),
                    ownerUid = json.getString(JSON_OWNER_UID)
                )
            }.getOrNull()
        }

    private fun isPending(context: Context, uriString: String): Boolean =
        pendingEntries(context).any { entry -> entry.uri == uriString }

    private fun removePendingEntries(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val keys = pendingEntries(context)
            .filter { entry -> entry.uri == uriString }
            .map(PendingMediaEntry::key)
        if (keys.isEmpty()) return
        synchronized(pendingLock) {
            val editor = pendingPreferences(context).edit()
            keys.forEach(editor::remove)
            check(editor.commit()) { "Pending media journal could not be updated." }
        }
    }

    private fun removePendingKey(context: Context, key: String) {
        synchronized(pendingLock) {
            check(pendingPreferences(context).edit().remove(key).commit()) {
                "Pending media journal could not be updated."
            }
        }
    }

    private fun removeDeletionEntries(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        synchronized(deletionLock) {
            val keys = deletionEntriesLocked(context)
                .filter { entry -> entry.uri == uriString }
                .map(PendingDeletionEntry::key)
            if (keys.isEmpty()) return@synchronized
            val editor = deletionPreferences(context).edit()
            keys.forEach(editor::remove)
            check(editor.commit()) { "Committed media deletion journal could not be updated." }
        }
    }

    private fun removeDeletionKey(context: Context, key: String) {
        synchronized(deletionLock) {
            check(deletionPreferences(context).edit().remove(key).commit()) {
                "Committed media deletion journal could not be updated."
            }
        }
    }

    private fun pendingPreferences(context: Context) =
        context.applicationContext.getSharedPreferences(PENDING_PREFERENCES, Context.MODE_PRIVATE)

    private fun deletionPreferences(context: Context) =
        context.applicationContext.getSharedPreferences(DELETION_PREFERENCES, Context.MODE_PRIVATE)

    private fun File.isInside(root: File): Boolean {
        val candidatePath = runCatching { canonicalPath }.getOrNull() ?: return false
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return false
        return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
    }

    private fun safeOwnerToken(value: String): String =
        value.ifBlank { "draft" }.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private data class PendingMediaEntry(
        val key: String,
        val uri: String,
        val ownerUid: String,
        val createdAtMillis: Long
    )

    private data class PendingDeletionEntry(
        val key: String,
        val uri: String,
        val ownerUid: String
    )
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
