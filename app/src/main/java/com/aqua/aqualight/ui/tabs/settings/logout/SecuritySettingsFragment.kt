package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentSecuritySettingsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType

class SecuritySettingsFragment :
    Fragment(R.layout.fragment_security_settings) {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentSecuritySettingsBinding.bind(view)

        setupHeader()
        setupUnavailableSecurityFeatures()
        setupInfoClicks()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_security_settings)
            )
        )
    }

    private fun setupUnavailableSecurityFeatures() {
        binding.switch2FA.isChecked = false
        binding.switch2FA.isEnabled = false

        binding.switchLoginAlerts.isChecked = false
        binding.switchLoginAlerts.isEnabled = false
    }

    private fun setupInfoClicks() {
        binding.cardSecurity.setOnClickListener {
            showSecurityRoadmapDialog()
        }

        binding.switch2FA.setOnLongClickListener {
            showSecurityRoadmapDialog()
            true
        }

        binding.switchLoginAlerts.setOnLongClickListener {
            showSecurityRoadmapDialog()
            true
        }
    }

    private fun showSecurityRoadmapDialog() {
        DialogManager.showInfoDialog(
            requireContext(),
            DialogType.INFO,
            title = getString(
                R.string.security_feature_in_development_title
            ),
            message = getString(
                R.string.security_settings_info_message
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}
