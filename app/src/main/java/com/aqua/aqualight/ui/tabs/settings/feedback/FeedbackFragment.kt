package com.aqua.aqualight.ui.tabs.settings.feedback

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentFeedbackBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    // Butonun orijinal metnini saklayalım (ör: "Send feedback")
    private var sendButtonOriginalText: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)

        sendButtonOriginalText = getString(R.string.feedback_send)

        setupHeader()
        setupCategoryDropdown()
        setupTextChangeListeners()
        setupListeners()
    }

    /** 🔹 Üst bar: back */
    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    /** 🔹 Kategori dropdown (AutoCompleteTextView) */
    private fun setupCategoryDropdown() = with(binding) {
        val categories = resources.getStringArray(R.array.feedback_categories)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1, // istersen custom layout yapabiliriz
            categories
        )
        autoCategory.setAdapter(adapter)

        // Tıklanınca direkt dropdown açılsın
        autoCategory.setOnClickListener {
            autoCategory.showDropDown()
        }
    }

    /** 🔹 Kullanıcı yazarken hata mesajlarını anında temizle */
    private fun setupTextChangeListeners() = with(binding) {
        autoCategory.doOnTextChanged { _, _, _, _ ->
            inputLayoutCategory.error = null
        }
        etEmail.doOnTextChanged { _, _, _, _ ->
            inputLayoutEmail.error = null
        }
        etMessage.doOnTextChanged { _, _, _, _ ->
            inputLayoutMessage.error = null
        }
    }

    /** 🔹 Click listener’lar */
    private fun setupListeners() = with(binding) {
        btnSend.setOnClickListener {
            hideKeyboard()
            if (!validateForm()) return@setOnClickListener
            sendFeedback()
        }
    }

    /** 🔹 Form validasyonu (kategori zorunlu, mesaj zorunlu, email opsiyonel ama formatlı) */
    private fun validateForm(): Boolean = with(binding) {
        var hasError = false

        inputLayoutCategory.error = null
        inputLayoutEmail.error = null
        inputLayoutMessage.error = null

        val category = autoCategory.text?.toString()?.trim().orEmpty()
        val email    = etEmail.text?.toString()?.trim().orEmpty()
        val message  = etMessage.text?.toString()?.trim().orEmpty()

        if (category.isEmpty()) {
            inputLayoutCategory.error =
                getString(R.string.feedback_error_category_required)
            hasError = true
        }

        if (email.isNotEmpty() &&
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            inputLayoutEmail.error =
                getString(R.string.feedback_error_email_invalid)
            hasError = true
        }

        if (message.length < 10) {
            inputLayoutMessage.error =
                getString(R.string.feedback_error_message_too_short)
            hasError = true
        }

        !hasError
    }

    /** 🔹 Feedback gönderme akışı (şimdilik sahte delay + success animasyonu) */
    private fun sendFeedback() = with(binding) {
        val category = autoCategory.text?.toString()?.trim().orEmpty()
        val email    = etEmail.text?.toString()?.trim().orEmpty()
        val message  = etMessage.text?.toString()?.trim().orEmpty()

        // Butonu kilitle, "Sending..." göster
        btnSend.isEnabled = false
        btnSend.text = getString(R.string.feedback_sending) // strings.xml'e ekle

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // TODO: Buraya gerçek gönderim (API / Firebase vb.) gelecek
                // Örn: feedbackRepository.sendFeedback(category, email, message)
                delay(1200) // sadece görsel amaçlı

                showSuccessMessage()

                // Mesaj alanını temizle (email'i istersen tut)
                etMessage.setText("")
            } catch (e: Exception) {
                Snackbar.make(
                    root,
                    R.string.feedback_error_generic,
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                // Butonu eski haline getir
                btnSend.isEnabled = true
                btnSend.text = sendButtonOriginalText
            }
        }
    }

    /** 🔹 Başarılı gönderim UI’si (fade-in success text + scroll) */
    private fun showSuccessMessage() = with(binding) {
        tvSuccessMessage.apply {
            text = getString(R.string.feedback_success_text)
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(250)
                .start()
        }

        // Success mesajı kartın altında ise oraya doğru hafifçe scroll et
        scrollContent.post {
            scrollContent.smoothScrollTo(0, tvSuccessMessage.bottom)
        }
    }

    /** 🔹 Klavyeyi kapatmak için küçük yardımcı */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = requireActivity().currentFocus ?: View(requireContext())
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}