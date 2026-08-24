package com.aqua.aqualight.application.devices

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/** Read-only application boundary for device status and settings summaries. */
interface DeviceStatusOperations {
    val statuses: Flow<List<OwnerDeviceStatusSnapshot>>

    fun start(scope: CoroutineScope): Job
}

data class OwnerDeviceStatusSnapshot(
    val deviceUid: String,
    val displayName: String,
    val serialText: String,
    val family: OwnerDeviceFamily,
    val availability: OwnerDeviceAvailability,
    val ipAddress: String = "",
    val lastSeenAtMillis: Long = 0L,
    /** Exact catalog-owned physical Dosing channel count; null when not applicable or unresolved. */
    val dosingChannelCount: Int? = null
)
