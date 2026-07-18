package com.aqua.aqualight.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackMediaProcessorInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun largeImageIsSampledAndCompressedWithinCommercialLimits() {
        runBlocking {
            val source = providerFile("feedback_large_${UUID.randomUUID()}.png")
            val bitmap = Bitmap.createBitmap(2_400, 1_800, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(32, 112, 176))
            }
            try {
                source.outputStream().buffered().use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                assertTrue("Generated source image is empty", source.length() > 0L)

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                assertEquals(2_400, bounds.outWidth)
                assertEquals(1_800, bounds.outHeight)

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    source
                )
                val processor = AndroidFeedbackMediaProcessor(context)
                val result = processor.process(contentUri)
                assertTrue(
                    "Unexpected processing result: $result; sourceBytes=${source.length()}",
                    result is FeedbackMediaProcessingResult.Success
                )

                val media = (result as FeedbackMediaProcessingResult.Success).media
                assertTrue(maxOf(media.width, media.height) <= FeedbackImagePolicy.MAX_OUTPUT_EDGE_PX)
                assertTrue(media.byteCount in 1..FeedbackImagePolicy.MAX_OUTPUT_BYTES)
                assertTrue(media.file.exists())

                processor.delete(media.path)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                source.delete()
            }
        }
    }

    @Test
    fun corruptImageReturnsTypedInvalidImageFailure() {
        runBlocking {
            val source = providerFile("feedback_corrupt_${UUID.randomUUID()}.png").apply {
                writeText("not an image")
            }
            try {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    source
                )
                val processor = AndroidFeedbackMediaProcessor(context)
                val result = processor.process(contentUri)

                assertTrue(result is FeedbackMediaProcessingResult.Failure)
                assertEquals(
                    FeedbackMediaFailureKind.INVALID_IMAGE,
                    (result as FeedbackMediaProcessingResult.Failure).kind
                )
            } finally {
                source.delete()
            }
        }
    }

    private fun providerFile(name: String): File {
        return File(context.filesDir, AppMediaScope.PROFILE.directoryName).apply {
            check(exists() || mkdirs()) { "Profile media test directory could not be created" }
        }.resolve(name).apply {
            check(createNewFile()) { "Media test fixture could not be created" }
        }
    }
}
