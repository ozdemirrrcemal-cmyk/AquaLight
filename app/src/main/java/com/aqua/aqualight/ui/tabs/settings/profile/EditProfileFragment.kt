package com.aqua.aqualight.ui.tabs.settings.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentEditProfileBinding

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditProfileBinding.bind(view)

        // Şimdilik hiçbir şey yapmıyoruz, sadece boş ekran
        // İleride burada form, kaydet butonu vs. ekleriz.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}