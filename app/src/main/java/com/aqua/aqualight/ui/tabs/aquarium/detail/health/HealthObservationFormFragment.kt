package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPreparation
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentHealthObservationFormBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.TankRecordPhotoFragment
import com.aqua.aqualight.ui.common.media.bindRecordPhoto

class HealthObservationFormFragment : TankRecordPhotoFragment(
    R.layout.fragment_health_observation_form, AppMediaScope.HEALTH,
    R.string.health_photo, R.string.health_photo_crop
) {
    private val args: HealthObservationFormFragmentArgs by navArgs()
    private val model: HealthObservationViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var binding: FragmentHealthObservationFormBinding? = null
    private var fields: HealthObservationFormFields? = null
    private var preparation: HealthObservationPreparation? = null
    private var parentReady = false
    private var ready = false
    override val photoTankId get() = args.tankId
    override val hasPhotoView get() = binding != null
    override val photoActionsBlocked get() = model.mutations.state.value != HealthMutationState.Idle

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = FragmentHealthObservationFormBinding.bind(view).also { binding = it }
        require(args.tankId == model.tankId && args.healthKind == model.kind.name)
        parentReady = model.previousId == 0L
        ui.appHeader.setupAquaHeader(this, AquaHeaderConfig(titleOverride = getString(R.string.health_new),
            onBackClick = { findNavController().navigateUp() }))
        fields = HealthObservationFormFields(ui, model).also { it.bind() }
        HealthObservationTime(this, ui, model).bind()
        setupPhoto(ui)
        ui.save.setOnClickListener { save() }
        ui.retry.setOnClickListener { model.retryPreparation() }
        observePreparation(ui)
        bindHealthMutation(model, R.id.healthObservationFormFragment,
            saved = { mediaFlow.commitSelection(deletePersistedMedia = false) }) { state ->
            ui.root.lockHealthInputs(state == HealthMutationState.Running)
            ui.save.isEnabled = ready && state == HealthMutationState.Idle
            ui.photoAction.isEnabled = state == HealthMutationState.Idle
            ui.date.isEnabled = state == HealthMutationState.Idle
            ui.time.isEnabled = state == HealthMutationState.Idle
            ui.subject.isEnabled = state == HealthMutationState.Idle && model.previousId == 0L
        }
    }

    private fun setupPhoto(ui: FragmentHealthObservationFormBinding) {
        mediaFlow.initializeSelection(null)
        setupPhotoSourceResultListener()
        ui.photo.bindRecordPhoto(model.draft.getString("photo"))
        ui.photoAction.setOnClickListener {
            val owner = requireContext().requireAppContainer().authenticatedOwnerIdentity.requireOwnerUid()
            showRecordPhotoSource(0L, owner)
        }
    }

    private fun observePreparation(ui: FragmentHealthObservationFormBinding) {
        model.preparation.observe(viewLifecycleOwner) { state ->
            preparation = (state as? HealthLoadState.Content)?.value
            ui.retry.isVisible = state == HealthLoadState.Failed
            if (preparation != null) renderReady(ui)
            else {
                ready = false
                ui.status.isVisible = true
                ui.save.isEnabled = false
                ui.status.setText(if (state == HealthLoadState.Failed)
                    R.string.health_failed else R.string.health_loading)
            }
        }
        if (model.previousId > 0L) model.previous.observe(viewLifecycleOwner) { state ->
            val record = (state as? HealthLoadState.Content)?.value
            parentReady = record != null
            record?.let {
                model.draft = model.draft.apply { putLong("subject", it.input.observation.subjectId ?: 0L) }
                ui.parent.text = getString(R.string.health_follow_parent,
                    LocaleFormatter.formatDateTime(requireContext(), it.input.identity.observedAtMillis))
            }
            if (record == null) ui.parent.setText(if (state == HealthLoadState.Failed) R.string.health_failed
                else if (state == HealthLoadState.Loading) R.string.health_loading else R.string.health_missing)
            renderReady(ui)
        }
    }

    private fun renderReady(ui: FragmentHealthObservationFormBinding) {
        val available = preparation?.let { fields?.preparation(it) } == true
        ready = available && parentReady
        ui.save.isEnabled = ready && model.mutations.state.value == HealthMutationState.Idle
        ui.status.isVisible = !ready
        ui.status.setText(if (!available) R.string.health_no_subject else R.string.health_missing)
    }

    private fun save() {
        if (!ready || photoTarget.isInProgress || photoActionsBlocked) return
        val input = runCatching { model.formInput(requireContext()) }.getOrElse {
            showPhotoSnackBar(getString(R.string.health_invalid), BaseActivity.SnackType.ERROR)
            return
        }
        model.save(input)
    }

    override suspend fun onPhotoSelected(photoUri: String?) {
        model.draft = model.draft.apply { putString("photo", photoUri) }
        binding?.photo?.bindRecordPhoto(photoUri)
    }

    override fun onDestroyView() {
        binding = null
        fields = null
        preparation = null
        ready = false
        super.onDestroyView()
    }
}

private fun View.lockHealthInputs(locked: Boolean) {
    if (this is EditText || this is CompoundButton) isEnabled = !locked
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).lockHealthInputs(locked)
}
