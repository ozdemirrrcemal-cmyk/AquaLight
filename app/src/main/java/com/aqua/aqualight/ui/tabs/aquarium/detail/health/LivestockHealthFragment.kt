package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.app.Activity
import android.net.Uri
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.BaselineChange
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentLivestockHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.yalantis.ucrop.UCrop
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailFragment
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.maintenance.TankActivityUiState
import java.util.Calendar
import kotlinx.coroutines.launch

/** Every displayed livestock entry comes from the selected tank, never from the full catalog. */
class LivestockHealthFragment : Fragment(R.layout.fragment_livestock_health) {
    internal val args: LivestockHealthFragmentArgs by navArgs()
    internal val tanks: AquariumTankViewModel by activityViewModels()
    internal val maintenance: MaintenanceViewModel by activityViewModels()
    internal val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(requireContext().applicationContext,
            AppMediaScope.TANK, "${args.tankId}_health",
            container.authenticatedOwnerIdentity.requireOwnerUid(), MediaCropSpec.TANK,
            container.imageMediaProcessor)
    }
    internal val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == HEALTH_CAPTURE_ACTION) openCamera()
    }
    internal val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && healthBinding != null) viewLifecycleOwner.lifecycleScope.launch { preparePhoto(uri) }
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        lifecycleScope.launch {
            val uri = mediaFlow.currentCameraUri()
            if (success && uri != null && healthBinding != null) preparePhoto(uri)
            else mediaFlow.cancelCamera()
        }
    }
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch {
            if (healthBinding == null) { mediaFlow.cancelCrop(); return@launch }
            val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.let(UCrop::getOutput)
                else null
            val accepted = uri?.let { mediaFlow.acceptCrop(it) }
            if (accepted == null) {
                mediaFlow.cancelCrop()
                showError(R.string.aquarium_photo_crop_failed)
            } else {
                photoUri = accepted.toString()
                tank?.let(::render)
            }
        }
    }
    internal var healthBinding: FragmentLivestockHealthBinding? = null
    internal val binding get() = requireNotNull(healthBinding)
    internal lateinit var ui: LivestockHealthUi
    internal var tank: AquariumTankSnapshot? = null
    internal var tankActivity: TankActivityUiState = TankActivityUiState()
    internal var selectedLivestockId = 0L
    internal val selectedSymptoms = linkedSetOf<LivestockHealthSymptom>()
    internal var affectedCount = 1
    internal var startedAtMillis = 0L
    internal var trend = LivestockHealthTrend.SAME
    internal var baseline: BaselineChange? = null
    internal var note = ""
    internal var photoUri: String? = null
    internal var showAllObservations = false
    internal var saving = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        healthBinding = FragmentLivestockHealthBinding.bind(view)
        ui = LivestockHealthUi(requireContext())
        if (args.page == PAGE_FORM) mediaFlow.initializeSelection(null)
        savedInstanceState?.let { state ->
            selectedLivestockId = state.getLong(STATE_LIVESTOCK)
            affectedCount = state.getInt(STATE_COUNT, 1)
            startedAtMillis = state.getLong(STATE_ONSET)
            note = state.getString(STATE_NOTE).orEmpty()
            photoUri = state.getString(STATE_PHOTO)
            showAllObservations = state.getBoolean(STATE_SHOW_ALL)
            trend = LivestockHealthTrend.fromCode(
                state.getString(STATE_TREND) ?: LivestockHealthTrend.SAME.code
            )
            baseline = state.getString(STATE_BASELINE)?.let(BaselineChange::fromCode)
            state.getStringArrayList(STATE_SYMPTOMS)?.forEach { code ->
                selectedSymptoms += LivestockHealthSymptom.fromCode(code)
            }
        }
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(
                    when (args.page) {
                        PAGE_FORM -> R.string.livestock_health_form_title
                        PAGE_ASSESSMENT -> R.string.livestock_health_assessment_title
                        PAGE_DETAIL -> R.string.livestock_health_detail_title
                        else -> R.string.livestock_health_home_title
                    }
                ),
                onBackClick = { findNavController().popBackStack() },
                actions = if (args.page == PAGE_FORM || args.page == PAGE_DETAIL) emptyList()
                else listOf(AquaHeaderAction(R.drawable.ic_info,
                    getString(R.string.livestock_health_home_info_title)) { showHealthInfo() })
            )
        )
        registerHealthResultListeners()
        observeHealthState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_LIVESTOCK, selectedLivestockId)
        outState.putInt(STATE_COUNT, affectedCount)
        outState.putLong(STATE_ONSET, startedAtMillis)
        outState.putString(STATE_TREND, trend.code)
        outState.putString(STATE_BASELINE, baseline?.code)
        outState.putString(STATE_NOTE, note)
        outState.putString(STATE_PHOTO, photoUri)
        outState.putBoolean(STATE_SHOW_ALL, showAllObservations)
        outState.putStringArrayList(STATE_SYMPTOMS, ArrayList(selectedSymptoms.map { it.code }))
        super.onSaveInstanceState(outState)
    }

    internal fun render(current: AquariumTankSnapshot) {
        val content = binding.healthContent
        content.removeAllViews()
        when (args.page) {
            PAGE_FORM -> renderForm(current, content)
            PAGE_ASSESSMENT -> renderAssessment(current, content)
            PAGE_DETAIL -> renderDetail(current, content)
            else -> renderHome(current, content)
        }
    }

    internal fun saveObservation() {
        val current = tank
        val item = current?.livestock?.firstOrNull { it.id == selectedLivestockId }
        if (current == null || item == null || !isValidObservation(item)) return
        val now = System.currentTimeMillis()
        val onset = startedAtMillis.takeIf { it in 1..now } ?: now
        val observation = LivestockHealthObservation(
            id = AquariumIdGenerator.newLong(current.healthObservations.mapTo(mutableSetOf()) { it.id }),
            livestockId = item.id, livestockName = item.name, livestockCategory = item.category,
            catalogEntryId = item.catalogEntryId, affectedCount = affectedCount,
            observedAtMillis = now, startedAtMillis = onset,
            symptoms = selectedSymptoms.toList(), trend = trend, note = note.trim(),
            baselineChange = baseline, closedAtMillis = null, outcome = null, checks = emptyList(),
            photoUri = photoUri
        )
        saving = true
        binding.healthPrimaryAction.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = runCatching {
                tanks.addHealthObservation(args.tankId, observation)
                mediaFlow.commitSelection(deletePersistedMedia = false)
                navigate(PAGE_ASSESSMENT, observation.id)
            }
            saving = false
            outcome.onFailure { error ->
                if (healthBinding != null) {
                    binding.healthPrimaryAction.isEnabled = true
                    reportHealthError(error, R.string.livestock_health_form_save_failed)
                }
            }
        }
    }

    internal fun openCamera() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uri = mediaFlow.createCameraUri()
            if (uri == null) showError(R.string.aquarium_photo_temp_file_failed)
            else cameraLauncher.launch(uri)
        }
    }

    internal suspend fun preparePhoto(uri: Uri) {
        when (val prepared = mediaFlow.prepareCropIntent(uri,
            getString(R.string.aquarium_photo_crop_title))) {
            is MediaCropPreparationResult.Ready -> cropLauncher.launch(prepared.intent)
            else -> showError(R.string.aquarium_photo_crop_failed)
        }
    }

    internal fun navigate(page: String, observationId: Long = 0L) {
        findNavController().navigateSafelyFrom(
            R.id.livestockHealthFragment,
            LivestockHealthFragmentDirections.actionLivestockHealthFragmentSelf(
                args.tankId, page, observationId)
        )
    }

    override fun onDestroyView() {
        healthBinding = null
        super.onDestroyView()
    }

    private companion object {
        const val PAGE_FORM = "form"
        const val PAGE_ASSESSMENT = "assessment"
        const val PAGE_DETAIL = "detail"
        const val STATE_LIVESTOCK = "selectedLivestock"
        const val STATE_COUNT = "affectedCount"
        const val STATE_ONSET = "onset"
        const val STATE_TREND = "trend"
        const val STATE_BASELINE = "baseline"
        const val STATE_NOTE = "note"
        const val STATE_SYMPTOMS = "symptoms"
        const val STATE_PHOTO = "photoUri"
        const val STATE_SHOW_ALL = "showAllObservations"
    }
}
