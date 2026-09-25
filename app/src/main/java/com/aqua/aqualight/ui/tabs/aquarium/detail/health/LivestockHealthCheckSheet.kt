package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockHealthCheck
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/** Fragment owned sheet: restores its draft and queries the current owner's tank on recreation. */
class LivestockHealthCheckSheet : BottomSheetDialogFragment() {
    private val tanks: AquariumTankViewModel by activityViewModels()
    private var checkTrend = LivestockHealthTrend.SAME
    private var checkCount = 1
    private var checkNote = ""
    private var saving = false
    private lateinit var body: LinearLayout
    private lateinit var ui: LivestockHealthUi
    private var current: AquariumTankSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            checkCount = state.getInt(STATE_COUNT, 1)
            checkNote = state.getString(STATE_NOTE).orEmpty()
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
        trends.forEach { (value, label) ->
            body.addView(ui.choice(getString(label), value == checkTrend) {
                checkTrend = value
                render(observation)
            })
            body.addView(ui.spacer(R.dimen.aqua_size_8))
        }
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
        body.addView(EditText(requireContext()).apply {
            hint = getString(R.string.livestock_health_form_note_hint)
            setText(checkNote)
            maxLines = 4
            doAfterTextChanged { checkNote = it?.toString().orEmpty() }
        })
        if (checkTrend == LivestockHealthTrend.RESOLVED) {
            body.addView(ui.text(getString(R.string.livestock_health_check_resolved_note)))
        }
        body.addView(ui.spacer())
        body.addView(ui.button(R.string.livestock_health_check_save) { save(observation) })
    }

    private fun save(observation: LivestockHealthObservation) {
        if (saving || !observation.isActive) return
        saving = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                tanks.addHealthCheck(requireArguments().getLong(ARG_TANK), observation.id,
                    LivestockHealthCheck(
                        AquariumIdGenerator.newLong(observation.checks.mapTo(mutableSetOf()) { it.id }),
                        System.currentTimeMillis(), checkCount, checkTrend, checkNote.trim()
                    ))
                dismiss()
            } catch (error: Exception) {
                (activity as? BaseActivity)?.showSnackBar(
                    getString(R.string.livestock_health_detail_check_failed),
                    BaseActivity.SnackType.ERROR)
            } finally {
                saving = false
            }
        }
    }

    companion object {
        private const val ARG_TANK = "tankId"
        private const val ARG_OBSERVATION = "observationId"
        private const val STATE_COUNT = "count"
        private const val STATE_TREND = "trend"
        private const val STATE_NOTE = "note"
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
