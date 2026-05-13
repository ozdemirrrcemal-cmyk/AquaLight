package com.aqua.aqualight.ui.tabs.settings.logout

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSecuritySettingsBinding
import kotlinx.coroutines.launch

class SecuritySettingsFragment : Fragment(R.layout.fragment_security_settings) {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    private var isInitializing = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSecuritySettingsBinding.bind(view)

        setupBackButton()
        observePreferences()
        setupListeners()
    }

    // ---------------------------------------------------
    // BACK BUTTON
    // ---------------------------------------------------

    private fun setupBackButton() {

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    // ---------------------------------------------------
    // OBSERVE DATASTORE
    // ---------------------------------------------------

    private fun observePreferences() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                userPrefs.userPrefsFlow.collect { prefs ->

                    isInitializing = true

                    binding.switch2FA.isChecked =
                        prefs.twoFactorEnabled

                    binding.switchLoginAlerts.isChecked =
                        prefs.loginAlertsEnabled

                    isInitializing = false
                }
            }
        }
    }

    // ---------------------------------------------------
    // SWITCH LISTENERS
    // ---------------------------------------------------

    private fun setupListeners() {

        binding.switch2FA.setOnCheckedChangeListener { _, isChecked ->

            if (isInitializing) return@setOnCheckedChangeListener

            performHapticFeedback(binding.switch2FA)

            animateSwitch(binding.switch2FA)

            showSavedIndicator()

            updateTwoFactor(isChecked)
        }

        binding.switchLoginAlerts.setOnCheckedChangeListener { _, isChecked ->

            if (isInitializing) return@setOnCheckedChangeListener

            performHapticFeedback(binding.switchLoginAlerts)

            animateSwitch(binding.switchLoginAlerts)

            showSavedIndicator()

            updateLoginAlerts(isChecked)
        }
    }

    // ---------------------------------------------------
    // UPDATE PREFS
    // ---------------------------------------------------

    private fun updateTwoFactor(enabled: Boolean) {

        viewLifecycleOwner.lifecycleScope.launch {

            userPrefs.updateTwoFactorEnabled(enabled)
        }
    }

    private fun updateLoginAlerts(enabled: Boolean) {

        viewLifecycleOwner.lifecycleScope.launch {

            userPrefs.updateLoginAlertsEnabled(enabled)
        }
    }

    // ---------------------------------------------------
    // HAPTIC FEEDBACK
    // ---------------------------------------------------

    private fun performHapticFeedback(view: View) {

        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    // ---------------------------------------------------
    // SWITCH ANIMATION
    // ---------------------------------------------------

    private fun animateSwitch(view: View) {

        ObjectAnimator.ofFloat(
            view,
            "scaleX",
            1f,
            1.08f,
            1f
        ).apply {
            duration = 180
            start()
        }

        ObjectAnimator.ofFloat(
            view,
            "scaleY",
            1f,
            1.08f,
            1f
        ).apply {
            duration = 180
            start()
        }
    }

    // ---------------------------------------------------
    // SAVED INDICATOR
    // ---------------------------------------------------

    private fun showSavedIndicator() {

        if (_binding == null) return

        binding.tvSaved.animate().cancel()

        binding.tvSaved.alpha = 0f
        binding.tvSaved.visibility = View.VISIBLE

        binding.tvSaved.animate()
            .alpha(1f)
            .setDuration(120)
            .withEndAction {

                if (_binding == null) return@withEndAction

                binding.tvSaved.animate()
                    .alpha(0f)
                    .setStartDelay(700)
                    .setDuration(250)
                    .withEndAction {

                        if (_binding == null) return@withEndAction

                        binding.tvSaved.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

    // ---------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------

    override fun onDestroyView() {

        binding.switch2FA.setOnCheckedChangeListener(null)

        binding.switchLoginAlerts.setOnCheckedChangeListener(null)

        binding.tvSaved.animate().cancel()

        _binding = null

        super.onDestroyView()
    }
}