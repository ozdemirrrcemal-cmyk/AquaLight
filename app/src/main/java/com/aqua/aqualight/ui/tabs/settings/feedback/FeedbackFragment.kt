package com.aqua.aqualight.ui.tabs.settings.feedback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.net.Uri
import android.os.Bundle
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
import com.google.firebase.storage.storage
import java.util.Locale

class FeedbackFragment :
    Fragment(R.layout.fragment_feedback) {

    // ---------------------------------------------------
    // CONFIG
    // ---------------------------------------------------

    private val SCREENSHOT_ENABLED = true

    private val MAX_SCREENSHOT_SIZE =
        3 * 1024 * 1024L // 3MB

    // ---------------------------------------------------
    // VIEW BINDING
    // ---------------------------------------------------

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    // ---------------------------------------------------
    // BASE ACTIVITY
    // ---------------------------------------------------

    private val baseActivity
        get() = activity as? BaseActivity

    // ---------------------------------------------------
    // FIREBASE
    // ---------------------------------------------------

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    // ---------------------------------------------------
    // STATE
    // ---------------------------------------------------

    private var originalButtonText: CharSequence? =
        null

    private var isSending = false

    private var screenshotUri: Uri? = null

    // ---------------------------------------------------
    // SCREENSHOT PICKER
    // ---------------------------------------------------

    private val pickScreenshot =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri == null) {
                return@registerForActivityResult
            }

            try {

                val size =
                    requireContext()
                        .contentResolver
                        .openAssetFileDescriptor(
                            uri,
                            "r"
                        )
                        ?.length ?: 0L

                // ---------------------------------------------------
                // SIZE CHECK
                // ---------------------------------------------------

                if (size > MAX_SCREENSHOT_SIZE) {

                    baseActivity?.showSnackBar(
                        getString(
                            R.string.feedback_image_too_large_message
                        ),
                        BaseActivity.SnackType.ERROR
                    )

                    return@registerForActivityResult
                }

                screenshotUri = uri

                binding.tvScreenshotInfo.text =
                    getString(
                        R.string.feedback_screenshot_selected
                    )

                binding.ivScreenshotClear.isVisible =
                    true

                baseActivity?.showSnackBar(
                    getString(
                        R.string.feedback_screenshot_selected
                    ),
                    BaseActivity.SnackType.SUCCESS
                )

            } catch (_: Exception) {

                showErrorSnackBar()
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

        setupSendButton()

        setupLottie()

        setupValidationWatchers()

        setupScreenshotSection()
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
    // CATEGORY DROPDOWN
    // ---------------------------------------------------

    private fun setupCategoryDropdown() =
        with(binding) {

            val categories =
                resources
                    .getStringArray(
                        R.array.feedback_categories
                    )
                    .toList()

            val adapter =
                android.widget.ArrayAdapter(
                    requireContext(),
                    R.layout.item_feedback_category,
                    categories
                )

            autoCategory.setAdapter(adapter)

            autoCategory
                .setDropDownBackgroundDrawable(
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
    // VALIDATION WATCHERS
    // ---------------------------------------------------

    private fun setupValidationWatchers() =
        with(binding) {

            autoCategory
                .addTextChangedListener { text ->

                    if (!text.isNullOrBlank()) {

                        inputLayoutCategory.error =
                            null
                    }
                }

            etMessage
                .addTextChangedListener { text ->

                    val value =
                        text?.toString()
                            ?.trim()
                            .orEmpty()

                    if (value.length >= 10) {

                        inputLayoutMessage.error =
                            null
                    }
                }

            etEmail
                .addTextChangedListener { text ->

                    val value =
                        text?.toString()
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
    // SCREENSHOT SECTION
    // ---------------------------------------------------

    private fun setupScreenshotSection() =
        with(binding) {

            if (SCREENSHOT_ENABLED) {

                tvScreenshotLabel.isVisible =
                    true

                cardScreenshot.isVisible =
                    true

                rowAddScreenshot
                    .setOnClickListener {

                        pickScreenshot.launch(
                            "image/*"
                        )
                    }

                ivScreenshotClear
                    .setOnClickListener {

                        screenshotUri = null

                        tvScreenshotInfo.text =
                            getString(
                                R.string.feedback_screenshot_add
                            )

                        ivScreenshotClear.isVisible =
                            false

                        baseActivity?.showSnackBar(
                            getString(
                                R.string.feedback_screenshot_add
                            ),
                            BaseActivity.SnackType.NORMAL
                        )
                    }

            } else {

                tvScreenshotLabel.isVisible =
                    false

                cardScreenshot.isVisible =
                    false
            }
        }

    // ---------------------------------------------------
    // LOTTIE
    // ---------------------------------------------------

    private fun setupLottie() =
        with(binding) {

            lottieSuccess
                .addAnimatorListener(
                    object : AnimatorListenerAdapter() {

                        override fun onAnimationEnd(
                            animation: Animator
                        ) {

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
    // SEND FEEDBACK
    // ---------------------------------------------------

    private fun sendFeedback() =
        with(binding) {

            root.clearFocus()

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

            var hasError = false

            // ---------------------------------------------------
            // CATEGORY VALIDATION
            // ---------------------------------------------------

            if (category.isEmpty()) {

                inputLayoutCategory.error =
                    getString(
                        R.string.feedback_error_category_required
                    )

                hasError = true
            }

            // ---------------------------------------------------
            // MESSAGE VALIDATION
            // ---------------------------------------------------

            if (message.length < 10) {

                inputLayoutMessage.error =
                    getString(
                        R.string.feedback_error_message_too_short
                    )

                hasError = true
            }

            // ---------------------------------------------------
            // EMAIL VALIDATION
            // ---------------------------------------------------

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

                hasError = true
            }

            // ---------------------------------------------------
            // VALIDATION FAILED
            // ---------------------------------------------------

            if (hasError) {

                baseActivity?.showSnackBar(
                    getString(
                        R.string.feedback_error_generic
                    ),
                    BaseActivity.SnackType.WARNING
                )

                return
            }

            setSendingState(true)

            val user =
                auth.currentUser

            val uid =
                user?.uid ?: "anonymous"

            val docRef =
                db.collection("feedback")
                    .document(uid)
                    .collection("items")
                    .document()

            // ---------------------------------------------------
            // FIRESTORE DATA
            // ---------------------------------------------------

            val baseData =
                hashMapOf<String, Any>(
                    "category" to category,
                    "message" to message,
                    "platform" to "android",
                    "appVersion" to getAppVersion(),
                    "locale" to Locale
                        .getDefault()
                        .toLanguageTag(),
                    "status" to "new",
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )

            if (email.isNotBlank()) {

                baseData["email"] =
                    email
            }

            val currentScreenshot =
                screenshotUri

            // ---------------------------------------------------
            // NO SCREENSHOT
            // ---------------------------------------------------

            if (
                !SCREENSHOT_ENABLED ||
                currentScreenshot == null
            ) {

                docRef.set(baseData)
                    .addOnSuccessListener {

                        setSendingState(false)

                        resetForm()

                        showSuccessUI()

                        showSuccessSnackBar()
                    }
                    .addOnFailureListener {

                        setSendingState(false)

                        showErrorSnackBar()
                    }

                return@with
            }

            // ---------------------------------------------------
            // STORAGE UPLOAD
            // ---------------------------------------------------

            val storageRef =
                Firebase.storage.reference
                    .child(
                        "feedback_screenshots/$uid/${docRef.id}.jpg"
                    )

            storageRef.putFile(currentScreenshot)
                .continueWithTask { task ->

                    if (!task.isSuccessful) {

                        throw (
                            task.exception
                                ?: Exception("Upload failed")
                            )
                    }

                    return@continueWithTask
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->

                    val dataWithScreenshot =
                        baseData.toMutableMap()

                    dataWithScreenshot["screenshotUrl"] =
                        downloadUri.toString()

                    docRef.set(dataWithScreenshot)
                        .addOnSuccessListener {

                            setSendingState(false)

                            resetForm()

                            showSuccessUI()

                            showSuccessSnackBar()
                        }
                        .addOnFailureListener {

                            setSendingState(false)

                            showErrorSnackBar()
                        }
                }
                .addOnFailureListener {

                    setSendingState(false)

                    showErrorSnackBar()
                }
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

            screenshotUri = null

            tvScreenshotInfo.text =
                getString(
                    R.string.feedback_screenshot_add
                )

            ivScreenshotClear.isVisible =
                false
        }

    // ---------------------------------------------------
    // BUTTON LOADING STATE
    // ---------------------------------------------------

    private fun setSendingState(
        sending: Boolean
    ) =
        with(binding) {

            isSending = sending

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

    // ---------------------------------------------------
    // SUCCESS UI
    // ---------------------------------------------------

    private fun showSuccessUI() =
        with(binding) {

            inputLayoutMessage.hint = ""

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
    // GLOBAL SNACKBAR
    // ---------------------------------------------------

    private fun showErrorSnackBar() {

        baseActivity?.showSnackBar(
            getString(
                R.string.feedback_error_generic
            ),
            BaseActivity.SnackType.ERROR
        )
    }

    private fun showSuccessSnackBar() {

        baseActivity?.showSnackBar(
            getString(
                R.string.feedback_success_text
            ),
            BaseActivity.SnackType.SUCCESS
        )
    }

    // ---------------------------------------------------
    // APP VERSION
    // ---------------------------------------------------

    private fun getAppVersion(): String {

        return try {

            val pInfo =
                requireContext()
                    .packageManager
                    .getPackageInfo(
                        requireContext().packageName,
                        0
                    )

            pInfo.versionName
                ?: "unknown"

        } catch (_: Exception) {

            "unknown"
        }
    }

    // ---------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}