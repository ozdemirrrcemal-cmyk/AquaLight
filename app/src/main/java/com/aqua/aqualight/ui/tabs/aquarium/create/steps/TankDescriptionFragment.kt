package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDescriptionBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel

class TankDescriptionFragment :
    Fragment(R.layout.fragment_tank_description),
    TankStepFragment {

    private var _binding: FragmentTankDescriptionBinding? = null
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
            FragmentTankDescriptionBinding.bind(view)

        setupExistingValue()
        setupInputListener()
    }

    private fun setupExistingValue() {
        val currentDescription =
            viewModel.tankDraft.description

        if (currentDescription.isNotBlank()) {
            binding.etTankDescription.setText(
                currentDescription
            )

            binding.etTankDescription.setSelection(
                currentDescription.length
            )
        }
    }

    private fun setupInputListener() {
        binding.etTankDescription.doAfterTextChanged {
            binding.tilTankDescription.error =
                null
        }
    }

    override fun validateAndSave(): Boolean {
        val description =
            binding.etTankDescription.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (description.isBlank()) {
            binding.tilTankDescription.error =
                getString(R.string.aquarium_validation_tank_description_required)

            binding.etTankDescription.requestFocus()

            return false
        }

        if (description.length < 10) {
            binding.tilTankDescription.error =
                getString(R.string.aquarium_validation_tank_description_min)

            binding.etTankDescription.requestFocus()

            return false
        }

        binding.tilTankDescription.error =
            null

        viewModel.updateTankDescription(
            description
        )

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}
