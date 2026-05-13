package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentChangePasswordBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {

private var _binding: FragmentChangePasswordBinding? = null  
private val binding get() = _binding!!  

private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }  

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {  
    super.onViewCreated(view, savedInstanceState)  
    _binding = FragmentChangePasswordBinding.bind(view)  

    // 🔙 Geri  
    binding.btnBack.setOnClickListener {  
        findNavController().popBackStack()  
    }  

    // 🔐 Eğer kullanıcı mail/şifre provider'ı yoksa formu gizle  
    if (!hasPasswordProvider()) {  
        showGoogleOnlyInfo()  
        return  
    }  

    // 💾 Kaydet  
    binding.btnSavePassword.setOnClickListener {  
        changePassword()  
    }  
}  

/**  
 * Kullanıcının provider listesinde "password" var mı?  
 * (Email/şifre hesabı demek)  
 */  
private fun hasPasswordProvider(): Boolean {  
    val user = auth.currentUser ?: return false  
    return user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }  
}  

/**  
 * Google-only kullanıcılar için UI'yı ayarla:  
 * - form ve butonu gizle  
 * - ortada bilgilendirici bir mesaj göster  
 */  
private fun showGoogleOnlyInfo() {  
    // formu ve butonu gizle  
    binding.formContainer.visibility = View.GONE  
    binding.btnSavePassword.visibility = View.GONE  

    // mesajı göster  
    binding.tvPasswordMessage.apply {  
        text = getString(R.string.change_password_google_only_info)  
        setTextColor(resources.getColor(R.color.settings_text_secondary, null))  
        visibility = View.VISIBLE  
        alpha = 1f  
    }  
}  

private fun changePassword() {  
    // Güvenlik: ekstra check (normalde hasPasswordProvider false ise buraya gelmez)  
    if (!hasPasswordProvider()) {  
        showGoogleOnlyInfo()  
        return  
    }  

    // Eski hata mesajlarını temizle  
    binding.inputLayoutCurrentPassword.error = null  
    binding.inputLayoutNewPassword.error = null  
    binding.inputLayoutConfirmPassword.error = null  
    binding.tvPasswordMessage.visibility = View.GONE  

    val currentPassword = binding.etCurrentPassword.text?.toString()?.trim().orEmpty()  
    val newPassword = binding.etNewPassword.text?.toString()?.trim().orEmpty()  
    val confirmPassword = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()  

    var hasError = false  

    if (currentPassword.isEmpty()) {  
        binding.inputLayoutCurrentPassword.error =  
            getString(R.string.change_password_error_current_empty)  
        hasError = true  
    }  

    if (newPassword.length < 6) {  
        binding.inputLayoutNewPassword.error =  
            getString(R.string.change_password_error_new_short)  
        hasError = true  
    }  

    if (newPassword != confirmPassword) {  
        binding.inputLayoutConfirmPassword.error =  
            getString(R.string.change_password_error_not_match)  
        hasError = true  
    }  

    if (hasError) return  

    val user = auth.currentUser  
    if (user == null) {  
        Snackbar.make(  
            binding.root,  
            R.string.change_password_error_not_logged_in,  
            Snackbar.LENGTH_LONG  
        ).show()  
        return  
    }  

    val email = user.email  
    if (email.isNullOrEmpty()) {  
        Snackbar.make(  
            binding.root,  
            R.string.change_password_error_no_email,  
            Snackbar.LENGTH_LONG  
        ).show()  
        return  
    }  

    // UI’yi kilitle  
    setLoading(true)  

    // 1️⃣ Re-authenticate  
    val credential = EmailAuthProvider.getCredential(email, currentPassword)  
    user.reauthenticate(credential).addOnCompleteListener { reauthTask ->  
        if (!reauthTask.isSuccessful) {  
            setLoading(false)  
            binding.inputLayoutCurrentPassword.error =  
                getString(R.string.change_password_error_current_wrong)  
            return@addOnCompleteListener  
        }  

        // 2️⃣ Yeni şifreyi güncelle  
        user.updatePassword(newPassword).addOnCompleteListener { updateTask ->  
            setLoading(false)  
            if (updateTask.isSuccessful) {  
                binding.tvPasswordMessage.apply {  
                    text = getString(R.string.change_password_success)  
                    setTextColor(resources.getColor(R.color.settings_text_secondary, null))  
                    visibility = View.VISIBLE  
                    alpha = 1f  
                }  

                // Alanları temizle  
                binding.etCurrentPassword.text?.clear()  
                binding.etNewPassword.text?.clear()  
                binding.etConfirmPassword.text?.clear()  
            } else {  
                Snackbar.make(  
                    binding.root,  
                    R.string.change_password_error_generic,  
                    Snackbar.LENGTH_LONG  
                ).show()  
            }  
        }  
    }  
}  

private fun setLoading(isLoading: Boolean) {  
    binding.btnSavePassword.isEnabled = !isLoading  
    binding.formContainer.isEnabled = !isLoading  
    binding.btnSavePassword.alpha = if (isLoading) 0.6f else 1f  
}  

override fun onDestroyView() {  
    super.onDestroyView()  
    _binding = null  
}

}
