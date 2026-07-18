package com.aqua.aqualight.ui.common.permission

import com.aqua.aqualight.platform.permissions.AppCapability

/** Serializable value state for one pending permission-gated user action. */
internal data class CapabilityPermissionContinuationSnapshot(
    val capabilityName: String?,
    val actionToken: String?,
    val notificationChannelId: String?,
    val waitingForSettings: Boolean
)

/**
 * One-shot continuation state used by [CapabilityPermissionCoordinator].
 *
 * Android can report a Settings return through both lifecycle resume and an Activity
 * Result callback. This state consumes the action atomically so the user operation can
 * never run twice, and clears denied Settings returns instead of retaining stale work.
 */
internal class CapabilityPermissionContinuationState {

    var pendingCapability: AppCapability? = null
        private set
    var pendingActionToken: String? = null
        private set
    var pendingNotificationChannelId: String? = null
        private set
    var waitingForSettings: Boolean = false
        private set

    fun begin(
        capability: AppCapability,
        actionToken: String,
        notificationChannelId: String? = null
    ) {
        require(actionToken.isNotBlank()) { "Permission action token must not be blank." }
        pendingCapability = capability
        pendingActionToken = actionToken
        pendingNotificationChannelId = notificationChannelId
            ?.trim()
            ?.takeIf(String::isNotBlank)
        waitingForSettings = false
    }

    fun markWaitingForSettings() {
        check(pendingCapability != null && !pendingActionToken.isNullOrBlank()) {
            "Cannot open Settings without a pending permission action."
        }
        waitingForSettings = true
    }

    fun consumeSettingsReturn(isGranted: Boolean): String? {
        if (!waitingForSettings) return null
        waitingForSettings = false
        return if (isGranted) consumeAction() else {
            clear()
            null
        }
    }

    fun consumeIfGranted(isGranted: Boolean): String? {
        return if (isGranted) consumeAction() else null
    }

    fun consumeAction(): String? {
        val action = pendingActionToken?.takeIf(String::isNotBlank) ?: return null
        clear()
        return action
    }

    fun clear() {
        pendingCapability = null
        pendingActionToken = null
        pendingNotificationChannelId = null
        waitingForSettings = false
    }

    fun snapshot(): CapabilityPermissionContinuationSnapshot {
        return CapabilityPermissionContinuationSnapshot(
            capabilityName = pendingCapability?.name,
            actionToken = pendingActionToken,
            notificationChannelId = pendingNotificationChannelId,
            waitingForSettings = waitingForSettings
        )
    }

    fun restore(snapshot: CapabilityPermissionContinuationSnapshot?) {
        val capability = snapshot
            ?.capabilityName
            ?.let { name -> runCatching { AppCapability.valueOf(name) }.getOrNull() }
        val action = snapshot?.actionToken?.trim()?.takeIf(String::isNotBlank)

        if (capability == null || action == null) {
            clear()
            return
        }

        pendingCapability = capability
        pendingActionToken = action
        pendingNotificationChannelId = snapshot.notificationChannelId
            ?.trim()
            ?.takeIf(String::isNotBlank)
        waitingForSettings = snapshot.waitingForSettings
    }
}
