package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentSecuritySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SecuritySettingsFragment : Fragment(R.layout.fragment_security_settings) {

private var _binding: FragmentSecuritySettingsBinding? = null  
private val binding get() = _binding!!  

private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }  

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {  
    super.onViewCreated(view, savedInstanceState)  
    _binding = FragmentSecuritySettingsBinding.bind(view)  

    // 🔙 Geri  
    binding.btnBack.setOnClickListener {  
        findNavController().popBackStack()  
    }  

    // Switch'leri DataStore ile bağla  
    bindSwitchesToDataStore()  
}  

private fun bindSwitchesToDataStore() {  
    // Listener'ların initial set sırasında tetiklenmemesi için flag  
    var isInitializing = true  

    viewLifecycleOwner.lifecycleScope.launch {  
        // 1️⃣ DataStore'dan mevcut değerleri oku  
        val prefs = userPrefs.userPrefsFlow.first()  

        // 2️⃣ UI'ya yansıt  
        binding.switch2FA.isChecked = prefs.twoFactorEnabled  
        binding.switchLoginAlerts.isChecked = prefs.loginAlertsEnabled  

        isInitializing = false  

        // 3️⃣ Kullanıcı değiştirince DataStore'a yaz  
        binding.switch2FA.setOnCheckedChangeListener { _, isChecked ->  
            if (isInitializing) return@setOnCheckedChangeListener  
            viewLifecycleOwner.lifecycleScope.launch {  
                userPrefs.updateTwoFactorEnabled(isChecked)  
            }  
        }  

        binding.switchLoginAlerts.setOnCheckedChangeListener { _, isChecked ->  
            if (isInitializing) return@setOnCheckedChangeListener  
            viewLifecycleOwner.lifecycleScope.launch {  
                userPrefs.updateLoginAlertsEnabled(isChecked)  
            }  
        }  
    }  
}  

override fun onDestroyView() {  
    super.onDestroyView()  
    _binding = null  
}

}