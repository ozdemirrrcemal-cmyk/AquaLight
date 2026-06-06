package com.aqua.aqualight.data.devices.light.runtime

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LightAppVisibilityMonitor {

    private val _isForeground =
        MutableStateFlow(false)

    val isForeground: StateFlow<Boolean> =
        _isForeground.asStateFlow()

    private val lock = Any()

    private var isRegistered = false
    private var startedActivityCount = 0

    fun register(
        context: Context
    ) {
        val application = context.applicationContext as? Application
            ?: return

        synchronized(lock) {
            if (isRegistered) {
                return
            }

            isRegistered = true

            // Bu monitor genelde ekrandaki ViewModel'den başlatıldığı için
            // ilk registration anında app'i foreground kabul ediyoruz.
            startedActivityCount = 1
            _isForeground.value = true

            application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {

                    override fun onActivityStarted(
                        activity: Activity
                    ) {
                        synchronized(lock) {
                            startedActivityCount += 1
                            _isForeground.value = startedActivityCount > 0
                        }
                    }

                    override fun onActivityStopped(
                        activity: Activity
                    ) {
                        synchronized(lock) {
                            startedActivityCount =
                                (startedActivityCount - 1).coerceAtLeast(0)

                            _isForeground.value = startedActivityCount > 0
                        }
                    }

                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?
                    ) = Unit

                    override fun onActivityResumed(
                        activity: Activity
                    ) = Unit

                    override fun onActivityPaused(
                        activity: Activity
                    ) = Unit

                    override fun onActivitySaveInstanceState(
                        activity: Activity,
                        outState: Bundle
                    ) = Unit

                    override fun onActivityDestroyed(
                        activity: Activity
                    ) = Unit
                }
            )
        }
    }
}