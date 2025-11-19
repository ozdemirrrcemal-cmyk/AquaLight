package com.aqua.aqualight.ui.tabs.settings.feedback

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.view.isGone
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
    }

    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener { findNavController().popBackStack() }

        tvSubInfo.text = getString(R.string.feedback_subinfo_text)
        tvFooter.text = getString(R.string.feedback_footer_text)
    }

    private fun setupCategoryDropdown() = with(binding) {
        val categories = resources.getStringArray(R.array.feedback_categories).toList()

        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            categories
        )
        autoCategory.setAdapter(adapter)

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

    private fun sendFeedback() {
        val user = auth.currentUser
        if (user == null) {
            // Kullanıcı login değilse – güvenlik gereği göndermiyoruz
            Snackbar.make(
                binding.root,
                getString(R.string.feedback_error_not_logged_in),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        // Eski hataları temizle
        binding.inputLayoutCategory.error = null
        binding.inputLayoutEmail.error = null
        binding.inputLayoutMessage.error = null
        binding.tvSuccessMessage.isGone = true
        binding.lottieSuccess.isGone = true

        val category = binding.autoCategory.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val message = binding.etMessage.text?.toString()?.trim().orEmpty()

        var hasError = false

        if (category.isEmpty()) {
            binding.inputLayoutCategory.error =
                getString(R.string.feedback_error_category_required)
            hasError = true
        }

        if (message.length < 5) {
            binding.inputLayoutMessage.error =
                getString(R.string.feedback_error_message_min)
            hasError = true
        } else if (message.length > 500) {
            binding.inputLayoutMessage.error =
                getString(R.string.feedback_error_message_max)
            hasError = true
        }

        if (email.isNotEmpty() &&
            !Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            binding.inputLayoutEmail.error =
                getString(R.string.feedback_error_email_invalid)
            hasError = true
        }

        if (hasError) return

        setSendingState(true)

        val uid = user.uid
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
                showSuccessUI()

                // Formu temizle (e-posta opsiyonel olduğu için onu istersen bırakabilirsin)
                binding.autoCategory.setText("")
                binding.etMessage.setText("")
            }
            .addOnFailureListener { e ->
                setSendingState(false)
                Snackbar.make(
                    binding.root,
                    getString(R.string.feedback_error_generic),
                    Snackbar.LENGTH_LONG
                ).show()
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
    }

    private fun showSuccessUI() = with(binding) {
        tvSuccessMessage.text = getString(R.string.feedback_success_text)
        tvSuccessMessage.isVisible = true

        lottieSuccess.isVisible = true
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