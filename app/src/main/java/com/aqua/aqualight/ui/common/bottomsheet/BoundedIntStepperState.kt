package com.aqua.aqualight.ui.common.bottomsheet

internal class BoundedIntStepperState(
    initialValue: Int,
    private val minimumValue: Int,
    private val maximumValue: Int,
    private val step: Int
) {
    init {
        require(minimumValue <= maximumValue) {
            "minimumValue must not exceed maximumValue."
        }
        require(initialValue in minimumValue..maximumValue) {
            "initialValue must be inside the allowed range."
        }
        require(step > 0) { "step must be positive." }
    }

    var value: Int = initialValue
        private set

    val canIncrement: Boolean
        get() = value < maximumValue

    val canDecrement: Boolean
        get() = value > minimumValue

    fun increment() {
        value = (value + step).coerceAtMost(maximumValue)
    }

    fun decrement() {
        value = (value - step).coerceAtLeast(minimumValue)
    }
}
