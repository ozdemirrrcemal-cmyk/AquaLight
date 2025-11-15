package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentLogoutBinding

class LogoutFragment : Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

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
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🟦 Kart içindeki satırlar
        binding.rowChangePassword.setOnClickListener {
            findNavController().navigate(
                R.id.action_logoutFragment_to_changePasswordFragment
            )
        }

        binding.rowChangeEmail.setOnClickListener {
            findNavController().navigate(
                R.id.action_logoutFragment_to_changeEmailFragment
            )
        }

        binding.rowSecuritySettings.setOnClickListener {
            findNavController().navigate(
                R.id.action_logoutFragment_to_securitySettingsFragment
            )
        }

        // 🚪 Çıkış yap
        binding.btnLogout.setOnClickListener {
            // TODO: logout dialog + logic
        }

        // 🗑 Hesabı sil
        binding.btnDeleteAccount.setOnClickListener {
            // TODO: delete account flow
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}