package com.aqua.aqualight.debug.dosing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aqua.aqualight.R

/** Small, selectable, copyable process-wide Dosing trace surface for the temporary debug branch. */
class DosingDebugOverlayView(context: Context) : LinearLayout(context) {
    private val titleView = TextView(context)
    private val logView = TextView(context)
    private val scrollView = ScrollView(context)
    private val toggleButton = Button(context)
    private var expanded = true

    init {
        configureContainer()
        addView(
            createHeader(),
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT_DP))
        )
        addView(
            createLogSurface(),
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(LOG_HEIGHT_DP))
        )
    }

    fun submit(lines: List<String>) {
        titleView.text = context.getString(R.string.dosing_debug_trace_count, lines.size)
        val next = lines.joinToString(separator = "\n")
        if (logView.text.toString() == next) return
        logView.text = next
        if (expanded) {
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun configureContainer() {
        orientation = VERTICAL
        setBackgroundColor(
            Color.argb(
                BACKGROUND_ALPHA,
                BACKGROUND_RED,
                BACKGROUND_GREEN,
                BACKGROUND_BLUE
            )
        )
        elevation = dp(ELEVATION_DP).toFloat()
        isClickable = true
    }

    private fun createHeader(): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            dp(HEADER_PADDING_START_DP),
            dp(HEADER_PADDING_VERTICAL_DP),
            dp(HEADER_PADDING_END_DP),
            dp(HEADER_PADDING_VERTICAL_DP)
        )

        titleView.apply {
            setTextColor(Color.WHITE)
            textSize = HEADER_TEXT_SIZE_SP
            typeface = Typeface.DEFAULT_BOLD
            setText(R.string.dosing_debug_trace_title)
        }
        addView(
            titleView,
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(actionButton(R.string.dosing_debug_copy) { copyAll() })
        addView(actionButton(R.string.dosing_debug_clear) { DosingDebugTrace.clear() })

        toggleButton.apply {
            setText(R.string.dosing_debug_collapse)
            isAllCaps = false
            textSize = ACTION_TEXT_SIZE_SP
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(ACTION_HORIZONTAL_PADDING_DP), 0, dp(ACTION_HORIZONTAL_PADDING_DP), 0)
            setOnClickListener { toggleExpanded() }
        }
        addView(toggleButton)
    }

    private fun createLogSurface(): ScrollView {
        logView.apply {
            setTextColor(Color.rgb(TRACE_TEXT_RED, TRACE_TEXT_GREEN, TRACE_TEXT_BLUE))
            textSize = TRACE_TEXT_SIZE_SP
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(
                dp(LOG_HORIZONTAL_PADDING_DP),
                dp(LOG_TOP_PADDING_DP),
                dp(LOG_HORIZONTAL_PADDING_DP),
                dp(LOG_BOTTOM_PADDING_DP)
            )
        }
        return scrollView.apply {
            isFillViewport = true
            addView(
                logView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun actionButton(labelRes: Int, action: () -> Unit): Button = Button(context).apply {
        setText(labelRes)
        isAllCaps = false
        textSize = ACTION_TEXT_SIZE_SP
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(ACTION_HORIZONTAL_PADDING_DP), 0, dp(ACTION_HORIZONTAL_PADDING_DP), 0)
        setOnClickListener { action() }
    }

    private fun toggleExpanded() {
        expanded = !expanded
        scrollView.visibility = if (expanded) View.VISIBLE else View.GONE
        toggleButton.setText(
            if (expanded) R.string.dosing_debug_collapse else R.string.dosing_debug_expand
        )
    }

    private fun copyAll() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(CLIP_LABEL, DosingDebugTrace.snapshotText())
        )
        Toast.makeText(
            context,
            R.string.dosing_debug_copy_confirmation,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CLIP_LABEL = "AquaLight Dosing Trace"
        private const val BACKGROUND_ALPHA = 238
        private const val BACKGROUND_RED = 8
        private const val BACKGROUND_GREEN = 15
        private const val BACKGROUND_BLUE = 24
        private const val TRACE_TEXT_RED = 207
        private const val TRACE_TEXT_GREEN = 255
        private const val TRACE_TEXT_BLUE = 230
        private const val ELEVATION_DP = 24
        private const val HEADER_PADDING_START_DP = 8
        private const val HEADER_PADDING_END_DP = 6
        private const val HEADER_PADDING_VERTICAL_DP = 4
        private const val HEADER_HEIGHT_DP = 34
        private const val ACTION_HORIZONTAL_PADDING_DP = 6
        private const val LOG_HORIZONTAL_PADDING_DP = 8
        private const val LOG_TOP_PADDING_DP = 4
        private const val LOG_BOTTOM_PADDING_DP = 8
        private const val LOG_HEIGHT_DP = 180
        private const val HEADER_TEXT_SIZE_SP = 11f
        private const val ACTION_TEXT_SIZE_SP = 9f
        private const val TRACE_TEXT_SIZE_SP = 9f

        fun enabled(context: Context): Boolean =
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
