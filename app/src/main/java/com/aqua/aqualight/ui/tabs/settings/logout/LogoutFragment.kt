package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLogoutBinding
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LogoutFragment : Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

    private val auth get() = FirebaseAuth.getInstance()
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }
    private val baseActivity get() = activity as? BaseActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔙 Geri
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // 🟦 Ayar satırları
        binding.rowChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_logoutFragment_to_changePasswordFragment)
        }

        binding.rowChangeEmail.setOnClickListener {
            findNavController().navigate(R.id.action_logoutFragment_to_changeEmailFragment)
        }

        binding.rowSecuritySettings.setOnClickListener {
            findNavController().navigate(R.id.action_logoutFragment_to_securitySettingsFragment)
        }

        // 🚪 Çıkış Yap
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        // 🗑 Hesabı Sil
        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    /** -----------------------------
     *  🔹 LOGOUT
     *  Sadece oturum kapatır
     *  Tema/dil ayarları silinmez
     *  ----------------------------- */
    private fun showLogoutDialog() {
        DialogManager.showConfirmDialog(
            requireContext(),
            DialogType.WARNING,
            title = getString(R.string.logout_title),
            message = getString(R.string.logout_message),
            onConfirm = { performLogout() }
        )
    }

    private fun performLogout() {
        auth.signOut()

        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.logout() // ⬅ sadece oturum verisini siler

            navigateToLogin()
        }
    }

    /** -----------------------------
     *  🔥 HESABI TAMAMEN SİL
     *  Firebase hesabı silinir
     *  Tüm DataStore sıfırlanır
     *  ----------------------------- */
    private fun showDeleteAccountDialog() {
        DialogManager.showConfirmDialog(
            requireContext(),
            DialogType.ERROR,
            title = getString(R.string.delete_account_title),
            message = getString(R.string.delete_account_message),
            onConfirm = { performDeleteAccount() }
        )
    }

    private fun performDeleteAccount() {
        val user = auth.currentUser ?: return

        baseActivity?.showLoading(true)

        user.delete().addOnCompleteListener { task ->
            baseActivity?.showLoading(false)

            if (task.isSuccessful) {
                viewLifecycleOwner.lifecycleScope.launch {
                    userPrefs.clearAllUserData() // ⬅ TÜM pref sıfırlanır
                    navigateToLogin()
                }
            } else {
                DialogManager.showInfoDialog(
                    requireContext(),
                    DialogType.ERROR,
                    title = getString(R.string.account_delete_failed),
                    message = task.exception?.localizedMessage ?: ""
                )
            }
        }
    }

    /** -----------------------------
     *  🔄 Giriş ekranına yönlendirme
     *  ----------------------------- */
    private fun navigateToLogin() {
        findNavController().navigate(
            R.id.action_logoutFragment_to_loginFragment,
            null,
            navOptions {
                popUpTo(R.id.nav_app) { inclusive = true }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}