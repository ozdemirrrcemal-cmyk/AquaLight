package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentNotificationsSettingsBinding

class NotificationsSettingsFragment : Fragment(R.layout.fragment_notifications_settings) {

    private var _binding: FragmentNotificationsSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNotificationsSettingsBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Buraya: notification toggle’ları vs. gelecek
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}