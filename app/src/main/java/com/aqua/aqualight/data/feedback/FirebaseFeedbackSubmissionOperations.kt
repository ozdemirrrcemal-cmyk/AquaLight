package com.aqua.aqualight.data.feedback

import android.net.Uri
import com.aqua.aqualight.application.feedback.FeedbackSubmissionCallback
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionOperations
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Firebase adapter for the existing feedback submission contract. */
class FirebaseFeedbackSubmissionOperations private constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : FeedbackSubmissionOperations {

    override fun submit(
        request: FeedbackSubmissionRequest,
        screenshotFile: File?,
        callback: FeedbackSubmissionCallback
    ) {
        val guardedCallback = SingleDeliveryCallback(callback)
        val ownerUid = auth.currentUser?.uid ?: ANONYMOUS_OWNER_UID
        val documentId = firestore.collection(FEEDBACK_COLLECTION)
            .document()
            .id
        val feedbackData = hashMapOf<String, Any?>(
            FIELD_CATEGORY to request.category,
            FIELD_EMAIL to request.email.ifBlank { null },
            FIELD_MESSAGE to request.message,
            FIELD_PLATFORM to PLATFORM_ANDROID,
            FIELD_APP_VERSION to request.appVersion,
            FIELD_LOCALE to request.localeTag,
            FIELD_STATUS to STATUS_NEW,
            FIELD_USER_ID to ownerUid,
            FIELD_CREATED_AT to FieldValue.serverTimestamp()
        )

        if (screenshotFile == null) {
            saveFeedback(
                documentId = documentId,
                feedbackData = feedbackData,
                callback = guardedCallback
            )
            return
        }

        val storageReference = storage.reference.child(
            "$SCREENSHOT_ROOT/$ownerUid/$documentId.jpg"
        )

        storageReference.putFile(Uri.fromFile(screenshotFile))
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("Feedback screenshot upload failed.")
                }
                storageReference.downloadUrl
            }
            .addOnSuccessListener { uri ->
                feedbackData[FIELD_SCREENSHOT_URL] = uri.toString()
                saveFeedback(
                    documentId = documentId,
                    feedbackData = feedbackData,
                    callback = guardedCallback
                )
            }
            .addOnFailureListener { error ->
                guardedCallback.onFailure(
                    FeedbackSubmissionFailure(
                        kind = FeedbackSubmissionFailureKind.UPLOAD,
                        cause = error
                    )
                )
            }
    }

    private fun saveFeedback(
        documentId: String,
        feedbackData: HashMap<String, Any?>,
        callback: FeedbackSubmissionCallback
    ) {
        firestore.collection(FEEDBACK_COLLECTION)
            .document(documentId)
            .set(feedbackData)
            .addOnSuccessListener {
                callback.onSuccess()
            }
            .addOnFailureListener { error ->
                callback.onFailure(
                    FeedbackSubmissionFailure(
                        kind = if (error is StorageException) {
                            FeedbackSubmissionFailureKind.UPLOAD
                        } else {
                            FeedbackSubmissionFailureKind.GENERIC
                        },
                        cause = error
                    )
                )
            }
    }

    private class SingleDeliveryCallback(
        private val delegate: FeedbackSubmissionCallback
    ) : FeedbackSubmissionCallback {
        private val delivered = AtomicBoolean(false)

        override fun onSuccess() {
            if (delivered.compareAndSet(false, true)) {
                delegate.onSuccess()
            }
        }

        override fun onFailure(failure: FeedbackSubmissionFailure) {
            if (delivered.compareAndSet(false, true)) {
                delegate.onFailure(failure)
            }
        }
    }

    companion object {
        private const val FEEDBACK_COLLECTION = "feedback_items"
        private const val SCREENSHOT_ROOT = "feedback_screenshots"
        private const val ANONYMOUS_OWNER_UID = "anonymous"
        private const val PLATFORM_ANDROID = "android"
        private const val STATUS_NEW = "new"

        private const val FIELD_CATEGORY = "category"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_MESSAGE = "message"
        private const val FIELD_PLATFORM = "platform"
        private const val FIELD_APP_VERSION = "appVersion"
        private const val FIELD_LOCALE = "locale"
        private const val FIELD_STATUS = "status"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_SCREENSHOT_URL = "screenshotUrl"

        fun create(): FirebaseFeedbackSubmissionOperations {
            return FirebaseFeedbackSubmissionOperations(
                auth = FirebaseAuth.getInstance(),
                firestore = FirebaseFirestore.getInstance(),
                storage = Firebase.storage
            )
        }
    }
}
