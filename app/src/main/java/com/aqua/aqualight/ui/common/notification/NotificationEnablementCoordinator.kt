package com.aqua.aqualight.ui.common.notification

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationChannelState
import com.aqua.aqualight.application.notifications.NotificationDeliveryReadiness
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

data class NotificationEnablementRequest(
    val category: NotificationCategory,
    val requiresPreciseReminders: Boolean
)

enum class NotificationEnablementStep {
    REQUEST_RUNTIME_PERMISSION,
    OPEN_APP_SETTINGS,
    OPEN_CHANNEL_SETTINGS,
    REQUEST_PRECISE_REMINDERS,
    READY
}

data class NotificationEnablementState(
    val ownerPreferenceEnabled: Boolean,
    val step: NotificationEnablementStep
) {
    val canDeliver: Boolean
        get() = ownerPreferenceEnabled && step == NotificationEnablementStep.READY
}

data class NotificationEnablementDependencies(
    val notificationPreferencesProvider: () -> NotificationPreferenceUseCase,
    val ownerUidProvider: () -> String,
    val requestResolver: (actionToken: String) -> NotificationEnablementRequest?
)

data class NotificationEnablementCallbacks(
    val onReady: (actionToken: String) -> Unit,
    val onStateChanged: (
        actionToken: String,
        state: NotificationEnablementState
    ) -> Unit,
    val onFailure: (actionToken: String, error: Throwable) -> Unit = { _, _ -> }
)

internal object NotificationEnablementDecisionResolver {
    fun resolve(
        delivery: NotificationDeliveryReadiness,
        requiresPreciseReminders: Boolean,
        preciseRemindersGranted: Boolean
    ): NotificationEnablementStep {
        return when {
            !delivery.runtimePermissionGranted -> {
                NotificationEnablementStep.REQUEST_RUNTIME_PERMISSION
            }
            !delivery.appNotificationsEnabled -> NotificationEnablementStep.OPEN_APP_SETTINGS
            delivery.channelState == NotificationChannelState.BLOCKED ||
                delivery.channelState == NotificationChannelState.MISSING -> {
                NotificationEnablementStep.OPEN_CHANNEL_SETTINGS
            }
            requiresPreciseReminders && !preciseRemindersGranted -> {
                NotificationEnablementStep.REQUEST_PRECISE_REMINDERS
            }
            else -> NotificationEnablementStep.READY
        }
    }
}

/**
 * Stateless feature-enablement adapter over the Stage 6 permission coordinator and
 * Stage 7 notification use-case.
 *
 * Feature switches keep owning their domain preference. This class stores no second
 * preference and never posts notifications; it only completes the shared Android
 * readiness flow before allowing a feature preference to become enabled.
 */
