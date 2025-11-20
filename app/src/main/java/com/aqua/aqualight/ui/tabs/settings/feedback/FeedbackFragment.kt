package com.aqua.aqualight.ui.tabs.settings.feedback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentFeedbackBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var originalButtonText: CharSequence? = null
    private var isSending = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)

        originalButtonText = binding.btnSend.text

        setupHeader()
        setupCategoryDropdown()
        setupSendButton()
        setupLottie()
    }

    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener { findNavController().popBackStack() }
        tvSubInfo.text = getString(R.string.feedback_subinfo)
        tvFooter.text = getString(R.string.feedback_footer)
    }

    private fun setupCategoryDropdown() = with(binding) {
    val categories = resources.getStringArray(R.array.feedback_categories).toList()

    // Kendi satır layout’umuzu kullan
    val adapter = android.widget.ArrayAdapter(
        requireContext(),
        R.layout.item_feedback_category,
        categories
    )
    autoCategory.setAdapter(adapter)

    // Popup arkaplanını temaya uygun yap
    autoCategory.setDropDownBackgroundDrawable(
        androidx.core.content.ContextCompat.getDrawable(
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

    /** Lottie bitince mesaj alanını normale döndür */
    private fun setupLottie() = with(binding) {
        lottieSuccess.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // animasyon bittikten sonra mesaj kutusunu tekrar yazılabilir yap
                etMessage.isEnabled = true
                etMessage.setText("")
                inputLayoutMessage.hint = getString(R.string.feedback_hint_message)
                lottieSuccess.isVisible = false
            }
        })
    }

    /** ARTIK BLOK GÖVDELİ, HİÇBİR ŞEY DÖNDÜRMÜYOR (Unit) */
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

            // Kategori zorunlu
            if (category.isEmpty()) {
                inputLayoutCategory.error =
                    getString(R.string.feedback_error_category_required)
                hasError = true
            }

            // Mesaj min 10 karakter
            if (message.length < 10) {
                inputLayoutMessage.error =
                    getString(R.string.feedback_error_message_too_short)
                hasError = true
            }

            // Email opsiyonel, ama doluysa valid olmalı
            if (email.isNotEmpty() &&
                !Patterns.EMAIL_ADDRESS.matcher(email).matches()
            ) {
                inputLayoutEmail.error =
                    getString(R.string.feedback_error_email_invalid)
                hasError = true
            }

            if (hasError) return

            setSendingState(true)

            // Kullanıcı id'si – login değilse anonim
            val user = auth.currentUser
            val uid = user?.uid ?: "anonymous"

            // feedback/{uid}/items/{autoId}
            val docRef = db.collection("feedback")
                .document(uid)
                .collection("items")
                .document()  // auto ID

            val data = hashMapOf(
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

            docRef.set(data)
                .addOnSuccessListener {
                    setSendingState(false)

                    // Kategori + mesaj + email TEMİZLENSİN
                    autoCategory.setText("")
                    etEmail.setText("")
                    etMessage.setText("")

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
    }

    private fun setSendingState(sending: Boolean) = with(binding) {
        isSending = sending
        btnSend.isEnabled = !sending
        btnSend.text = if (sending) {
            getString(R.string.feedback_sending) // "Sending..."
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