package com.aqua.aqualight.composition

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.application.auth.AccountSecurityOperations
import com.aqua.aqualight.application.auth.AuthOperations
import com.aqua.aqualight.application.auth.SessionExitOperations
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.application.user.UserSettingsOperations
import com.aqua.aqualight.data.auth.AuthRepository
import com.aqua.aqualight.data.auth.DefaultSessionExitOperations
import com.aqua.aqualight.data.auth.FirebaseAccountSecurityOperations
import com.aqua.aqualight.data.auth.FirebaseAuthOperations
import com.aqua.aqualight.data.auth.LogoutManager
import com.aqua.aqualight.data.feedback.FirebaseFeedbackSubmissionOperations
import com.aqua.aqualight.data.user.DefaultUserProfileOperations
import com.aqua.aqualight.data.user.DefaultUserSettingsOperations
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.platform.auth.DefaultGoogleIdentityClient
import com.aqua.aqualight.platform.auth.GoogleIdentityClient
import com.aqua.aqualight.ui.auth.viewmodel.AuthViewModelFactory

/**
 * Application composition root.
 *
 * Object construction belongs here rather than in Fragments or ViewModels. The
 * container is process-scoped and only exposes already-wired dependencies.
 */
interface AppContainer {
    val startupAppearanceCache: StartupAppearanceCache
    val userPreferencesManager: UserPreferencesManager
    val userSettingsOperations: UserSettingsOperations
    val userProfileOperations: UserProfileOperations
    val feedbackSubmissionUseCase: FeedbackSubmissionUseCase
    val authViewModelFactory: ViewModelProvider.Factory
    val sessionExitOperations: SessionExitOperations
    val accountSecurityOperations: AccountSecurityOperations
    val googleIdentityClient: GoogleIdentityClient
}

internal class DefaultAppContainer(
    context: Context
) : AppContainer {

    private val appContext = context.applicationContext

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
            context = appContext,
            preferences = userPreferencesManager,
            startupAppearanceCache = startupAppearanceCache
        )
    }

    override val userProfileOperations: UserProfileOperations by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        DefaultUserProfileOperations(userPreferencesManager)
    }

    override val feedbackSubmissionUseCase: FeedbackSubmissionUseCase by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        FeedbackSubmissionUseCase(
            FirebaseFeedbackSubmissionOperations.create()
        )
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
