package com.aqua.aqualight.ui.tabs.aquarium.create.plants

import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.text.setTextSizeResource
import com.google.android.material.button.MaterialButton

internal object PlantPickerLoadStateBinder {
    fun loading(container: LinearLayout) = message(container, R.string.loading)

    fun failed(container: LinearLayout, retry: () -> Unit) {
        message(container, R.string.plant_picker_load_failed)
        container.addView(MaterialButton(container.context).apply {
            setText(R.string.plant_picker_retry)
            setOnClickListener { retry() }
        })
    }

    private fun message(container: LinearLayout, @StringRes label: Int) {
        container.removeAllViews()
        container.addView(TextView(container.context).apply {
            setText(label)
            setTextColor(ContextCompat.getColor(context, R.color.aqua_state_text_secondary))
            setTextSizeResource(R.dimen.aqua_text_size_body)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
    }
}
