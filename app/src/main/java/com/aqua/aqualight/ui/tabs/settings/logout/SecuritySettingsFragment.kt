package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSecuritySettingsBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SecuritySettingsFragment :
    Fragment(R.layout.fragment_security_settings) {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    // ---------------------------------------------------
    // ON VIEW CREATED
    // ---------------------------------------------------

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        _binding =
            FragmentSecuritySettingsBinding.bind(view)

        setupNavigation()

        bindSwitchesToDataStore()

        setupInfoClicks()
    }

    // ---------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------

    private fun setupNavigation() {

        binding.btnBack.setOnClickListener {

            findNavController().popBackStack()
        }
    }

    // ---------------------------------------------------
    // SWITCHES
    // ---------------------------------------------------

    private fun bindSwitchesToDataStore() {

        var isInitializing = true

        viewLifecycleOwner.lifecycleScope.launch {

            val prefs =
                userPrefs.userPrefsFlow.first()

            // Mevcut değerleri yükle
            binding.switch2FA.isChecked =
                prefs.twoFactorEnabled

            binding.switchLoginAlerts.isChecked =
                prefs.loginAlertsEnabled

            isInitializing = false

            // ---------------------------------------------------
            // 2FA SWITCH
            // ---------------------------------------------------

            binding.switch2FA
                .setOnCheckedChangeListener { _, isChecked ->

                    if (isInitializing) {
                        return@setOnCheckedChangeListener
                    }

                    viewLifecycleOwner.lifecycleScope.launch {

                        userPrefs.updateTwoFactorEnabled(
                            isChecked
                        )

                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.INFO,
                            title = getString(
                                R.string.security_feature_in_development_title
                            ),
                            message = getString(
                                R.string.security_2fa_placeholder_message
                            )
                        )
                    }
                }

            // ---------------------------------------------------
            // LOGIN ALERTS SWITCH
            // ---------------------------------------------------

            binding.switchLoginAlerts
                .setOnCheckedChangeListener { _, isChecked ->

                    if (isInitializing) {
                        return@setOnCheckedChangeListener
                    }

                    viewLifecycleOwner.lifecycleScope.launch {

                        userPrefs.updateLoginAlertsEnabled(
                            isChecked
                        )

                        DialogManager.showInfoDialog(
                            requireContext(),
                            DialogType.INFO,
                            title = getString(
                                R.string.security_login_alerts_title
                            ),
                            message =
                                if (isChecked) {

                                    getString(
                                        R.string.security_login_alerts_enabled
                                    )

                                } else {

                                    getString(
                                        R.string.security_login_alerts_disabled
                                    )
                                }
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------
    // INFO CLICKS
    // ---------------------------------------------------

    private fun setupInfoClicks() {

        // Kartın tamamına tıklanınca açıklama göster
        binding.cardSecurity.setOnClickListener {

            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.INFO,
                title = getString(
                    R.string.security_settings_title
                ),
                message = getString(
                    R.string.security_settings_info_message
                )
            )
        }

        // 2FA switch uzun basma
        binding.switch2FA.setOnLongClickListener {

            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.INFO,
                title = getString(
                    R.string.security_2fa_info_title
                ),
                message = getString(
                    R.string.security_2fa_info_message
                )
            )

            true
        }

        // Login alerts uzun basma
        binding.switchLoginAlerts.setOnLongClickListener {

            DialogManager.showInfoDialog(
                requireContext(),
                DialogType.INFO,
                title = getString(
                    R.string.security_login_alerts_info_title
                ),
                message = getString(
                    R.string.security_login_alerts_info_message
                )
            )

            true
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