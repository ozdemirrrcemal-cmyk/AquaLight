package com.aqua.aqualight.ui.tabs.settings.usage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil3.load
import com.aqua.aqualight.R
import com.aqua.aqualight.application.user.UsageAnalyticsSnapshot
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentUsageBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
            fragment = this
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
            usage.weeklyAutomationCount.toString()

        binding.tvTotalTimeValue.text =
            usage.weeklyAlertCount.toString()

        binding.tvTodaySessionsValue.text =
            usage.todayAutomationCount.toString()

        binding.tvTodayTimeValue.text =
            usage.todayManualActionCount.toString()

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

        val date =
            Date(timeMillis)

        val formatter =
            SimpleDateFormat(
                "MMM d, HH:mm",
                Locale.getDefault()
            )

        return formatter.format(
            date
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}
