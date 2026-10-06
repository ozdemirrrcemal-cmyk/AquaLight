package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentPlantHealthObservationBinding
import com.aqua.aqualight.databinding.ItemPlantHealthSymptomBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel

class PlantHealthObservationFragment : Fragment(R.layout.fragment_plant_health_observation) {

    private val args: PlantHealthObservationFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentPlantHealthObservationBinding? = null
    private val binding get() = _binding!!

    private val selectedSymptoms = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L && args.plantId > 0L) {
            "PlantHealthObservationFragment requires positive tankId and plantId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlantHealthObservationBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.plant_health_observation_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        renderSymptoms()

        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val plant = tanks.firstOrNull { tank -> tank.id == args.tankId }
                ?.plants
                ?.firstOrNull { item -> item.id == args.plantId }
            if (plant == null) {
                findNavController().navigateUp()
            } else {
                renderPlant(plant)
            }
        }
    }

    private fun renderPlant(plant: AquariumPlantTag) {
        binding.tvPlantName.text = plant.plantName
        binding.tvPlantCategory.text = plant.category
        bindPlantPhoto(binding.imgPlant, plant.photoUri)
    }

    private fun renderSymptoms() {
        binding.symptomGridContainer.removeAllViews()
        SYMPTOMS.chunked(COLUMN_COUNT).forEach { rowOptions ->
            binding.symptomGridContainer.addView(createSymptomRow(rowOptions))
        }
    }

    private fun createSymptomRow(rowOptions: List<SymptomOption>): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = COLUMN_COUNT.toFloat()
            rowOptions.forEachIndexed { index, option ->
                addView(createSymptomItem(this, index, option))
            }
            repeat(COLUMN_COUNT - rowOptions.size) {
                addView(
                    View(requireContext()),
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                    )
                )
            }
        }

    private fun createSymptomItem(
        parent: LinearLayout,
        index: Int,
        option: SymptomOption
    ): View {
        val item = ItemPlantHealthSymptomBinding.inflate(
            LayoutInflater.from(requireContext()),
            parent,
            false
        )
        item.tvSymptom.setText(option.labelRes)
        item.ivSymptom.setImageResource(option.iconRes)
        renderSymptomSelection(item, option.key in selectedSymptoms)
        item.root.setOnClickListener {
            if (!selectedSymptoms.add(option.key)) {
                selectedSymptoms.remove(option.key)
            }
            renderSymptomSelection(item, option.key in selectedSymptoms)
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
        return item.root
    }

    private fun renderSymptomSelection(
        item: ItemPlantHealthSymptomBinding,
        selected: Boolean
    ) {
        item.root.isChecked = selected
        item.root.strokeColor = ContextCompat.getColor(
            requireContext(),
            if (selected) R.color.aqua_status_success else R.color.aqua_card_outline
        )
    }

    private fun bindPlantPhoto(imageView: ImageView, photoUri: String?) {
        if (photoUri.isNullOrBlank()) {
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.ic_health_plant_24)
            return
        }
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.load(photoUri.toUri()) {
            crossfade(true)
            error(R.drawable.ic_health_plant_24)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private data class SymptomOption(
        val key: String,
        val labelRes: Int,
        val iconRes: Int
    )

    private companion object {
        const val COLUMN_COUNT = 3

        val SYMPTOMS = listOf(
            SymptomOption("healthy", R.string.plant_health_symptom_healthy, R.drawable.ic_check_24),
            SymptomOption("yellowing", R.string.plant_health_symptom_yellowing, R.drawable.ic_warning),
            SymptomOption("melting", R.string.plant_health_symptom_melting, R.drawable.ic_health_plant_24),
            SymptomOption("damage", R.string.plant_health_symptom_damage, R.drawable.ic_warning),
            SymptomOption("slow_growth", R.string.plant_health_symptom_slow_growth, R.drawable.ic_health_plant_24),
            SymptomOption("brown_spots", R.string.plant_health_symptom_brown_spots, R.drawable.ic_warning),
            SymptomOption("algae", R.string.plant_health_symptom_algae, R.drawable.ic_care_algae_24),
            SymptomOption("deformation", R.string.plant_health_symptom_deformation, R.drawable.ic_health_plant_24),
            SymptomOption("other", R.string.plant_health_symptom_other, R.drawable.ic_info)
        )
    }
}
