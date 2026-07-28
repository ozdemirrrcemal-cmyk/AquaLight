package com.aqua.aqualight.data.devices.repository

import android.content.Context
import android.net.Network
import com.aqua.aqualight.data.devices.runtime.ws.AqlLocalNetworkSocketFactory
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore

/** Creates an owner-scoped runtime whose new sockets follow the canonical local Network. */
internal fun DeviceRuntimeRepository.Companion.withCredentialStoreOnLocalNetwork(
    context: Context,
    ownerUid: String,
    networkProvider: () -> Network?
): DeviceRuntimeRepository {
    return DeviceRuntimeRepository(
        tokenProvider = DeviceCredentialStore(
            context = context,
            ownerUid = ownerUid
        ),
        wsClientFactory = { tokenProvider ->
            val localNetworkClient = AqlWsClient.defaultOkHttpClient()
                .newBuilder()
                .socketFactory(AqlLocalNetworkSocketFactory(networkProvider))
                .build()
            AqlWsClient(
                okHttpClient = localNetworkClient,
                tokenProvider = tokenProvider
            )
        }
    )
}
