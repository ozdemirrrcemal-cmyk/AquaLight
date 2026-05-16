package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDescriptionBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel

class TankDescriptionFragment : Fragment(R.layout.fragment_tank_description), TankStepFragment {

    private var _binding: FragmentTankDescriptionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankDescriptionBinding.bind(view)
    }

    override fun validateAndSave(): Boolean {
        viewModel.updateTankDescription("")
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}