package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailPlantsBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumPlant
import com.google.android.material.card.MaterialCardView

class TankDetailPlantsFragment : Fragment(R.layout.fragment_tank_detail_plants) {

    private var _binding: FragmentTankDetailPlantsBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var isOpeningPlantTagScreen: Boolean = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentTankDetailPlantsBinding.bind(view)

        setupClickListeners()
        observeTank()
    }

    override fun onResume() {
        super.onResume()

        isOpeningPlantTagScreen = false
    }

    private fun setupClickListeners() {
        binding.btnAddPlant.setOnClickListener {
            openPlantTagScreen()
        }
    }

    private fun openPlantTagScreen() {
        if (isOpeningPlantTagScreen) {
            return
        }

        val navController = findNavController()

        if (navController.currentDestination?.id != R.id.tankDetailFragment) {
            return
        }

        isOpeningPlantTagScreen = true

        navController.navigate(
            R.id.action_tankDetailFragment_to_tankDetailPlantTagFragment,
            bundleOf(
                "tankId" to tankId
            )
        )
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { tank ->
                tank.id == tankId
            } ?: return@observe

            renderPlants(
                plants = tank.plants
            )
        }
    }

    private fun renderPlants(
        plants: List<SavedAquariumPlant>
    ) {
        binding.plantListContainer.removeAllViews()

        plants.forEachIndexed { index, plant ->
            binding.plantListContainer.addView(
                createPlantCard(
                    index = index,
                    plant = plant
                )
            )
        }
    }

    private fun createPlantCard(
        index: Int,
        plant: SavedAquariumPlant
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 12.dp()
            layoutParams = params
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                14.dp(),
                12.dp(),
                14.dp(),
                12.dp()
            )
        }

        val number = TextView(requireContext()).apply {
            text = "${index + 1}"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.NORMAL)
            setBackgroundResource(R.drawable.bg_plant_number_circle)
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                38.dp(),
                38.dp()
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginStart = 14.dp()
            layoutParams = params
        }

        val categoryText = TextView(requireContext()).apply {
            text = plant.category
            textSize = 12f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
        }

        val nameText = TextView(requireContext()).apply {
            text = plant.plantName
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6.dp()
            layoutParams = params
        }

        textBox.addView(categoryText)
        textBox.addView(nameText)

        row.addView(number)
        row.addView(textBox)

        card.addView(row)

        return card
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankDetailPlantsFragment {
            return TankDetailPlantsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}