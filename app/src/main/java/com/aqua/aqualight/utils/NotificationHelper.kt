package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSecuritySettingsBinding
import com.aqua.aqualight.utils.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SecuritySettingsFragment : Fragment(R.layout.fragment_security_settings) {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var userPrefs: UserPreferencesManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSecuritySettingsBinding.bind(view)

        userPrefs = UserPreferencesManager.create(requireContext())

        setupHeader()
        observeLoginAlertsSwitch()
        setupSwitchListeners()
    }

    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    /** DataStore’daki loginAlertsEnabled değerini switch’e yansıt */
    private fun observeLoginAlertsSwitch() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.loginAlertsEnabled.collect { enabled ->
                    // setOnCheckedChangeListener içinde loop olmaması için
                    if (binding.switchLoginAlerts.isChecked != enabled) {
                        binding.switchLoginAlerts.isChecked = enabled
                    }
                }
            }
        }
    }

    private fun setupSwitchListeners() = with(binding) {

        // 🔔 Giriş uyarıları
        switchLoginAlerts.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val ctx = requireContext()

                val hasPermission = NotificationHelper.hasSystemPermission(ctx)
                val systemEnabled = NotificationHelper.areSystemNotificationsEnabled(ctx)

                if (!hasPermission || !systemEnabled) {
                    // Switch’i geri kapat
                    switchLoginAlerts.isChecked = false

                    // Kullanıcıya diyalog göster
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle(R.string.security_login_alerts_dialog_title)
                        .setMessage(R.string.security_login_alerts_dialog_message)
                        .setPositiveButton(R.string.security_login_alerts_dialog_open_settings) { _, _ ->
                            NotificationHelper.openNotificationSettings(ctx)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    // Her şey yolunda → DataStore’a kaydet
                    viewLifecycleOwner.lifecycleScope.launch {
                        userPrefs.updateLoginAlertsEnabled(true)
                    }
                }
            } else {
                // Kullanıcı kapattı
                viewLifecycleOwner.lifecycleScope.launch {
                    userPrefs.updateLoginAlertsEnabled(false)
                }
            }
        }

        // 🧩 2FA switch (şimdilik sadece "yakında" mesajı gösterebilir)
        switch2FA.setOnCheckedChangeListener { _, isChecked ->
            // Şimdilik sadece UI; backend yoksa isChecked durumunu kalıcı yapma istersen
            if (isChecked) {
                // İstersen buraya "Yakında" Snackbar vs. koyarsın
                // switch2FA.isChecked = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}