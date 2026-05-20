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

  private val axisLineColor = Color.parseColor("#2E4258")

  private val todayNodeColor = Color.parseColor("#46DCC9")
  private val upcomingNodeColor = Color.parseColor("#7C8897")
  private val pastNodeColor = Color.parseColor("#38B8A8")

  private val nodeCoverColor = Color.parseColor("#152B45")

  private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = axisLineColor
    strokeWidth = 2.dp().toFloat()
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    pathEffect = DashPathEffect(
      floatArrayOf(
        8.dp().toFloat(),
        9.dp().toFloat()
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
    strokeWidth = 3.dp().toFloat()
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

    val centerX = width / 2f

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
      13.dp().toFloat(),
      nodeCoverPaint
    )

    canvas.drawCircle(
      centerX,
      centerY,
      10.dp().toFloat(),
      nodeStrokePaint
    )

    if (status != TimelineDayStatus.UPCOMING) {
      canvas.drawCircle(
        centerX,
        centerY,
        4.dp().toFloat(),
        nodeFillPaint
      )
    }
  }

  private fun getNodeColor(): Int {
    return when (status) {
      TimelineDayStatus.TODAY -> todayNodeColor
      TimelineDayStatus.UPCOMING -> upcomingNodeColor
      TimelineDayStatus.PAST -> pastNodeColor
    }
  }

  private fun Int.dp(): Int {
    return (this * resources.displayMetrics.density).toInt()
  }
}