package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthBinding
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class TankHealthFragment : Fragment(R.layout.fragment_tank_health) {

    private val args: TankHealthFragmentArgs by navArgs()

    private var _binding: FragmentTankHealthBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthBinding.bind(view)

        setupHeader()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health),
                onBackClick = {
                    findNavController().navigateUp()
                },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_info,
                        contentDescription = getString(
                            R.string.tank_health_info_content_description
                        ),
                        onClick = ::showHealthInfo
                    )
                )
            )
        )
    }

    private fun showHealthInfo() {
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.screen_title_tank_health),
            message = getString(R.string.tank_health_info_message),
            primaryText = getString(R.string.ok),
            cancelText = null,
            tone = FeedbackBottomSheet.FeedbackTone.INFO,
            requestKey = HEALTH_INFO_REQUEST_KEY,
            actionId = ""
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val HEALTH_INFO_REQUEST_KEY = "tank_health_info_result"
    }
}
