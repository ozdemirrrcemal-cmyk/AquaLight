package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankNameBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel

class TankNameFragment :
    Fragment(R.layout.fragment_tank_name),
    TankStepFragment {

    private var _binding: FragmentTankNameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentTankNameBinding.bind(view)

        setupExistingValue()
        setupInputListener()
    }

    private fun setupExistingValue() {
        val currentName =
            viewModel.tankDraft.name

        if (currentName.isNotBlank()) {
            binding.etTankName.setText(
                currentName
            )

            binding.etTankName.setSelection(
                currentName.length
            )
        }
    }

    private fun setupInputListener() {
        binding.etTankName.doAfterTextChanged {
            binding.tilTankName.error =
                null
        }
    }

    override fun validateAndSave(): Boolean {
        val tankName =
            binding.etTankName.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (tankName.isBlank()) {
            binding.tilTankName.error =
                getString(R.string.aquarium_validation_tank_name_required)

            binding.etTankName.requestFocus()

            return false
        }

        if (tankName.length < 2) {
            binding.tilTankName.error =
                getString(R.string.aquarium_validation_tank_name_min)

            binding.etTankName.requestFocus()

            return false
        }

        binding.tilTankName.error =
            null

        viewModel.updateTankName(
            tankName
        )

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}
