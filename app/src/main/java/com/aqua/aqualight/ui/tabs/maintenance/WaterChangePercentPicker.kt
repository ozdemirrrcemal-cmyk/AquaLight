package com.aqua.aqualight.ui.tabs.maintenance

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet

internal object WaterChangePercentPicker {

    private const val FIRST_PERCENT = 10
    private const val LAST_PERCENT = 100
    private const val PERCENT_STEP = 10
    private const val COLUMN_COUNT = 4

    private val supportedPercents =
        (FIRST_PERCENT..LAST_PERCENT step PERCENT_STEP).toList()

    fun show(
        fragmentManager: FragmentManager,
        context: Context,
        requestKey: String,
        selectedPercent: Int? = null
    ) {
        SingleChoiceBottomSheet.show(
            fragmentManager = fragmentManager,
            title = context.getString(
                R.string.maintenance_select_water_change_percentage
            ),
            options = supportedPercents.map { percent ->
                percent.toString() to context.getString(
                    R.string.maintenance_percent_value,
                    percent
                )
            },
            selectedId = selectedPercent?.toString(),
            columns = COLUMN_COUNT,
            requestKey = requestKey
        )
    }

    fun selectedPercent(result: Bundle): Int? {
        if (result.getString(SingleChoiceBottomSheet.RESULT_KEY) !=
            SingleChoiceBottomSheet.RESULT_SELECTED
        ) {
            return null
        }

        return result.getString(SingleChoiceBottomSheet.RESULT_SELECTED_ID)
            ?.toIntOrNull()
            ?.takeIf(supportedPercents::contains)
    }
}
