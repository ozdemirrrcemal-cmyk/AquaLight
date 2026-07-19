package com.aqua.aqualight.ui.tabs.devices.add

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import kotlin.math.min

class DeviceScanPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.device_scan_ring_stroke_width)
        color = ContextCompat.getColor(context, R.color.aqua_accent_aqua)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.aqua_device_scan_pulse_view_color)
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.aqua_accent_aqua)
    }

    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            progress = animation.animatedValue as Float
            invalidate()
        }
    }

    fun startScan() {
        if (!animator.isStarted) {
            animator.start()
        }
    }

    fun stopScan() {
        animator.cancel()
        progress = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = min(width, height) * 0.42f

        canvas.drawCircle(centerX, centerY, maxRadius * 0.18f, glowPaint)
        canvas.drawCircle(centerX, centerY, maxRadius * 0.08f, corePaint)

        repeat(RING_COUNT) { index ->
            val offset = (progress + index / RING_COUNT.toFloat()) % 1f
            val radius = maxRadius * (0.22f + offset * 0.78f)
            val alpha = ((1f - offset) * 170).toInt().coerceIn(0, 170)

            ringPaint.alpha = alpha
            canvas.drawCircle(centerX, centerY, radius, ringPaint)
        }

        ringPaint.alpha = 210
        canvas.drawCircle(centerX, centerY, maxRadius * 0.34f, ringPaint)
    }

    private companion object {
        const val RING_COUNT = 3
    }
}
