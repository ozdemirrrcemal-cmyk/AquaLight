package com.aqua.aqualight.ui.tabs.aquarium.create

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentCreateTankBinding
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankDescriptionFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankMaterialFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankNameFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankPhotoFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankStepFragment
import kotlinx.coroutines.launch

class CreateTankFragment : Fragment(R.layout.fragment_create_tank) {

    private var _binding: FragmentCreateTankBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var createTankNavController: NavController

    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null

    private var isCompletingTank: Boolean = false
    private var isNavigatingStep: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentCreateTankBinding.bind(view)

        setupCreateTankNavController()
        setupSystemBackButton()
        setupNextButton()
    }

    private fun setupCreateTankNavController() {
        val navHostFragment = childFragmentManager.findFragmentById(
            R.id.createTankNavHost
        ) as NavHostFragment

        createTankNavController =
            navHostFragment.navController

        destinationChangedListener =
            NavController.OnDestinationChangedListener { _, destination, _ ->
                renderChromeForDestination(destination)
            }

        createTankNavController.addOnDestinationChangedListener(
            destinationChangedListener!!
        )

        createTankNavController.currentDestination?.let { destination ->
            renderChromeForDestination(destination)
        }
    }

    private fun setupSystemBackButton() {
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        handleBackNavigation()
                    }
                }
            )
    }

    private fun setupNextButton() {
        binding.btnNext.setOnClickListener {
            handleNextClick()
        }
    }

    private fun renderChromeForDestination(
        destination: NavDestination
    ) {
        isNavigatingStep = false
        val stepIndex = stepIndexOf(
            destination.id
        )

        val isStepDestination =
            stepIndex != null

        binding.appHeader.root.isVisible =
            isStepDestination

        binding.progressBar.isVisible =
            isStepDestination

        binding.bottomContainer.isVisible =
            isStepDestination

        if (!isStepDestination) {
            return
        }

        val safeStepIndex =
            stepIndex ?: 0

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(
                    R.string.aquarium_create_step_title,
                    safeStepIndex + 1
                ),
                onBackClick = {
                    handleBackNavigation()
                }
            )
        )

        binding.progressBar.progress =
            ((safeStepIndex + 1) * 100) / TOTAL_STEPS

        binding.btnNext.text =
            if (safeStepIndex == TOTAL_STEPS - 1) {
                getString(R.string.aquarium_action_complete)
            } else {
                getString(R.string.aquarium_action_next)
            }

        binding.btnNext.isEnabled =
            !isCompletingTank && !isNavigatingStep
    }

    private fun handleNextClick() {
        if (isCompletingTank || isNavigatingStep) {
            return
        }

        val currentStepFragment =
            currentCreateFlowFragment()

        if (currentStepFragment is TankStepFragment) {
            val isValid =
                currentStepFragment.validateAndSave()

            if (!isValid) {
                return
            }
        }

        when (createTankNavController.currentDestination?.id) {
            R.id.tankNameStepFragment -> {
                navigateToStepIfCurrent(
                    sourceDestinationId = R.id.tankNameStepFragment,
                    directions = TankNameFragmentDirections
                        .actionTankNameStepFragmentToTankDescriptionStepFragment()
                )
            }

            R.id.tankDescriptionStepFragment -> {
                navigateToStepIfCurrent(
                    sourceDestinationId = R.id.tankDescriptionStepFragment,
                    directions = TankDescriptionFragmentDirections
                        .actionTankDescriptionStepFragmentToTankPhotoStepFragment()
                )
            }

            R.id.tankPhotoStepFragment -> {
                navigateToStepIfCurrent(
                    sourceDestinationId = R.id.tankPhotoStepFragment,
                    directions = TankPhotoFragmentDirections
                        .actionTankPhotoStepFragmentToTankMaterialStepFragment()
                )
            }

            R.id.tankMaterialStepFragment -> {
                navigateToStepIfCurrent(
                    sourceDestinationId = R.id.tankMaterialStepFragment,
                    directions = TankMaterialFragmentDirections
                        .actionTankMaterialStepFragmentToTankInfoStepFragment()
                )
            }

            R.id.tankInfoStepFragment -> {
                completeTank()
            }
        }
    }

    private fun navigateToStepIfCurrent(
        sourceDestinationId: Int,
        directions: NavDirections
    ) {
        if (isNavigatingStep || createTankNavController.currentDestination?.id != sourceDestinationId) {
            return
        }

        isNavigatingStep = true
        binding.btnNext.isEnabled = false

        runCatching {
            createTankNavController.navigate(directions)
        }.onFailure { exception ->
            exception.printStackTrace()
            isNavigatingStep = false
            _binding?.btnNext?.isEnabled = !isCompletingTank
        }
    }

    private fun handleBackNavigation() {
        if (isCompletingTank) {
            return
        }

        if (!::createTankNavController.isInitialized) {
            closeCreateFlow()
            return
        }

        val currentDestinationId =
            createTankNavController.currentDestination?.id

        if (currentDestinationId == R.id.tankNameStepFragment) {
            closeCreateFlow()
            return
        }

        val handledByCreateFlow =
            createTankNavController.navigateUp()

        if (!handledByCreateFlow) {
            closeCreateFlow()
        }
    }

    private fun closeCreateFlow() {
        cleanupDraftPhotoIfNotCompleted()
        findNavController().navigateUp()
    }

    private fun cleanupDraftPhotoIfNotCompleted() {
        if (isCompletingTank || !::createTankNavController.isInitialized) {
            return
        }

        val draftPhotoUri = runCatching {
            createTankViewModel().tankDraft.photoUri
        }.getOrNull()

        AppMediaStorage.deleteInternalMedia(
            context = requireContext(),
            uriString = draftPhotoUri
        )
    }

    private fun completeTank() {
        if (isCompletingTank) {
            return
        }

        isCompletingTank =
            true

        binding.btnNext.isEnabled =
            false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.addTankFromDraft(
                    createTankViewModel().tankDraft
                )

                findNavController().popBackStack(
                    R.id.aquariumFragment,
                    false
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                isCompletingTank =
                    false

                _binding?.btnNext?.isEnabled =
                    true

                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(R.string.aquarium_error_tank_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun createTankViewModel(): CreateTankViewModel {
        val owner =
            createTankNavController.getViewModelStoreOwner(
                R.id.nav_create_tank
            )

        return ViewModelProvider(owner)[CreateTankViewModel::class.java]
    }

    private fun currentCreateFlowFragment(): Fragment? {
        val navHostFragment =
            childFragmentManager.findFragmentById(
                R.id.createTankNavHost
            ) as? NavHostFragment

        return navHostFragment
            ?.childFragmentManager
            ?.primaryNavigationFragment
    }

    private fun stepIndexOf(
        destinationId: Int
    ): Int? {
        return when (destinationId) {
            R.id.tankNameStepFragment -> 0
            R.id.tankDescriptionStepFragment -> 1
            R.id.tankPhotoStepFragment -> 2
            R.id.tankMaterialStepFragment -> 3
            R.id.tankInfoStepFragment -> 4
            else -> null
        }
    }

    override fun onDestroyView() {
        if (::createTankNavController.isInitialized) {
            destinationChangedListener?.let { listener ->
                createTankNavController.removeOnDestinationChangedListener(
                    listener
                )
            }
        }

        destinationChangedListener =
            null

        super.onDestroyView()

        _binding =
            null
    }

    companion object {
        private const val TOTAL_STEPS = 5
    }
}
