package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDetailLifeBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.livestock.TankLivestockCardFactory
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs

class TankDetailLifeFragment : Fragment(R.layout.fragment_tank_detail_life) {

    private var _binding: FragmentTankDetailLifeBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var cardFactory: TankLivestockCardFactory

    private var tankId: Long = 0L
    private var isOpeningLivestockForm: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailLifeBinding.bind(view)
        cardFactory = TankLivestockCardFactory(
            context = requireContext(),
            onClick = { livestockId ->
                openLivestockForm(livestockId)
            }
        )

        setupClickListeners()
        observeTank()
    }

    override fun onResume() {
        super.onResume()
        isOpeningLivestockForm = false
    }

    private fun setupClickListeners() {
        binding.btnAddLife.setOnClickListener {
            openLivestockPicker()
        }

        binding.btnEmptyAddLife.setOnClickListener {
            openLivestockPicker()
        }
    }

    private fun openLivestockPicker() {
        if (isOpeningLivestockForm) {
            return
        }

        val navController = findNavController()

        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                TankDetailFragment.KEY_SELECTED_TAB,
                TankDetailTabArgs.TANK_LIFE
            )

        val didNavigate = navController.navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailFragment,
            directions = TankDetailFragmentDirections
                .actionTankDetailFragmentToTankLivestockPickerFragment(
                    tankId = tankId
                )
        )

        isOpeningLivestockForm = didNavigate
    }

    private fun openLivestockForm(
        livestockId: Long = 0L
    ) {
        if (isOpeningLivestockForm) {
            return
        }

        val navController = findNavController()

        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                TankDetailFragment.KEY_SELECTED_TAB,
                TankDetailTabArgs.TANK_LIFE
            )

        val didNavigate = navController.navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailFragment,
            directions = TankDetailFragmentDirections.actionTankDetailFragmentToTankDetailLivestockFormFragment(
                tankId = tankId,
                livestockId = livestockId
            )
        )

        isOpeningLivestockForm = didNavigate
    }

    private fun observeTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->
            val tank = tanks.firstOrNull {
                tank ->
                tank.id == tankId
            } ?: return@observe

            renderLivestock(
                livestock = tank.livestock
            )
        }
    }

    private fun renderLivestock(
        livestock: List<AquariumLivestock>
    ) {
        binding.tankLifeListContainer.removeAllViews()

        val totalQuantity = livestock.sumOf {
            item ->
            item.quantity.coerceAtLeast(1)
        }

        binding.tvTankLifeSummary.text = if (livestock.isEmpty()) {
            getString(R.string.aquarium_no_livestock_yet)
        } else {
            getString(
                R.string.aquarium_livestock_summary_format,
                livestock.size,
                totalQuantity
            )
        }

        binding.cardTankLifeEmpty.isVisible = livestock.isEmpty()
        binding.tankLifeListContainer.isVisible = livestock.isNotEmpty()

        livestock.forEach {
            item ->
            binding.tankLifeListContainer.addView(
                cardFactory.create(
                    parent = binding.tankLifeListContainer,
                    livestock = item
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankDetailLifeFragment {
            return TankDetailLifeFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
