package com.aqua.aqualight.ui.tabs.aquarium

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAquariumBinding

class AquariumFragment : Fragment(R.layout.fragment_aquarium) {

    private var _binding: FragmentAquariumBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private val tankAdapter = AquariumTankAdapter { tank ->
        /*
          Sonraki aşamada burada detay ekranına gideceğiz.

          findNavController().navigate(
              AquariumFragmentDirections.actionAquariumFragmentToTankDetailFragment(tank.id)
          )
        */
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAquariumBinding.bind(view)

        setupRecyclerView()
        setupClickListeners()
        observeTanks()
    }

    private fun setupRecyclerView() {
        binding.rvTanks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTanks.adapter = tankAdapter
        binding.rvTanks.setHasFixedSize(false)
    }

    private fun setupClickListeners() {
        binding.btnAdd.setOnClickListener {
            findNavController().navigate(
                R.id.action_aquariumFragment_to_createTankFragment
            )
        }
    }

    private fun observeTanks() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            tankAdapter.submitList(tanks)

            binding.rvTanks.isVisible = tanks.isNotEmpty()
            binding.tvEmptyState.isVisible = tanks.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvTanks.adapter = null
        _binding = null
    }
}