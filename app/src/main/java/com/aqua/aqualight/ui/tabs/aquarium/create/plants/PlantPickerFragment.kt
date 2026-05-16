package com.aqua.aqualight.ui.tabs.aquarium.create.plants

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPlantPickerBinding

class PlantPickerFragment : Fragment(R.layout.fragment_plant_picker) {

    private var _binding: FragmentPlantPickerBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlantPickerBinding.bind(view)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.cardPlantOne.setOnClickListener {
            selectPlant(
                plantName = "Bucephalandra sp. \"Catarina\"",
                category = "Epiphytes"
            )
        }

        binding.cardPlantTwo.setOnClickListener {
            selectPlant(
                plantName = "Eriocaulon cinereum",
                category = "Foreground"
            )
        }
    }

    private fun selectPlant(
        plantName: String,
        category: String
    ) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                RESULT_PLANT_NAME to plantName,
                RESULT_PLANT_CATEGORY to category
            )
        )

        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "plant_picker_result"
        const val RESULT_PLANT_NAME = "plant_name"
        const val RESULT_PLANT_CATEGORY = "plant_category"
    }
}