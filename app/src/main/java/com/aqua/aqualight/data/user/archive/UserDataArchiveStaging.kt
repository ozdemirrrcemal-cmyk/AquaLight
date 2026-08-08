package com.aqua.aqualight.data.user.archive

import android.content.Context
import java.io.File
import java.util.UUID

/** Private cache-backed staging for large archive payloads; presentation sees only opaque handles. */
internal class UserDataArchiveStaging(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val root = File(context.applicationContext.cacheDir, ROOT_DIRECTORY)

    data class Session(
        val handle: String,
        val payload: File
    )

    fun createSession(): Session {
        ensureRoot()
        cleanupExpired()
        val handle = UUID.randomUUID().toString()
        val directory = File(root, handle)
        check(directory.mkdir()) { "User-data archive staging directory could not be created." }
        directory.setLastModified(nowMillis())
        return Session(handle = handle, payload = File(directory, PAYLOAD_FILE))
    }

    fun payload(handle: String): File {
        val directory = requireSessionDirectory(handle)
        directory.setLastModified(nowMillis())
        return File(directory, PAYLOAD_FILE)
    }

    fun createScratchDirectory(handle: String): File {
        val directory = requireSessionDirectory(handle)
        val scratch = File(directory, "$SCRATCH_PREFIX${UUID.randomUUID()}")
        check(scratch.mkdir()) { "User-data archive scratch directory could not be created." }
        directory.setLastModified(nowMillis())
        return scratch
    }

    fun discardScratch(directory: File) {
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return
        val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (
            canonical.parentFile?.parentFile == canonicalRoot &&
            canonical.name.startsWith(SCRATCH_PREFIX)
        ) {
            canonical.deleteRecursively()
        }
    }

    fun discard(handle: String) {
        val directory = runCatching { requireSessionDirectory(handle, requireExists = false) }
            .getOrNull() ?: return
        directory.deleteRecursively()
    }

    fun cleanupExpired() {
        if (!root.isDirectory) return
        val cutoff = nowMillis() - MAX_SESSION_AGE_MILLIS
        root.listFiles()
            ?.filter { file -> file.isDirectory && file.lastModified() > 0L && file.lastModified() < cutoff }
            ?.forEach(File::deleteRecursively)
    }

    private fun requireSessionDirectory(
        handle: String,
        requireExists: Boolean = true
    ): File {
        val canonicalHandle = runCatching { UUID.fromString(handle).toString() }
            .getOrElse { throw IllegalArgumentException("Invalid user-data archive handle.", it) }
        require(canonicalHandle == handle) { "Invalid user-data archive handle." }
        ensureRoot()
        val directory = File(root, canonicalHandle)
        val canonicalRoot = root.canonicalFile
        val canonicalDirectory = directory.canonicalFile
        require(canonicalDirectory.parentFile == canonicalRoot) {
            "User-data archive handle escaped its staging root."
        }
        if (requireExists) {
            require(canonicalDirectory.isDirectory) { "User-data archive handle is unavailable." }
        }
        return canonicalDirectory
    }

    private fun ensureRoot() {
        if (!root.exists()) {
            check(root.mkdirs() || root.exists()) {
                "User-data archive staging root could not be created."
            }
        }
        check(root.isDirectory) { "User-data archive staging root is not a directory." }
    }

    private companion object {
        const val ROOT_DIRECTORY = "user_data_archive_staging"
        const val PAYLOAD_FILE = "payload"
        const val SCRATCH_PREFIX = "scratch_"
        const val MAX_SESSION_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
