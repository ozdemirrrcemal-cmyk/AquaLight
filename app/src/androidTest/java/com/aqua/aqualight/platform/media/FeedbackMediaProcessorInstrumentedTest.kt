package com.aqua.aqualight.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackMediaProcessorInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun largeImageIsSampledAndCompressedWithinCommercialLimits() = runBlocking {
        val source = File(context.cacheDir, "feedback_large_source.jpg")
        val bitmap = Bitmap.createBitmap(2_400, 1_800, Bitmap.Config.ARGB_8888)
        source.outputStream().buffered().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output))
        }
        bitmap.recycle()

        val processor = AndroidFeedbackMediaProcessor(context)
        val result = processor.process(Uri.fromFile(source))
        assertTrue(result is FeedbackMediaProcessingResult.Success)

        val media = (result as FeedbackMediaProcessingResult.Success).media
        assertTrue(maxOf(media.width, media.height) <= FeedbackImagePolicy.MAX_OUTPUT_EDGE_PX)
        assertTrue(media.byteCount in 1..FeedbackImagePolicy.MAX_OUTPUT_BYTES)
        assertTrue(media.file.exists())

        processor.delete(media.path)
        source.delete()
    }

    @Test
    fun corruptImageReturnsTypedInvalidImageFailure() = runBlocking {
        val source = File(context.cacheDir, "feedback_corrupt.jpg").apply {
            writeText("not an image")
        }
        val processor = AndroidFeedbackMediaProcessor(context)

        val result = processor.process(Uri.fromFile(source))

        assertTrue(result is FeedbackMediaProcessingResult.Failure)
        assertEquals(
            FeedbackMediaFailureKind.INVALID_IMAGE,
            (result as FeedbackMediaProcessingResult.Failure).kind
        )
        source.delete()
    }
}
