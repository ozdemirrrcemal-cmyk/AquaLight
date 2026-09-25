package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.BaselineChange
import com.aqua.aqualight.application.aquarium.LivestockHealthCheck
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import com.aqua.aqualight.databinding.FragmentLivestockHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar
import kotlinx.coroutines.launch

/** Every displayed livestock entry comes from the selected tank, never from the full catalog. */
class LivestockHealthFragment : Fragment(R.layout.fragment_livestock_health) {
    private val args: LivestockHealthFragmentArgs by navArgs()
    private val tanks: AquariumTankViewModel by activityViewModels()
    private var _binding: FragmentLivestockHealthBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var ui: LivestockHealthUi
    private var tank: AquariumTankSnapshot? = null
    private var selectedLivestockId = 0L
    private val selectedSymptoms = linkedSetOf<LivestockHealthSymptom>()
    private var affectedCount = 1
    private var startedAtMillis = 0L
    private var trend = LivestockHealthTrend.SAME
    private var baseline: BaselineChange? = null
    private var note = ""
    private var saving = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthBinding.bind(view)
        ui = LivestockHealthUi(requireContext())
        savedInstanceState?.let { state ->
            selectedLivestockId = state.getLong(STATE_LIVESTOCK)
            affectedCount = state.getInt(STATE_COUNT, 1)
            startedAtMillis = state.getLong(STATE_ONSET)
            note = state.getString(STATE_NOTE).orEmpty()
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
        tanks.tanks.observe(viewLifecycleOwner) { snapshots ->
            val current = snapshots.firstOrNull { it.id == args.tankId }
            if (current == null) {
                findNavController().popBackStack()
            } else {
                tank = current
                render(current)
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
        binding.healthScroll.post { binding.healthScroll.scrollTo(0, 0) }
    }

    private fun renderHome(current: AquariumTankSnapshot, content: LinearLayout) {
        content.addView(ui.hero(current.photoUri))
        content.addView(ui.spacer())
        section(content, R.string.livestock_health_home_active)
        val active = current.healthObservations.filter { it.isActive }.sortedByDescending { it.observedAtMillis }
        val recent = current.healthObservations.filterNot { it.isActive }.sortedByDescending { it.observedAtMillis }
        if (active.isEmpty()) {
            panel(content, getString(R.string.livestock_health_home_no_records),
                getString(R.string.livestock_health_home_no_records_hint))
        } else {
            active.forEach { record -> observationCard(content, current, record) }
        }
        if (recent.isNotEmpty()) {
            section(content, R.string.livestock_health_home_recent)
            recent.forEach { record -> observationCard(content, current, record) }
        }
        section(content, R.string.livestock_health_home_tank_data)
        panel(content, getString(R.string.livestock_health_home_tank_data_pending),
            getString(R.string.livestock_health_assessment_missing_water))
        content.addView(ui.spacer())
        content.addView(ui.text(getString(R.string.livestock_health_home_no_records_disclaimer),
            colorRes = R.color.aqua_card_text_secondary))
        primary(R.string.livestock_health_home_new_observation, current.livestock.isNotEmpty()) {
            navigate(PAGE_FORM)
        }
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
        if (existing != null) header.addView(ui.speciesImage(existing,
            R.dimen.aqua_size_52, R.dimen.aqua_size_52))
        val textColumn = ui.column()
        textColumn.addView(ui.text(observation.livestockName, bold = true))
        textColumn.addView(ui.text(observation.symptoms.joinToString { ui.symptomName(it) },
            colorRes = R.color.aqua_card_text_secondary))
        header.addView(textColumn)
        cardContent.addView(header)
        cardContent.addView(ui.spacer(R.dimen.aqua_size_8))
        cardContent.addView(ui.text(
            if (observation.isActive) getString(R.string.livestock_health_home_tracking)
            else getString(if (observation.outcome == LivestockHealthTrend.RESOLVED)
                R.string.livestock_health_home_resolved else R.string.livestock_health_home_ended)
        ))
        cardContent.addView(ui.text(getString(R.string.livestock_health_home_affected_short,
            observation.latestAffectedCount, existing?.quantity ?: observation.affectedCount)))
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
            content.addView(ui.choice(item.name + " · " + item.quantity,
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
        symptomsFor(selected.category).forEach { symptom ->
            content.addView(ui.choice(ui.symptomName(symptom), symptom in selectedSymptoms) {
                if (!selectedSymptoms.remove(symptom) && selectedSymptoms.size < 5) {
                    selectedSymptoms += symptom
                }
                if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE !in selectedSymptoms) baseline = null
                render(current)
            })
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
            val calendar = Calendar.getInstance().apply {
                timeInMillis = if (startedAtMillis > 0L) startedAtMillis else System.currentTimeMillis()
            }
            DatePickerDialog(requireContext(), { _, year, month, day ->
                startedAtMillis = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                render(current)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        })
        section(content, R.string.livestock_health_form_trend_title)
        trendOptions().filterNot { it.first == LivestockHealthTrend.RESOLVED }.forEach { (value, label) ->
            content.addView(ui.choice(getString(label), trend == value) {
                trend = value
                render(current)
            })
            content.addView(ui.spacer(R.dimen.aqua_size_8))
        }
        noteField(content, note) { note = it }
    }

    private fun saveObservation() {
        val current = tank ?: return
        val item = current.livestock.firstOrNull { it.id == selectedLivestockId } ?: return
        if (saving || selectedSymptoms.isEmpty() || affectedCount !in 1..item.quantity) return
        if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE in selectedSymptoms && baseline == null) return
        val now = System.currentTimeMillis()
        val onset = startedAtMillis.takeIf { it in 1..now } ?: now
        val observation = LivestockHealthObservation(
            id = AquariumIdGenerator.newLong(current.healthObservations.mapTo(mutableSetOf()) { it.id }),
            livestockId = item.id, livestockName = item.name, livestockCategory = item.category,
            catalogEntryId = item.catalogEntryId, affectedCount = affectedCount,
            observedAtMillis = now, startedAtMillis = onset,
            symptoms = selectedSymptoms.toList(), trend = trend, note = note.trim(),
            baselineChange = baseline, closedAtMillis = null, outcome = null, checks = emptyList()
        )
        saving = true
        binding.healthPrimaryAction.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                tanks.addHealthObservation(args.tankId, observation)
                navigate(PAGE_ASSESSMENT, observation.id)
            } catch (error: Exception) {
                if (_binding != null) {
                    binding.healthPrimaryAction.isEnabled = true
                    android.widget.Toast.makeText(requireContext(),
                        R.string.livestock_health_form_save_failed, android.widget.Toast.LENGTH_SHORT).show()
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
        primary(R.string.livestock_health_assessment_follow) { navigate(PAGE_DETAIL, record.id) }
    }

    private fun renderDetail(current: AquariumTankSnapshot, content: LinearLayout) {
        val record = current.healthObservations.firstOrNull { it.id == args.observationId }
        if (record == null) { missing(content); return }
        panel(content, record.livestockName,
            getString(R.string.livestock_health_detail_first, date(record.observedAtMillis)))
        section(content, R.string.livestock_health_detail_status_title)
        panel(content, getString(if (record.isActive) R.string.livestock_health_home_tracking
            else if (record.outcome == LivestockHealthTrend.RESOLVED)
                R.string.livestock_health_home_resolved else R.string.livestock_health_home_ended),
            getString(R.string.livestock_health_home_affected_short,
                record.latestAffectedCount,
                current.livestock.firstOrNull { it.id == record.livestockId }?.quantity
                    ?: record.affectedCount))
        section(content, R.string.livestock_health_detail_history)
        panel(content, getString(R.string.livestock_health_detail_initial),
            date(record.observedAtMillis) + " · " + record.symptoms.joinToString { ui.symptomName(it) })
        record.checks.forEach { check ->
            panel(content, date(check.observedAtMillis),
                getString(trendOptions().first { it.first == check.trend }.second) +
                    " · " + check.affectedCount + " · " + check.note)
        }
        if (record.isActive) {
            content.addView(ui.button(R.string.livestock_health_detail_end) {
                closeDialog(record)
            })
            content.addView(ui.spacer())
        }
        primary(R.string.livestock_health_detail_add_check, record.isActive) {
            showCheckSheet(record, current)
        }
    }

    private fun showCheckSheet(record: LivestockHealthObservation, current: AquariumTankSnapshot) {
        val sheet = BottomSheetDialog(requireContext())
        val sheetUi = LivestockHealthUi(requireContext())
        val body = sheetUi.column().apply {
            setPadding(sheetUi.size(R.dimen.aqua_size_20), sheetUi.size(R.dimen.aqua_size_24),
                sheetUi.size(R.dimen.aqua_size_20), sheetUi.size(R.dimen.aqua_size_24))
        }
        var checkTrend = LivestockHealthTrend.SAME
        var checkCount = record.latestAffectedCount.coerceAtMost(
            current.livestock.firstOrNull { it.id == record.livestockId }?.quantity
                ?: record.latestAffectedCount)
        val maxCount = current.livestock.firstOrNull { it.id == record.livestockId }?.quantity
            ?: record.latestAffectedCount
        var checkNote = ""
        fun fill() {
            body.removeAllViews()
            body.addView(sheetUi.heading(R.string.livestock_health_check_title))
            body.addView(sheetUi.spacer())
            body.addView(sheetUi.heading(R.string.livestock_health_check_question))
            trendOptions().forEach { (value, label) ->
                body.addView(sheetUi.choice(getString(label), value == checkTrend) {
                    checkTrend = value
                    fill()
                })
                body.addView(sheetUi.spacer(R.dimen.aqua_size_8))
            }
            body.addView(sheetUi.heading(R.string.livestock_health_check_count))
            val count = sheetUi.text("$checkCount / $maxCount")
            body.addView(sheetUi.button(R.string.livestock_health_count_decrease) {
                checkCount = (checkCount - 1).coerceAtLeast(1)
                fill()
            }.apply { text = "−"; contentDescription = getString(R.string.livestock_health_count_decrease) })
            body.addView(count)
            body.addView(sheetUi.button(R.string.livestock_health_count_increase) {
                checkCount = (checkCount + 1).coerceAtMost(maxCount)
                fill()
            }.apply { text = "+"; contentDescription = getString(R.string.livestock_health_count_increase) })
            noteField(body, checkNote) { checkNote = it }
            if (checkTrend == LivestockHealthTrend.RESOLVED) {
                body.addView(sheetUi.text(getString(R.string.livestock_health_check_resolved_note)))
            }
            body.addView(sheetUi.spacer())
            body.addView(sheetUi.button(R.string.livestock_health_check_save) {
                if (saving) return@button
                saving = true
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        tanks.addHealthCheck(args.tankId, record.id,
                            LivestockHealthCheck(AquariumIdGenerator.newLong(
                                record.checks.mapTo(mutableSetOf()) { it.id }),
                                System.currentTimeMillis(), checkCount, checkTrend, checkNote.trim()))
                        sheet.dismiss()
                    } catch (error: Exception) {
                        android.widget.Toast.makeText(requireContext(),
                            R.string.livestock_health_detail_check_failed,
                            android.widget.Toast.LENGTH_SHORT).show()
                    } finally { saving = false }
                }
            })
        }
        fill()
        sheet.setContentView(android.widget.ScrollView(requireContext()).apply { addView(body) })
        sheet.show()
    }

    private fun closeDialog(record: LivestockHealthObservation) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.livestock_health_detail_end)
            .setMessage(R.string.livestock_health_detail_end_info)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.livestock_health_detail_end) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        tanks.closeHealthObservation(args.tankId, record.id, System.currentTimeMillis())
                    } catch (error: Exception) {
                        android.widget.Toast.makeText(requireContext(),
                            R.string.livestock_health_detail_check_failed,
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun noteField(content: LinearLayout, value: String, onChange: (String) -> Unit) {
        val field = EditText(requireContext()).apply {
            hint = getString(R.string.livestock_health_form_note_hint)
            minLines = 2
            maxLines = 4
            setText(value)
            doAfterTextChanged { onChange(it?.toString().orEmpty()) }
        }
        content.addView(field)
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
            LivestockCategories.FISH -> general + listOf(
                LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE,
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
    }
}
