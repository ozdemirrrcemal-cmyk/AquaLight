package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisHistoryBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.google.android.material.button.MaterialButton

class TankHealthAnalysisHistoryFragment :
    Fragment(R.layout.fragment_tank_health_analysis_history) {

    private var _binding: FragmentTankHealthAnalysisHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: TankHealthAnalysisHistoryAdapter
    private var selectedFilter: HistoryFilter = HistoryFilter.ALL

    private val tankId: Long
        get() = requireArguments().getLong(ARG_TANK_ID)

    private val allRecords: List<TankHealthAnalysisHistoryRecord> by lazy {
        listOf(
            record(
                R.string.tank_health_analysis_record_date_1,
                R.string.tank_health_analysis_record_time_1,
                R.string.tank_health_value_ph,
                R.string.tank_health_value_no3,
                R.string.tank_health_value_temperature,
                R.string.tank_health_status_moderate,
                TemperatureSource.SENSOR
            ),
            record(
                R.string.tank_health_analysis_record_date_2,
                R.string.tank_health_analysis_record_time_2,
                R.string.tank_health_analysis_value_ph_70,
                R.string.tank_health_analysis_value_no3_10,
                R.string.tank_health_analysis_value_temp_24,
                R.string.tank_health_status_normal,
                TemperatureSource.MANUAL
            ),
            record(
                R.string.tank_health_analysis_record_date_3,
                R.string.tank_health_analysis_record_time_3,
                R.string.tank_health_analysis_value_ph_66,
                R.string.tank_health_analysis_value_no3_22,
                R.string.tank_health_analysis_value_temp_26,
                R.string.tank_health_status_moderate,
                TemperatureSource.SENSOR
            ),
            record(
                R.string.tank_health_analysis_record_date_4,
                R.string.tank_health_analysis_record_time_4,
                R.string.tank_health_analysis_value_ph_72,
                R.string.tank_health_analysis_value_no3_8,
                R.string.tank_health_analysis_value_temp_24,
                R.string.tank_health_status_normal,
                TemperatureSource.MANUAL
            ),
            record(
                R.string.tank_health_analysis_record_date_5,
                R.string.tank_health_analysis_record_time_5,
                R.string.tank_health_analysis_value_ph_69,
                R.string.tank_health_analysis_value_no3_16,
                R.string.tank_health_value_temperature,
                R.string.tank_health_status_normal,
                TemperatureSource.SENSOR
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(tankId > 0L) {
            "TankHealthAnalysisHistoryFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisHistoryBinding.bind(view)

        setupHeader()
        setupHistoryList()
        setupFilters()
        setupNewAnalysisAction()
        renderFilter()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_history),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupHistoryList() {
        historyAdapter = TankHealthAnalysisHistoryAdapter(
            items = allRecords,
            onRecordClick = {
                openRecordDetail()
            }
        )
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = historyAdapter
        binding.historyList.itemAnimator = null
    }

    private fun setupFilters() {
        binding.btnFilterAll.setOnClickListener {
            selectedFilter = HistoryFilter.ALL
            renderFilter()
        }
        binding.btnFilterSensor.setOnClickListener {
            selectedFilter = HistoryFilter.SENSOR
            renderFilter()
        }
        binding.btnFilterManual.setOnClickListener {
            selectedFilter = HistoryFilter.MANUAL
            renderFilter()
        }
    }

    private fun setupNewAnalysisAction() {
        binding.btnNewAnalysis.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.tankHealthAnalysisHistoryFragment) {
                return@setOnClickListener
            }
            navController.navigate(
                R.id.action_tankHealthAnalysisHistoryFragment_to_tankHealthAnalysisAddFragment,
                bundleOf(ARG_TANK_ID to tankId)
            )
        }
    }

    private fun renderFilter() {
        styleFilterButton(binding.btnFilterAll, selectedFilter == HistoryFilter.ALL)
        styleFilterButton(binding.btnFilterSensor, selectedFilter == HistoryFilter.SENSOR)
        styleFilterButton(binding.btnFilterManual, selectedFilter == HistoryFilter.MANUAL)

        historyAdapter.submitList(
            when (selectedFilter) {
                HistoryFilter.ALL -> allRecords
                HistoryFilter.SENSOR -> allRecords.filter {
                    it.temperatureSource == TemperatureSource.SENSOR
                }
                HistoryFilter.MANUAL -> allRecords.filter {
                    it.temperatureSource == TemperatureSource.MANUAL
                }
            }
        )
    }

    private fun styleFilterButton(button: MaterialButton, selected: Boolean) {
        val context = requireContext()
        val primary = ContextCompat.getColor(context, R.color.aqua_accent_primary)
        val transparent = ContextCompat.getColor(context, R.color.aqua_color_transparent)
        val outline = ContextCompat.getColor(context, R.color.aqua_card_metric_outline)
        val selectedText = ContextCompat.getColor(context, R.color.aqua_content_on_dark)
        val normalText = ContextCompat.getColor(context, R.color.aqua_card_text_primary)

        button.backgroundTintList = ColorStateList.valueOf(if (selected) primary else transparent)
        button.strokeColor = ColorStateList.valueOf(if (selected) primary else outline)
        button.strokeWidth = resources.getDimensionPixelSize(R.dimen.aqua_size_1)
        button.setTextColor(if (selected) selectedText else normalText)
    }

    private fun openRecordDetail() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.tankHealthAnalysisHistoryFragment) {
            return
        }
        navController.navigate(
            R.id.action_tankHealthAnalysisHistoryFragment_to_tankHealthAnalysisDetailFragment,
            bundleOf(ARG_TANK_ID to tankId)
        )
    }

    override fun onDestroyView() {
        binding.historyList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun record(
        dateRes: Int,
        timeRes: Int,
        phValueRes: Int,
        no3ValueRes: Int,
        temperatureValueRes: Int,
        no3StatusRes: Int,
        temperatureSource: TemperatureSource
    ): TankHealthAnalysisHistoryRecord {
        return TankHealthAnalysisHistoryRecord(
            dateRes = dateRes,
            timeRes = timeRes,
            phValueRes = phValueRes,
            no3ValueRes = no3ValueRes,
            temperatureValueRes = temperatureValueRes,
            no3StatusRes = no3StatusRes,
            temperatureSource = temperatureSource
        )
    }

    private enum class HistoryFilter {
        ALL,
        SENSOR,
        MANUAL
    }

    private companion object {
        const val ARG_TANK_ID = "tankId"
    }
}
