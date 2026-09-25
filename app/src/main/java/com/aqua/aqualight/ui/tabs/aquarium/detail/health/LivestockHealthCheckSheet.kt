package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.text.format.DateFormat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockHealthCheck
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.media.MediaCropPreparationResult
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.yalantis.ucrop.UCrop
import java.util.Date
import kotlinx.coroutines.launch

private const val CHECK_NOTE_MAX_LINES = 4

/** Fragment owned sheet: restores its draft and queries the current owner's tank on recreation. */
class LivestockHealthCheckSheet : BottomSheetDialogFragment() {
    private val tanks: AquariumTankViewModel by activityViewModels()
    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(requireContext().applicationContext,
            AppMediaScope.TANK, "${requireArguments().getLong(ARG_TANK)}_health_check",
            container.authenticatedOwnerIdentity.requireOwnerUid(), MediaCropSpec.TANK,
            container.imageMediaProcessor)
    }
    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        if (action == ACTION_CAPTURE) openCamera()
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && view != null) viewLifecycleOwner.lifecycleScope.launch { preparePhoto(uri) }
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        lifecycleScope.launch {
            val uri = mediaFlow.currentCameraUri()
            if (success && uri != null && view != null) preparePhoto(uri)
            else mediaFlow.cancelCamera()
        }
    }
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch {
            if (view == null) { mediaFlow.cancelCrop(); return@launch }
            val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.let(UCrop::getOutput)
                else null
            val accepted = uri?.let { mediaFlow.acceptCrop(it) }
            if (accepted == null) {
                mediaFlow.cancelCrop()
                showError(R.string.aquarium_photo_crop_failed)
            } else {
                photoUri = accepted.toString()
                observation()?.let(::render)
            }
        }
    }
    private var checkTrend = LivestockHealthTrend.SAME
    private var checkCount = 1
    private var checkNote = ""
    private var photoUri: String? = null
    private var checkTimeMillis = System.currentTimeMillis()
    private var saving = false
    private lateinit var body: LinearLayout
    private lateinit var ui: LivestockHealthUi
    private var current: AquariumTankSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            checkCount = state.getInt(STATE_COUNT, 1)
            checkNote = state.getString(STATE_NOTE).orEmpty()
            photoUri = state.getString(STATE_PHOTO)
            checkTimeMillis = state.getLong(STATE_TIME, System.currentTimeMillis())
            checkTrend = LivestockHealthTrend.fromCode(
                state.getString(STATE_TREND) ?: LivestockHealthTrend.SAME.code)
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui = LivestockHealthUi(requireContext())
        body = ui.column().apply {
            setPadding(ui.size(R.dimen.aqua_size_20), ui.size(R.dimen.aqua_size_24),
                ui.size(R.dimen.aqua_size_20), ui.size(R.dimen.aqua_size_24))
        }
        return ScrollView(requireContext()).apply { addView(body) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mediaFlow.initializeSelection(null)
        childFragmentManager.setFragmentResultListener(DATE_REQUEST, viewLifecycleOwner) { _, result ->
            if (result.getString(AppDatePickerDialogFragment.RESULT_KEY) ==
                AppDatePickerDialogFragment.RESULT_SELECTED) {
                checkTimeMillis = result.getLong(AppDatePickerDialogFragment.RESULT_MILLIS)
                observation()?.let(::render)
            }
        }
        childFragmentManager.setFragmentResultListener(TIME_REQUEST, viewLifecycleOwner) { _, result ->
            if (result.getString(AppTimePickerDialogFragment.RESULT_KEY) ==
                AppTimePickerDialogFragment.RESULT_SELECTED) {
                checkTimeMillis = result.getLong(AppTimePickerDialogFragment.RESULT_MILLIS)
                observation()?.let(::render)
            }
        }
        childFragmentManager.setFragmentResultListener(PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner) { _, result ->
            when (result.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> permissionCoordinator.runWhenGranted(
                    AppCapability.CAMERA_PHOTO, ACTION_CAPTURE)
                PhotoSourceBottomSheet.RESULT_GALLERY -> galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                PhotoSourceBottomSheet.RESULT_REMOVE -> viewLifecycleOwner.lifecycleScope.launch {
                    mediaFlow.selectRemoval()
                    photoUri = null
                    observation()?.let(::render)
                }
            }
        }
        tanks.tanks.observe(viewLifecycleOwner) { snapshots ->
            current = snapshots.firstOrNull { it.id == requireArguments().getLong(ARG_TANK) }
            val observation = observation()
            if (observation == null || !observation.isActive) {
                dismiss()
            } else {
                val maxCount = maxCount(observation)
                if (savedInstanceState == null && checkCount == 1) {
                    checkCount = observation.latestAffectedCount.coerceIn(1, maxCount)
                }
                checkCount = checkCount.coerceIn(1, maxCount)
                render(observation)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_COUNT, checkCount)
        outState.putString(STATE_NOTE, checkNote)
        outState.putString(STATE_PHOTO, photoUri)
        outState.putLong(STATE_TIME, checkTimeMillis)
        outState.putString(STATE_TREND, checkTrend.code)
        super.onSaveInstanceState(outState)
    }

    private fun observation(): LivestockHealthObservation? = current?.healthObservations
        ?.firstOrNull { it.id == requireArguments().getLong(ARG_OBSERVATION) }

    private fun maxCount(observation: LivestockHealthObservation): Int =
        current?.livestock?.firstOrNull { it.id == observation.livestockId }?.quantity
            ?: observation.latestAffectedCount

    private fun render(observation: LivestockHealthObservation) {
        body.removeAllViews()
        body.addView(ui.heading(R.string.livestock_health_check_title))
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_question))
        val trends = listOf(
            LivestockHealthTrend.INCREASING to R.string.livestock_health_form_increasing,
            LivestockHealthTrend.SAME to R.string.livestock_health_form_same,
            LivestockHealthTrend.DECREASING to R.string.livestock_health_form_decreasing,
            LivestockHealthTrend.RESOLVED to R.string.livestock_health_form_resolved
        )
        val trendsRow = ui.row()
        trends.forEach { (value, label) ->
            trendsRow.addView(ui.choice(getString(label), value == checkTrend) {
                checkTrend = value
                render(observation)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = ui.size(R.dimen.aqua_size_8)
                }
            })
        }
        body.addView(trendsRow)
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_count))
        val maximum = maxCount(observation)
        val counter = ui.row()
        counter.addView(ui.button(R.string.livestock_health_count_decrease) {
            checkCount = (checkCount - 1).coerceAtLeast(1)
            render(observation)
        }.apply { text = "−"; contentDescription = getString(R.string.livestock_health_count_decrease) })
        counter.addView(ui.text("$checkCount / $maximum"))
        counter.addView(ui.button(R.string.livestock_health_count_increase) {
            checkCount = (checkCount + 1).coerceAtMost(maximum)
            render(observation)
        }.apply { text = "+"; contentDescription = getString(R.string.livestock_health_count_increase) })
        body.addView(counter)
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_when_title))
        body.addView(ui.choice(DateFormat.getDateFormat(requireContext()).format(Date(checkTimeMillis)),
            false) {
            val last = observation.checks.lastOrNull()?.observedAtMillis
                ?: observation.observedAtMillis
            AppDatePickerDialogFragment.show(childFragmentManager, DATE_REQUEST,
                checkTimeMillis, minMillis = last, maxMillis = System.currentTimeMillis())
        })
        body.addView(ui.spacer(R.dimen.aqua_size_8))
        body.addView(ui.choice(DateFormat.getTimeFormat(requireContext()).format(Date(checkTimeMillis)),
            false) {
            AppTimePickerDialogFragment.show(childFragmentManager, TIME_REQUEST, checkTimeMillis)
        })
        body.addView(ui.spacer())
        body.addView(EditText(requireContext()).apply {
            hint = getString(R.string.livestock_health_form_note_hint)
            setText(checkNote)
            maxLines = CHECK_NOTE_MAX_LINES
            doAfterTextChanged { checkNote = it?.toString().orEmpty() }
        })
        body.addView(ui.spacer())
        body.addView(ui.heading(R.string.livestock_health_check_photo_title))
        if (!photoUri.isNullOrBlank()) {
            current?.livestock?.firstOrNull { it.id == observation.livestockId }?.let { item ->
                body.addView(ui.speciesImage(item, R.dimen.aqua_size_96,
                    R.dimen.aqua_size_96, photoUri))
                body.addView(ui.spacer(R.dimen.aqua_size_8))
            }
        }
        body.addView(ui.choice(if (photoUri.isNullOrBlank())
            getString(R.string.livestock_health_form_photo_add)
            else getString(R.string.livestock_health_form_photo_selected),
            !photoUri.isNullOrBlank()) {
            PhotoSourceBottomSheet.newInstance(getString(R.string.livestock_health_check_photo_title),
                !photoUri.isNullOrBlank()).show(childFragmentManager, PhotoSourceBottomSheet.TAG)
        })
        if (checkTrend == LivestockHealthTrend.RESOLVED) {
            body.addView(ui.text(getString(R.string.livestock_health_check_resolved_note)))
        }
        body.addView(ui.spacer())
        body.addView(ui.button(R.string.livestock_health_check_save) { save(observation) })
    }

    private fun save(observation: LivestockHealthObservation) {
        if (saving || !observation.isActive) return
        val last = observation.checks.lastOrNull()?.observedAtMillis ?: observation.observedAtMillis
        if (checkTimeMillis !in last..System.currentTimeMillis()) {
            showError(R.string.livestock_health_check_invalid_time)
            return
        }
        saving = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                tanks.addHealthCheck(requireArguments().getLong(ARG_TANK), observation.id,
                    LivestockHealthCheck(
                        AquariumIdGenerator.newLong(observation.checks.mapTo(mutableSetOf()) { it.id }),
                        checkTimeMillis, checkCount, checkTrend, checkNote.trim(), photoUri
                    ))
                mediaFlow.commitSelection(deletePersistedMedia = false)
                dismiss()
            } catch (error: Exception) {
                showError(R.string.livestock_health_detail_check_failed)
            } finally {
                saving = false
            }
        }
    }

    private fun showError(resource: Int) {
        (activity as? BaseActivity)?.showSnackBar(getString(resource), BaseActivity.SnackType.ERROR)
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

    companion object {
        private const val ARG_TANK = "tankId"
        private const val ARG_OBSERVATION = "observationId"
        private const val STATE_COUNT = "count"
        private const val STATE_TREND = "trend"
        private const val STATE_NOTE = "note"
        private const val STATE_PHOTO = "photoUri"
        private const val STATE_TIME = "checkTimeMillis"
        private const val DATE_REQUEST = "livestock_health_check_date"
        private const val TIME_REQUEST = "livestock_health_check_time"
        private const val ACTION_CAPTURE = "health_check_photo_capture"
        private const val TAG = "livestock-health-check"

        fun show(manager: FragmentManager, tankId: Long, observationId: Long) {
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            LivestockHealthCheckSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK, tankId)
                    putLong(ARG_OBSERVATION, observationId)
                }
            }.show(manager, TAG)
        }
    }
}
