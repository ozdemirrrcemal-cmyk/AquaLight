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
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
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

private const val SYMPTOMS_PER_ROW = 3
private const val MAX_SELECTED_SYMPTOMS = 5
private const val NOTE_MAX_LINES = 4

/** Every displayed livestock entry comes from the selected tank, never from the full catalog. */
class LivestockHealthFragment : Fragment(R.layout.fragment_livestock_health) {
    private val args: LivestockHealthFragmentArgs by navArgs()
    private val tanks: AquariumTankViewModel by activityViewModels()
    private val maintenance: MaintenanceViewModel by activityViewModels()
    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(requireContext().applicationContext,
            AppMediaScope.TANK, "${args.tankId}_health",
            container.authenticatedOwnerIdentity.requireOwnerUid(), MediaCropSpec.TANK,
            container.imageMediaProcessor)
    }
    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE) openCamera()
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && _binding != null) viewLifecycleOwner.lifecycleScope.launch { preparePhoto(uri) }
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        lifecycleScope.launch {
            val uri = mediaFlow.currentCameraUri()
            if (success && uri != null && _binding != null) preparePhoto(uri)
            else mediaFlow.cancelCamera()
        }
    }
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch {
            if (_binding == null) { mediaFlow.cancelCrop(); return@launch }
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
    private var _binding: FragmentLivestockHealthBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var ui: LivestockHealthUi
    private var tank: AquariumTankSnapshot? = null
    private var tankActivity: TankActivityUiState = TankActivityUiState()
    private var selectedLivestockId = 0L
    private val selectedSymptoms = linkedSetOf<LivestockHealthSymptom>()
    private var affectedCount = 1
    private var startedAtMillis = 0L
    private var trend = LivestockHealthTrend.SAME
    private var baseline: BaselineChange? = null
    private var note = ""
    private var photoUri: String? = null
    private var saving = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthBinding.bind(view)
        ui = LivestockHealthUi(requireContext())
        if (args.page == PAGE_FORM) mediaFlow.initializeSelection(null)
        savedInstanceState?.let { state ->
            selectedLivestockId = state.getLong(STATE_LIVESTOCK)
            affectedCount = state.getInt(STATE_COUNT, 1)
            startedAtMillis = state.getLong(STATE_ONSET)
            note = state.getString(STATE_NOTE).orEmpty()
            photoUri = state.getString(STATE_PHOTO)
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
                onBackClick = { findNavController().popBackStack() }
            )
        )
        childFragmentManager.setFragmentResultListener(DATE_REQUEST, viewLifecycleOwner) { _, result ->
            if (result.getString(AppDatePickerDialogFragment.RESULT_KEY) ==
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) {
                startedAtMillis = result.getLong(AppDatePickerDialogFragment.RESULT_MILLIS)
                tank?.let(::render)
            }
        }
        childFragmentManager.setFragmentResultListener(CLOSE_REQUEST, viewLifecycleOwner) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) closeRecord(result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)?.toLongOrNull())
        }
        childFragmentManager.setFragmentResultListener(PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner) { _, result ->
            when (result.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> permissionCoordinator.runWhenGranted(
                    AppCapability.CAMERA_PHOTO, ACTION_CAPTURE)
                PhotoSourceBottomSheet.RESULT_GALLERY -> galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                PhotoSourceBottomSheet.RESULT_REMOVE -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        mediaFlow.selectRemoval()
                        photoUri = null
                        tank?.let(::render)
                    }
                }
            }
        }
        tanks.tanks.observe(viewLifecycleOwner) { snapshots ->
            val current = snapshots.firstOrNull { it.id == args.tankId }
            if (current == null) {
                findNavController().popBackStack()
            } else {
                tank = current
                if (args.page == PAGE_FORM && selectedLivestockId == 0L) {
                    selectedLivestockId = current.livestock.firstOrNull()?.id ?: 0L
                }
                render(current)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenance.tankActivityStateFlow(args.tankId).collect { state ->
                    tankActivity = state
                    tank?.let(::render)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_LIVESTOCK, selectedLivestockId)
        outState.putInt(STATE_COUNT, affectedCount)
        outState.putLong(STATE_ONSET, startedAtMillis)
        outState.putString(STATE_TREND, trend.code)
        outState.putString(STATE_BASELINE, baseline?.code)
        outState.putString(STATE_NOTE, note)
        outState.putString(STATE_PHOTO, photoUri)
        outState.putStringArrayList(STATE_SYMPTOMS, ArrayList(selectedSymptoms.map { it.code }))
        super.onSaveInstanceState(outState)
    }

    private fun render(current: AquariumTankSnapshot) {
        val content = binding.healthContent
        content.removeAllViews()
        when (args.page) {
            PAGE_FORM -> renderForm(current, content)
            PAGE_ASSESSMENT -> renderAssessment(current, content)
            PAGE_DETAIL -> renderDetail(current, content)
            else -> renderHome(current, content)
        }
    }

    private fun renderHome(current: AquariumTankSnapshot, content: LinearLayout) {
        val noRecords = current.healthObservations.isEmpty()
        content.addView(ui.hero(noRecords, current.livestock.isNotEmpty()) { navigate(PAGE_FORM) })
        section(content, if (noRecords) R.string.livestock_health_home_followups
            else R.string.livestock_health_home_active)
        val active = current.healthObservations.filter { it.isActive }.sortedByDescending { it.observedAtMillis }
        val recent = current.healthObservations.sortedByDescending { it.observedAtMillis }
        if (active.isEmpty()) {
            if (noRecords) content.addView(ui.emptyState())
        } else {
            active.forEach { record -> observationCard(content, current, record) }
        }
        if (recent.isNotEmpty()) {
            section(content, R.string.livestock_health_home_recent)
            recent.forEach { record -> observationCard(content, current, record) }
        }
        content.addView(ui.spacer())
        content.addView(ui.tankContext(
            getString(R.string.livestock_health_home_tank_data_pending)) { openTank() })
        if (noRecords) {
            content.addView(ui.spacer(R.dimen.aqua_size_12))
            panel(content, getString(R.string.livestock_health_home_no_records_disclaimer))
        }
        binding.healthPrimaryAction.isVisible = false
        if (current.livestock.isEmpty()) {
            panel(content, getString(R.string.livestock_health_home_no_livestock))
        }
    }

    private fun observationCard(
        content: LinearLayout,
        current: AquariumTankSnapshot,
        observation: LivestockHealthObservation
    ) {
        val cardContent = ui.column()
        val existing = current.livestock.firstOrNull { it.id == observation.livestockId }
        val header = ui.row()
        val animal = existing ?: AquariumLivestock(id = observation.livestockId,
            name = observation.livestockName, category = observation.livestockCategory,
            quantity = observation.affectedCount, catalogEntryId = observation.catalogEntryId)
        header.addView(ui.speciesImage(animal,
            R.dimen.aqua_size_52, R.dimen.aqua_size_52, observation.photoUri))
        val textColumn = ui.column()
        textColumn.addView(ui.text(observation.livestockName,
            R.dimen.aqua_text_size_body_large, bold = true))
        textColumn.addView(ui.text(observation.symptoms.joinToString { ui.symptomName(it) },
            colorRes = R.color.aqua_card_text_secondary))
        textColumn.addView(ui.text(date(observation.observedAtMillis),
            colorRes = R.color.aqua_card_text_secondary))
        textColumn.layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ui.size(R.dimen.aqua_size_12)
        }
        header.addView(textColumn)
        header.addView(ui.statusBadge(getString(if (observation.isActive)
            R.string.livestock_health_home_tracking
        else if (observation.outcome == LivestockHealthTrend.RESOLVED)
            R.string.livestock_health_home_resolved
        else R.string.livestock_health_home_ended), observation.isActive))
        cardContent.addView(header)
        content.addView(ui.card(content = cardContent).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { navigate(PAGE_DETAIL, observation.id) }
        })
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }

    private fun renderForm(current: AquariumTankSnapshot, content: LinearLayout) {
        section(content, R.string.livestock_health_form_species_title)
        content.addView(ui.text(getString(R.string.livestock_health_form_species_hint),
            colorRes = R.color.aqua_card_text_secondary))
        content.addView(ui.spacer())
        current.livestock.forEach { item ->
            content.addView(ui.speciesChoice(item,
                selectedLivestockId == item.id) {
                selectedLivestockId = item.id
                affectedCount = affectedCount.coerceIn(1, item.quantity)
                selectedSymptoms.clear()
                baseline = null
                render(current)
            })
            content.addView(ui.spacer(R.dimen.aqua_size_8))
        }
        val selected = current.livestock.firstOrNull { it.id == selectedLivestockId }
        if (selected != null) renderFormDetails(current, selected, content)
        primary(R.string.livestock_health_form_save, selected != null &&
            selectedSymptoms.isNotEmpty() &&
            (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE !in selectedSymptoms || baseline != null)) {
            saveObservation()
        }
    }

    private fun renderFormDetails(
        current: AquariumTankSnapshot, selected: AquariumLivestock, content: LinearLayout
    ) {
        section(content, R.string.livestock_health_form_symptoms_title)
        content.addView(ui.text(getString(R.string.livestock_health_form_symptoms_hint),
            colorRes = R.color.aqua_card_text_secondary))
        content.addView(ui.spacer())
        symptomsFor(selected.category).chunked(SYMPTOMS_PER_ROW).forEach { group ->
            val row = ui.row()
            group.forEach { symptom ->
                row.addView(ui.symptomChoice(symptom, symptom in selectedSymptoms) {
                    if (!selectedSymptoms.remove(symptom) &&
                        selectedSymptoms.size < MAX_SELECTED_SYMPTOMS) {
                        selectedSymptoms += symptom
                    }
                    if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE !in selectedSymptoms) baseline = null
                    render(current)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = ui.size(R.dimen.aqua_size_8)
                    }
                    minimumHeight = ui.size(R.dimen.aqua_size_80)
                })
            }
            content.addView(row)
            content.addView(ui.spacer(R.dimen.aqua_size_8))
        }
        if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE in selectedSymptoms) {
            section(content, R.string.livestock_health_form_baseline_general)
            listOf(BaselineChange.YES to R.string.livestock_health_form_yes,
                BaselineChange.UNSURE to R.string.livestock_health_form_unsure).forEach { (value, label) ->
                content.addView(ui.choice(getString(label), baseline == value) {
                    baseline = value
                    render(current)
                })
                content.addView(ui.spacer(R.dimen.aqua_size_8))
            }
        }
        section(content, R.string.livestock_health_form_count_title)
        val countRow = ui.row()
        countRow.addView(ui.button(R.string.livestock_health_count_decrease) {
            affectedCount = (affectedCount - 1).coerceAtLeast(1)
            render(current)
        }.apply { text = "−"; contentDescription = getString(R.string.livestock_health_count_decrease);
            layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        countRow.addView(ui.text("$affectedCount / ${selected.quantity}").apply {
            setPadding(ui.size(R.dimen.aqua_size_16), 0, ui.size(R.dimen.aqua_size_16), 0)
        })
        countRow.addView(ui.button(R.string.livestock_health_count_increase) {
            affectedCount = (affectedCount + 1).coerceAtMost(selected.quantity)
            render(current)
        }.apply { text = "+"; contentDescription = getString(R.string.livestock_health_count_increase);
            layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        content.addView(countRow)
        section(content, R.string.livestock_health_form_when_title)
        content.addView(ui.choice(getString(R.string.livestock_health_form_started_today),
            startedAtMillis == 0L) { startedAtMillis = 0L; render(current) })
        content.addView(ui.spacer(R.dimen.aqua_size_8))
        content.addView(ui.choice(if (startedAtMillis > 0L) date(startedAtMillis)
            else getString(R.string.livestock_health_form_started_earlier), startedAtMillis > 0L) {
            AppDatePickerDialogFragment.show(childFragmentManager, DATE_REQUEST,
                startedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                maxMillis = System.currentTimeMillis())
        })
        section(content, R.string.livestock_health_form_trend_title)
        trendOptions().filterNot { it.first == LivestockHealthTrend.RESOLVED }.forEach { (value, label) ->
            content.addView(ui.choice(getString(label), trend == value) {
                trend = value
                render(current)
            })
            content.addView(ui.spacer(R.dimen.aqua_size_8))
        }
        section(content, R.string.livestock_health_form_photo_title)
        if (!photoUri.isNullOrBlank()) {
            content.addView(ui.speciesImage(selected, R.dimen.aqua_size_96,
                R.dimen.aqua_size_96, photoUri))
            content.addView(ui.spacer(R.dimen.aqua_size_8))
        }
        content.addView(ui.choice(if (photoUri.isNullOrBlank())
            getString(R.string.livestock_health_form_photo_add)
            else getString(R.string.livestock_health_form_photo_selected),
            !photoUri.isNullOrBlank()) {
            PhotoSourceBottomSheet.newInstance(getString(R.string.livestock_health_form_photo_title),
                !photoUri.isNullOrBlank()).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
        })
        content.addView(ui.spacer())
        noteField(content, note) { note = it }
    }

    private fun saveObservation() {
        val current = tank
        val item = current?.livestock?.firstOrNull { it.id == selectedLivestockId }
        if (current == null || item == null || saving || selectedSymptoms.isEmpty() ||
            affectedCount !in 1..item.quantity ||
            (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE in selectedSymptoms && baseline == null)
        ) return
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
            try {
                tanks.addHealthObservation(args.tankId, observation)
                mediaFlow.commitSelection(deletePersistedMedia = false)
                navigate(PAGE_ASSESSMENT, observation.id)
            } catch (error: Exception) {
                if (_binding != null) {
                    binding.healthPrimaryAction.isEnabled = true
                    showError(R.string.livestock_health_form_save_failed)
                }
            } finally {
                saving = false
            }
        }
    }

    private fun renderAssessment(current: AquariumTankSnapshot, content: LinearLayout) {
        val record = current.healthObservations.firstOrNull { it.id == args.observationId }
        if (record == null) { missing(content); return }
        panel(content, getString(R.string.livestock_health_assessment_attention),
            getString(R.string.livestock_health_assessment_context))
        section(content, R.string.livestock_health_assessment_evidence)
        panel(content, record.livestockName,
            record.symptoms.joinToString { ui.symptomName(it) } + " · " + date(record.observedAtMillis))
        panel(content, getString(R.string.livestock_health_assessment_missing_water),
            getString(R.string.livestock_health_assessment_missing_data))
        maintenanceContext(content)
        section(content, R.string.livestock_health_assessment_check_title)
        val tips = if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE in record.symptoms)
            listOf(R.string.livestock_health_assessment_check_surface,
                R.string.livestock_health_assessment_check_water,
                R.string.livestock_health_assessment_check_others)
        else listOf(R.string.livestock_health_assessment_check_general,
            R.string.livestock_health_assessment_check_others)
        tips.forEach { panel(content, getString(it)) }
        panel(content, getString(R.string.livestock_health_assessment_no_diagnosis),
            getString(R.string.livestock_health_assessment_disclaimer))
        content.addView(ui.button(R.string.livestock_health_assessment_open_tank) { openTank() })
        content.addView(ui.spacer())
        primary(R.string.livestock_health_assessment_follow) { navigate(PAGE_DETAIL, record.id) }
    }

    private fun renderDetail(current: AquariumTankSnapshot, content: LinearLayout) {
        val record = current.healthObservations.firstOrNull { it.id == args.observationId }
        if (record == null) { missing(content); return }
        binding.appHeader.setupAquaHeader(this, AquaHeaderConfig(
            titleOverride = getString(R.string.livestock_health_detail_title),
            onBackClick = { findNavController().popBackStack() },
            primaryAction = if (record.isActive) AquaHeaderPrimaryAction(
                getString(R.string.livestock_health_detail_end)) { closeDialog(record) }
            else null
        ))
        val animal = current.livestock.firstOrNull { it.id == record.livestockId }
            ?: AquariumLivestock(id = record.livestockId, name = record.livestockName,
                category = record.livestockCategory, quantity = record.affectedCount,
                catalogEntryId = record.catalogEntryId)
        val header = ui.column()
        val overview = ui.row()
        overview.addView(ui.speciesImage(animal, R.dimen.aqua_size_96,
            R.dimen.aqua_size_96, record.photoUri))
        val labels = ui.column().apply {
            setPadding(ui.size(R.dimen.aqua_size_12), 0, 0, 0)
            addView(ui.text(record.livestockName, bold = true))
            addView(ui.text(getString(R.string.livestock_health_home_affected_short,
                record.latestAffectedCount, animal.quantity),
                colorRes = R.color.aqua_card_text_secondary))
            addView(ui.text(getString(if (record.isActive) R.string.livestock_health_home_tracking
                else if (record.outcome == LivestockHealthTrend.RESOLVED)
                    R.string.livestock_health_home_resolved
                else R.string.livestock_health_home_ended),
                colorRes = R.color.aqua_content_warning))
        }
        labels.layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        overview.addView(labels)
        header.addView(overview)
        header.addView(ui.spacer(R.dimen.aqua_size_12))
        header.addView(ui.text(record.symptoms.joinToString { ui.symptomName(it) }, bold = true))
        header.addView(ui.text(getString(R.string.livestock_health_detail_first,
            date(record.observedAtMillis)), colorRes = R.color.aqua_card_text_secondary))
        content.addView(ui.card(content = header))
        section(content, R.string.livestock_health_detail_status_title)
        val latest = ui.row()
        trendOptions().forEach { (value, label) ->
            latest.addView(ui.choice(getString(label), record.latestTrend == value) {
                if (record.isActive) LivestockHealthCheckSheet.show(
                    childFragmentManager, args.tankId, record.id)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = ui.size(R.dimen.aqua_size_8)
                }
            })
        }
        content.addView(latest)
        section(content, R.string.livestock_health_detail_history)
        record.checks.asReversed().forEach { check ->
            historyCard(content, animal, date(check.observedAtMillis),
                getString(trendOptions().first { it.first == check.trend }.second),
                check.affectedCount, check.note, check.photoUri)
        }
        historyCard(content, animal, date(record.observedAtMillis),
            getString(R.string.livestock_health_detail_initial),
            record.affectedCount, record.note, record.photoUri)
        section(content, R.string.livestock_health_home_tank_data)
        panel(content, getString(R.string.livestock_health_home_tank_data_pending))
        maintenanceContext(content)
        content.addView(ui.button(R.string.livestock_health_home_tank_open) { openTank() })
        content.addView(ui.spacer())
        primary(R.string.livestock_health_detail_add_check, record.isActive) {
            LivestockHealthCheckSheet.show(childFragmentManager, args.tankId, record.id)
        }
    }

    private fun historyCard(content: LinearLayout, animal: AquariumLivestock,
        dateText: String, status: String, count: Int, noteText: String, imageUri: String?) {
        val line = ui.row()
        val labels = ui.column()
        labels.addView(ui.text(dateText, colorRes = R.color.aqua_accent_primary))
        labels.addView(ui.text(status, bold = true))
        labels.addView(ui.text(getString(R.string.livestock_health_detail_affected,
            count) + if (noteText.isBlank()) "" else " · $noteText",
            colorRes = R.color.aqua_card_text_secondary))
        line.addView(labels.apply {
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        line.addView(ui.speciesImage(animal, R.dimen.aqua_size_80,
            R.dimen.aqua_size_80, imageUri))
        content.addView(ui.card(content = ui.column().apply { addView(line) }))
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }

    private fun closeDialog(record: LivestockHealthObservation) {
        FeedbackBottomSheet.show(childFragmentManager,
            getString(R.string.livestock_health_detail_end),
            getString(R.string.livestock_health_detail_end_info),
            getString(R.string.livestock_health_detail_end), getString(android.R.string.cancel),
            FeedbackBottomSheet.FeedbackTone.WARNING, CLOSE_REQUEST, record.id.toString())
    }

    private fun closeRecord(observationId: Long?) {
        if (observationId == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                tanks.closeHealthObservation(args.tankId, observationId, System.currentTimeMillis())
            } catch (error: Exception) {
                showError(R.string.livestock_health_detail_check_failed)
            }
        }
    }

    private fun showError(resource: Int) {
        (requireActivity() as? BaseActivity)?.showSnackBar(
            getString(resource), BaseActivity.SnackType.ERROR
        )
    }

    private fun openCamera() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uri = mediaFlow.createCameraUri()
            if (uri == null) showError(R.string.aquarium_photo_temp_file_failed)
            else cameraLauncher.launch(uri)
        }
    }

    private suspend fun preparePhoto(uri: Uri) {
        when (val prepared = mediaFlow.prepareCropIntent(uri,
            getString(R.string.aquarium_photo_crop_title))) {
            is MediaCropPreparationResult.Ready -> cropLauncher.launch(prepared.intent)
            else -> showError(R.string.aquarium_photo_crop_failed)
        }
    }

    private fun noteField(content: LinearLayout, value: String, onChange: (String) -> Unit) {
        val field = EditText(requireContext()).apply {
            hint = getString(R.string.livestock_health_form_note_hint)
            minLines = 2
            maxLines = NOTE_MAX_LINES
            setText(value)
            doAfterTextChanged { onChange(it?.toString().orEmpty()) }
        }
        content.addView(field)
    }

    private fun maintenanceContext(content: LinearLayout) {
        val lastWater = tankActivity.completedTasks.firstOrNull { it.type == CareTaskType.WATER_CHANGE }
        if (lastWater == null) {
            panel(content, getString(R.string.livestock_health_maintenance_none))
        } else {
            val date = date(lastWater.completedAtMillis ?: lastWater.dueAtMillis)
            val percent = lastWater.waterChangePercent?.let {
                getString(R.string.livestock_health_maintenance_percent, it)
            }.orEmpty()
            panel(content, getString(R.string.livestock_health_maintenance_water, date, percent))
        }
        val lastFilter = tankActivity.completedTasks.firstOrNull { it.type in setOf(
            CareTaskType.FILTER_MAINTENANCE, CareTaskType.FILTER_CHANGE,
            CareTaskType.PRE_FILTER_CLEANING
        ) }
        if (lastFilter != null) {
            panel(content, getString(R.string.livestock_health_maintenance_filter,
                date(lastFilter.completedAtMillis ?: lastFilter.dueAtMillis)))
        }
    }

    private fun panel(content: LinearLayout, title: String, subtitle: String? = null) {
        val body = ui.column()
        body.addView(ui.text(title, bold = true))
        if (subtitle != null) {
            body.addView(ui.spacer(R.dimen.aqua_size_8))
            body.addView(ui.text(subtitle, colorRes = R.color.aqua_card_text_secondary))
        }
        content.addView(ui.card(content = body))
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }

    private fun section(content: LinearLayout, title: Int) {
        content.addView(ui.spacer())
        content.addView(ui.heading(title))
        content.addView(ui.spacer(R.dimen.aqua_size_12))
    }

    private fun missing(content: LinearLayout) {
        binding.healthPrimaryAction.isVisible = false
        panel(content, getString(R.string.aquarium_livestock_no_longer_exists_message))
    }

    private fun primary(label: Int, enabled: Boolean = true, onClick: () -> Unit) {
        binding.healthPrimaryAction.isVisible = true
        binding.healthPrimaryAction.setText(label)
        binding.healthPrimaryAction.isEnabled = enabled && !saving
        binding.healthPrimaryAction.setOnClickListener { onClick() }
    }

    private fun navigate(page: String, observationId: Long = 0L) {
        findNavController().navigateSafelyFrom(
            R.id.livestockHealthFragment,
            LivestockHealthFragmentDirections.actionLivestockHealthFragmentSelf(
                args.tankId, page, observationId)
        )
    }

    private fun openTank() {
        val controller = findNavController()
        controller.getBackStackEntry(R.id.tankDetailFragment)
            .savedStateHandle[TankDetailFragment.KEY_RETURN_TAB] = TankDetailTabArgs.TANK
        controller.popBackStack(R.id.tankDetailFragment, false)
    }

    private fun date(timestamp: Long): String =
        DateFormat.getDateFormat(requireContext()).format(java.util.Date(timestamp))

    private fun trendOptions(): List<Pair<LivestockHealthTrend, Int>> = listOf(
        LivestockHealthTrend.INCREASING to R.string.livestock_health_form_increasing,
        LivestockHealthTrend.SAME to R.string.livestock_health_form_same,
        LivestockHealthTrend.DECREASING to R.string.livestock_health_form_decreasing,
        LivestockHealthTrend.RESOLVED to R.string.livestock_health_form_resolved
    )

    private fun symptomsFor(category: String): List<LivestockHealthSymptom> {
        val general = listOf(
            LivestockHealthSymptom.APPETITE_CHANGE,
            LivestockHealthSymptom.ACTIVITY_CHANGE,
            LivestockHealthSymptom.COLOR_CHANGE
        )
        return when (category) {
            LivestockCategories.FISH -> listOf(
                LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE,
                LivestockHealthSymptom.APPETITE_CHANGE,
                LivestockHealthSymptom.SWIMMING_CHANGE,
                LivestockHealthSymptom.SKIN_OR_SPOTS,
                LivestockHealthSymptom.FIN_CHANGE,
                LivestockHealthSymptom.OTHER
            )
            LivestockCategories.SHRIMP, LivestockCategories.CRAB_CRAYFISH -> general + listOf(
                LivestockHealthSymptom.MOLTING_CHANGE, LivestockHealthSymptom.OTHER
            )
            LivestockCategories.SNAIL -> general + listOf(
                LivestockHealthSymptom.SHELL_CHANGE, LivestockHealthSymptom.OTHER
            )
            LivestockCategories.CORAL -> listOf(
                LivestockHealthSymptom.COLOR_CHANGE,
                LivestockHealthSymptom.POLYP_RETRACTION,
                LivestockHealthSymptom.OTHER
            )
            else -> general + LivestockHealthSymptom.OTHER
        }
    }

    override fun onDestroyView() {
        _binding = null
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
        const val ACTION_CAPTURE = "health_photo_capture"
        const val DATE_REQUEST = "livestock_health_onset_date"
        const val CLOSE_REQUEST = "livestock_health_close_confirmation"
    }
}
