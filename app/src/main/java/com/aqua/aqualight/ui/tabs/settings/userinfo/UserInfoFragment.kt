package com.aqua.aqualight.ui.tabs.settings.userinfo

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.placeholder
import coil3.request.error
import coil3.request.crossfade
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUserInfoBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserInfoFragment : Fragment(R.layout.fragment_user_info) {

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUserInfoBinding.bind(view)

        observeUserInfo()
        setupClickListeners()
    }

    /** 🔹 DataStore’daki bilgileri UI’a yaz */
    private fun observeUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.userPrefsFlow.collectLatest { prefs ->

                binding.tvName.text =
                    prefs.fullName.ifBlank { getString(R.string.user_info_name_default) }

                binding.tvUserEmail.text =
                    prefs.email.ifBlank { getString(R.string.user_info_email_default) }

                // Username
                val username = prefs.username
                if (username.isNotBlank()) {
                    if (binding.etUsername.text?.toString() != username) {
                        binding.etUsername.setText(username)
                    }
                } else {
                    binding.etUsername.setText("")
                }

                // Profil fotoğrafı
                if (prefs.profilePhotoUrl.isNotBlank()) {
                    binding.ivUserPhoto.load(prefs.profilePhotoUrl) {
                        placeholder(R.drawable.ic_profile_placeholder)
                        error(R.drawable.ic_profile_placeholder)
                        crossfade(true)
                    }
                } else {
                    binding.ivUserPhoto.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
        }
    }

    /** 🔹 Click Listener’lar */
    private fun setupClickListeners() = with(binding) {

        btnBack.setOnClickListener { findNavController().popBackStack() }

        rowAddress.setOnClickListener {
            findNavController().navigate(R.id.userAddressFragment)
        }

        /** 💾 Kaydet */
        btnSave.setOnClickListener {
            val newUsername = etUsername.text?.toString()?.trim().orEmpty()

            if (newUsername.isEmpty()) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.user_info_username_empty_message),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.update { prefs ->
                    prefs.toBuilder()
                        .setUsername(newUsername)
                        .build()
                }

                Snackbar.make(
                    binding.root,
                    getString(R.string.user_info_save_success_message),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}