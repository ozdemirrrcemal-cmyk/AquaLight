package com.aqua.aqualight.ui.tabs.settings.usage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentUsageBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageFragment : Fragment(R.layout.fragment_usage) {

    private var _binding: FragmentUsageBinding? = null
    private val binding get() = _binding!!

    // DataStore manager
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUsageBinding.bind(view)

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🔄 DataStore'dan usage verisini dinle ve UI'ye bağla
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.usageAnalyticsFlow.collectLatest { usage ->
                bindUsageToUi(usage)
            }
        }
    }

    private fun bindUsageToUi(usage: UserPreferencesManager.UsageAnalyticsUi) {
        // 📊 Son 7 gün (otomasyon + uyarılar)
        binding.tvTotalSessionsValue.text = usage.weeklyAutomationCount.toString()
        binding.tvTotalTimeValue.text = usage.weeklyAlertCount.toString()

        // 📅 Bugün (otomasyon + manuel)
        binding.tvTodaySessionsValue.text = usage.todayAutomationCount.toString()
        binding.tvTodayTimeValue.text = usage.todayManualActionCount.toString()

        // 🕒 Son olay
        binding.tvLastOpenValue.text = formatLastEventTime(usage.lastEventTimeMillis)
        binding.tvMostUsedDeviceValue.text =
            usage.lastEventDescription.ifBlank {
                getString(R.string.usage_last_event_none) // "Henüz kayıtlı olay yok" vb.
            }
    }

    private fun formatLastEventTime(timeMillis: Long): String {
        if (timeMillis <= 0L) {
            return getString(R.string.usage_last_open_never) // "No events yet" gibi bir string
        }
        val date = Date(timeMillis)
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return sdf.format(date)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}