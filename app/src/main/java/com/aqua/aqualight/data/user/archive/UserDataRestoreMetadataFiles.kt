package com.aqua.aqualight.data.user.archive

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Small atomic JSON-file store for owner-scoped restore metadata. */
internal class UserDataRestoreMetadataFiles(
    context: Context,
    namespace: String
) {
    private val directory = File(
        context.applicationContext.filesDir,
        ROOT_DIRECTORY
    ).resolve(requireSafeNamespace(namespace))

    fun read(ownerUid: String): String? {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        val atomicFile = AtomicFile(ownerFile(owner))
        val input = try {
            atomicFile.openRead()
        } catch (_: FileNotFoundException) {
            return null
        }
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_METADATA_BYTES) {
                    "Restore metadata exceeds the supported size."
                }
                output.write(buffer, 0, read)
            }
            output.toString(StandardCharsets.UTF_8.name())
        }
    }

    fun write(ownerUid: String, content: String) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_METADATA_BYTES) {
            "Restore metadata size is invalid."
        }
        ensureDirectory()
        val atomicFile = AtomicFile(ownerFile(owner))
        val output = atomicFile.startWrite()
        val writeResult = runCatching {
            output.write(bytes)
            atomicFile.finishWrite(output)
        }
        val failure = writeResult.exceptionOrNull()
        if (failure != null) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    fun delete(ownerUid: String) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        val atomicFile = AtomicFile(ownerFile(owner))
        atomicFile.delete()
        check(!ownerFile(owner).exists()) {
            "Restore metadata could not be cleared."
        }
    }

    private fun ownerFile(ownerUid: String): File {
        val token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ownerUid.toByteArray(StandardCharsets.UTF_8))
        return File(directory, "$token.json")
    }

    private fun ensureDirectory() {
        if (!directory.exists()) {
            check(directory.mkdirs() || directory.exists()) {
                "Restore metadata directory could not be created."
            }
        }
        check(directory.isDirectory) {
            "Restore metadata path is not a directory."
        }
    }

    private fun requireSafeNamespace(namespace: String): String {
        val canonical = namespace.trim()
        require(canonical.matches(NAMESPACE_PATTERN)) {
            "Restore metadata namespace is invalid."
        }
        return canonical
    }

    private companion object {
        const val ROOT_DIRECTORY = "user_data_restore"
        const val MAX_METADATA_BYTES = 1024 * 1024
        const val BUFFER_SIZE = 8 * 1024
        val NAMESPACE_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
    }
}
