package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.text.format.DateFormat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailFragment
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs
import kotlinx.coroutines.launch

internal fun LivestockHealthFragment.closeDialog(record: LivestockHealthObservation) {
        FeedbackBottomSheet.show(childFragmentManager,
            getString(R.string.livestock_health_detail_end),
            getString(R.string.livestock_health_detail_end_info),
            getString(R.string.livestock_health_detail_end), getString(android.R.string.cancel),
            FeedbackBottomSheet.FeedbackTone.WARNING, CLOSE_REQUEST, record.id.toString())
    }

internal fun LivestockHealthFragment.closeRecord(observationId: Long?) {
        if (observationId == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                tanks.closeHealthObservation(args.tankId, observationId, System.currentTimeMillis())
            }.onFailure { error ->
                reportHealthError(error, R.string.livestock_health_detail_check_failed)
            }
        }
    }

internal fun LivestockHealthFragment.showError(resource: Int) {
        (requireActivity() as? BaseActivity)?.showSnackBar(
            getString(resource), BaseActivity.SnackType.ERROR
        )
    }

internal fun LivestockHealthFragment.openTank() {
        val controller = findNavController()
        controller.getBackStackEntry(R.id.tankDetailFragment)
            .savedStateHandle[TankDetailFragment.KEY_RETURN_TAB] = TankDetailTabArgs.TANK
        controller.popBackStack(R.id.tankDetailFragment, false)
    }

internal fun LivestockHealthFragment.date(timestamp: Long): String =
        DateFormat.getDateFormat(requireContext()).format(java.util.Date(timestamp))
