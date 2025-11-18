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
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest

class SettingsFragment : Fragment(R.layout.fragment_settings) {

private var _binding: FragmentSettingsBinding? = null
private val binding get() = _binding!!

private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
super.onViewCreated(view, savedInstanceState)
_binding = FragmentSettingsBinding.bind(view)

observeUserInfo()    
setupClickListeners()

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

// Profil foto tıklaması – EditProfileFragment'e git (zaten hazır)    
ivProfilePhoto.setOnClickListener {    
    findNavController().navigate(R.id.editProfileFragment)    
}    

// 1️⃣ User Info    
rowUserInfo.setOnClickListener {    
    findNavController().navigate(R.id.userInfoFragment)    
}    

// 2️⃣ Device Status    
rowDeviceStatus.setOnClickListener {    
    findNavController().navigate(R.id.deviceStatusFragment)    
}    

// 3️⃣ Network    
rowNetwork.setOnClickListener {    
    findNavController().navigate(R.id.networkFragment)    
}    

// 4️⃣ App Settings    
rowSettings.setOnClickListener {    
    findNavController().navigate(R.id.appSettingsFragment)    
}    

// 5️⃣ Usage Statistics    
rowUsage.setOnClickListener {    
    findNavController().navigate(R.id.usageFragment)    
}    

// 6️⃣ Privacy Policy    
rowPrivacy.setOnClickListener {    
    findNavController().navigate(R.id.privacyFragment)    
}    

// 7️⃣ Feedback / Support    
rowFeedback.setOnClickListener {    
    findNavController().navigate(R.id.feedbackFragment)    
}    

// 8️⃣ Logout – artık dialog yok, direkt logout fragment'ine gidiyoruz    
rowLogout.setOnClickListener {    
    findNavController().navigate(R.id.logoutFragment)    
}

}

override fun onDestroyView() {
super.onDestroyView()
_binding = null
}

}