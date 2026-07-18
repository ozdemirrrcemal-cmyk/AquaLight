package com.aqua.aqualight.ui.common.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R

class TimelineAxisView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  var status: TimelineDayStatus = TimelineDayStatus.PAST
    set(value) {
      field = value
      invalidate()
    }

  var showNode: Boolean = false
    set(value) {
      field = value
      invalidate()
    }

  private val axisLineColor = ContextCompat.getColor(context, R.color.aqua_palette_hex_26384c)

  private val todayNodeColor = ContextCompat.getColor(context, R.color.aqua_palette_hex_4fd6c8)
  private val pastNodeColor = ContextCompat.getColor(context, R.color.aqua_palette_hex_45bfaf)
  private val upcomingNodeColor = axisLineColor

  private val nodeCoverColor = ContextCompat.getColor(context, R.color.aqua_palette_hex_0b1d33)

  private val axisOffsetX = resources.getDimension(R.dimen.aqua_size_negative_5)

  private val nodeCoverRadius = resources.getDimension(R.dimen.aqua_size_13)
  private val nodeOuterRadius = resources.getDimension(R.dimen.aqua_size_10)
  private val nodeInnerRadius = resources.getDimension(R.dimen.aqua_size_8)

  private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = axisLineColor
    strokeWidth = resources.getDimension(R.dimen.aqua_size_2)
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    pathEffect = DashPathEffect(
      floatArrayOf(
        resources.getDimension(R.dimen.aqua_size_8),
        resources.getDimension(R.dimen.aqua_size_9)
      ),
      0f
    )
  }

  private val nodeCoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = nodeCoverColor
  }

  private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = resources.getDimension(R.dimen.aqua_size_1_5)
  }

  private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }

  fun bind(
    status: TimelineDayStatus,
    showNode: Boolean
  ) {
    this.status = status
    this.showNode = showNode
  }

  override fun onDraw(
    canvas: Canvas
  ) {
    super.onDraw(canvas)

    val centerX = width / 2f + axisOffsetX

    canvas.drawLine(
      centerX,
      0f,
      centerX,
      height.toFloat(),
      linePaint
    )

    if (showNode) {
      drawNode(
        canvas = canvas,
        centerX = centerX
      )
    }
  }

  private fun drawNode(
    canvas: Canvas,
    centerX: Float
  ) {
    val centerY = height / 2f
    val nodeColor = getNodeColor()

    nodeStrokePaint.color = nodeColor
    nodeFillPaint.color = nodeColor

    canvas.drawCircle(
      centerX,
      centerY,
      nodeCoverRadius,
      nodeCoverPaint
    )

    canvas.drawCircle(
      centerX,
      centerY,
      nodeOuterRadius,
      nodeStrokePaint
    )

    canvas.drawCircle(
      centerX,
      centerY,
      nodeInnerRadius,
      nodeFillPaint
    )
  }

  private fun getNodeColor(): Int {
    return when (status) {
      TimelineDayStatus.TODAY -> todayNodeColor
      TimelineDayStatus.UPCOMING -> upcomingNodeColor
      TimelineDayStatus.PAST -> pastNodeColor
    }
  }
}
