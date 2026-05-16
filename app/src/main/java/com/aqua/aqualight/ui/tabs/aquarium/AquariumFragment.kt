package com.aqua.aqualight.ui.tabs.aquarium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.databinding.FragmentAquariumBinding
import com.aqua.aqualight.R

class AquariumFragment : Fragment(R.layout.fragment_aquarium) {

    private var _binding: FragmentAquariumBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAquariumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔹 Add butonu tıklandığında CreateTankFragment aç
        binding.btnAdd.setOnClickListener {
    findNavController().navigate(R.id.action_aquariumFragment_to_createTankFragment)
      }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}