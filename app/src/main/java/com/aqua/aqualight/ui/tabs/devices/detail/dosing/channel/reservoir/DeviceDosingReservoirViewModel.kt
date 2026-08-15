package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityValidation
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceDosingReservoirDraft(
    val reservoirCapacityMicroliters: Long =
        DeviceDosingReservoirCapacityPolicy.DEFAULT_CAPACITY_MICROLITERS,
    val trackingEnabled: Boolean = false,
    val lowLevelAlertEnabled: Boolean = false
)

internal enum class DeviceDosingReservoirNotificationAvailability {
    AVAILABLE,
    OWNER_PREFERENCE_DISABLED,
    ANDROID_BLOCKED
}

/** Single presentation-state owner for the firmware-independent reservoir draft. */
internal class DeviceDosingReservoirViewModel : ViewModel() {
    private val mutableDraft = MutableStateFlow(DeviceDosingReservoirDraft())
    val draft: StateFlow<DeviceDosingReservoirDraft> = mutableDraft.asStateFlow()
    private val mutableCapacityRejection =
        MutableStateFlow<DeviceDosingReservoirCapacityRejection?>(null)
    val capacityRejection: StateFlow<DeviceDosingReservoirCapacityRejection?> =
        mutableCapacityRejection.asStateFlow()
    private val mutableNotificationAvailability = MutableStateFlow(
        DeviceDosingReservoirNotificationAvailability.AVAILABLE
    )
    val notificationAvailability: StateFlow<DeviceDosingReservoirNotificationAvailability> =
        mutableNotificationAvailability.asStateFlow()
    private var initialized = false

    fun bindInitial(initial: DeviceDosingReservoirDraft?) {
        if (initialized) return
        initialized = true
        mutableDraft.value = (initial ?: DeviceDosingReservoirDraft()).let { draft ->
            draft.copy(
                reservoirCapacityMicroliters =
                    DeviceDosingReservoirCapacityPolicy.normalizePersistedMicroliters(
                        draft.reservoirCapacityMicroliters
                    )
            )
        }
    }

    fun currentDraft(): DeviceDosingReservoirDraft = mutableDraft.value

    fun setTrackingEnabled(enabled: Boolean) {
        mutableDraft.value = mutableDraft.value.copy(trackingEnabled = enabled)
    }

    fun setLowLevelAlertEnabled(enabled: Boolean) {
        mutableDraft.value = mutableDraft.value.copy(lowLevelAlertEnabled = enabled)
        if (!enabled) {
            mutableNotificationAvailability.value =
                DeviceDosingReservoirNotificationAvailability.AVAILABLE
        }
    }

    fun setNotificationAvailability(
        availability: DeviceDosingReservoirNotificationAvailability
    ) {
        mutableNotificationAvailability.value = availability
    }

    fun setCapacityInput(rawValue: String, locale: Locale) {
        when (val validation = DeviceDosingReservoirCapacityPolicy.validate(rawValue, locale)) {
            is DeviceDosingReservoirCapacityValidation.Accepted -> {
                mutableDraft.value = mutableDraft.value.copy(
                    reservoirCapacityMicroliters = validation.capacityMicroliters
                )
                mutableCapacityRejection.value = null
            }
            is DeviceDosingReservoirCapacityValidation.Rejected -> {
                mutableCapacityRejection.value = validation.reason
            }
        }
    }
}
