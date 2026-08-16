package com.aqua.aqualight.application.devices.dosing

internal val DeviceDosingCalibrationSnapshot.hasActiveCalibrationSession: Boolean
    get() = sessionPhase != DeviceDosingCalibrationSessionPhase.IDLE
