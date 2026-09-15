package com.aqua.aqualight.application.devices.light.smartsetup

data class SmartSetupLifecycle(
    val setupDay: Int,
    val stage: SmartSetupLifecycleStage
)

object SmartSetupLifecycleClassifier {
    fun classify(setupDateEpochDay: Long, evaluationEpochDay: Long): SmartSetupLifecycle? {
        if (setupDateEpochDay > evaluationEpochDay) return null
        val elapsedDays = evaluationEpochDay - setupDateEpochDay
        if (elapsedDays >= Int.MAX_VALUE) return null
        val setupDay = (elapsedDays + 1L).toInt()
        return SmartSetupLifecycle(
            setupDay = setupDay,
            stage = when (setupDay) {
                in 1..STARTUP_END_DAY -> SmartSetupLifecycleStage.STARTUP
                in STARTUP_END_DAY + 1..ESTABLISHING_END_DAY ->
                    SmartSetupLifecycleStage.ESTABLISHING
                else -> SmartSetupLifecycleStage.MATURE
            }
        )
    }

    const val STARTUP_END_DAY = 21
    const val ESTABLISHING_END_DAY = 90
}
