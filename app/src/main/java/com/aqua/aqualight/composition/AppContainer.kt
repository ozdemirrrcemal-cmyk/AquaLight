package com.aqua.aqualight.composition

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.application.auth.AccountSecurityOperations
import com.aqua.aqualight.application.auth.AuthOperations
import com.aqua.aqualight.application.auth.AuthenticatedOwnerIdentity
import com.aqua.aqualight.application.auth.SessionExitOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.application.user.UserSettingsOperations
import com.aqua.aqualight.data.auth.AuthRepository
import com.aqua.aqualight.data.auth.DefaultSessionExitOperations
import com.aqua.aqualight.data.auth.FirebaseAccountSecurityOperations
import com.aqua.aqualight.data.auth.FirebaseAuthOperations
import com.aqua.aqualight.data.auth.LogoutManager
import com.aqua.aqualight.data.feedback.FirebaseFeedbackSubmissionOperations
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.user.DefaultUserProfileOperations
import com.aqua.aqualight.data.user.DefaultUserSettingsOperations
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.platform.auth.DefaultGoogleIdentityClient
import com.aqua.aqualight.platform.auth.GoogleIdentityClient
import com.aqua.aqualight.platform.media.AndroidImageMediaProcessor
import com.aqua.aqualight.platform.media.ImageMediaProcessor
import com.aqua.aqualight.platform.vision.MlKitProvisioningQrFrameDecoderFactory
import com.aqua.aqualight.platform.vision.ProvisioningQrFrameDecoderFactory
import com.aqua.aqualight.ui.auth.viewmodel.AuthViewModelFactory

/**
 * Process composition root.
 *
 * The container itself is owner-neutral. Authenticated-owner repositories are
 * opened only by OwnerSessionCoordinator and consumed through the fail-closed
 * OwnerDependencyGraphResolver after that session barrier has completed.
 */
interface AppContainer {
    val startupAppearanceCache: StartupAppearanceCache
    val userPreferencesManager: UserPreferencesManager
    val userSettingsOperations: UserSettingsOperations
    val notificationPreferenceUseCase: NotificationPreferenceUseCase
    val notificationDispatchUseCase: NotificationDispatchUseCase
    val deviceFirmwareNotificationRouteOperations:
        DeviceFirmwareNotificationRouteOperations
        get() = DeviceFirmwareNotificationRouteOperations {
            DeviceFirmwareNotificationRouteDecision.DEFER
        }
    val authenticatedOwnerIdentity: AuthenticatedOwnerIdentity
    val userProfileOperations: UserProfileOperations
    val feedbackSubmissionOperations: FeedbackSubmissionUseCase
    val imageMediaProcessor: ImageMediaProcessor
    val provisioningDraftOperations: ProvisioningDraftOperations
    val provisioningQrFrameDecoderFactory: ProvisioningQrFrameDecoderFactory
    val authViewModelFactory: ViewModelProvider.Factory
    val defaultViewModelFactory: ViewModelProvider.Factory
    val sessionExitOperations: SessionExitOperations
    val accountSecurityOperations: AccountSecurityOperations
    val googleIdentityClient: GoogleIdentityClient
}

internal class DefaultAppContainer(
    context: Context
) : AppContainer {

    private val appContext = context.applicationContext
    private val notificationPlatform by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationPlatform.get(appContext)
    }

    override val startupAppearanceCache: StartupAppearanceCache by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        StartupAppearanceCache.create(appContext)
    }

    override val userPreferencesManager: UserPreferencesManager by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        UserPreferencesManager.create(appContext)
    }

    override val userSettingsOperations: UserSettingsOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        DefaultUserSettingsOperations(
            preferences = userPreferencesManager,
            startupAppearanceCache = startupAppearanceCache,
            reconcileDeviceUpdateWork = {
                notificationPlatform.deviceUpdateWorkCoordinator.reconcileOwner(
                    authenticatedOwnerIdentity.requireOwnerUid()
                )
            }
        )
    }

    override val notificationPreferenceUseCase: NotificationPreferenceUseCase by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        notificationPlatform.preferenceUseCase
    }

    override val notificationDispatchUseCase: NotificationDispatchUseCase by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        notificationPlatform.dispatchUseCase
    }

    override val userProfileOperations: UserProfileOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        DefaultUserProfileOperations(appContext, userPreferencesManager)
    }

    override val feedbackSubmissionOperations: FeedbackSubmissionUseCase by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        FeedbackSubmissionUseCase(
            FirebaseFeedbackSubmissionOperations.create()
        )
    }

    override val imageMediaProcessor: ImageMediaProcessor by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        AndroidImageMediaProcessor(appContext)
    }

    private val ownerGraphResolver: OwnerDependencyGraphResolver by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        ActiveOwnerDependencyGraphResolver(
            context = appContext,
            deviceFirmwareNotifications = notificationPlatform.deviceFirmwareUpdates,
            notificationPreferenceUseCase = notificationPreferenceUseCase,
            userPreferencesManager = userPreferencesManager
        )
    }

    override val deviceFirmwareNotificationRouteOperations:
        DeviceFirmwareNotificationRouteOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        ResolvingDeviceFirmwareNotificationRouteOperations(ownerGraphResolver)
    }

    override val authenticatedOwnerIdentity: AuthenticatedOwnerIdentity by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        ResolvingAuthenticatedOwnerIdentity(ownerGraphResolver)
    }

    override val provisioningDraftOperations: ProvisioningDraftOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        ResolvingProvisioningDraftOperations(ownerGraphResolver)
    }

    override val provisioningQrFrameDecoderFactory: ProvisioningQrFrameDecoderFactory by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        MlKitProvisioningQrFrameDecoderFactory()
    }

    private val authRepository: AuthRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        AuthRepository.create(appContext)
    }

    private val authOperations: AuthOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        FirebaseAuthOperations(authRepository)
    }

    override val authViewModelFactory: ViewModelProvider.Factory by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        AuthViewModelFactory(authOperations)
    }

    override val defaultViewModelFactory: ViewModelProvider.Factory by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        AquaViewModelFactory(
            processFactory = ProcessViewModelFactory(),
            ownerFactory = OwnerViewModelFactory(
                context = appContext,
                userProfileOperations = userProfileOperations,
                notificationPreferenceUseCase = notificationPreferenceUseCase,
                ownerGraphResolver = ownerGraphResolver
            )
        )
    }

    private val logoutManager: LogoutManager by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        LogoutManager.create(appContext)
    }

    override val sessionExitOperations: SessionExitOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        DefaultSessionExitOperations(logoutManager)
    }

    override val accountSecurityOperations: AccountSecurityOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        FirebaseAccountSecurityOperations.create(appContext)
    }

    override val googleIdentityClient: GoogleIdentityClient by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        DefaultGoogleIdentityClient(appContext)
    }
}

fun Context.requireAppContainer(): AppContainer {
    val application = applicationContext
    check(application is AquaApp) {
        "AquaLight application container is unavailable."
    }
    return application.appContainer
}
