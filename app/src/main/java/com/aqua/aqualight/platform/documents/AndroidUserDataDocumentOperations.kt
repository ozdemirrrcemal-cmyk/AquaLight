package com.aqua.aqualight.platform.documents

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Central Storage Access Framework boundary that streams backup/export documents to private staging. */
internal class AndroidUserDataDocumentOperations(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun importDocument(
        documentHandle: String,
        destination: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        operationResult {
            val uri = requireContentUri(documentHandle)
            val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) {
                "Selected document could not be opened."
            }
            destination.parentFile?.let { parent ->
                check(parent.isDirectory || parent.mkdirs()) {
                    "Document staging directory could not be created."
                }
            }
            var completed = false
            try {
                input.use { source ->
                    destination.outputStream().buffered().use { target ->
                        copyLimited(source, target, MAX_DOCUMENT_BYTES)
                    }
                }
                require(destination.length() in 1L..MAX_DOCUMENT_BYTES.toLong()) {
                    "Selected document size is invalid."
                }
                completed = true
            } finally {
                if (!completed) destination.delete()
            }
        }
    }

    suspend fun exportDocument(
        documentHandle: String,
        source: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        operationResult {
            require(source.isFile && source.length() in 1L..MAX_DOCUMENT_BYTES.toLong()) {
                "Document content size is invalid."
            }
            val uri = requireContentUri(documentHandle)
            val output = requireNotNull(
                appContext.contentResolver.openOutputStream(uri, "wt")
            ) {
                "Selected document could not be opened for writing."
            }
            source.inputStream().buffered().use { input ->
                output.buffered().use { target ->
                    copyLimited(input, target, MAX_DOCUMENT_BYTES)
                    target.flush()
                }
            }
        }
    }

    private fun requireContentUri(documentHandle: String): Uri {
        val uri = documentHandle.trim().toUri()
        require(uri.scheme == "content") {
            "Only Storage Access Framework content documents are supported."
        }
        return uri
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        maximumBytes: Int
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes.toLong()) {
                "Document exceeds the supported size."
            }
            output.write(buffer, 0, read)
        }
    }

    private inline fun <T> operationResult(block: () -> T): Result<T> {
        return runCatching(block).also { result ->
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
        }
    }

    private companion object {
        const val MAX_DOCUMENT_BYTES = 64 * 1024 * 1024
        const val BUFFER_SIZE = 8 * 1024
    }
}
