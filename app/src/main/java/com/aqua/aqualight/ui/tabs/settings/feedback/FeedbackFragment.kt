package com.aqua.aqualight.ui.tabs.settings.feedback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.util.Patterns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentFeedbackBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class FeedbackFragment :
    Fragment(R.layout.fragment_feedback) {

    companion object {

        private const val TAG =
            "FeedbackFragment"

        // Blaze planına geçene kadar
        private const val SCREENSHOT_ENABLED =
            true

        // Max 3 MB
        private const val MAX_SCREENSHOT_SIZE_MB =
            3

        private const val MAX_SCREENSHOT_SIZE_BYTES =
            MAX_SCREENSHOT_SIZE_MB * 1024 * 1024
    }

    // ---------------------------------------------------
    // VIEW BINDING
    // ---------------------------------------------------

    private var _binding:
        FragmentFeedbackBinding? = null

    private val binding get() = _binding!!

    // ---------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------

    private val db:
        FirebaseFirestore by lazy {

            FirebaseFirestore.getInstance()
        }

    private val auth:
        FirebaseAuth by lazy {

            FirebaseAuth.getInstance()
        }

    // ---------------------------------------------------
    // UI STATE
    // ---------------------------------------------------

    private lateinit var originalButtonText:
        CharSequence

    private var isSending =
        false

    private var screenshotUri:
        Uri? = null

    // ---------------------------------------------------
    // IMAGE PICKER
    // ---------------------------------------------------

    private val pickScreenshot =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (
                !isAdded ||
                _binding == null
            ) return@registerForActivityResult

            if (uri != null) {

                // ---------------------------------------------------
                // FILE SIZE CONTROL
                // ---------------------------------------------------

                if (!isFileSizeValid(uri)) {

                    showSnackBar(
                        getString(
                            R.string.feedback_error_file_too_large
                        ),
                        BaseActivity.SnackType.ERROR
                    )

                    return@registerForActivityResult
                }

                screenshotUri = uri

                binding.tvScreenshotInfo.text =
                    getFileName(uri)

                binding.ivScreenshotClear.isVisible =
                    true

                showSnackBar(
                    getString(
                        R.string.feedback_screenshot_selected
                    ),
                    BaseActivity.SnackType.SUCCESS
                )
            }
        }

    // ---------------------------------------------------
    // ON VIEW CREATED
    // ---------------------------------------------------

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentFeedbackBinding.bind(view)

        originalButtonText =
            binding.btnSend.text

        setupHeader()

        setupCategoryDropdown()

        setupValidationWatchers()

        setupSendButton()

        setupLottie()

        setupScreenshotVisibility()
    }

    // ---------------------------------------------------
    // HEADER
    // ---------------------------------------------------

    private fun setupHeader() =
        with(binding) {

            btnBack.setOnClickListener {

                findNavController()
                    .popBackStack()
            }

            tvSubInfo.text =
                getString(
                    R.string.feedback_subinfo
                )

            tvFooter.text =
                getString(
                    R.string.feedback_footer
                )
        }

    // ---------------------------------------------------
    // CATEGORY
    // ---------------------------------------------------

    private fun setupCategoryDropdown() =
        with(binding) {

            val categories =
                resources.getStringArray(
                    R.array.feedback_categories
                ).toList()

            val adapter =
                android.widget.ArrayAdapter(
                    requireContext(),
                    R.layout.item_feedback_category,
                    categories
                )

            autoCategory.setAdapter(adapter)

            autoCategory.setDropDownBackgroundDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.bg_dropdown_popup
                )
            )

            autoCategory.setOnClickListener {

                autoCategory.showDropDown()
            }
        }

    // ---------------------------------------------------
    // VALIDATION WATCHERS
    // ---------------------------------------------------

    private fun setupValidationWatchers() =
        with(binding) {

            autoCategory.addTextChangedListener {

                if (!it.isNullOrBlank()) {

                    inputLayoutCategory.error =
                        null
                }
            }

            etMessage.addTextChangedListener {

                val value =
                    it?.toString()
                        ?.trim()
                        .orEmpty()

                if (value.length >= 10) {

                    inputLayoutMessage.error =
                        null
                }
            }

            etEmail.addTextChangedListener {

                val value =
                    it?.toString()
                        ?.trim()
                        .orEmpty()

                if (
                    value.isEmpty() ||
                    Patterns.EMAIL_ADDRESS
                        .matcher(value)
                        .matches()
                ) {

                    inputLayoutEmail.error =
                        null
                }
            }
        }

    // ---------------------------------------------------
    // SCREENSHOT VISIBILITY
    // ---------------------------------------------------

    private fun setupScreenshotVisibility() =
        with(binding) {

            if (SCREENSHOT_ENABLED) {

                tvScreenshotLabel.isVisible =
                    true

                cardScreenshot.isVisible =
                    true

                setupScreenshotRow()

            } else {

                tvScreenshotLabel.isVisible =
                    false

                cardScreenshot.isVisible =
                    false
            }
        }

    // ---------------------------------------------------
    // SCREENSHOT ROW
    // ---------------------------------------------------

    private fun setupScreenshotRow() =
        with(binding) {

            rowAddScreenshot.setOnClickListener {

                pickScreenshot.launch(
                    "image/*"
                )
            }

            ivScreenshotClear.setOnClickListener {

                clearScreenshot()
            }
        }

    // ---------------------------------------------------
    // SEND BUTTON
    // ---------------------------------------------------

    private fun setupSendButton() =
        with(binding) {

            btnSend.setOnClickListener {

                if (!isSending) {

                    sendFeedback()
                }
            }
        }

    // ---------------------------------------------------
    // LOTTIE
    // ---------------------------------------------------

    private fun setupLottie() =
        with(binding) {

            lottieSuccess.addAnimatorListener(
                object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(
                        animation: Animator
                    ) {

                        if (
                            !isAdded ||
                            _binding == null
                        ) return

                        etMessage.isEnabled =
                            true

                        etMessage.setText("")

                        etMessage.clearFocus()

                        inputLayoutMessage.hint =
                            getString(
                                R.string.feedback_hint_message
                            )

                        inputLayoutMessage.error =
                            null

                        lottieSuccess.isVisible =
                            false
                    }
                }
            )
        }

    // ---------------------------------------------------
    // VALIDATE FORM
    // ---------------------------------------------------

    private fun validateForm():
        Boolean {

        with(binding) {

            inputLayoutCategory.error =
                null

            inputLayoutEmail.error =
                null

            inputLayoutMessage.error =
                null

            val category =
                autoCategory.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            val email =
                etEmail.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            val message =
                etMessage.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            var hasError =
                false

            if (category.isEmpty()) {

                inputLayoutCategory.error =
                    getString(
                        R.string.feedback_error_category_required
                    )

                hasError =
                    true
            }

            if (message.length < 10) {

                inputLayoutMessage.error =
                    getString(
                        R.string.feedback_error_message_too_short
                    )

                hasError =
                    true
            }

            if (
                email.isNotEmpty() &&
                !Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
            ) {

                inputLayoutEmail.error =
                    getString(
                        R.string.feedback_error_email_invalid
                    )

                hasError =
                    true
            }

            return !hasError
        }
    }

    // ---------------------------------------------------
    // SEND FEEDBACK
    // ---------------------------------------------------

    private fun sendFeedback() {

        if (!validateForm()) {
            return
        }

        setSendingState(true)

        val user =
            auth.currentUser

        val uid =
            user?.uid ?: "anonymous"

        val category =
            binding.autoCategory.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val email =
            binding.etEmail.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val message =
            binding.etMessage.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val docRef =
            db.collection("feedback_items")
                .document()

        val feedbackData =
            hashMapOf<String, Any?>(
                "category" to category,
                "email" to email.ifBlank { null },
                "message" to message,
                "platform" to "android",
                "appVersion" to getAppVersion(),
                "locale" to Locale.getDefault()
                    .toLanguageTag(),
                "status" to "new",
                "userId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )

        val currentScreenshot =
            screenshotUri

        if (
            !SCREENSHOT_ENABLED ||
            currentScreenshot == null
        ) {

            saveFeedbackOnly(
                docRef.id,
                feedbackData
            )

            return
        }

        uploadScreenshotAndSaveFeedback(
            uid = uid,
            documentId = docRef.id,
            screenshotUri = currentScreenshot,
            feedbackData = feedbackData
        )
    }

    // ---------------------------------------------------
    // SAVE FEEDBACK ONLY
    // ---------------------------------------------------

    private fun saveFeedbackOnly(
        documentId: String,
        feedbackData: HashMap<String, Any?>
    ) {

        db.collection("feedback_items")
            .document(documentId)
            .set(feedbackData)

            .addOnSuccessListener {

                handleSuccess()
            }

            .addOnFailureListener {

                handleError(it)
            }
    }

    // ---------------------------------------------------
    // UPLOAD IMAGE
    // ---------------------------------------------------

    private fun uploadScreenshotAndSaveFeedback(
        uid: String,
        documentId: String,
        screenshotUri: Uri,
        feedbackData: HashMap<String, Any?>
    ) {

        try {

            val compressedFile =
                compressImage(screenshotUri)

            val storageRef =
                Firebase.storage.reference
                    .child(
                        "feedback_screenshots/$uid/$documentId.jpg"
                    )

            storageRef.putFile(
                Uri.fromFile(compressedFile)
            )

                .continueWithTask { task ->

                    if (!task.isSuccessful) {

                        throw task.exception
                            ?: Exception("Upload failed")
                    }

                    storageRef.downloadUrl
                }

                .addOnSuccessListener { uri ->

                    if (
                        !isAdded ||
                        _binding == null
                    ) return@addOnSuccessListener

                    feedbackData["screenshotUrl"] =
                        uri.toString()

                    saveFeedbackOnly(
                        documentId,
                        feedbackData
                    )
                }

                .addOnFailureListener {

                    handleError(it)
                }

        } catch (e: Exception) {

            handleError(e)
        }
    }

    // ---------------------------------------------------
    // IMAGE COMPRESSION
    // ---------------------------------------------------

    private fun compressImage(
        uri: Uri
    ): File {

        val inputStream =
            requireContext()
                .contentResolver
                .openInputStream(uri)

        val originalBitmap =
            BitmapFactory.decodeStream(
                inputStream
            )

        // ---------------------------------------------------
        // MAX RESOLUTION
        // ---------------------------------------------------

        val maxSize =
            1440

        val ratio =
            minOf(
                maxSize.toFloat() /
                        originalBitmap.width,

                maxSize.toFloat() /
                        originalBitmap.height
            )

        val width =
            (originalBitmap.width * ratio)
                .toInt()

        val height =
            (originalBitmap.height * ratio)
                .toInt()

        val resizedBitmap =
            Bitmap.createScaledBitmap(
                originalBitmap,
                width,
                height,
                true
            )

        val file =
            File(
                requireContext().cacheDir,
                "feedback_temp.jpg"
            )

        val outputStream =
            FileOutputStream(file)

        resizedBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            75,
            outputStream
        )

        outputStream.flush()

        outputStream.close()

        return file
    }

    // ---------------------------------------------------
    // FILE SIZE CONTROL
    // ---------------------------------------------------

    private fun isFileSizeValid(
        uri: Uri
    ): Boolean {

        return try {

            val descriptor =
                requireContext()
                    .contentResolver
                    .openAssetFileDescriptor(
                        uri,
                        "r"
                    )

            val size =
                descriptor?.length ?: 0L

            descriptor?.close()

            size <= MAX_SCREENSHOT_SIZE_BYTES

        } catch (e: Exception) {

            false
        }
    }

    // ---------------------------------------------------
    // FILE NAME
    // ---------------------------------------------------

    private fun getFileName(
        uri: Uri
    ): String {

        var result =
            "screenshot"

        val cursor =
            requireContext()
                .contentResolver
                .query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )

        cursor?.use {

            val nameIndex =
                it.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

            if (
                nameIndex != -1 &&
                it.moveToFirst()
            ) {

                result =
                    it.getString(nameIndex)
            }
        }

        return result
    }

    // ---------------------------------------------------
    // SUCCESS
    // ---------------------------------------------------

    private fun handleSuccess() {

        if (
            !isAdded ||
            _binding == null
        ) return

        setSendingState(false)

        resetForm()

        showSuccessUI()

        showSnackBar(
            getString(
                R.string.feedback_sent_success
            ),
            BaseActivity.SnackType.SUCCESS
        )
    }

    // ---------------------------------------------------
    // ERROR
    // ---------------------------------------------------

    private fun handleError(
        throwable: Throwable?
    ) {

        if (
            !isAdded ||
            _binding == null
        ) return

        Log.e(
            TAG,
            "Feedback error",
            throwable
        )

        setSendingState(false)

        val message =
            when (throwable) {

                is StorageException -> {

                    getString(
                        R.string.feedback_error_upload
                    )
                }

                else -> {

                    getString(
                        R.string.feedback_error_generic
                    )
                }
            }

        showSnackBar(
            message,
            BaseActivity.SnackType.ERROR
        )
    }

    // ---------------------------------------------------
    // RESET FORM
    // ---------------------------------------------------

    private fun resetForm() =
        with(binding) {

            autoCategory.setText("")

            etEmail.setText("")

            etMessage.setText("")

            etMessage.clearFocus()

            inputLayoutMessage.error =
                null

            inputLayoutMessage.hint =
                getString(
                    R.string.feedback_hint_message
                )

            clearScreenshot()
        }

    // ---------------------------------------------------
    // CLEAR SCREENSHOT
    // ---------------------------------------------------

    private fun clearScreenshot() =
        with(binding) {

            screenshotUri =
                null

            tvScreenshotInfo.text =
                getString(
                    R.string.feedback_screenshot_add
                )

            ivScreenshotClear.isVisible =
                false
        }

    // ---------------------------------------------------
    // SENDING STATE
    // ---------------------------------------------------

    private fun setSendingState(
        sending: Boolean
    ) {

        if (
            !isAdded ||
            _binding == null
        ) return

        with(binding) {

            isSending =
                sending

            btnSend.isEnabled =
                !sending

            btnSend.text =
                if (sending) {

                    getString(
                        R.string.feedback_sending
                    )

                } else {

                    originalButtonText
                }

            progressBarSending.isVisible =
                sending
        }

        (
            requireActivity()
                as? BaseActivity
            )?.showLoading(
            sending
        )
    }

    // ---------------------------------------------------
    // SUCCESS UI
    // ---------------------------------------------------

    private fun showSuccessUI() =
        with(binding) {

            inputLayoutMessage.hint =
                ""

            etMessage.isEnabled =
                false

            etMessage.setText(
                getString(
                    R.string.feedback_success_text
                )
            )

            lottieSuccess.isVisible =
                true

            lottieSuccess.progress =
                0f

            lottieSuccess.playAnimation()
        }

    // ---------------------------------------------------
    // APP VERSION
    // ---------------------------------------------------

    private fun getAppVersion():
        String {

        return try {

            val info =
                requireContext()
                    .packageManager
                    .getPackageInfo(
                        requireContext().packageName,
                        0
                    )

            info.versionName
                ?: "unknown"

        } catch (e: Exception) {

            "unknown"
        }
    }

    // ---------------------------------------------------
    // GLOBAL SNACKBAR
    // ---------------------------------------------------

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {

        (
            requireActivity()
                as? BaseActivity
            )?.showSnackBar(
            message,
            type
        )
    }

    // ---------------------------------------------------
    // DESTROY
    // ---------------------------------------------------

    override fun onDestroyView() {

        super.onDestroyView()

        _binding =
            null
    }
}