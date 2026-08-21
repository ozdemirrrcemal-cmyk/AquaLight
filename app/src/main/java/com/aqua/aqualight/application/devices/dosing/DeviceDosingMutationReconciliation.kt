package com.aqua.aqualight.application.devices.dosing

/**
 * Save-result envelope consumed by long-lived editors.
 *
 * Retry-safe assignment, revision rebase and transport reconciliation live in the central data
 * mutation coordinator. [authoritativeSnapshot] is reserved for the final state returned after an
 * exhausted real conflict; editors never run a second mutation loop of their own.
 */
internal data class DeviceDosingMutationReconciliation(
    val result: DeviceDosingChannelOperationResult,
    val authoritativeSnapshot: DeviceDosingChannelSnapshot? = null
)
