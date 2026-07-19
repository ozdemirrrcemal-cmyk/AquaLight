package com.aqua.aqualight.ui.common.text

import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.DimenRes

/** Applies an sp-backed dimension token without converting it through a raw Kotlin literal. */
fun TextView.setTextSizeResource(@DimenRes textSizeRes: Int) {
    setTextSize(
        TypedValue.COMPLEX_UNIT_PX,
        resources.getDimension(textSizeRes)
    )
}
