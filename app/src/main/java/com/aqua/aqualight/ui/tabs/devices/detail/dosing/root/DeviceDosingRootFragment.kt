package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceDosingRootBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import kotlinx.coroutines.launch

class DeviceDosingRootFragment : Fragment(R.layout.fragment_device_dosing_root) {

    private val args: DeviceDosingRootFragmentArgs by navArgs()
    private val viewModel: DeviceDosingRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceDosingRootBinding? = null
    private val binding get() = _binding!!
    private var hasStartedOnce = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceDosingRootBinding.bind(view)

        viewModel.bind(args.deviceUid)
        setupHeader(
            title = viewModel.uiState.value.title.ifBlank {
                getString(R.string.device_family_dosing)
            }
        )
        setupPumpContent()
        observeHeaderTitle()
        observeChannelNavigation()
        observeChannelNavigationFailures()
    }

    override fun onStart() {
        super.onStart()
        if (hasStartedOnce) {
            viewModel.refreshAuthoritative()
        } else {
            hasStartedOnce = true
        }
    }

    private fun setupHeader(title: String) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                onBackClick = {
                    findNavController().navigateUp()
                },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = getString(
                            R.string.device_dosing_open_settings_description
                        ),
                        onClick = ::openSettings
                    )
                )
            )
        )
    }

    private fun openSettings() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingRootFragment) return
        navController.navigate(
            DeviceDosingRootFragmentDirections
                .actionDeviceDosingRootFragmentToDeviceDosingSettingsFragment(
                    deviceUid = args.deviceUid
                )
        )
    }

    private fun setupPumpContent() {
        binding.dosingPumpCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceDosingCatalogScreen(
                    pumpCount = state.pumpCount,
                    channels = state.channels,
                    onChannelClick = viewModel::openChannel,
                    pumpStates = state.pumpStates
                )
            }
        }
    }

    private fun observeChannelNavigation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvents.collect { target ->
                    AppRouteNavigator.openDosingChannel(
                        navController = findNavController(),
                        target = target
                    )
                }
            }
        }
    }

    private fun observeChannelNavigationFailures() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationFailureEvents.collect {
                    (activity as? BaseActivity)?.showSnackBar(
                        message = getString(R.string.device_dosing_channel_open_failed),
                        type = BaseActivity.SnackType.ERROR
                    )
                }
            }
        }
    }

    private fun observeHeaderTitle() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (_binding == null) return@collect
                    setupHeader(
                        title = state.title.ifBlank { getString(R.string.device_family_dosing) }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
