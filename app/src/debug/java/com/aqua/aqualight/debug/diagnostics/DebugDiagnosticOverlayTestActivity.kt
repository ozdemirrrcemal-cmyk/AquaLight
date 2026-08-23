package com.aqua.aqualight.debug.diagnostics

import android.os.Bundle
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.aqua.aqualight.base.BaseActivity

/** Debug-only host proving that the overlay survives an Activity's setContentView call. */
class DebugDiagnosticOverlayTestActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }
        setContentView(content)
    }
}
