package com.aqua.aqualight.ui.common.notification

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationChannelState
import com.aqua.aqualight.application.notifications.NotificationDeliveryReadiness
import com.aqua.aqualight.application.notifications.NotificationDeliveryRetrySignal
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
 * Fragment-scoped feature-enablement adapter over the Stage 6 permission coordinator and
 * Stage 7 notification use-case.
 *
 * Feature switches keep owning their domain preference. This class stores no second
 * preference and never posts notifications. It serializes enablement intents and completes
 * the shared Android readiness flow before allowing a feature preference to become enabled.
 */
class NotificationEnablementCoordinator(
    private val fragment: Fragment,
    instanceKey: String,
    private val dependencies: NotificationEnablementDependencies,
    private val callbacks: NotificationEnablementCallbacks
) {
    private val operationGate = NotificationEnablementOperationGate()
    private var refreshJob: Job? = null
    private var refreshGeneration: Long = 0L
    private val capabilityCoordinator = CapabilityPermissionCoordinator(
        fragment = fragment,
        instanceKey = instanceKey
    ) { continuationToken ->
        val continuation = NotificationEnablementContinuation.parse(continuationToken)
        if (continuation != null) {
            cancelRefresh()
            launchEvaluation(
                ticket = operationGate.resume(continuation.actionToken),
                returnedFrom = continuation.step
            )
        }
    }

    fun requestEnable(actionToken: String) {
        val ticket = operationGate.begin(actionToken) ?: return
        capabilityCoordinator.cancelPending()
        cancelRefresh()
        launchEvaluation(ticket = ticket, returnedFrom = null)
    }

    fun refresh(actionToken: String) {
        val request = if (operationGate.hasActiveOperation) {
            null
        } else {
            dependencies.requestResolver(actionToken)
        } ?: return
        cancelRefresh()
        val generation = refreshGeneration
        val lifecycleOwner = fragment.viewLifecycleOwnerLiveData.value ?: return
        val job = lifecycleOwner.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                val failure = runCatching {
                    val state = evaluate(request).state
                    if (isCurrentRefresh(generation)) {
                        callbacks.onStateChanged(actionToken, state)
                    }
                }.exceptionOrNull()
                when (failure) {
                    null -> Unit
                    is CancellationException -> throw failure
                    else -> if (isCurrentRefresh(generation)) {
                        callbacks.onFailure(actionToken, failure)
                    }
                }
            } finally {
                if (refreshGeneration == generation) refreshJob = null
            }
        }
        refreshJob = job
        job.start()
    }

    fun cancelPending() {
        operationGate.cancel()
        cancelRefresh()
        capabilityCoordinator.cancelPending()
    }

    private fun launchEvaluation(
        ticket: NotificationEnablementOperationGate.Ticket,
        returnedFrom: NotificationEnablementStep?
    ) {
        val request = dependencies.requestResolver(ticket.actionToken)
        if (request == null) {
            operationGate.complete(ticket)
            return
        }
        val lifecycleOwner = fragment.viewLifecycleOwnerLiveData.value
        if (lifecycleOwner == null) {
            operationGate.complete(ticket)
            return
        }
        val job = lifecycleOwner.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            val failure = runCatching {
                val evaluation = evaluate(request)
                if (!operationGate.isCurrent(ticket)) return@launch
                callbacks.onStateChanged(ticket.actionToken, evaluation.state)
                if (!operationGate.isCurrent(ticket)) return@launch
                if (evaluation.state.step == returnedFrom) {
                    operationGate.complete(ticket)
                    return@launch
                }
                val waitingForPlatform = continueEvaluation(ticket, evaluation)
                if (!waitingForPlatform) operationGate.complete(ticket)
            }.exceptionOrNull()
            when (failure) {
                null -> Unit
                is CancellationException -> throw failure
                else -> if (operationGate.isCurrent(ticket)) {
                    operationGate.complete(ticket)
                    callbacks.onFailure(ticket.actionToken, failure)
                }
            }
        }
        operationGate.attach(ticket, job)
        job.start()
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
        ticket: NotificationEnablementOperationGate.Ticket,
        evaluation: NotificationEnablementEvaluation
    ): Boolean {
        if (!operationGate.isCurrent(ticket)) return false
        val step = evaluation.state.step
        val continuationToken = NotificationEnablementContinuation(
            actionToken = ticket.actionToken,
            step = step
        ).encode()
        return when (step) {
            NotificationEnablementStep.REQUEST_RUNTIME_PERMISSION -> {
                capabilityCoordinator.runWhenGranted(
                    capability = AppCapability.NOTIFICATIONS,
                    actionToken = continuationToken
                )
                true
            }
            NotificationEnablementStep.OPEN_APP_SETTINGS -> {
                capabilityCoordinator.openSettingsFor(
                    capability = AppCapability.NOTIFICATIONS,
                    actionToken = continuationToken
                )
                true
            }
            NotificationEnablementStep.OPEN_CHANNEL_SETTINGS -> {
                capabilityCoordinator.openNotificationChannelSettingsFor(
                    channelId = evaluation.notificationPreferences.channelId(
                        evaluation.request.category
                    ),
                    actionToken = continuationToken
                )
                true
            }
            NotificationEnablementStep.REQUEST_PRECISE_REMINDERS -> {
                capabilityCoordinator.runWhenGranted(
                    capability = AppCapability.PRECISE_REMINDERS,
                    actionToken = continuationToken
                )
                true
            }
            NotificationEnablementStep.READY -> {
                completeReadyEvaluation(ticket, evaluation)
                false
            }
        }
    }

    private suspend fun completeReadyEvaluation(
        ticket: NotificationEnablementOperationGate.Ticket,
        evaluation: NotificationEnablementEvaluation
    ) {
        if (!evaluation.state.ownerPreferenceEnabled) {
            evaluation.notificationPreferences.setEnabled(
                ownerUid = evaluation.ownerUid,
                enabled = true
            )
        }
        NotificationDeliveryRetrySignal.requestRetry()
        if (operationGate.isCurrent(ticket)) {
            callbacks.onStateChanged(
                ticket.actionToken,
                NotificationEnablementState(
                    ownerPreferenceEnabled = true,
                    step = NotificationEnablementStep.READY
                )
            )
        }
        if (operationGate.isCurrent(ticket)) callbacks.onReady(ticket.actionToken)
    }

    private fun cancelRefresh() {
        refreshGeneration = if (refreshGeneration == Long.MAX_VALUE) {
            0L
        } else {
            refreshGeneration + 1L
        }
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun isCurrentRefresh(generation: Long): Boolean =
        generation == refreshGeneration && !operationGate.hasActiveOperation
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
