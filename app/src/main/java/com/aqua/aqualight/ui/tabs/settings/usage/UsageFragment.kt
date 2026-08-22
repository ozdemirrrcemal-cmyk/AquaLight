package com.aqua.aqualight.ui.tabs.settings.usage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.user.UsageAnalyticsSnapshot
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentUsageBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UsageFragment : Fragment(R.layout.fragment_usage) {

    private var _binding: FragmentUsageBinding? = null
    private val binding get() = _binding!!

    private val settingsOperations by lazy {
        requireContext().requireAppContainer().userSettingsOperations
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentUsageBinding.bind(view)

        setupHeader()

        observeUsageAnalytics()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_usage)
            )
        )
    }

    private fun observeUsageAnalytics() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.usageAnalytics.collectLatest { usage ->
                bindUsageToUi(
                    usage
                )
            }
        }
    }

    private fun bindUsageToUi(
        usage: UsageAnalyticsSnapshot
    ) {
        binding.tvTotalSessionsValue.text =
            LocaleFormatter.formatInteger(
                requireContext(),
                usage.weeklyAutomationCount
            )

        binding.tvTotalTimeValue.text =
            LocaleFormatter.formatInteger(
                requireContext(),
                usage.weeklyAlertCount
            )

        binding.tvTodaySessionsValue.text =
            LocaleFormatter.formatInteger(
                requireContext(),
                usage.todayAutomationCount
            )

        binding.tvTodayTimeValue.text =
            LocaleFormatter.formatInteger(
                requireContext(),
                usage.todayManualActionCount
            )

        binding.tvLastOpenValue.text =
            formatLastEventTime(
                usage.lastEventTimeMillis
            )

        binding.tvMostUsedDeviceValue.text =
            usage.lastEventDescription.ifBlank {
                getString(
                    R.string.usage_last_event_none
                )
            }
    }

    private fun formatLastEventTime(
        timeMillis: Long
    ): String {
        if (
            timeMillis <= 0L
        ) {
            return getString(
                R.string.usage_last_open_never
            )
        }

        return LocaleFormatter.formatDateTime(
            requireContext(),
            timeMillis
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}
