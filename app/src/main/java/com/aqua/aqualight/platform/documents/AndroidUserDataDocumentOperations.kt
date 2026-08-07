package com.aqua.aqualight.platform.documents

import android.content.Context
import android.net.Uri
import com.aqua.aqualight.application.user.UserDataDocumentOperations
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Central Storage Access Framework boundary for backup and export documents. */
internal class AndroidUserDataDocumentOperations(
    context: Context
) : UserDataDocumentOperations {
    private val appContext = context.applicationContext

    override suspend fun read(documentHandle: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            operationResult {
                val uri = requireContentUri(documentHandle)
                val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) {
                    "Selected document could not be opened."
                }
                input.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_DOCUMENT_BYTES) {
                            "Selected document exceeds the supported size."
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            }
        }

    override suspend fun write(
        documentHandle: String,
        content: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        operationResult {
            require(content.isNotEmpty()) { "Document content is empty." }
            require(content.size <= MAX_DOCUMENT_BYTES) {
                "Document content exceeds the supported size."
            }
            val uri = requireContentUri(documentHandle)
            val output = requireNotNull(
                appContext.contentResolver.openOutputStream(uri, "wt")
            ) {
                "Selected document could not be opened for writing."
            }
            output.use { stream ->
                stream.write(content)
                stream.flush()
            }
        }
    }

    private fun requireContentUri(documentHandle: String): Uri {
        val uri = Uri.parse(documentHandle.trim())
        require(uri.scheme == "content") {
            "Only Storage Access Framework content documents are supported."
        }
        return uri
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
