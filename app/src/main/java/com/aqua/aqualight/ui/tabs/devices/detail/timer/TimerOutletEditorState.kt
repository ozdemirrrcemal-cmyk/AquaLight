package com.aqua.aqualight.ui.tabs.devices.detail.timer

data class TimerOutletEditorState(
    val outletIndex: Int,
    val timerRuleIndex: Int,
    val gpioPwm: String,
    val outletName: String,
    val regime: TimerDeviceRepository.OutletRegime,
    val timerEnabled: Boolean,
    val startTime: String,
    val runDurationMinutes: Int,
    val offDurationMinutes: Int,
    val repeatCount: Int,
    val weekDays: List<Boolean>
)