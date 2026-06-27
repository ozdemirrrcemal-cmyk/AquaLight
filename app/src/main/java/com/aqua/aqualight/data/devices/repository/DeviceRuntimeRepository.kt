package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsAuthManager
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceRuntimeRepository(
    private val wsClient: AqlWsClient = AqlWsClient(),
    tokenProvider: AqlWsTokenProvider? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    val connectionState: StateFlow<AqlWsConnectionState> = wsClient.connectionState
    val events: SharedFlow<AqlWsEvent> = wsClient.events

    private val commandClient = AqlWsCommandClient(wsClient)
    private val authManager = tokenProvider?.let { provider ->
        AqlWsAuthManager(provider)
    }

    init {
        observeAuthLifecycle()
    }

    fun connect(snapshot: DeviceSnapshot): Result<Unit> {
        return wsClient.connect(
            deviceUid = snapshot.deviceUid,
            endpoint = snapshot.endpoint
        )
    }

    fun commandClient(): AqlWsCommandClient {
        return commandClient
    }

    suspend fun saveToken(
        deviceUid: DeviceUid,
        token: String
    ) {
        authManager?.saveToken(
            deviceUid = deviceUid,
            token = token
        )
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        authManager?.clearToken(deviceUid)
    }

    fun close() {
        wsClient.close()
    }

    private fun observeAuthLifecycle() {
        val manager = authManager ?: return

        scope.launch {
            wsClient.events.collect { event ->
                when (event) {
                    is AqlWsEvent.Opened -> {
                        manager.authenticateIfTokenExists(
                            deviceUid = event.deviceUid,
                            commandClient = commandClient
                        )
                    }

                    is AqlWsEvent.Message -> {
                        manager.handleIncomingMessage(
                            deviceUid = event.deviceUid,
                            message = event.parsed,
                            wsClient = wsClient
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    companion object {
        fun withCredentialStore(
            context: Context,
            wsClient: AqlWsClient = AqlWsClient()
        ): DeviceRuntimeRepository {
            return DeviceRuntimeRepository(
                wsClient = wsClient,
                tokenProvider = DeviceCredentialStore(context)
            )
        }
    }
}
