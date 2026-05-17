package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankSettingsBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel

class TankSettingsFragment : Fragment(R.layout.fragment_tank_settings) {

    private var _binding: FragmentTankSettingsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankSettingsBinding.bind(view)

        setupClickListeners()
        observeTank()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { it.id == tankId }

            if (tank == null) {
                Toast.makeText(
                    requireContext(),
                    "Tank not found.",
                    Toast.LENGTH_SHORT
                ).show()

                findNavController().navigateUp()
                return@observe
            }

            binding.tvTankName.text = tank.name
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
    }
}