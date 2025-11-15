package com.aqua.aqualight.ui.tabs.settings.network

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentNetworkBinding

class NetworkFragment : Fragment(R.layout.fragment_network) {

    private var _binding: FragmentNetworkBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNetworkBinding.bind(view)

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Buraya ileride:
        // - bağlantı bilgilerini (tvStatusValue, tvTypeValue, tvIpValue)
        // - switchWifiOnly, switchDataSaver logic’lerini
        // ekleyebilirsin.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}