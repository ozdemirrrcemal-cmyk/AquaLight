package com.aqua.aqualight.ui.tabs.settings

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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }
    private val auth get() = FirebaseAuth.getInstance()
    private val baseActivity get() = activity as? BaseActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        observeUserInfo()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        syncEmailFromFirebase()
    }

    /**
     *  🔄 Firebase'teki güncel email'i çek ve DataStore'a yaz
     */
    private fun syncEmailFromFirebase() {
        val user = auth.currentUser ?: return

        baseActivity?.showLoading(true)

        user.reload()
            .addOnCompleteListener { task ->
                baseActivity?.showLoading(false)

                if (task.isSuccessful) {
                    val updatedEmail = user.email ?: ""

                    // Sadece email alanını güncelle
                    viewLifecycleOwner.lifecycleScope.launch {
                        userPrefs.update { prefs ->
                            prefs.toBuilder()
                                .setEmail(updatedEmail)
                                .build()
                        }
                    }
                }
                // task başarısız olursa şimdilik sessiz geçiyoruz;
                // istersen burada dialog ile hata gösterebilirsin.
            }
    }

    // 🔹 DataStore'dan kullanıcı bilgilerini oku ve UI'ya bas
    private fun observeUserInfo() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.userPrefsFlow.collectLatest { prefs ->
                val username =
                    prefs.username.ifBlank { getString(R.string.settings_default_username) }
                val email =
                    prefs.email.ifBlank { getString(R.string.settings_default_email) }

                binding.tvUsername.text = username
                binding.tvEmail.text = email

                // Profil fotoğrafı URL'i varsa Coil ile yükle
                if (prefs.profilePhotoUrl.isNotBlank()) {
                    binding.ivProfilePhoto.load(prefs.profilePhotoUrl) {
                        placeholder(R.drawable.ic_profile_placeholder)
                        error(R.drawable.ic_profile_placeholder)
                        crossfade(true)
                    }
                } else {
                    binding.ivProfilePhoto.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
        }
    }

    // 🔹 Satır click'lerini ayarla
    private fun setupClickListeners() = with(binding) {

        ivProfilePhoto.setOnClickListener {
            findNavController().navigate(R.id.editProfileFragment)
        }

        rowUserInfo.setOnClickListener {
            findNavController().navigate(R.id.userInfoFragment)
        }

        rowDeviceStatus.setOnClickListener {
            findNavController().navigate(R.id.deviceStatusFragment)
        }

        rowNetwork.setOnClickListener {
            findNavController().navigate(R.id.networkFragment)
        }

        rowSettings.setOnClickListener {
            findNavController().navigate(R.id.appSettingsFragment)
        }

        rowUsage.setOnClickListener {
            findNavController().navigate(R.id.usageFragment)
        }

        rowPrivacy.setOnClickListener {
            findNavController().navigate(R.id.privacyFragment)
        }

        rowFeedback.setOnClickListener {
            findNavController().navigate(R.id.feedbackFragment)
        }

        rowLogout.setOnClickListener {
            findNavController().navigate(R.id.logoutFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}