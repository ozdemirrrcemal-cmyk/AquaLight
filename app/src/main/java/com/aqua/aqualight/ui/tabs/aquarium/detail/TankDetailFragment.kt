package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.google.android.material.card.MaterialCardView

class TankDetailFragment : Fragment(R.layout.fragment_tank_detail) {

    private var _binding: FragmentTankDetailBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: TankDetailTab = TankDetailTab.DEVICES
    private var currentTank: SavedAquariumTank? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankDetailBinding.bind(view)

        setupClickListeners()
        setupSystemBackButton()
        observeTank()
        selectTab(TankDetailTab.DEVICES)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnEdit.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Edit tank will be added later.",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnAddDevice.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Add device will be connected later.",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnAddPlant.setOnClickListener {
            openPlantTagFlow()
        }

        binding.tabDevices.setOnClickListener {
            selectTab(TankDetailTab.DEVICES)
        }

        binding.tabActivity.setOnClickListener {
            selectTab(TankDetailTab.ACTIVITY)
        }

        binding.tabTank.setOnClickListener {
            selectTab(TankDetailTab.TANK)
        }

        binding.tabPlants.setOnClickListener {
            selectTab(TankDetailTab.PLANTS)
        }

        binding.tabTankLife.setOnClickListener {
            selectTab(TankDetailTab.TANK_LIFE)
        }
    }

    private fun setupSystemBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (handlePlantFlowBack()) {
                        return
                    }

                    findNavController().navigateUp()
                }
            }
        )
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { it.id == tankId }

            if (tank == null) {
                Toast.makeText(
                    requireContext(),
                    "Tank not found.",
                    Toast.LENGTH_SHORT
                ).show()

                findNavController().navigateUp()
                return@observe
            }

            bindTank(tank)
        }
    }

    private fun bindTank(
        tank: SavedAquariumTank
    ) {
        currentTank = tank
        binding.tvTankTitle.text = tank.name

        if (!tank.photoUri.isNullOrBlank()) {
            binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
                placeholder(R.drawable.nature_aquarium)
                error(R.drawable.nature_aquarium)
                crossfade(true)
            }
        } else {
            binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
        }

        binding.markerContainer.removeAllViews()
        binding.markerContainer.isVisible = false

        if (selectedTab == TankDetailTab.PLANTS) {
            renderPlantsSection(tank)
        }
    }

    private fun selectTab(
        tab: TankDetailTab
    ) {
        selectedTab = tab

        resetTabs()

        when (tab) {
            TankDetailTab.DEVICES -> {
                activateTab(binding.tabDevices)
                moveTabUnderline(binding.tabDevices)

                binding.devicesSection.isVisible = true
                binding.tvEmptyTab.isVisible = false
            }

            TankDetailTab.ACTIVITY -> {
                activateTab(binding.tabActivity)
                moveTabUnderline(binding.tabActivity)

                showEmptySection()
            }

            TankDetailTab.TANK -> {
                activateTab(binding.tabTank)
                moveTabUnderline(binding.tabTank)

                showEmptySection()
            }

            TankDetailTab.PLANTS -> {
                activateTab(binding.tabPlants)
                moveTabUnderline(binding.tabPlants)

                binding.plantsSection.isVisible = true
                binding.tvEmptyTab.isVisible = false

                currentTank?.let { tank ->
                    renderPlantsSection(tank)
                }
            }

            TankDetailTab.TANK_LIFE -> {
                activateTab(binding.tabTankLife)
                moveTabUnderline(binding.tabTankLife)

                showEmptySection()
            }
        }
    }

    private fun renderPlantsSection(
        tank: SavedAquariumTank
    ) {
        binding.plantListContainer.removeAllViews()

        tank.plants.forEachIndexed { index, plant ->
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

    fun openPlantTagFlow() {
        binding.plantFlowContainer.isVisible = true

        childFragmentManager.commit {
            replace(
                R.id.plantFlowContainer,
                TankDetailPlantTagFragment.newInstance(tankId),
                "TANK_DETAIL_PLANT_TAG_FRAGMENT"
            )
        }
    }

    fun openPlantPickerFlow() {
        childFragmentManager.commit {
            setReorderingAllowed(true)
            add(
                R.id.plantFlowContainer,
                PlantPickerFragment(),
                "PLANT_PICKER_FRAGMENT"
            )
            addToBackStack("PLANT_PICKER_FRAGMENT")
        }
    }

    fun closePlantTagFlow() {
        childFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        val currentPlantFragment = childFragmentManager.findFragmentById(
            R.id.plantFlowContainer
        )

        if (currentPlantFragment != null) {
            childFragmentManager.commit {
                remove(currentPlantFragment)
            }
        }

        binding.plantFlowContainer.isVisible = false
    }

    private fun handlePlantFlowBack(): Boolean {
        if (!binding.plantFlowContainer.isVisible) {
            return false
        }

        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        } else {
            closePlantTagFlow()
        }

        return true
    }

    private fun activateTab(
        tabView: TextView
    ) {
        tabView.setTextColor(Color.WHITE)
        tabView.setTypeface(null, Typeface.BOLD)
    }

    private fun moveTabUnderline(
        tabView: TextView
    ) {
        binding.tabsContainer.post {
            val underlineWidth = (tabView.width * 0.58f)
                .toInt()
                .coerceIn(
                    34.dp(),
                    68.dp()
                )

            val params = binding.tabUnderline.layoutParams
            params.width = underlineWidth
            binding.tabUnderline.layoutParams = params

            val targetX = tabView.x + ((tabView.width - underlineWidth) / 2f)

            binding.tabUnderline.animate()
                .translationX(targetX)
                .setDuration(180)
                .start()
        }
    }

    private fun resetTabs() {
        val inactiveColor = Color.parseColor("#8FA4BE")

        listOf(
            binding.tabDevices,
            binding.tabActivity,
            binding.tabTank,
            binding.tabPlants,
            binding.tabTankLife
        ).forEach { tab ->
            tab.setTextColor(inactiveColor)
            tab.setTypeface(null, Typeface.NORMAL)
        }

        binding.tabUnderline.isVisible = true
        binding.devicesSection.isVisible = false
        binding.plantsSection.isVisible = false
        binding.tvEmptyTab.isVisible = false
    }

    private fun showEmptySection() {
        binding.devicesSection.isVisible = false
        binding.plantsSection.isVisible = false
        binding.tvEmptyTab.isVisible = true
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class TankDetailTab {
        DEVICES,
        ACTIVITY,
        TANK,
        PLANTS,
        TANK_LIFE
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
    }
}