package com.aqua.aqualight.composition

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.data.auth.AuthRepository
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
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
    val authViewModelFactory: ViewModelProvider.Factory
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

    private val authRepository: AuthRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        AuthRepository.create(appContext)
    }

    override val authViewModelFactory: ViewModelProvider.Factory by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        AuthViewModelFactory(authRepository)
    }
}

fun Context.requireAppContainer(): AppContainer {
    val application = applicationContext
    check(application is AquaApp) {
        "AquaLight application container is unavailable."
    }
    return application.appContainer
}
