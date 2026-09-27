package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAction
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentHealthObservationDetailBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.utils.DialogType

class HealthObservationDetailFragment : Fragment(R.layout.fragment_health_observation_detail) {
    private val args: HealthObservationDetailFragmentArgs by navArgs()
    private val model: HealthObservationViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var binding: FragmentHealthObservationDetailBinding? = null
    private var record: HealthObservationSnapshot? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = FragmentHealthObservationDetailBinding.bind(view).also { binding = it }
        require(args.observationId == model.observationId && args.tankId == model.tankId)
        ui.appHeader.setupAquaHeader(this, AquaHeaderConfig(titleOverride = getString(R.string.health_detail),
            onBackClick = { findNavController().navigateUp() }))
        actions(ui)
        model.record.observe(viewLifecycleOwner) { state ->
            record = (state as? HealthLoadState.Content)?.value
            ui.status.isVisible = record == null
            ui.status.setText(when (state) {
                HealthLoadState.Loading -> R.string.health_loading
                HealthLoadState.Failed -> R.string.health_failed
                is HealthLoadState.Content -> R.string.health_missing
            })
            listOf(ui.date, ui.subject, ui.findings, ui.evidence, ui.assessment, ui.notes, ui.historical,
                ui.safety, ui.follow, ui.delete).forEach { it.isVisible = record != null }
            ui.algae.isVisible = record?.assessment?.actions?.contains(ObservationAction.OPEN_ALGAE_CONTROL) == true
            ui.photo.isVisible = !record?.input?.notes?.photoUri.isNullOrBlank()
            record?.let { render(ui, it) }
        }
        bindHealthMutation(model, R.id.healthObservationDetailFragment) { state ->
            ui.delete.isEnabled = record != null && state == HealthMutationState.Idle
            ui.follow.isEnabled = record != null && state == HealthMutationState.Idle
        }
    }

    private fun render(ui: FragmentHealthObservationDetailBinding, row: HealthObservationSnapshot) {
        val context = requireContext()
        ui.date.text = listOf(LocaleFormatter.formatDateTime(context, row.input.identity.observedAtMillis),
            getString(HealthObservationLabels.label(row.input.notes.followUp.phase))).joinToString("\n")
        ui.subject.text = row.subject?.displayName ?: getString(R.string.tank_health_tab_algae_control)
        ui.findings.text = HealthObservationPresentation.findings(context, row.input.observation)
        ui.evidence.text = HealthObservationPresentation.evidence(context, row)
        ui.assessment.text = HealthObservationPresentation.assessment(context, row)
        ui.notes.text = row.input.notes.text
        ui.photo.bindRecordPhoto(row.input.notes.photoUri)
        ui.delete.isEnabled = model.mutations.state.value == HealthMutationState.Idle
        ui.follow.isEnabled = ui.delete.isEnabled
    }

    private fun actions(ui: FragmentHealthObservationDetailBinding) {
        ui.follow.setOnClickListener {
            if (record != null) findNavController().navigateSafelyFrom(R.id.healthObservationDetailFragment,
                HealthObservationDetailFragmentDirections
                    .actionHealthObservationDetailFragmentToHealthObservationFormFragment(
                    tankId = args.tankId, healthKind = args.healthKind, previousObservationId = args.observationId))
        }
        ui.algae.setOnClickListener {
            findNavController().navigateSafelyFrom(R.id.healthObservationDetailFragment,
                HealthObservationDetailFragmentDirections.actionHealthObservationDetailFragmentToAlgaeControlFragment(
                    tankId = args.tankId))
        }
        ui.delete.setOnClickListener { record?.let(::confirmDelete) }
        childFragmentManager.setFragmentResultListener(DELETE, viewLifecycleOwner) { _, result ->
            if (result.getString(ConfirmDialogFragment.RESULT_KEY) == ConfirmDialogFragment.RESULT_CONFIRM &&
                result.getString(ConfirmDialogFragment.RESULT_ACTION_ID) == DELETE && record != null) model.delete()
        }
    }

    private fun confirmDelete(row: HealthObservationSnapshot) {
        ConfirmDialogFragment.show(childFragmentManager, ConfirmDialogFragment.Request(
            title = getString(R.string.health_delete),
            message = getString(R.string.health_delete_message,
                LocaleFormatter.formatDateTime(requireContext(), row.input.identity.observedAtMillis)),
            confirmText = getString(R.string.health_delete), cancelText = getString(R.string.health_cancel),
            presentation = ConfirmDialogFragment.Presentation(type = DialogType.ERROR, destructive = true),
            resultTarget = ConfirmDialogFragment.ResultTarget(requestKey = DELETE, actionId = DELETE)
        ))
    }

    override fun onDestroyView() {
        binding = null
        record = null
        super.onDestroyView()
    }

    private companion object { const val DELETE = "health_observation_delete" }
}
