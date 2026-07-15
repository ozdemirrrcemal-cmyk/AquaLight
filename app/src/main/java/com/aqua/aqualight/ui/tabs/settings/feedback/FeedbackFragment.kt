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
import com.aqua.aqualight.R
import com.aqua.aqualight.application.feedback.FeedbackSubmissionCallback
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailure
import com.aqua.aqualight.application.feedback.FeedbackSubmissionFailureKind
import com.aqua.aqualight.application.feedback.FeedbackSubmissionRequest
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentFeedbackBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    companion object {
        private const val TAG = "FeedbackFragment"
        private const val SCREENSHOT_ENABLED = true
        private const val MAX_SCREENSHOT_SIZE_MB = 3
        private const val MAX_SCREENSHOT_SIZE_BYTES = MAX_SCREENSHOT_SIZE_MB * 1024 * 1024
    }

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val feedbackOperations by lazy {
        requireContext().requireAppContainer().feedbackSubmissionOperations
    }

    private lateinit var originalButtonText: CharSequence
    private var isSending = false
    private var screenshotUri: Uri? = null

    private val pickScreenshot = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (!isAdded || _binding == null || uri == null) {
            return@registerForActivityResult
        }

        if (!isFileSizeValid(uri)) {
            showSnackBar(
                getString(R.string.feedback_error_file_too_large),
                BaseActivity.SnackType.ERROR
            )
            return@registerForActivityResult
        }

        screenshotUri = uri
        binding.tvScreenshotInfo.text = getFileName(uri)
        binding.ivScreenshotClear.isVisible = true
        applySelectedScreenshotStyle()
        showSnackBar(
            getString(R.string.feedback_screenshot_selected),
            BaseActivity.SnackType.SUCCESS
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)
        originalButtonText = binding.btnSend.text

        setupHeader()
        setupCategoryDropdown()
        setupValidationWatchers()
        setupSendButton()
        setupLottie()
        setupScreenshotVisibility()
        resetScreenshotStyle()
    }

    private fun setupHeader() = with(binding) {
        appHeader.setupAquaHeader(fragment = this@FeedbackFragment)
        tvSubInfo.text = getString(R.string.feedback_subinfo)
        tvFooter.text = getString(R.string.feedback_footer)
    }

    private fun setupCategoryDropdown() = with(binding) {
        val categories = resources.getStringArray(R.array.feedback_categories).toList()
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            R.layout.item_feedback_category,
            categories
        )
        autoCategory.setAdapter(adapter)
        autoCategory.setDropDownBackgroundDrawable(
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_dropdown_popup)
        )
        autoCategory.setOnClickListener {
            autoCategory.showDropDown()
        }
    }

    private fun setupValidationWatchers() = with(binding) {
        autoCategory.addTextChangedListener {
            if (!it.isNullOrBlank()) {
                inputLayoutCategory.error = null
            }
        }
        etMessage.addTextChangedListener {
            if (it?.toString()?.trim().orEmpty().length >= 10) {
                inputLayoutMessage.error = null
            }
        }
        etEmail.addTextChangedListener {
            val value = it?.toString()?.trim().orEmpty()
            if (value.isEmpty() || Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                inputLayoutEmail.error = null
            }
        }
    }

    private fun setupScreenshotVisibility() = with(binding) {
        tvScreenshotLabel.isVisible = SCREENSHOT_ENABLED
        cardScreenshot.isVisible = SCREENSHOT_ENABLED
        if (SCREENSHOT_ENABLED) {
            rowAddScreenshot.setOnClickListener {
                pickScreenshot.launch("image/*")
            }
            ivScreenshotClear.setOnClickListener {
                clearScreenshot()
            }
        }
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            if (!isSending) {
                sendFeedback()
            }
        }
    }

    private fun setupLottie() {
        binding.lottieSuccess.addAnimatorListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isAdded || _binding == null) {
                        return
                    }
                    binding.etMessage.isEnabled = true
                    binding.etMessage.setText("")
                    binding.etMessage.clearFocus()
                    binding.inputLayoutMessage.hint = getString(R.string.feedback_hint_message)
                    binding.inputLayoutMessage.error = null
                    binding.lottieSuccess.isVisible = false
                }
            }
        )
    }

    private fun applySelectedScreenshotStyle() = with(binding) {
        tvScreenshotInfo.setTextColor(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )
        ivScreenshotIcon.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.aqua_accent)
        )
        ivScreenshotClear.setColorFilter(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )
        cardScreenshot.strokeColor =
            ContextCompat.getColor(requireContext(), R.color.aqua_accent)
    }

    private fun resetScreenshotStyle() = with(binding) {
        val secondaryColor = ContextCompat.getColor(
            requireContext(),
            R.color.aqua_card_text_secondary
        )
        tvScreenshotInfo.setTextColor(secondaryColor)
        ivScreenshotIcon.setColorFilter(secondaryColor)
        ivScreenshotClear.setColorFilter(secondaryColor)
        cardScreenshot.strokeColor = ContextCompat.getColor(
            requireContext(),
            R.color.aqua_card_outline_subtle
        )
    }

    private fun validateForm(): Boolean = with(binding) {
        inputLayoutCategory.error = null
        inputLayoutEmail.error = null
        inputLayoutMessage.error = null

        val category = autoCategory.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val message = etMessage.text?.toString()?.trim().orEmpty()
        var hasError = false

        if (category.isEmpty()) {
            inputLayoutCategory.error = getString(R.string.feedback_error_category_required)
            hasError = true
        }
        if (message.length < 10) {
            inputLayoutMessage.error = getString(R.string.feedback_error_message_too_short)
            hasError = true
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputLayoutEmail.error = getString(R.string.feedback_error_email_invalid)
            hasError = true
        }

        !hasError
    }

    private fun sendFeedback() {
        if (!validateForm()) {
            return
        }

        setSendingState(true)

        val screenshotFile = try {
            screenshotUri?.takeIf { SCREENSHOT_ENABLED }?.let(::compressImage)
        } catch (error: Throwable) {
            handleError(
                FeedbackSubmissionFailure(
                    kind = FeedbackSubmissionFailureKind.GENERIC,
                    cause = error
                )
            )
            return
        }

        feedbackOperations.submit(
            request = FeedbackSubmissionRequest(
                category = binding.autoCategory.text?.toString()?.trim().orEmpty(),
                email = binding.etEmail.text?.toString()?.trim().orEmpty(),
                message = binding.etMessage.text?.toString()?.trim().orEmpty(),
                appVersion = getAppVersion(),
                localeTag = Locale.getDefault().toLanguageTag()
            ),
            screenshotFile = screenshotFile,
            callback = object : FeedbackSubmissionCallback {
                override fun onSuccess() {
                    handleSuccess()
                }

                override fun onFailure(failure: FeedbackSubmissionFailure) {
                    handleError(failure)
                }
            }
        )
    }

    private fun compressImage(uri: Uri): File {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val originalBitmap = requireNotNull(BitmapFactory.decodeStream(inputStream)) {
            "Feedback screenshot could not be decoded."
        }
        val maxSize = 1440
        val ratio = minOf(
            maxSize.toFloat() / originalBitmap.width,
            maxSize.toFloat() / originalBitmap.height
        )
        val width = (originalBitmap.width * ratio).toInt()
        val height = (originalBitmap.height * ratio).toInt()
        val resizedBitmap = Bitmap.createScaledBitmap(
            originalBitmap,
            width,
            height,
            true
        )
        val file = File(requireContext().cacheDir, "feedback_temp.jpg")
        val outputStream = FileOutputStream(file)
        resizedBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            75,
            outputStream
        )
        outputStream.flush()
        outputStream.close()
        return file
    }

    private fun isFileSizeValid(uri: Uri): Boolean {
        return try {
            val descriptor = requireContext().contentResolver.openAssetFileDescriptor(uri, "r")
            val size = descriptor?.length ?: 0L
            descriptor?.close()
            size <= MAX_SCREENSHOT_SIZE_BYTES
        } catch (_: Exception) {
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = "screenshot"
        val cursor = requireContext().contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && it.moveToFirst()) {
                result = it.getString(nameIndex)
            }
        }
        return result
    }

    private fun handleSuccess() {
        if (!isAdded || _binding == null) {
            return
        }
        setSendingState(false)
        resetForm()
        showSuccessUI()
        showSnackBar(
            getString(R.string.feedback_sent_success),
            BaseActivity.SnackType.SUCCESS
        )
    }

    private fun handleError(failure: FeedbackSubmissionFailure) {
        if (!isAdded || _binding == null) {
            return
        }
        Log.e(TAG, "Feedback error", failure.cause)
        setSendingState(false)
        val message = when (failure.kind) {
            FeedbackSubmissionFailureKind.UPLOAD ->
                getString(R.string.feedback_error_upload)
            FeedbackSubmissionFailureKind.GENERIC ->
                getString(R.string.feedback_error_generic)
        }
        showSnackBar(message, BaseActivity.SnackType.ERROR)
    }

    private fun resetForm() = with(binding) {
        autoCategory.setText("")
        etEmail.setText("")
        etMessage.setText("")
        etMessage.clearFocus()
        inputLayoutMessage.error = null
        inputLayoutMessage.hint = getString(R.string.feedback_hint_message)
        clearScreenshot()
    }

    private fun clearScreenshot() = with(binding) {
        screenshotUri = null
        tvScreenshotInfo.text = getString(R.string.feedback_screenshot_add)
        ivScreenshotClear.isVisible = false
        resetScreenshotStyle()
    }

    private fun setSendingState(sending: Boolean) {
        if (!isAdded || _binding == null) {
            return
        }
        isSending = sending
        binding.btnSend.isEnabled = !sending
        binding.btnSend.text = if (sending) {
            getString(R.string.feedback_sending)
        } else {
            originalButtonText
        }
        binding.progressBarSending.isVisible = sending
        setFragmentGlobalLoading(sending)
    }

    private fun showSuccessUI() = with(binding) {
        inputLayoutMessage.hint = ""
        etMessage.isEnabled = false
        etMessage.setText(getString(R.string.feedback_success_text))
        lottieSuccess.isVisible = true
        lottieSuccess.progress = 0f
        lottieSuccess.playAnimation()
    }

    private fun getAppVersion(): String {
        return try {
            val info = requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            )
            info.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (requireActivity() as? BaseActivity)?.showSnackBar(message, type)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
