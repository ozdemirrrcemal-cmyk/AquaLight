package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceUid

interface AqlWsTokenProvider {
    suspend fun getToken(deviceUid: DeviceUid): String?
    suspend fun saveToken(deviceUid: DeviceUid, token: String)
    suspend fun clearToken(deviceUid: DeviceUid)
}
