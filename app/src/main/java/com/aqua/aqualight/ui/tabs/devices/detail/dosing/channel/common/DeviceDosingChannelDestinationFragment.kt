package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingSelectedPumpSection
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.exactDosingPumpCountOrNull

/** Shared header and selected-pump shell for centrally resolved Dosing channel destinations. */
abstract class DeviceDosingChannelDestinationFragment(
    @LayoutRes layoutRes: Int
) : Fragment(layoutRes) {

    protected abstract val destinationTitle: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val header = LayoutAquaHeaderBinding.bind(view.findViewById(R.id.appHeader))
        header.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = destinationTitle,
                onBackClick = ::onBackRequested
            )
        )
    }

    protected open fun onBackRequested() {
        findNavController().navigateUp()
    }

    protected fun setupSelectedPump(
        view: View,
        deviceUid: String,
        slotId: String,
        pumpCount: Int,
        channelNumber: Int
    ) {
        val hasRouteIdentity = deviceUid.isNotBlank() && slotId.isNotBlank()
        val selectedPumpCount = exactDosingPumpCountOrNull(pumpCount)
            ?.takeIf { exactPumpCount -> channelNumber in 1..exactPumpCount }

        if (!hasRouteIdentity || selectedPumpCount == null) {
            findNavController().navigateUp()
            return
        }

        view.findViewById<ComposeView>(R.id.dosingPumpCompose).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DosingSelectedPumpSection(
                    pumpCount = selectedPumpCount,
                    selectedChannelNumber = channelNumber
                )
            }
        }
    }
}
