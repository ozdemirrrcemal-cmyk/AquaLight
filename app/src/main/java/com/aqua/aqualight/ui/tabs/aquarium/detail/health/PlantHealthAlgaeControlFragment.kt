package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPlantHealthAlgaeControlBinding
import com.aqua.aqualight.databinding.ItemPlantHealthSymptomBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class PlantHealthAlgaeControlFragment :
    Fragment(R.layout.fragment_plant_health_algae_control) {

    private var _binding: FragmentPlantHealthAlgaeControlBinding? = null
    private val binding get() = _binding!!

    private var selectedAlgaeKey: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlantHealthAlgaeControlBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.plant_health_algae_control_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        renderAlgaeTypes()
    }

    private fun renderAlgaeTypes() {
        binding.algaeGridContainer.removeAllViews()
        ALGAE_TYPES.chunked(COLUMN_COUNT).forEach { rowTypes ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = COLUMN_COUNT.toFloat()
            }
            rowTypes.forEachIndexed { index, option ->
                val item = ItemPlantHealthSymptomBinding.inflate(
                    LayoutInflater.from(requireContext()),
                    row,
                    false
                )
                item.ivSymptom.setImageResource(R.drawable.ic_care_algae_24)
                item.tvSymptom.setText(option.labelRes)
                renderSelection(item, option.key == selectedAlgaeKey)
                item.root.setOnClickListener {
                    selectedAlgaeKey = if (selectedAlgaeKey == option.key) null else option.key
                    renderAlgaeTypes()
                }
                item.root.layoutParams = LinearLayout.LayoutParams(
                    0,
                    resources.getDimensionPixelSize(R.dimen.aqua_size_96),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart = resources.getDimensionPixelSize(R.dimen.aqua_size_6)
                    }
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.aqua_size_6)
                }
                row.addView(item.root)
            }
            binding.algaeGridContainer.addView(row)
        }
    }

    private fun renderSelection(
        item: ItemPlantHealthSymptomBinding,
        selected: Boolean
    ) {
        item.root.isChecked = selected
        item.root.strokeColor = ContextCompat.getColor(
            requireContext(),
            if (selected) R.color.aqua_status_success else R.color.aqua_card_outline
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private data class AlgaeOption(
        val key: String,
        val labelRes: Int
    )

    private companion object {
        const val COLUMN_COUNT = 3

        val ALGAE_TYPES = listOf(
            AlgaeOption("hair", R.string.plant_health_algae_hair),
            AlgaeOption("spot", R.string.plant_health_algae_spot),
            AlgaeOption("diatom", R.string.plant_health_algae_diatom),
            AlgaeOption("bba", R.string.plant_health_algae_bba),
            AlgaeOption("cyano", R.string.plant_health_algae_cyano),
            AlgaeOption("dust", R.string.plant_health_algae_dust)
        )
    }
}
