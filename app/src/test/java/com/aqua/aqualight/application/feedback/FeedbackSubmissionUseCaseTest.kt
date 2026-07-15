package com.aqua.aqualight.application.feedback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSubmissionUseCaseTest {

    @Test
    fun forwardsRequestFileAndSuccessThroughOneFakeBoundary() {
        val fake = FakeFeedbackSubmissionOperations()
        val useCase = FeedbackSubmissionUseCase(fake)
        val request = request()
        val screenshotFile = File("feedback.jpg")
        var success = false

        useCase.submit(
            request = request,
            screenshotFile = screenshotFile,
            callback = object : FeedbackSubmissionCallback {
                override fun onSuccess() {
                    success = true
                }

                override fun onFailure(failure: FeedbackSubmissionFailure) = Unit
            }
        )

        assertSame(request, fake.request)
        assertSame(screenshotFile, fake.screenshotFile)
        fake.callback?.onSuccess()
        assertTrue(success)
    }

    @Test
    fun forwardsTypedFailureWithoutFirebaseOrAndroidDependencies() {
        val fake = FakeFeedbackSubmissionOperations()
        val useCase = FeedbackSubmissionUseCase(fake)
        val expectedCause = IllegalStateException("upload failed")
        var received: FeedbackSubmissionFailure? = null

        useCase.submit(
            request = request(),
            screenshotFile = null,
            callback = object : FeedbackSubmissionCallback {
                override fun onSuccess() = Unit

                override fun onFailure(failure: FeedbackSubmissionFailure) {
                    received = failure
                }
            }
        )

        fake.callback?.onFailure(
            FeedbackSubmissionFailure(
                kind = FeedbackSubmissionFailureKind.UPLOAD,
                cause = expectedCause
            )
        )

        assertEquals(FeedbackSubmissionFailureKind.UPLOAD, received?.kind)
        assertSame(expectedCause, received?.cause)
    }

    private fun request(): FeedbackSubmissionRequest {
        return FeedbackSubmissionRequest(
            category = "Bug",
            email = "user@example.com",
            message = "A reproducible feedback message",
            appVersion = "1.0",
            localeTag = "tr-TR"
        )
    }

    private class FakeFeedbackSubmissionOperations : FeedbackSubmissionOperations {
        var request: FeedbackSubmissionRequest? = null
        var screenshotFile: File? = null
        var callback: FeedbackSubmissionCallback? = null

        override fun submit(
            request: FeedbackSubmissionRequest,
            screenshotFile: File?,
            callback: FeedbackSubmissionCallback
        ) {
            this.request = request
            this.screenshotFile = screenshotFile
            this.callback = callback
        }
    }
}
