package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank

class TankDetailFragment : Fragment(R.layout.fragment_tank_detail) {

    private var _binding: FragmentTankDetailBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var selectedTab: TankDetailTab = TankDetailTab.DEVICES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankDetailBinding.bind(view)

        setupClickListeners()
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

        renderPlantMarkers(tank)
    }

    private fun renderPlantMarkers(
        tank: SavedAquariumTank
    ) {
        binding.markerContainer.removeAllViews()

        binding.markerContainer.post {
            val width = binding.markerContainer.width
            val height = binding.markerContainer.height

            tank.plants.forEachIndexed { index, plant ->
                val marker = TextView(requireContext()).apply {
                    text = "${index + 1}"
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_plant_marker)
                    includeFontPadding = false
                }

                val size = 30.dp()

                val params = FrameLayout.LayoutParams(
                    size,
                    size
                )

                marker.x = (plant.markerX * width) - size / 2f
                marker.y = (plant.markerY * height) - size / 2f

                binding.markerContainer.addView(marker, params)
            }
        }
    }

    private fun selectTab(
        tab: TankDetailTab
    ) {
        selectedTab = tab

        resetTabs()

        when (tab) {
            TankDetailTab.DEVICES -> {
                binding.tabDevices.setTextColor(Color.WHITE)
                binding.tabDevices.setTypeface(null, Typeface.BOLD)
                binding.tabUnderline.isVisible = true
                binding.devicesSection.isVisible = true
                binding.tvEmptyTab.isVisible = false
            }

            TankDetailTab.ACTIVITY -> {
                binding.tabActivity.setTextColor(Color.WHITE)
                binding.tabActivity.setTypeface(null, Typeface.BOLD)
                showEmptySection()
            }

            TankDetailTab.TANK -> {
                binding.tabTank.setTextColor(Color.WHITE)
                binding.tabTank.setTypeface(null, Typeface.BOLD)
                showEmptySection()
            }

            TankDetailTab.PLANTS -> {
                binding.tabPlants.setTextColor(Color.WHITE)
                binding.tabPlants.setTypeface(null, Typeface.BOLD)
                showEmptySection()
            }

            TankDetailTab.TANK_LIFE -> {
                binding.tabTankLife.setTextColor(Color.WHITE)
                binding.tabTankLife.setTypeface(null, Typeface.BOLD)
                showEmptySection()
            }
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

        binding.devicesSection.isVisible = false
        binding.tvEmptyTab.isVisible = false
    }

    private fun showEmptySection() {
        binding.tabUnderline.isVisible = false
        binding.devicesSection.isVisible = false
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