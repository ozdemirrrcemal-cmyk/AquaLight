package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPillTextAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel

class LivestockHealthFollowUpFragment :
    Fragment(R.layout.fragment_livestock_health_follow_up) {

    private val args: LivestockHealthFollowUpFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthFollowUpBinding? = null
    private val binding get() = _binding!!

    private var currentLivestock: AquariumLivestock? = null
    private var currentStatus: String = LivestockHealthCheckBottomSheet.STATUS_SAME
    private val historyState = LivestockHealthFollowUpHistoryState()
    private var renderer: LivestockHealthFollowUpRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthFollowUpFragment requires a positive tankId."
        }
        historyState.restore(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthFollowUpBinding.bind(view)
        renderer = LivestockHealthFollowUpRenderer(
            fragment = this,
            binding = binding,
            symptomKey = args.symptomKey,
            affectedCount = args.affectedCount,
            readOnly = args.readOnly,
            closeReason = args.closeReason
        )
        setupHeader()
        setupCheckResultListener()
        setupNewCheckAction()
        observeLivestock()
        renderer?.renderStatus(currentStatus)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_followup_title),
                onBackClick = { findNavController().navigateUp() },
                pillTextAction = if (args.readOnly) {
                    null
                } else {
                    AquaHeaderPillTextAction(
                        text = getString(R.string.livestock_health_end_followup),
                        backgroundRes = R.drawable.bg_aqua_toolbar_pill_action_primary,
                        onClick = {
                            endSessionFollowup(
                                LivestockHealthUiSessionState.CLOSE_REASON_MANUAL
                            )
                        }
                    )
                }
            )
        )
    }

    private fun setupCheckResultListener() {
        parentFragmentManager.setFragmentResultListener(
            LivestockHealthCheckBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val result = LivestockHealthCheckResultUi(
                status = bundle.getString(
                    LivestockHealthCheckBottomSheet.RESULT_STATUS,
                    LivestockHealthCheckBottomSheet.STATUS_SAME
                ),
                affectedCount = bundle.getInt(
                    LivestockHealthCheckBottomSheet.RESULT_AFFECTED_COUNT,
                    1
                ),
                checkTimeMillis = bundle.getLong(
                    LivestockHealthCheckBottomSheet.RESULT_TIME_MILLIS,
                    System.currentTimeMillis()
                ),
                photoUri = bundle.getString(
                    LivestockHealthCheckBottomSheet.RESULT_PHOTO_URI
                ),
                note = bundle.getString(
                    LivestockHealthCheckBottomSheet.RESULT_NOTE
                ).orEmpty()
            )
            currentStatus = result.status
            currentLivestock?.let { livestock ->
                historyState.append(this, args.symptomKey, livestock, result)
                updateMainLastCheck(result.checkTimeMillis, result.affectedCount)
                if (result.status == LivestockHealthCheckBottomSheet.STATUS_RECOVERED) {
                    endSessionFollowup(
                        LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED
                    )
                } else {
                    renderer?.renderStatus(currentStatus)
                    renderer?.renderHistory(livestock, historyState.entries)
                }
            }
        }
    }

    private fun setupNewCheckAction() {
        binding.btnNewCheck.isVisible = !args.readOnly
        binding.btnNewCheck.setOnClickListener {
            if (!args.readOnly) {
                currentLivestock?.let { livestock ->
                    LivestockHealthCheckBottomSheet.show(
                        fragmentManager = parentFragmentManager,
                        request = LivestockHealthCheckSheetRequest(
                            livestockId = livestock.id,
                            livestockName = livestock.name.ifBlank {
                                getString(R.string.aquarium_unnamed_livestock)
                            },
                            category = livestock.category,
                            issueLabel = binding.tvFollowupIssue.text.toString(),
                            totalCount = livestock.quantity.coerceAtLeast(1),
                            photoUri = livestock.photoUri
                        )
                    )
                }
            }
        }
    }

    private fun observeLivestock() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentLivestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                ?.firstOrNull { item -> item.id == args.livestockId }
                ?: tanks
                    .firstOrNull { tank -> tank.id == args.tankId }
                    ?.livestock
                    ?.firstOrNull()

            currentLivestock?.let { livestock ->
                historyState.ensureInitial(
                    fragment = this,
                    symptomKey = args.symptomKey,
                    totalCount = livestock.quantity.coerceAtLeast(1)
                )
                renderer?.renderLivestock(livestock)
                renderer?.renderHistory(livestock, historyState.entries)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        historyState.save(outState)
        super.onSaveInstanceState(outState)
    }

    private fun updateMainLastCheck(
        checkTimeMillis: Long,
        affectedCount: Int
    ) {
        runCatching {
            findNavController().getBackStackEntry(R.id.livestockHealthFragment)
                .savedStateHandle
                .apply {
                    set(LivestockHealthFragment.KEY_LAST_CHECK_AT, checkTimeMillis)
                    set(LivestockHealthFragment.KEY_AFFECTED_COUNT, affectedCount)
                    LivestockHealthUiSessionState.updateActiveFollowup(
                        handle = this,
                        livestockId = args.livestockId,
                        symptomKey = args.symptomKey,
                        affectedCount = affectedCount,
                        lastCheckAtMillis = checkTimeMillis
                    )
                }
        }
    }

    private fun endSessionFollowup(closeReason: String) {
        val navController = findNavController()
        val livestock = currentLivestock
        runCatching {
            val handle = navController
                .getBackStackEntry(R.id.livestockHealthFragment)
                .savedStateHandle
            val now = System.currentTimeMillis()
            val active = LivestockHealthUiSessionState.removeActiveFollowup(
                handle = handle,
                livestockId = args.livestockId,
                symptomKey = args.symptomKey
            )
            val startedAt = active?.startedAtMillis
                ?: handle.get<Long>(LivestockHealthFragment.KEY_STARTED_AT)
                ?: now
            if (livestock != null) {
                LivestockHealthUiSessionState.addClosedFollowup(
                    handle = handle,
                    entry = ClosedLivestockFollowupUi(
                        livestockId = livestock.id,
                        symptomKey = args.symptomKey,
                        affectedCount = active?.affectedCount
                            ?: args.affectedCount.coerceIn(
                                1,
                                livestock.quantity.coerceAtLeast(1)
                            ),
                        totalCount = livestock.quantity.coerceAtLeast(1),
                        startedAtMillis = startedAt,
                        closedAtMillis = now,
                        closeReason = closeReason,
                        checkCount = historyState.entries.size.coerceAtLeast(1)
                    )
                )
            }
        }
        navController.popBackStack(R.id.livestockHealthFragment, false)
    }

    override fun onDestroyView() {
        renderer = null
        _binding = null
        super.onDestroyView()
    }
}
