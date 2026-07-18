package com.aqua.aqualight.ui.common.feedback

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

/** Debug-only host used by instrumentation recreation tests. */
class Stage8DialogTestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = android.R.id.content })
    }
}
