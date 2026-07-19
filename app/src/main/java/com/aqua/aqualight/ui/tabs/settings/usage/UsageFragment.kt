package com.aqua.aqualight.ui.tabs.settings.usage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.user.UsageAnalyticsSnapshot
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentUsageBinding
import com.aqua.aqualight.localization.LocaleFormatters
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UsageFragment : Fragment(R.layout.fragment_usage) {

    private var _binding: FragmentUsageBinding? = null
    private val binding get() = _binding!!

    private val settingsOperations by lazy {
        requireContext().requireAppContainer().userSettingsOperations
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUsageBinding.bind(view)

        setupHeader()
        observeUsageAnalytics()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(fragment = this)
    }

    private fun observeUsageAnalytics() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.usageAnalytics.collectLatest(::bindUsageToUi)
        }
    }

    private fun bindUsageToUi(usage: UsageAnalyticsSnapshot) {
        val locale = currentLocale()
        binding.tvTotalSessionsValue.text = LocaleFormatters.formatInteger(
            usage.weeklyAutomationCount.toLong(),
            locale
        )
        binding.tvTotalTimeValue.text = LocaleFormatters.formatInteger(
            usage.weeklyAlertCount.toLong(),
            locale
        )
        binding.tvTodaySessionsValue.text = LocaleFormatters.formatInteger(
            usage.todayAutomationCount.toLong(),
            locale
        )
        binding.tvTodayTimeValue.text = LocaleFormatters.formatInteger(
            usage.todayManualActionCount.toLong(),
            locale
        )
        binding.tvLastOpenValue.text = formatLastEventTime(
            usage.lastEventTimeMillis,
            locale
        )
        binding.tvMostUsedDeviceValue.text = usage.lastEventDescription.ifBlank {
            getString(R.string.usage_last_event_none)
        }
    }

    private fun formatLastEventTime(timeMillis: Long, locale: Locale): String {
        if (timeMillis <= 0L) {
            return getString(R.string.usage_last_open_never)
        }
        return LocaleFormatters.formatDateTime(timeMillis, locale)
    }

    private fun currentLocale(): Locale {
        val locales = resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
