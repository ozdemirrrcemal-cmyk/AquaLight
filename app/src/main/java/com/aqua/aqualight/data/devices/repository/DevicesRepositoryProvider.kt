package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-level repository holder.
 *
 * A real owner change tears down old runtime/discovery state before loading the newly authenticated
 * owner's durable devices. Re-entering MainActivity for the same owner keeps the active runtime
 * session instead of reconnecting unnecessarily.
 */
object DevicesRepositoryProvider {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var instance: DevicesRepository? = null

    @Volatile
    private var activeOwnerUid: String = ""

    fun get(context: Context? = null): DevicesRepository {
        return repository(context).also { deviceRepository ->
            deviceRepository.start(repositoryScope)
        }
    }

    fun restartForCurrentOwner(
        context: Context
    ) {
        val ownerUid = UserDataScope.normalizeOwnerUid(
            UserDataScope.currentUid()
        )

        if (ownerUid.isBlank()) {
            return
        }

        val deviceRepository = repository(context.applicationContext)
        val ownerChanged = synchronized(this) {
            if (activeOwnerUid == ownerUid) {
                false
            } else {
                activeOwnerUid = ownerUid
                true
            }
        }

        if (!ownerChanged) {
            deviceRepository.start(repositoryScope)
            return
        }

        repositoryScope.launch {
            deviceRepository.stopSession()
            deviceRepository.start(repositoryScope)
        }
    }

    suspend fun stopSession() {
        instance?.stopSession()
        activeOwnerUid = ""
    }

    private fun repository(context: Context?): DevicesRepository {
        return instance ?: synchronized(this) {
            instance ?: createRepository(context).also { created ->
                instance = created
            }
        }
    }

    private fun createRepository(context: Context?): DevicesRepository {
        val appContext = context?.applicationContext
        return if (appContext != null) {
            DevicesRepository(
                knownStore = DeviceKnownStore(appContext),
                runtimeRepository = DeviceRuntimeRepository.withCredentialStore(appContext),
                connectivityObserver = DeviceConnectivityObserver(appContext)
            )
        } else {
            DevicesRepository()
        }
    }
}
