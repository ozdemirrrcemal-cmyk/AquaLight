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
        applyLocalUsageSummaryCopy()
        observeUsageSummary()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun applyLocalUsageSummaryCopy() = with(binding) {
        tvUsageInfo.setText(R.string.usage_local_subinfo)
        tvUsageInfo.contentDescription = getString(R.string.usage_local_subinfo_desc)
        tvSummaryTitle.setText(R.string.usage_local_week_title)
        cardUsageSummary.contentDescription = getString(R.string.usage_local_week_card_desc)
        cardTodayUsage.contentDescription = getString(R.string.usage_local_today_card_desc)
        cardLastActivity.contentDescription = getString(
            R.string.usage_local_last_activity_card_desc
        )
        cardUsageInfo.contentDescription = getString(R.string.usage_local_privacy_card_desc)
        tvUsagePrivacyTitle.setText(R.string.usage_local_privacy_title)
        tvUsagePrivacyBody.setText(R.string.usage_local_privacy_body)
    }

    private fun observeUsageSummary() {
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
