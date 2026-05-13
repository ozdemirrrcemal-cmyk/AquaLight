package com.aqua.aqualight.ui.auth.security

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentReAuthenticateBinding

class ReAuthenticateFragment :
    Fragment(R.layout.fragment_re_authenticate) {

    private var _binding: FragmentReAuthenticateBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentReAuthenticateBinding.bind(view)

        binding.btnContinue.setOnClickListener {

            val password =
                binding.etPassword.text.toString().trim()

            if (password.isBlank()) {
                binding.etPassword.error = "Password required"
                return@setOnClickListener
            }

            // ŞİMDİLİK TEST
        }
    }

    override fun onDestroyView() {

        _binding = null

        super.onDestroyView()
    }
}