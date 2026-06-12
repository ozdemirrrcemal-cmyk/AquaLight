package com.aqua.aqualight.data.aquarium.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object TankPhotoStorage {

    private const val PHOTO_DIRECTORY_NAME = "tank_photos"
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    fun createCameraCaptureUri(
        context: Context,
        ownerToken: String = "draft"
    ): Uri? {
        return runCatching {
            val file = File.createTempFile(
                "tank_camera_${safeOwnerToken(ownerToken)}_",
                ".jpg",
                photoDirectory(context)
            )

            toContentUri(
                context = context,
                file = file
            )
        }.getOrNull()
    }

    fun createCropOutputUri(
        context: Context,
        ownerToken: String = "draft"
    ): Uri? {
        return runCatching {
            val file = File.createTempFile(
                "tank_crop_${safeOwnerToken(ownerToken)}_",
                ".jpg",
                photoDirectory(context)
            )

            Uri.fromFile(file)
        }.getOrNull()
    }

    fun toContentUriIfInternalFile(
        context: Context,
        uri: Uri
    ): Uri? {
        val file = resolveInternalPhotoFile(
            context = context,
            uriString = uri.toString()
        ) ?: return null

        return toContentUri(
            context = context,
            file = file
        )
    }

    fun copyInternalPhotoForTank(
        context: Context,
        sourceUriString: String?,
        tankId: Long
    ): String? {
        val sourceFile = resolveInternalPhotoFile(
            context = context,
            uriString = sourceUriString
        ) ?: return sourceUriString

        return runCatching {
            val targetFile = File(
                photoDirectory(context),
                "tank_copy_${tankId}_${System.currentTimeMillis()}.jpg"
            )

            sourceFile.copyTo(
                target = targetFile,
                overwrite = true
            )

            toContentUri(
                context = context,
                file = targetFile
            ).toString()
        }.getOrElse {
            sourceUriString
        }
    }

    fun deleteInternalPhoto(
        context: Context,
        uriString: String?
    ) {
        val file = resolveInternalPhotoFile(
            context = context,
            uriString = uriString
        ) ?: return

        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun deleteInternalPhotos(
        context: Context,
        uriStrings: Collection<String?>
    ) {
        uriStrings.forEach { uriString ->
            deleteInternalPhoto(
                context = context,
                uriString = uriString
            )
        }
    }

    fun deleteTankOwnedTemporaryFiles(
        context: Context,
        tankId: Long
    ) {
        if (tankId <= 0L) {
            return
        }

        val directory = photoDirectory(context)
        val token = "_${tankId}_"

        directory.listFiles()
            ?.filter { file ->
                file.isFile && file.name.contains(token)
            }
            ?.forEach { file ->
                runCatching {
                    file.delete()
                }
            }
    }

    private fun toContentUri(
        context: Context,
        file: File
    ): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}$FILE_PROVIDER_SUFFIX",
            file
        )
    }

    private fun photoDirectory(
        context: Context
    ): File {
        return File(
            context.filesDir,
            PHOTO_DIRECTORY_NAME
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun resolveInternalPhotoFile(
        context: Context,
        uriString: String?
    ): File? {
        if (uriString.isNullOrBlank()) {
            return null
        }

        val uri = runCatching {
            Uri.parse(uriString)
        }.getOrNull() ?: return null

        val candidate = when (uri.scheme) {
            "file" -> {
                uri.path?.let { path ->
                    File(path)
                }
            }

            "content" -> {
                val expectedAuthority = "${context.packageName}$FILE_PROVIDER_SUFFIX"

                if (uri.authority != expectedAuthority) {
                    null
                } else {
                    uri.lastPathSegment?.let { fileName ->
                        File(
                            photoDirectory(context),
                            fileName.substringAfterLast('/')
                        )
                    }
                }
            }

            else -> null
        } ?: return null

        val directory = photoDirectory(context)
        val directoryPath = runCatching {
            directory.canonicalPath
        }.getOrNull() ?: return null

        val candidatePath = runCatching {
            candidate.canonicalPath
        }.getOrNull() ?: return null

        val isInsidePhotoDirectory = candidatePath == directoryPath ||
            candidatePath.startsWith("$directoryPath${File.separator}")

        return if (isInsidePhotoDirectory) {
            candidate
        } else {
            null
        }
    }

    private fun safeOwnerToken(
        ownerToken: String
    ): String {
        return ownerToken
            .ifBlank {
                "draft"
            }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
