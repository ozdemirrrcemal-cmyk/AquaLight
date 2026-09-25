package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisHistoryBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankHealthAnalysisHistoryFragment :
    Fragment(R.layout.fragment_tank_health_analysis_history) {

    private val args: TankHealthAnalysisHistoryFragmentArgs by navArgs()

    private var _binding: FragmentTankHealthAnalysisHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: TankHealthAnalysisHistoryAdapter

    private val allRecords: List<TankHealthAnalysisHistoryRecord> by lazy {
        listOf(
            record(
                R.string.tank_health_analysis_record_date_1,
                R.string.tank_health_analysis_record_time_1,
                R.string.tank_health_value_ph,
                R.string.tank_health_value_no3,
                R.string.tank_health_value_temperature,
                R.string.tank_health_status_moderate
            ),
            record(
                R.string.tank_health_analysis_record_date_2,
                R.string.tank_health_analysis_record_time_2,
                R.string.tank_health_analysis_value_ph_70,
                R.string.tank_health_analysis_value_no3_10,
                R.string.tank_health_analysis_value_temp_24,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_3,
                R.string.tank_health_analysis_record_time_3,
                R.string.tank_health_analysis_value_ph_66,
                R.string.tank_health_analysis_value_no3_22,
                R.string.tank_health_analysis_value_temp_26,
                R.string.tank_health_status_moderate
            ),
            record(
                R.string.tank_health_analysis_record_date_4,
                R.string.tank_health_analysis_record_time_4,
                R.string.tank_health_analysis_value_ph_72,
                R.string.tank_health_analysis_value_no3_8,
                R.string.tank_health_analysis_value_temp_24,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_5,
                R.string.tank_health_analysis_record_time_5,
                R.string.tank_health_analysis_value_ph_69,
                R.string.tank_health_analysis_value_no3_16,
                R.string.tank_health_value_temperature,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_6,
                R.string.tank_health_analysis_record_time_6,
                R.string.tank_health_value_ph,
                R.string.tank_health_analysis_value_no3_10,
                R.string.tank_health_value_temperature,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_7,
                R.string.tank_health_analysis_record_time_7,
                R.string.tank_health_analysis_value_ph_70,
                R.string.tank_health_analysis_value_no3_22,
                R.string.tank_health_analysis_value_temp_26,
                R.string.tank_health_status_moderate
            ),
            record(
                R.string.tank_health_analysis_record_date_8,
                R.string.tank_health_analysis_record_time_8,
                R.string.tank_health_analysis_value_ph_66,
                R.string.tank_health_analysis_value_no3_8,
                R.string.tank_health_analysis_value_temp_24,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_9,
                R.string.tank_health_analysis_record_time_9,
                R.string.tank_health_analysis_value_ph_72,
                R.string.tank_health_analysis_value_no3_16,
                R.string.tank_health_value_temperature,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_10,
                R.string.tank_health_analysis_record_time_10,
                R.string.tank_health_analysis_value_ph_69,
                R.string.tank_health_analysis_value_no3_10,
                R.string.tank_health_analysis_value_temp_24,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_11,
                R.string.tank_health_analysis_record_time_11,
                R.string.tank_health_value_ph,
                R.string.tank_health_analysis_value_no3_8,
                R.string.tank_health_value_temperature,
                R.string.tank_health_status_normal
            ),
            record(
                R.string.tank_health_analysis_record_date_12,
                R.string.tank_health_analysis_record_time_12,
                R.string.tank_health_analysis_value_ph_70,
                R.string.tank_health_analysis_value_no3_16,
                R.string.tank_health_analysis_value_temp_26,
                R.string.tank_health_status_normal
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisHistoryFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisHistoryBinding.bind(view)

        setupHeader()
        setupHistoryList()
        setupNewAnalysisAction()
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

    private fun setupNewAnalysisAction() {
        binding.btnNewAnalysis.setOnClickListener {
            findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.tankHealthAnalysisHistoryFragment,
                directions = TankHealthAnalysisHistoryFragmentDirections
                    .actionTankHealthAnalysisHistoryFragmentToTankHealthAnalysisAddFragment(
                        args.tankId
                    )
            )
        }
    }

    private fun openRecordDetail() {
        findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankHealthAnalysisHistoryFragment,
            directions = TankHealthAnalysisHistoryFragmentDirections
                .actionTankHealthAnalysisHistoryFragmentToTankHealthAnalysisDetailFragment(
                    args.tankId
                )
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
        no3StatusRes: Int
    ): TankHealthAnalysisHistoryRecord {
        return TankHealthAnalysisHistoryRecord(
            dateRes = dateRes,
            timeRes = timeRes,
            phValueRes = phValueRes,
            no3ValueRes = no3ValueRes,
            temperatureValueRes = temperatureValueRes,
            no3StatusRes = no3StatusRes
        )
    }

}
