package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** UI-only child destination for one Dosing detail menu entry. */
class DeviceDosingChannelMenuFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelMenuFragmentArgs by navArgs()
    private val menuItem: DosingDetailMenuItem?
        get() = DosingDetailMenuItem.fromRouteKey(args.menuKey)

    override val destinationTitle: String
        get() = menuItem
            ?.let { item -> getString(item.titleRes) }
            ?: getString(R.string.device_family_dosing)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val item = menuItem ?: run {
            findNavController().navigateUp()
            return
        }
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
                DeviceDosingChannelMenuScreen(item = item)
            }
        }
    }
}
