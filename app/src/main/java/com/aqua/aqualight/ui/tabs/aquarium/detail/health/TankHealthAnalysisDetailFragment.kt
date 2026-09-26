package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TankHealthAnalysisDetailFragment :
    Fragment(R.layout.fragment_tank_health_analysis_detail) {

    private val args: TankHealthAnalysisDetailFragmentArgs by navArgs()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by activityViewModels()

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
        waterAnalysisViewModel.analysis(args.analysisId)
            .observe(viewLifecycleOwner) { record ->
                if (record == null || record.tankId != args.tankId) {
                    currentRecord = null
                    if (findNavController().currentDestination?.id ==
                        R.id.tankHealthAnalysisDetailFragment
                    ) {
                        findNavController().navigateUp()
                    }
                    return@observe
                }
                currentRecord = record
                renderRecord(record)
            }
    }

    private fun renderRecord(record: WaterAnalysisSnapshot) {
        val context = requireContext()
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
                ConfirmDialogFragment.RESULT_CONFIRM
            ) {
                deleteCurrentRecord()
            }
        }
    }

    private fun deleteCurrentRecord() {
        if (currentRecord == null) return
        binding.btnDeleteRecord.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                waterAnalysisViewModel.deleteAnalysis(args.analysisId)
                if (_binding != null) {
                    findNavController().navigateUp()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _binding?.btnDeleteRecord?.isEnabled = true
                if (_binding != null) {
                    (activity as? BaseActivity)?.showSnackBar(
                        message = getString(R.string.tank_health_analysis_delete_failed),
                        type = BaseActivity.SnackType.ERROR
                    )
                }
            }
        }
    }

    private fun setupActions() {
        binding.btnDetailBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnDeleteRecord.setOnClickListener {
            if (currentRecord == null) return@setOnClickListener
            ConfirmDialogFragment.show(
                fragmentManager = childFragmentManager,
                request = ConfirmDialogFragment.Request(
                    title = getString(R.string.tank_health_analysis_delete_title),
                    message = getString(R.string.tank_health_analysis_delete_message),
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
