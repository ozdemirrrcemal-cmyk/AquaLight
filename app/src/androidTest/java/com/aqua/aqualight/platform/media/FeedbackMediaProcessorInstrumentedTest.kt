package com.aqua.aqualight.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackMediaProcessorInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun largeImageIsSampledAndCompressedWithinCommercialLimits() {
        runBlocking {
            val sourceFileUri = AppMediaStorage.createCropOutputUri(
                context = context,
                scope = AppMediaScope.PROFILE,
                ownerToken = "feedback-large-test"
            )
            assertNotNull(sourceFileUri)
            val source = requireNotNull(
                AppMediaStorage.resolveInternalMediaFile(
                    context = context,
                    uriString = requireNotNull(sourceFileUri).toString(),
                    expectedScope = AppMediaScope.PROFILE
                )
            )
            val bitmap = Bitmap.createBitmap(2_400, 1_800, Bitmap.Config.ARGB_8888)
            try {
                source.outputStream().buffered().use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output))
                }
                val contentUri = requireNotNull(
                    AppMediaStorage.toContentUriIfInternalFile(context, Uri.fromFile(source))
                )

                val processor = AndroidFeedbackMediaProcessor(context)
                val result = processor.process(contentUri)
                assertTrue("Unexpected processing result: $result", result is FeedbackMediaProcessingResult.Success)

                val media = (result as FeedbackMediaProcessingResult.Success).media
                assertTrue(maxOf(media.width, media.height) <= FeedbackImagePolicy.MAX_OUTPUT_EDGE_PX)
                assertTrue(media.byteCount in 1..FeedbackImagePolicy.MAX_OUTPUT_BYTES)
                assertTrue(media.file.exists())

                processor.delete(media.path)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                AppMediaStorage.deleteInternalMedia(context, sourceFileUri?.toString())
            }
        }
    }

    @Test
    fun corruptImageReturnsTypedInvalidImageFailure() {
        runBlocking {
            val sourceFileUri = AppMediaStorage.createCropOutputUri(
                context = context,
                scope = AppMediaScope.PROFILE,
                ownerToken = "feedback-corrupt-test"
            )
            assertNotNull(sourceFileUri)
            val source = requireNotNull(
                AppMediaStorage.resolveInternalMediaFile(
                    context = context,
                    uriString = requireNotNull(sourceFileUri).toString(),
                    expectedScope = AppMediaScope.PROFILE
                )
            ).apply {
                writeText("not an image")
            }
            try {
                val contentUri = requireNotNull(
                    AppMediaStorage.toContentUriIfInternalFile(context, Uri.fromFile(source))
                )
                val processor = AndroidFeedbackMediaProcessor(context)
                val result = processor.process(contentUri)

                assertTrue(result is FeedbackMediaProcessingResult.Failure)
                assertEquals(
                    FeedbackMediaFailureKind.INVALID_IMAGE,
                    (result as FeedbackMediaProcessingResult.Failure).kind
                )
            } finally {
                AppMediaStorage.deleteInternalMedia(context, sourceFileUri?.toString())
            }
        }
    }
}
