package com.aqua.aqualight.ui.auth.security

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentReAuthenticateGoogleBinding

class ReAuthenticateGoogleFragment :
    Fragment(R.layout.fragment_re_authenticate_google) {

    private var _binding: FragmentReAuthenticateGoogleBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        _binding =
            FragmentReAuthenticateGoogleBinding.bind(view)

        binding.btnContinueGoogle.setOnClickListener {

            // ŞİMDİLİK TEST
        }
    }

    override fun onDestroyView() {

        _binding = null

        super.onDestroyView()
    }
}