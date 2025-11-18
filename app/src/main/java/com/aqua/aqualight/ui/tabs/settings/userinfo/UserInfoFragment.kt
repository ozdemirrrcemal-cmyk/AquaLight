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
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
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

    /**
     *  🔹 DataStore'dan kullanıcı bilgilerini oku ve UI'a bas
     */
    private fun observeUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.userPrefsFlow.collectLatest { prefs ->
                // Full name
                val fullName =
                    prefs.fullName.ifBlank { getString(R.string.user_info_name_default) }

                // Email
                val email =
                    prefs.email.ifBlank { getString(R.string.user_info_email_default) }

                // Username
                val username = prefs.username

                binding.tvName.text = fullName
                binding.tvUserEmail.text = email

                // Username alanı: prefs doluysa text'e set et, boşsa editText'i boş bırak (hint görünsün)
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

    /**
     *  🔹 Click listener'lar
     */
    private fun setupClickListeners() = with(binding) {
        // 🔙 Geri
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 📍 Adres kartı – yeni sayfaya git
        rowAddress.setOnClickListener {
            findNavController().navigate(R.id.userAddressFragment)
        }

        // 💾 Username kaydet → DataStore'a yaz
        btnSave.setOnClickListener {
            val newUsername = etUsername.text?.toString()?.trim().orEmpty()

            if (newUsername.isEmpty()) {
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.WARNING,
                    title = getString(R.string.user_info_username_empty_title),
                    message = getString(R.string.user_info_username_empty_message)
                )
                return@setOnClickListener
            }

            // DataStore'a sadece username yazıyoruz, diğer alanlara dokunmuyoruz
            viewLifecycleOwner.lifecycleScope.launch {
                userPrefs.update { prefs ->
                    prefs.toBuilder()
                        .setUsername(newUsername)
                        .build()
                }

                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.SUCCESS,
                    title = getString(R.string.user_info_save_success_title),
                    message = getString(R.string.user_info_save_success_message),
                    autoDismissMillis = 1000L
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}