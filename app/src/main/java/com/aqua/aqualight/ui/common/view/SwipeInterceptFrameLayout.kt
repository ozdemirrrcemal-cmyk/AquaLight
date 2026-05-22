package com.aqua.aqualight.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeInterceptFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var isHorizontalSwipe: Boolean = false
    private var swipeEnabled: Boolean = true

    private var onSwipeLeft: (() -> Unit)? = null
    private var onSwipeRight: (() -> Unit)? = null

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private val interceptDistance: Float
        get() = maxOf(touchSlop.toFloat(), 12.dp().toFloat())

    private val minSwipeDistance: Float
        get() = 46.dp().toFloat()

    fun setSwipeEnabled(
        enabled: Boolean
    ) {
        swipeEnabled = enabled
    }

    fun setOnSwipeLeftListener(
        listener: (() -> Unit)?
    ) {
        onSwipeLeft = listener
    }

    fun setOnSwipeRightListener(
        listener: (() -> Unit)?
    ) {
        onSwipeRight = listener
    }

    override fun onInterceptTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (!swipeEnabled) {
            return super.onInterceptTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isHorizontalSwipe = false

                parent?.requestDisallowInterceptTouchEvent(false)

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val diffX = event.x - downX
                val diffY = event.y - downY

                val absX = abs(diffX)
                val absY = abs(diffY)

                if (
                    absX > interceptDistance &&
                    absX > absY * 1.18f
                ) {
                    isHorizontalSwipe = true
                    parent?.requestDisallowInterceptTouchEvent(true)

                    return true
                }

                return false
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                isHorizontalSwipe = false
                parent?.requestDisallowInterceptTouchEvent(false)

                return false
            }

            else -> {
                return false
            }
        }
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (!swipeEnabled) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                return isHorizontalSwipe
            }

            MotionEvent.ACTION_UP -> {
                val diffX = event.x - downX
                val diffY = event.y - downY

                val absX = abs(diffX)
                val absY = abs(diffY)

                val shouldSwipe =
                    isHorizontalSwipe &&
                        absX > minSwipeDistance &&
                        absX > absY * 1.12f

                if (shouldSwipe) {
                    if (diffX < 0) {
                        onSwipeLeft?.invoke()
                    } else {
                        onSwipeRight?.invoke()
                    }
                }

                resetTouchState()

                return shouldSwipe
            }

            MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
                return false
            }

            else -> {
                return true
            }
        }
    }

    private fun resetTouchState() {
        isHorizontalSwipe = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}