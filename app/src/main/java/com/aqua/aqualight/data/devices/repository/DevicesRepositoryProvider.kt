package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeDataRepository
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Process-level owner-bound device repository holder.
 *
 * A repository can never be created without Android context or authenticated owner identity. Owner
 * switches pass through the suspending [clear] barrier. Foreground state is retained independently
 * of owner creation so a newly opened owner repository starts with the correct probe policy.
 */
object DevicesRepositoryProvider {

    private data class Entry(
        val ownerUid: String,
        val scope: CoroutineScope,
        val repository: DevicesRepository,
        val runtimeData: DeviceRuntimeDataRepository
    ) : AutoCloseable {
        override fun close() {
            try {
                runtimeData.close()
                repository.stop()
            } finally {
                scope.cancel()
            }
        }

        suspend fun shutdown() {
            try {
                runtimeData.close()
                repository.shutdown()
            } finally {
                val scopeJob = scope.coroutineContext[Job]
                scope.cancel()
                scopeJob?.join()
            }
        }
    }

    @Volatile
    private var entry: Entry? = null

    @Volatile
    private var closingOwnerUid: String? = null

    @Volatile
    private var appForeground: Boolean = false

    fun get(
        context: Context
    ): DevicesRepository = getOrCreateEntry(context).repository

    fun getRuntimeData(
        context: Context
    ): DeviceRuntimeDataRepository = getOrCreateEntry(context).runtimeData

    private fun getOrCreateEntry(context: Context): Entry {
        val appContext = context.applicationContext
        val ownerUid = UserDataScope.requireCurrentUid()
        val current = entry

        if (current?.ownerUid == ownerUid) {
            return current
        }

        return synchronized(this) {
            check(closingOwnerUid == null) {
                "Previous owner device repository is still shutting down."
            }

            val synchronizedEntry = entry
            if (synchronizedEntry?.ownerUid == ownerUid) {
                synchronizedEntry
            } else {
                check(synchronizedEntry == null) {
                    "Owner repository must be cleared before switching authenticated owners."
                }

                val repositoryScope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO
                )
                val connectivityObserver = DeviceConnectivityObserver(appContext)
                val repository = DevicesRepository(
                    discoveryRepository = DeviceDiscoveryRepository.withConnectivityObserver(
                        connectivityObserver
                    ),
                    knownStore = DeviceKnownStore(
                        context = appContext,
                        ownerUid = ownerUid
                    ),
                    runtimeRepository =
                        DeviceRuntimeRepository.withCredentialStoreOnLocalNetwork(
                            context = appContext,
                            ownerUid = ownerUid,
                            networkProvider = connectivityObserver::currentLocalNetwork
                        ),
                    connectivityObserver = connectivityObserver
                )
                val runtimeData = DeviceRuntimeDataRepository(repository)

                repository.setAppForeground(appForeground)
                runtimeData.start(repositoryScope)
                repository.start(repositoryScope)

                Entry(
                    ownerUid = ownerUid,
                    scope = repositoryScope,
                    repository = repository,
                    runtimeData = runtimeData
                ).also { created -> entry = created }
            }
        }
    }

    fun setAppForeground(isForeground: Boolean) {
        appForeground = isForeground
        entry?.repository?.setAppForeground(isForeground)
    }

    suspend fun clear(
        expectedOwnerUid: String? = null
    ): Boolean {
        val normalizedExpected = expectedOwnerUid
            ?.trim()
            ?.takeIf(String::isNotBlank)

        val current = synchronized(this) {
            check(closingOwnerUid == null) {
                "Owner device repository shutdown is already in progress."
            }

            val active = entry
            if (
                normalizedExpected != null &&
                active?.ownerUid != normalizedExpected
            ) {
                return@synchronized null
            }

            if (active != null) {
                entry = null
                closingOwnerUid = active.ownerUid
            }
            active
        } ?: return false

        return try {
            current.shutdown()
            true
        } finally {
            synchronized(this) {
                if (closingOwnerUid == current.ownerUid) {
                    closingOwnerUid = null
                }
            }
        }
    }

    fun currentOwnerUid(): String? = entry?.ownerUid

    fun currentRepository(
        expectedOwnerUid: String
    ): DevicesRepository? {
        val normalizedOwnerUid = expectedOwnerUid.trim()
        if (normalizedOwnerUid.isBlank()) return null

        return entry?.takeIf { current ->
            current.ownerUid == normalizedOwnerUid
        }?.repository
    }

    fun currentRuntimeData(
        expectedOwnerUid: String
    ): DeviceRuntimeDataRepository? {
        val normalizedOwnerUid = expectedOwnerUid.trim()
        if (normalizedOwnerUid.isBlank()) return null

        return entry?.takeIf { current ->
            current.ownerUid == normalizedOwnerUid
        }?.runtimeData
    }
}
