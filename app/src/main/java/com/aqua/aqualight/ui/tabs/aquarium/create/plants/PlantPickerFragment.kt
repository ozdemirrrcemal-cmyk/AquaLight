package com.aqua.aqualight.ui.tabs.aquarium.create.plants

import com.aqua.aqualight.data.aquarium.catalog.plant.PlantCatalog
import com.aqua.aqualight.data.aquarium.catalog.plant.AquariumPlant
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPlantPickerBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderSearchField
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.google.android.material.card.MaterialCardView

class PlantPickerFragment : Fragment(R.layout.fragment_plant_picker) {

    private var _binding: FragmentPlantPickerBinding? = null
    private val binding get() = _binding!!

    private val args: PlantPickerFragmentArgs by navArgs()

    private val plants: List<AquariumPlant> = PlantCatalog.plants

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentPlantPickerBinding.bind(view)

        setupHeader()
        renderPlantList(plants)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                onBackClick = {
                    closePicker()
                },
                searchField = AquaHeaderSearchField(
                    hint = getString(R.string.catalog_search_hint),
                    onTextChanged = { query ->
                        filterPlants(query)
                    },
                    onClearClick = {
                        renderPlantList(plants)
                    }
                )
            )
        )
    }

    private fun filterPlants(
        query: String
    ) {
        val normalizedQuery = query.trim()

        val filteredPlants = if (normalizedQuery.isBlank()) {
            plants
        } else {
            plants.filter { plant ->
                plant.name.contains(
                    normalizedQuery,
                    ignoreCase = true
                ) ||
                    plant.category.contains(
                        normalizedQuery,
                        ignoreCase = true
                    )
            }
        }

        renderPlantList(filteredPlants)
    }

    private fun renderPlantList(
        plantList: List<AquariumPlant>
    ) {
        binding.listContainer.removeAllViews()

        val title = TextView(requireContext()).apply {
            text = if (plantList.size == plants.size) {
                getString(R.string.plant_picker_title)
            } else {
                getString(R.string.plant_picker_found, plantList.size)
            }

            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_secondary
                )
            )
            textSize = 14f
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16.dp()
            layoutParams = params
        }

        binding.listContainer.addView(title)

        if (plantList.isEmpty()) {
            showEmptySearchResult()
            return
        }

        plantList.forEach { plant ->
            binding.listContainer.addView(
                createPlantCard(plant)
            )
        }
    }

    private fun showEmptySearchResult() {
        val emptyText = TextView(requireContext()).apply {
            text = getString(R.string.plant_picker_empty)
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_state_text_secondary
                )
            )
            setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.aqua_text_size_state_title_small)
            )
            gravity = Gravity.CENTER
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 32.dp()
            layoutParams = params
        }

        binding.listContainer.addView(emptyText)
    }

    private fun createPlantCard(
        plant: AquariumPlant
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(
                requireContext(),
                R.color.aqua_card_outline
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_surface
                )
            )
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 12.dp()
            layoutParams = params

            setOnClickListener {
                selectPlant(
                    plantName = plant.name,
                    category = plant.category
                )
            }
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                18.dp(),
                15.dp(),
                18.dp(),
                15.dp()
            )
        }

        val categoryText = TextView(requireContext()).apply {
            text = plant.category
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_secondary
                )
            )
            textSize = 12f
            includeFontPadding = false
        }

        val plantNameText = TextView(requireContext()).apply {
            text = plant.name
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_primary
                )
            )
            setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.aqua_text_size_state_title_small)
            )
            includeFontPadding = false
            maxLines = 2

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 7.dp()
            layoutParams = params
        }

        content.addView(categoryText)
        content.addView(plantNameText)

        card.addView(content)

        return card
    }

    private fun selectPlant(
        plantName: String,
        category: String
    ) {
        val navController = findNavController()
        val resultBundle = bundleOf(
            RESULT_PLANT_NAME to plantName,
            RESULT_PLANT_CATEGORY to category
        )

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(
                RESULT_BUNDLE_KEY,
                resultBundle
            )

        navController.navigateUp()
    }

    private fun closePicker() {
        findNavController().navigateUp()
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_USE_NAV_RESULT = "useNavResult"

        const val RESULT_BUNDLE_KEY = "plant_picker_result_bundle"

        const val RESULT_PLANT_NAME = "plant_name"
        const val RESULT_PLANT_CATEGORY = "plant_category"
    }
}
