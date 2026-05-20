package com.aqua.aqualight.ui.common.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

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

  private val axisLineColor = Color.parseColor("#33475D")

  private val todayNodeColor = Color.parseColor("#45CDBD")
  private val pastNodeColor = Color.parseColor("#3FB7A9")
  private val upcomingNodeColor = axisLineColor

  private val nodeCoverColor = Color.parseColor("#152B45")

  private val axisOffsetX = -3f.dp()

  private val nodeOuterRadius = 10f.dp()
  private val nodeCoverRadius = 13f.dp()
  private val nodeInnerRadius = 8f.dp()

  private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = axisLineColor
    strokeWidth = 2f.dp()
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    pathEffect = DashPathEffect(
      floatArrayOf(
        8f.dp(),
        9f.dp()
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
    strokeWidth = 1.5f.dp()
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

  private fun Float.dp(): Float {
    return this * resources.displayMetrics.density
  }
}