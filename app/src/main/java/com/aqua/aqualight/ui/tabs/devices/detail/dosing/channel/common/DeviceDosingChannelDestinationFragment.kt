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
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingSelectedPumpSection
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.exactDosingPumpCountOrNull

/** Shared header and selected-pump shell for centrally resolved Dosing channel destinations. */
abstract class DeviceDosingChannelDestinationFragment(
    @LayoutRes layoutRes: Int
) : Fragment(layoutRes) {

    protected abstract val destinationTitle: String
    private var headerBinding: LayoutAquaHeaderBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerBinding = LayoutAquaHeaderBinding.bind(view.findViewById(R.id.appHeader))
        updateDestinationTitle(destinationTitle)
    }

    /** Updates the shared header from destination-owned central presentation state. */
    protected fun updateDestinationTitle(title: String) {
        headerBinding?.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = title,
                onBackClick = ::onBackRequested
            )
        )
    }

    override fun onDestroyView() {
        headerBinding = null
        super.onDestroyView()
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
