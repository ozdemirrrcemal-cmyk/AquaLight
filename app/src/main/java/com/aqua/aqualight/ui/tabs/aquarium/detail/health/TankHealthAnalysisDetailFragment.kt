package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisDetailBinding
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisDetailMeasurementBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogType

class TankHealthAnalysisDetailFragment :
    Fragment(R.layout.fragment_tank_health_analysis_detail) {

    private val args: TankHealthAnalysisDetailFragmentArgs by navArgs()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by viewModels()

    private var _binding: FragmentTankHealthAnalysisDetailBinding? = null
    private val binding get() = _binding!!
    private var currentRecord: WaterAnalysisSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisDetailFragment requires a positive tankId."
        }
        require(args.analysisId > 0L) {
            "TankHealthAnalysisDetailFragment requires a positive analysisId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisDetailBinding.bind(view)

        setupHeader()
        setupDeleteResult()
        setupActions()
        observeRecord()
        bindWaterAnalysisMutation(waterAnalysisViewModel.mutations, R.id.tankHealthAnalysisDetailFragment) { state ->
            binding.btnDeleteRecord.isEnabled = currentRecord != null && state == WaterAnalysisMutationState.Idle
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_detail),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun observeRecord() {
        waterAnalysisViewModel.analysisState(args.tankId, args.analysisId)
            .observe(viewLifecycleOwner) { state ->
                if (state !is WaterAnalysisLoadState.Content) {
                    renderUnavailable(if (state is WaterAnalysisLoadState.Error) {
                        R.string.water_analysis_read_failed
                    } else R.string.water_analysis_loading)
                    return@observe
                }
                val record = state.value
                if (record == null || record.tankId != args.tankId) {
                    renderUnavailable(R.string.water_analysis_record_missing)
                    return@observe
                }
                currentRecord = record
                renderRecord(record)
            }
    }

    private fun renderUnavailable(@androidx.annotation.StringRes message: Int) {
        currentRecord = null
        binding.btnDeleteRecord.isEnabled = false
        binding.tvRecordDateTime.setText(message)
        binding.tvRecordTemperature.text = null
        binding.tvAssessmentSummary.text = null
        binding.measurementContainer.removeAllViews()
    }

    private fun renderRecord(record: WaterAnalysisSnapshot) {
        binding.btnDeleteRecord.isEnabled =
            waterAnalysisViewModel.mutations.state.value == WaterAnalysisMutationState.Idle
        val context = requireContext()
        binding.tvAssessmentSummary.text = WaterAssessmentPresentation.detail(context, record)
        binding.tvRecordDateTime.text = getString(
            R.string.tank_health_analysis_date_time_format,
            LocaleFormatter.formatDate(context, record.measuredAtMillis),
            LocaleFormatter.formatTime(context, record.measuredAtMillis)
        )
        binding.tvRecordTemperature.text =
            WaterAnalysisPresentation.temperatureValueText(
                context,
                record.temperatureCelsius
            )

        binding.measurementContainer.removeAllViews()
        record.measurements.forEach { measurement ->
            val item = ItemTankHealthAnalysisDetailMeasurementBinding.inflate(
                layoutInflater,
                binding.measurementContainer,
                false
            )
            item.tvParameterName.setText(
                WaterAnalysisPresentation.parameterNameRes(measurement.parameter)
            )
            item.tvMeasurementValue.text =
                WaterAnalysisPresentation.measurementValueText(context, measurement)
            item.tvMeasurementMeta.text =
                WaterAnalysisPresentation.measurementMetaText(context, measurement)
            binding.measurementContainer.addView(item.root)
        }
    }

    private fun setupDeleteResult() {
        childFragmentManager.setFragmentResultListener(
            DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(ConfirmDialogFragment.RESULT_KEY) ==
                ConfirmDialogFragment.RESULT_CONFIRM &&
                result.getString(ConfirmDialogFragment.RESULT_ACTION_ID) == DELETE_ACTION_ID
            ) {
                deleteCurrentRecord()
            }
        }
    }

    private fun deleteCurrentRecord() {
        if (currentRecord == null) return
        waterAnalysisViewModel.deleteAnalysis(args.analysisId)
    }

    private fun setupActions() {
        binding.btnDetailBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnDeleteRecord.setOnClickListener {
            val record = currentRecord ?: return@setOnClickListener
            ConfirmDialogFragment.show(
                fragmentManager = childFragmentManager,
                request = ConfirmDialogFragment.Request(
                    title = getString(R.string.tank_health_analysis_delete_title),
                    message = getString(R.string.water_analysis_delete_dated,
                        LocaleFormatter.formatDate(requireContext(), record.measuredAtMillis),
                        LocaleFormatter.formatTime(requireContext(), record.measuredAtMillis)),
                    confirmText = getString(R.string.tank_health_analysis_delete_confirm),
                    cancelText = getString(R.string.tank_health_analysis_delete_cancel),
                    presentation = ConfirmDialogFragment.Presentation(
                        type = DialogType.ERROR,
                        destructive = true
                    ),
                    resultTarget = ConfirmDialogFragment.ResultTarget(
                        requestKey = DELETE_REQUEST_KEY,
                        actionId = DELETE_ACTION_ID
                    )
                )
            )
        }
    }

    override fun onDestroyView() {
        currentRecord = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DELETE_REQUEST_KEY = "tank_health_analysis_delete_request"
        const val DELETE_ACTION_ID = "delete_analysis_record"
    }
}
