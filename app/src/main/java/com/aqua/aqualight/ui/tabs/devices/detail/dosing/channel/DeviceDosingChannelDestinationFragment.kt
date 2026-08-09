package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

/** Shared header behavior for distinct centrally resolved Dosing destinations. */
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
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }
}
