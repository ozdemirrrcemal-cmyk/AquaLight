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
import com.aqua.aqualight.databinding.FragmentFeedbackBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import java.util.Locale

class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    // 🔒 Blaze’e geçene kadar screenshot özelliğini buradan kontrol et
    private val SCREENSHOT_ENABLED = true   // Spark’ta istersen false yaparsın

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var originalButtonText: CharSequence? = null
    private var isSending = false

    // 📎 Seçilen ekran görüntüsü Uri'si (opsiyonel)
    private var screenshotUri: Uri? = null

    // 📂 Galeriden görüntü seçmek için launcher
    private val pickScreenshot =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                screenshotUri = uri
                binding.tvScreenshotInfo.text =
                    binding.root.context.getString(R.string.feedback_screenshot_selected)
                binding.ivScreenshotClear.isVisible = true
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)

        originalButtonText = binding.btnSend.text

        // 🔧 Screenshot satırını flag’e göre göster/gizle
        if (SCREENSHOT_ENABLED) {
            binding.tvScreenshotLabel.isVisible = true
            binding.cardScreenshot.isVisible = true
            setupScreenshotRow()
        } else {
            binding.tvScreenshotLabel.isVisible = false
            binding.cardScreenshot.isVisible = false
        }

        setupHeader()
        setupCategoryDropdown()
        setupSendButton()
        setupLottie()
        setupValidationWatchers()
    }

    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener { findNavController().popBackStack() }
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
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_dropdown_popup
            )
        )

        autoCategory.setOnClickListener {
            autoCategory.showDropDown()
        }
    }

    private fun setupSendButton() = with(binding) {
        btnSend.setOnClickListener {
            if (!isSending) {
                sendFeedback()
            }
        }
    }

    /** Kullanıcı yazdıkça hata mesajlarını otomatik temizle */
    private fun setupValidationWatchers() = with(binding) {
        // Kategori
        autoCategory.addTextChangedListener { text ->
            if (!text.isNullOrBlank()) {
                inputLayoutCategory.error = null
            }
        }

        // Mesaj (10+ karakter olunca hata kalksın)
        etMessage.addTextChangedListener { text ->
            val value = text?.toString()?.trim().orEmpty()
            if (value.length >= 10) {
                inputLayoutMessage.error = null
            }
        }

        // Email: boşsa veya valid ise hata kalksın
        etEmail.addTextChangedListener { text ->
            val value = text?.toString()?.trim().orEmpty()
            if (value.isEmpty() ||
                Patterns.EMAIL_ADDRESS.matcher(value).matches()
            ) {
                inputLayoutEmail.error = null
            }
        }
    }

    /** Ekran görüntüsü satırı: seçme + temizleme */
    private fun setupScreenshotRow() = with(binding) {
        rowAddScreenshot.setOnClickListener {
            pickScreenshot.launch("image/*")
        }

        ivScreenshotClear.setOnClickListener {
            screenshotUri = null
            tvScreenshotInfo.text = getString(R.string.feedback_screenshot_add)
            ivScreenshotClear.isVisible = false
        }
    }

    /** Lottie bitince mesaj alanını normale döndür */
    private fun setupLottie() = with(binding) {
        lottieSuccess.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // animasyon bittikten sonra mesaj kutusunu tekrar yazılabilir yap
                etMessage.isEnabled = true
                etMessage.setText("")
                etMessage.clearFocus()
                inputLayoutMessage.hint = getString(R.string.feedback_hint_message)
                inputLayoutMessage.error = null
                lottieSuccess.isVisible = false
            }
        })
    }

    private fun sendFeedback() {
        with(binding) {
            // Eski hataları temizle
            inputLayoutCategory.error = null
            inputLayoutEmail.error = null
            inputLayoutMessage.error = null

            val category = autoCategory.text?.toString()?.trim().orEmpty()
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val message = etMessage.text?.toString()?.trim().orEmpty()

            var hasError = false

            if (category.isEmpty()) {
                inputLayoutCategory.error =
                    getString(R.string.feedback_error_category_required)
                hasError = true
            }

            if (message.length < 10) {
                inputLayoutMessage.error =
                    getString(R.string.feedback_error_message_too_short)
                hasError = true
            }

            if (email.isNotEmpty() &&
                !Patterns.EMAIL_ADDRESS.matcher(email).matches()
            ) {
                inputLayoutEmail.error =
                    getString(R.string.feedback_error_email_invalid)
                hasError = true
            }

            if (hasError) return

            setSendingState(true)

            val user = auth.currentUser
            val uid = user?.uid ?: "anonymous"

            val docRef = db.collection("feedback")
                .document(uid)
                .collection("items")
                .document()

            val baseData = hashMapOf(
                "category" to category,
                "email" to email.ifBlank { null },
                "message" to message,
                "platform" to "android",
                "appVersion" to getAppVersion(),
                "locale" to Locale.getDefault().toLanguageTag(),
                "status" to "new",
                "userId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val currentScreenshot = screenshotUri

            // Screenshot kapalıysa veya seçili değilse direkt Firestore
            if (!SCREENSHOT_ENABLED || currentScreenshot == null) {
                docRef.set(baseData)
                    .addOnSuccessListener {
                        setSendingState(false)
                        resetForm()
                        showSuccessUI()
                    }
                    .addOnFailureListener {
                        setSendingState(false)
                        Snackbar.make(
                            root,
                            getString(R.string.feedback_error_generic),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                return
            }

            // Screenshot açık + seçili → önce Storage, sonra Firestore
            val storageRef = Firebase.storage.reference
                .child("feedback_screenshots/$uid/${docRef.id}.jpg")

            storageRef.putFile(currentScreenshot)
                .continueWithTask { task ->
                    if (!task.isSuccessful) {
                        throw task.exception ?: Exception("Upload failed")
                    }
                    storageRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    val dataWithScreenshot = baseData.toMutableMap()
                    dataWithScreenshot["screenshotUrl"] = downloadUri.toString()

                    docRef.set(dataWithScreenshot)
                        .addOnSuccessListener {
                            setSendingState(false)
                            resetForm()
                            showSuccessUI()
                        }
                        .addOnFailureListener {
                            setSendingState(false)
                            Snackbar.make(
                                root,
                                getString(R.string.feedback_error_generic),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener {
                    setSendingState(false)
                    Snackbar.make(
                        root,
                        getString(R.string.feedback_error_generic),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

    /** Form alanlarını ve screenshot durumunu sıfırla
     *  + mesaj kutusundaki floating label bug’ını düzelt */
    private fun resetForm() = with(binding) {
        autoCategory.setText("")
        etEmail.setText("")

        etMessage.setText("")
        etMessage.clearFocus()
        inputLayoutMessage.error = null
        inputLayoutMessage.hint = getString(R.string.feedback_hint_message)

        screenshotUri = null
        tvScreenshotInfo.text = getString(R.string.feedback_screenshot_add)
        ivScreenshotClear.isVisible = false
    }

    private fun setSendingState(sending: Boolean) = with(binding) {
        isSending = sending
        btnSend.isEnabled = !sending
        btnSend.text = if (sending) {
            getString(R.string.feedback_sending)
        } else {
            originalButtonText
        }
        progressBarSending.isVisible = sending
    }

    /** Teşekkür mesajını mesaj kutusunun içine yaz + Lottie oynat */
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
            val pInfo = requireContext()
                .packageManager
                .getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}