package com.aqua.aqualight.ui.common.permission

import com.aqua.aqualight.platform.permissions.AppCapability

internal enum class CapabilityPermissionExplanationMode {
    RATIONALE,
    OPEN_SETTINGS
}

/** Serializable value state for one pending permission-gated user action. */
internal data class CapabilityPermissionContinuationSnapshot(
    val capabilityName: String?,
    val actionToken: String?,
    val notificationChannelId: String?,
    val waitingForSettings: Boolean,
    val explanationModeName: String? = null,
    val waitingForRuntimePermission: Boolean = false
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
    var pendingExplanationMode: CapabilityPermissionExplanationMode? = null
        private set
    var waitingForRuntimePermission: Boolean = false
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
        pendingExplanationMode = null
        waitingForRuntimePermission = false
        waitingForSettings = false
    }

    fun markShowingExplanation(mode: CapabilityPermissionExplanationMode) {
        check(pendingCapability != null && !pendingActionToken.isNullOrBlank()) {
            "Cannot show an explanation without a pending permission action."
        }
        pendingExplanationMode = mode
        waitingForRuntimePermission = false
        waitingForSettings = false
    }

    fun markWaitingForRuntimePermission() {
        check(pendingCapability != null && !pendingActionToken.isNullOrBlank()) {
            "Cannot request permission without a pending action."
        }
        pendingExplanationMode = null
        waitingForRuntimePermission = true
        waitingForSettings = false
    }

    fun markWaitingForSettings() {
        check(pendingCapability != null && !pendingActionToken.isNullOrBlank()) {
            "Cannot open Settings without a pending permission action."
        }
        pendingExplanationMode = null
        waitingForRuntimePermission = false
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
        pendingExplanationMode = null
        waitingForRuntimePermission = false
        waitingForSettings = false
    }

    fun snapshot(): CapabilityPermissionContinuationSnapshot {
        return CapabilityPermissionContinuationSnapshot(
            capabilityName = pendingCapability?.name,
            actionToken = pendingActionToken,
            notificationChannelId = pendingNotificationChannelId,
            waitingForSettings = waitingForSettings,
            explanationModeName = pendingExplanationMode?.name,
            waitingForRuntimePermission = waitingForRuntimePermission
        )
    }

    fun restore(snapshot: CapabilityPermissionContinuationSnapshot?) {
        val capability = snapshot
            ?.capabilityName
            ?.let { name -> runCatching { AppCapability.valueOf(name) }.getOrNull() }
        val action = snapshot?.actionToken?.trim()?.takeIf(String::isNotBlank)
        val explanationMode = snapshot
            ?.explanationModeName
            ?.let { name ->
                runCatching { CapabilityPermissionExplanationMode.valueOf(name) }.getOrNull()
            }

        val invalidExplanationMode = snapshot?.explanationModeName != null &&
            explanationMode == null
        val stageCount = (if (explanationMode != null) 1 else 0) +
            (if (snapshot?.waitingForRuntimePermission == true) 1 else 0) +
            (if (snapshot?.waitingForSettings == true) 1 else 0)
        val invalidIdentity = capability == null || action == null
        val invalidStage = invalidExplanationMode || stageCount > 1
        if (invalidIdentity || invalidStage) {
            clear()
            return
        }

        pendingCapability = capability
        pendingActionToken = action
        pendingNotificationChannelId = snapshot.notificationChannelId
            ?.trim()
            ?.takeIf(String::isNotBlank)
        pendingExplanationMode = explanationMode
        waitingForRuntimePermission = snapshot.waitingForRuntimePermission
        waitingForSettings = snapshot.waitingForSettings
    }
}
