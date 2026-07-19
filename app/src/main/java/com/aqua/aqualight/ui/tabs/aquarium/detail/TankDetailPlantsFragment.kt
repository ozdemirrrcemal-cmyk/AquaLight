package com.aqua.aqualight.ui.tabs.aquarium.detail

import com.aqua.aqualight.ui.common.text.setTextSizeResource
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentTankDetailPlantsBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.google.android.material.card.MaterialCardView

class TankDetailPlantsFragment : Fragment(R.layout.fragment_tank_detail_plants) {
    private var _binding: FragmentTankDetailPlantsBinding? = null
    private val binding get() = _binding!!
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private var tankId: Long = 0L
    private var isOpeningPlantTagScreen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankDetailPlantsBinding.bind(view)
        binding.btnAddPlant.setOnClickListener { openPlantTagScreen() }
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { it.id == tankId } ?: return@observe
            renderPlants(tank.plants)
        }
    }

    override fun onResume() {
        super.onResume()
        isOpeningPlantTagScreen = false
    }

    private fun openPlantTagScreen() {
        if (isOpeningPlantTagScreen) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.tankDetailFragment) return
        isOpeningPlantTagScreen = true
        navController.navigate(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankDetailPlantTagFragment(
                tankId = tankId
            )
        )
    }

    private fun renderPlants(plants: List<AquariumPlantTag>) {
        binding.plantListContainer.removeAllViews()
        plants.forEachIndexed { index, plant ->
            binding.plantListContainer.addView(createPlantCard(index, plant))
        }
    }

    private fun createPlantCard(index: Int, plant: AquariumPlantTag): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = resources.getDimensionPixelOffset(R.dimen.aqua_size_18).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_card_surface))
            cardElevation = 0f
            useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_12) }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(resources.getDimensionPixelOffset(R.dimen.aqua_size_14), resources.getDimensionPixelOffset(R.dimen.aqua_size_12), resources.getDimensionPixelOffset(R.dimen.aqua_size_14), resources.getDimensionPixelOffset(R.dimen.aqua_size_12))
        }
        val number = TextView(requireContext()).apply {
            text = "${index + 1}"
            gravity = Gravity.CENTER
            setTextSizeResource(R.dimen.aqua_text_size_body_small)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))
            setTypeface(null, Typeface.NORMAL)
            setBackgroundResource(R.drawable.bg_plant_number_circle)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelOffset(R.dimen.aqua_size_38), resources.getDimensionPixelOffset(R.dimen.aqua_size_38))
        }
        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = resources.getDimensionPixelOffset(R.dimen.aqua_size_14) }
        }
        val categoryText = TextView(requireContext()).apply {
            text = plant.category
            setTextSizeResource(R.dimen.aqua_text_size_caption)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_card_text_secondary))
            includeFontPadding = false
        }
        val nameText = TextView(requireContext()).apply {
            text = plant.plantName
            setTextSizeResource(R.dimen.aqua_text_size_body)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_card_text_primary))
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_6) }
        }
        textBox.addView(categoryText)
        textBox.addView(nameText)
        row.addView(number)
        row.addView(textBox)
        card.addView(row)
        return card
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(tankId: Long): TankDetailPlantsFragment {
            return TankDetailPlantsFragment().apply {
                arguments = Bundle().apply { putLong(ARG_TANK_ID, tankId) }
            }
        }
    }
}
