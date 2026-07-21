package com.aqua.aqualight.platform.media

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageMediaProcessorCompatibilityInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun legacyPreparedOutputRemainsRestorableShareableAndDeletable() = runBlocking {
        val legacyDirectory = File(context.cacheDir, "feedback_media").apply {
            check(exists() || mkdirs()) { "Legacy media directory could not be created" }
        }
        val legacyFile = File(
            legacyDirectory,
            "feedback_output_${UUID.randomUUID()}.jpg"
        ).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                legacyFile
            )
            assertEquals("content", contentUri.scheme)

            val processor = AndroidImageMediaProcessor(
                context = context,
                dispatcher = Dispatchers.Unconfined
            )
            val restored = processor.restore(
                path = legacyFile.canonicalPath,
                displayName = "legacy-profile.jpg",
                width = 640,
                height = 640,
                byteCount = legacyFile.length()
            )

            assertNotNull(restored)
            assertEquals(legacyFile.canonicalPath, restored?.path)
            assertEquals("legacy-profile.jpg", restored?.displayName)

            processor.delete(legacyFile.canonicalPath)
            assertFalse(legacyFile.exists())
        } finally {
            legacyFile.delete()
        }
    }
}
