package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
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
 * A repository can never be created without Android context or authenticated
 * owner identity. Owner switches must pass through the suspending [clear] barrier;
 * a new owner repository is never exposed while the old owner's collectors,
 * sockets or token operations are still shutting down.
 */
object DevicesRepositoryProvider {

    private data class Entry(
        val ownerUid: String,
        val scope: CoroutineScope,
        val repository: DevicesRepository
    ) : AutoCloseable {
        override fun close() {
            try {
                repository.stop()
            } finally {
                scope.cancel()
            }
        }

        suspend fun shutdown() {
            try {
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
            check(closingOwnerUid == null) {
                "Previous owner device repository is still shutting down."
            }

            val synchronizedEntry = entry
            if (synchronizedEntry?.ownerUid == ownerUid) {
                synchronizedEntry.repository
            } else {
                check(synchronizedEntry == null) {
                    "Owner repository must be cleared before switching authenticated owners."
                }

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

    fun currentOwnerUid(): String? {
        return entry?.ownerUid
    }

    fun currentRepository(
        expectedOwnerUid: String
    ): DevicesRepository? {
        val normalizedOwnerUid = expectedOwnerUid.trim()
        if (normalizedOwnerUid.isBlank()) return null

        return entry?.takeIf { current ->
            current.ownerUid == normalizedOwnerUid
        }?.repository
    }
}
