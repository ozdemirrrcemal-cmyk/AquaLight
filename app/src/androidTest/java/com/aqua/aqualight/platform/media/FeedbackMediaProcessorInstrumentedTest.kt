package com.aqua.aqualight.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackMediaProcessorInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun oversizedProviderImageUsesSampledDecodeAndCompressesWithinCommercialLimits() = runBlocking {
        val sourceWidth = 3_600
        val sourceHeight = 3_400
        val expectedSample = FeedbackImagePolicy.calculateInSampleSize(sourceWidth, sourceHeight)
        assertTrue("Fixture must exercise sampled decode", expectedSample > 1)

        val source = providerFile("feedback_sampled_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.RGB_565).apply {
            eraseColor(Color.rgb(32, 112, 176))
        }
        try {
            source.outputStream().buffered().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            }
            assertTrue("Generated source image is empty", source.length() > 0L)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            assertEquals(sourceWidth, bounds.outWidth)
            assertEquals(sourceHeight, bounds.outHeight)

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
            assertNoStagedSourceFiles()
            processor.delete(media.path)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            source.delete()
        }
    }

    @Test
    fun largeUnknownLengthImageIsBoundedAndSourceStreamIsClosed() = runBlocking {
        val sourceAccess = FakeSourceAccess(
            bytes = jpegBytes(2_400, 1_800),
            declaredLength = null,
            mimeType = "application/octet-stream"
        )
        val processor = processor(sourceAccess)

        val result = processor.process(TEST_URI)

        assertTrue("Unexpected result: $result", result is FeedbackMediaProcessingResult.Success)
        val media = (result as FeedbackMediaProcessingResult.Success).media
        assertTrue(maxOf(media.width, media.height) <= FeedbackImagePolicy.MAX_OUTPUT_EDGE_PX)
        assertTrue(media.byteCount in 1..FeedbackImagePolicy.MAX_OUTPUT_BYTES)
        assertTrue(sourceAccess.lastStream?.closed == true)
        assertNoStagedSourceFiles()
        processor.delete(media.path)
    }

    @Test
    fun sourceBeyondByteLimitIsRejectedBeforeDecodeAndStreamIsClosed() = runBlocking {
        val sourceAccess = FakeSourceAccess(
            bytes = ByteArray((FeedbackImagePolicy.MAX_SOURCE_BYTES + 1L).toInt()),
            declaredLength = null,
            mimeType = null
        )
        val processor = processor(sourceAccess)

        val result = processor.process(TEST_URI)

        assertTrue(result is FeedbackMediaProcessingResult.Failure)
        assertEquals(
            FeedbackMediaFailureKind.SOURCE_TOO_LARGE,
            (result as FeedbackMediaProcessingResult.Failure).kind
        )
        assertTrue(sourceAccess.lastStream?.closed == true)
        assertNoStagedSourceFiles()
    }

    @Test
    fun corruptProviderImageReturnsTypedInvalidImageFailure() = runBlocking {
        val source = providerFile("feedback_corrupt_${UUID.randomUUID()}.png").apply {
            writeText("not an image")
        }
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                source
            )
            val result = AndroidFeedbackMediaProcessor(context).process(contentUri)

            assertTrue(result is FeedbackMediaProcessingResult.Failure)
            assertEquals(
                FeedbackMediaFailureKind.INVALID_IMAGE,
                (result as FeedbackMediaProcessingResult.Failure).kind
            )
            assertNoStagedSourceFiles()
        } finally {
            source.delete()
        }
    }

    @Test
    fun explicitNonImageMimeTypeIsRejectedWithoutOpeningSource() = runBlocking {
        val sourceAccess = FakeSourceAccess(
            bytes = jpegBytes(320, 240),
            declaredLength = null,
            mimeType = "text/plain"
        )
        val result = processor(sourceAccess).process(TEST_URI)

        assertTrue(result is FeedbackMediaProcessingResult.Failure)
        assertEquals(
            FeedbackMediaFailureKind.UNSUPPORTED_TYPE,
            (result as FeedbackMediaProcessingResult.Failure).kind
        )
        assertEquals(0, sourceAccess.openCount)
    }

    @Test
    fun cancellationIsNotConvertedToIoFailureAndStagedFileIsDeleted() = runBlocking {
        val sourceAccess = CancellingSourceAccess()
        var cancellationObserved = false

        try {
            processor(sourceAccess).process(TEST_URI)
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
        assertTrue(sourceAccess.stream.closed)
        assertNoStagedSourceFiles()
    }

    private fun processor(sourceAccess: FeedbackMediaSourceAccess) =
        AndroidFeedbackMediaProcessor(
            context = context,
            dispatcher = Dispatchers.IO,
            clockMillis = { System.currentTimeMillis() },
            sourceAccess = sourceAccess
        )

    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(18, 72, 96))
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output))
                output.toByteArray()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun assertNoStagedSourceFiles() {
        val directory = File(context.cacheDir, "image_processing")
        assertFalse(
            directory.listFiles().orEmpty().any { it.name.startsWith("image_source_") }
        )
    }

    private fun providerFile(name: String): File {
        return File(context.filesDir, AppMediaScope.PROFILE.directoryName).apply {
            check(exists() || mkdirs()) { "Profile media test directory could not be created" }
        }.resolve(name).apply {
            check(createNewFile()) { "Media test fixture could not be created" }
        }
    }

    private class FakeSourceAccess(
        private val bytes: ByteArray,
        private val declaredLength: Long?,
        private val mimeType: String?
    ) : FeedbackMediaSourceAccess {
        var lastStream: TrackingInputStream? = null
        var openCount: Int = 0

        override fun mimeType(uri: Uri): String? = mimeType
        override fun declaredLength(uri: Uri): Long? = declaredLength
        override fun open(uri: Uri): InputStream {
            openCount += 1
            return TrackingInputStream(bytes).also { lastStream = it }
        }
        override fun displayName(uri: Uri): String = "test-source.jpg"
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class CancellingInputStream : InputStream() {
        var closed: Boolean = false
            private set

        override fun read(): Int = throw CancellationException("cancelled source")

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            throw CancellationException("cancelled source")

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class CancellingSourceAccess : FeedbackMediaSourceAccess {
        val stream = CancellingInputStream()

        override fun mimeType(uri: Uri): String? = "image/jpeg"
        override fun declaredLength(uri: Uri): Long? = null
        override fun open(uri: Uri): InputStream = stream
        override fun displayName(uri: Uri): String = "cancelled.jpg"
    }

    private companion object {
        val TEST_URI: Uri = Uri.parse("content://aqualight.test/feedback-source")
    }
}
