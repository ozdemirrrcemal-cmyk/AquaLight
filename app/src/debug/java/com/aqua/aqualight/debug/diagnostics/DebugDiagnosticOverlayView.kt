package com.aqua.aqualight.debug.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Debug-only top trace surface. Transparent areas never become touch targets. */
internal class DebugDiagnosticOverlayView(
    context: Context
) : FrameLayout(context) {

    private val headerHeight = context.dp(HEADER_HEIGHT_DP)
    private val bodyHeight = (resources.displayMetrics.heightPixels * BODY_HEIGHT_RATIO)
        .roundToInt()
        .coerceIn(
            context.dp(MIN_BODY_HEIGHT_DP),
            context.dp(MAX_BODY_HEIGHT_DP)
        )
    private val header = TextView(context).apply {
        background = roundedBackground(HEADER_BACKGROUND_COLOR, context.dp(CORNER_RADIUS_DP))
        contentDescription = OPEN_DESCRIPTION
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        maxWidth = context.dp(MAX_HEADER_WIDTH_DP)
        maxLines = 1
        minHeight = headerHeight
        setPadding(
            context.dp(HORIZONTAL_PADDING_DP),
            0,
            context.dp(HORIZONTAL_PADDING_DP),
            0
        )
        setTextColor(HEADER_TEXT_COLOR)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_TEXT_SIZE_SP)
        typeface = Typeface.MONOSPACE
        setOnClickListener { toggleExpanded() }
        setOnLongClickListener {
            this@DebugDiagnosticOverlayView.copyLatestRecords()
            true
        }
    }
    private val bodyText = TextView(context).apply {
        setPadding(
            context.dp(HORIZONTAL_PADDING_DP),
            context.dp(BODY_PADDING_DP),
            context.dp(HORIZONTAL_PADDING_DP),
            context.dp(BODY_PADDING_DP)
        )
        setTextColor(BODY_TEXT_COLOR)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_TEXT_SIZE_SP)
        setTextIsSelectable(true)
        typeface = Typeface.MONOSPACE
    }
    private val body = ScrollView(context).apply {
        background = roundedBackground(BODY_BACKGROUND_COLOR, context.dp(CORNER_RADIUS_DP))
        isFillViewport = true
        visibility = View.GONE
        addView(
            bodyText,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    private var expanded = false
    private var latestRecords: List<DebugDiagnosticRecord> = emptyList()

    init {
        tag = OVERLAY_TAG
        isClickable = false
        elevation = context.dp(ELEVATION_DP).toFloat()

        addView(
            body,
            LayoutParams(LayoutParams.MATCH_PARENT, bodyHeight).apply {
                gravity = Gravity.TOP
                topMargin = headerHeight
            }
        )
        addView(
            header,
            LayoutParams(LayoutParams.WRAP_CONTENT, headerHeight).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        )
        render(emptyList())
    }

    fun render(records: List<DebugDiagnosticRecord>) {
        latestRecords = records
        renderHeader(records.lastOrNull())
        if (expanded) renderBody()
    }

    private fun toggleExpanded() {
        expanded = !expanded
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        header.contentDescription = if (expanded) CLOSE_DESCRIPTION else OPEN_DESCRIPTION
        renderHeader(latestRecords.lastOrNull())
        if (expanded) renderBody()
    }

    private fun renderHeader(latest: DebugDiagnosticRecord?) {
        val marker = if (expanded) COLLAPSE_MARKER else EXPAND_MARKER
        header.text = if (latest == null) {
            "$marker $READY_LABEL • $COPY_HINT"
        } else {
            "$marker #${latest.sequence} ${latest.event.category}/${latest.event.name} • $COPY_HINT"
        }
    }

    private fun copyLatestRecords() {
        if (latestRecords.isEmpty()) {
            Toast.makeText(context, EMPTY_COPY_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        val trace = latestRecords.joinToString(
            separator = "\n",
            transform = DebugDiagnosticFormatter::format
        )
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, trace))
        Toast.makeText(
            context,
            "$COPIED_MESSAGE ${latestRecords.size}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderBody() {
        bodyText.text = latestRecords
            .takeLast(MAX_VISIBLE_RECORDS)
            .joinToString(separator = "\n", transform = DebugDiagnosticFormatter::format)
        body.post { body.fullScroll(View.FOCUS_DOWN) }
    }

    private companion object {
        const val OVERLAY_TAG = "aqualight-debug-diagnostic-overlay"
        const val OPEN_DESCRIPTION = "Open diagnostic trace"
        const val CLOSE_DESCRIPTION = "Close diagnostic trace"
        const val READY_LABEL = "TRACE ready"
        const val COPY_HINT = "HOLD=COPY"
        const val CLIP_LABEL = "AquaLight diagnostic trace"
        const val COPIED_MESSAGE = "TRACE copied:"
        const val EMPTY_COPY_MESSAGE = "TRACE is empty"
        const val EXPAND_MARKER = "▼"
        const val COLLAPSE_MARKER = "▲"
        const val BODY_HEIGHT_RATIO = 0.36f
        const val HEADER_HEIGHT_DP = 28f
        const val MIN_BODY_HEIGHT_DP = 180f
        const val MAX_BODY_HEIGHT_DP = 360f
        const val MAX_HEADER_WIDTH_DP = 340f
        const val HORIZONTAL_PADDING_DP = 8f
        const val BODY_PADDING_DP = 6f
        const val CORNER_RADIUS_DP = 6f
        const val ELEVATION_DP = 24f
        const val HEADER_TEXT_SIZE_SP = 10f
        const val BODY_TEXT_SIZE_SP = 9f
        const val MAX_VISIBLE_RECORDS = 80
        const val HEADER_BACKGROUND_COLOR = -317_712_352 // ARGB ED101820
        const val BODY_BACKGROUND_COLOR = -183_494_880 // ARGB F5101720
        const val HEADER_TEXT_COLOR = -16_711_936 // ARGB FF00FF00
        const val BODY_TEXT_COLOR = -1 // ARGB FFFFFFFF
    }
}

private fun Context.dp(value: Float): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    resources.displayMetrics
).roundToInt()

private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }
