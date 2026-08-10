package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.utils.DialogType

/** Detail destination for one centrally identified, calibrated Dosing channel. */
class DeviceDosingChannelDetailFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelDetailFragmentArgs by navArgs()

    override val destinationTitle: String
        get() = args.channelTitle
            .ifBlank { getString(R.string.device_family_dosing) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupResetConfirmationResult()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingChannelDetailScreen(
                    onMenuItemClick = ::openMenuItem,
                    onResetChannelClick = ::showResetChannelConfirmation
                )
            }
        }
    }

    private fun setupResetConfirmationResult() {
        childFragmentManager.setFragmentResultListener(
            RESET_CONFIRM_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(ConfirmDialogFragment.RESULT_ACTION_ID) != ACTION_RESET_CHANNEL) {
                return@setFragmentResultListener
            }
            when (result.getString(ConfirmDialogFragment.RESULT_KEY)) {
                ConfirmDialogFragment.RESULT_CONFIRM,
                ConfirmDialogFragment.RESULT_CANCEL -> Unit
            }
        }
    }

    private fun openMenuItem(item: DosingDetailMenuItem) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingChannelDetailFragment) {
            return
        }
        navController.navigate(
            DeviceDosingChannelDetailFragmentDirections
                .actionDeviceDosingChannelDetailFragmentToDeviceDosingChannelMenuFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    menuKey = item.routeKey
                )
        )
    }

    private fun showResetChannelConfirmation() {
        ConfirmDialogFragment.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_detail_reset_warning_title),
            message = getString(R.string.device_dosing_detail_reset_warning_description),
            confirmText = getString(R.string.device_dosing_detail_reset_action),
            cancelText = getString(R.string.cancel),
            type = DialogType.WARNING,
            requestKey = RESET_CONFIRM_REQUEST_KEY,
            actionId = ACTION_RESET_CHANNEL,
            destructive = true
        )
    }

    private companion object {
        const val RESET_CONFIRM_REQUEST_KEY = "dosing_channel_reset_confirm"
        const val ACTION_RESET_CHANNEL = "reset_dosing_channel"
    }
}
