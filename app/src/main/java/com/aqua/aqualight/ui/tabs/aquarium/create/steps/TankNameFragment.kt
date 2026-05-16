package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankNameBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel

class TankNameFragment : Fragment(R.layout.fragment_tank_name), TankStepFragment {

    private var _binding: FragmentTankNameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankNameBinding.bind(view)
    }

    override fun validateAndSave(): Boolean {
        viewModel.updateTankName("")
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}