class NotificationEnablementCoordinator(
    private val fragment: Fragment,
    instanceKey: String,
    private val dependencies: NotificationEnablementDependencies,
    private val callbacks: NotificationEnablementCallbacks
) {
    private val capabilityCoordinator = CapabilityPermissionCoordinator(
        fragment = fragment,
        instanceKey = instanceKey
    ) { continuationToken ->
        val continuation = NotificationEnablementContinuation.parse(continuationToken)
        if (continuation != null) {
            evaluateAndContinue(
                actionToken = continuation.actionToken,
                returnedFrom = continuation.step
            )
        }
    }

    fun requestEnable(actionToken: String) {
        evaluateAndContinue(actionToken = actionToken, returnedFrom = null)
    }

    fun refresh(actionToken: String) {
        val request = dependencies.requestResolver(actionToken) ?: return
        launchSafely(actionToken) {
            val state = evaluate(request).state
            callbacks.onStateChanged(actionToken, state)
        }
    }

    fun cancelPending() {
        capabilityCoordinator.cancelPending()
    }

    private fun evaluateAndContinue(
        actionToken: String,
        returnedFrom: NotificationEnablementStep?
    ) {
        val request = dependencies.requestResolver(actionToken) ?: return
        launchSafely(actionToken) {
            val evaluation = evaluate(request)
            callbacks.onStateChanged(actionToken, evaluation.state)
            if (evaluation.state.step != returnedFrom) {
                continueEvaluation(actionToken, evaluation)
            }
        }
    }

    private suspend fun evaluate(
        request: NotificationEnablementRequest
    ): NotificationEnablementEvaluation {
        val notificationPreferences = dependencies.notificationPreferencesProvider()
        val ownerUid = dependencies.ownerUidProvider()
        val snapshot = notificationPreferences.snapshot(ownerUid)
        val preciseRemindersGranted = !request.requiresPreciseReminders ||
            capabilityCoordinator.isGranted(AppCapability.PRECISE_REMINDERS)
        return NotificationEnablementEvaluation(
            request = request,
            notificationPreferences = notificationPreferences,
            ownerUid = ownerUid,
            state = NotificationEnablementState(
                ownerPreferenceEnabled = snapshot.ownerPreferenceEnabled,
                step = NotificationEnablementDecisionResolver.resolve(
                    delivery = snapshot.readiness(request.category),
                    requiresPreciseReminders = request.requiresPreciseReminders,
                    preciseRemindersGranted = preciseRemindersGranted
                )
            )
        )
    }

    private suspend fun continueEvaluation(
        actionToken: String,
        evaluation: NotificationEnablementEvaluation
    ) {
        val step = evaluation.state.step
        val continuationToken = NotificationEnablementContinuation(
            actionToken = actionToken,
            step = step
        ).encode()
        when (step) {
            NotificationEnablementStep.REQUEST_RUNTIME_PERMISSION -> {
                capabilityCoordinator.runWhenGranted(
                    capability = AppCapability.NOTIFICATIONS,
                    actionToken = continuationToken
                )
            }
            NotificationEnablementStep.OPEN_APP_SETTINGS -> {
                capabilityCoordinator.openSettingsFor(
                    capability = AppCapability.NOTIFICATIONS,
                    actionToken = continuationToken
                )
            }
            NotificationEnablementStep.OPEN_CHANNEL_SETTINGS -> {
                capabilityCoordinator.openNotificationChannelSettingsFor(
                    channelId = evaluation.notificationPreferences.channelId(
                        evaluation.request.category
                    ),
                    actionToken = continuationToken
                )
            }
            NotificationEnablementStep.REQUEST_PRECISE_REMINDERS -> {
                capabilityCoordinator.runWhenGranted(
                    capability = AppCapability.PRECISE_REMINDERS,
                    actionToken = continuationToken
                )
            }
            NotificationEnablementStep.READY -> {
                if (!evaluation.state.ownerPreferenceEnabled) {
                    evaluation.notificationPreferences.setEnabled(
                        ownerUid = evaluation.ownerUid,
                        enabled = true
                    )
                }
                callbacks.onStateChanged(
                    actionToken,
                    NotificationEnablementState(
                        ownerPreferenceEnabled = true,
                        step = NotificationEnablementStep.READY
                    )
                )
                callbacks.onReady(actionToken)
            }
        }
    }

    private fun launchSafely(
        actionToken: String,
        action: suspend () -> Unit
    ) {
        val lifecycleOwner = fragment.viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val failure = runCatching { action() }.exceptionOrNull()
            when (failure) {
                null -> Unit
                is CancellationException -> throw failure
                else -> callbacks.onFailure(actionToken, failure)
            }
        }
    }
}

private data class NotificationEnablementEvaluation(
    val request: NotificationEnablementRequest,
    val notificationPreferences: NotificationPreferenceUseCase,
    val ownerUid: String,
    val state: NotificationEnablementState
)

private class NotificationEnablementContinuation(
    val actionToken: String,
    val step: NotificationEnablementStep
) {
    fun encode(): String = "$actionToken$SEPARATOR${step.name}"

    companion object {
        private const val SEPARATOR = "::notification-enablement::"

        fun parse(value: String): NotificationEnablementContinuation? {
            return value.lastIndexOf(SEPARATOR)
                .takeIf { separatorIndex -> separatorIndex > 0 }
                ?.let { separatorIndex ->
                    val actionToken = value.substring(0, separatorIndex)
                    val stepName = value.substring(separatorIndex + SEPARATOR.length)
                    runCatching {
                        NotificationEnablementStep.valueOf(stepName)
                    }.getOrNull()?.let { step ->
                        NotificationEnablementContinuation(actionToken, step)
                    }
                }
        }
    }
}
