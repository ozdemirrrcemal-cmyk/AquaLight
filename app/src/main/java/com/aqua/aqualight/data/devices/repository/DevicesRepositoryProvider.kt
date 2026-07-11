package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-level owner-bound device repository holder.
 *
 * A repository can never be created without Android context or authenticated
 * owner identity. Switching owners tears down runtime sessions and in-memory
 * registry state before the next repository is exposed.
 */
object DevicesRepositoryProvider {

    private data class Entry(
        val ownerUid: String,
        val scope: CoroutineScope,
        val repository: DevicesRepository
    )

    @Volatile
    private var entry: Entry? = null

    fun get(
        context: Context
    ): DevicesRepository {
        val appContext = context.applicationContext
        val ownerUid = UserDataScope.requireCurrentUid()
        val current = entry

        if (current?.ownerUid == ownerUid) {
            return current.repository
        }

        return synchronized(this) {
            val synchronizedEntry = entry
            if (synchronizedEntry?.ownerUid == ownerUid) {
                synchronizedEntry.repository
            } else {
                synchronizedEntry?.repository?.stop()

                val repositoryScope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO
                )
                val repository = DevicesRepository(
                    knownStore = DeviceKnownStore(
                        context = appContext,
                        ownerUid = ownerUid
                    ),
                    runtimeRepository = DeviceRuntimeRepository.withCredentialStore(
                        context = appContext,
                        ownerUid = ownerUid
                    ),
                    connectivityObserver = DeviceConnectivityObserver(appContext)
                )

                repository.start(repositoryScope)

                entry = Entry(
                    ownerUid = ownerUid,
                    scope = repositoryScope,
                    repository = repository
                )

                repository
            }
        }
    }

    fun clear(
        expectedOwnerUid: String? = null
    ): Boolean {
        val normalizedExpected = expectedOwnerUid
            ?.trim()
            ?.takeIf(String::isNotBlank)

        return synchronized(this) {
            val current = entry

            if (
                normalizedExpected != null &&
                current?.ownerUid != normalizedExpected
            ) {
                false
            } else {
                current?.repository?.stop()
                entry = null
                current != null
            }
        }
    }

    fun currentOwnerUid(): String? {
        return entry?.ownerUid
    }
}
