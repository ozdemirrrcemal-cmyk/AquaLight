package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.platform.media.UserDataArchiveMediaFingerprint
import java.io.File

internal data class LivestockRestoreMedia(
    val snapshot: (String?) -> UserDataArchiveMediaFingerprint? = { null },
    val prepare: suspend (String, String, File) -> String = { _, _, _ ->
        error("No livestock photo restore operation is configured.")
    }
)
