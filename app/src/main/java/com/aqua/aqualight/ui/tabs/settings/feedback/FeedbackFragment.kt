package com.aqua.aqualight.ui.tabs.settings.feedback

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentFeedbackBinding

class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // İleride:
        // binding.inputLayoutCategory
        // binding.autoCategory
        // binding.inputLayoutEmail
        // binding.inputLayoutMessage
        // binding.btnSend
        // binding.tvSuccessMessage
        // gibi her şeyi buradan kullanırsın.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}