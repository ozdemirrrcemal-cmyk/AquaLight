package com.aqua.aqualight.data.devices.runtime.modules.firmware

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareManifestHttpSourceTest {

    @Test
    fun `missing product channel manifest is typed as not published`() = runTest {
        val failure = sourceReturning(404).load(MANIFEST_URL).exceptionOrNull()

        assertTrue(failure is DeviceFirmwareManifestNotPublishedException)
        assertEquals(404, (failure as DeviceFirmwareManifestNotPublishedException).statusCode)
    }

    @Test
    fun `server failure remains a technical manifest failure`() = runTest {
        val failure = sourceReturning(503).load(MANIFEST_URL).exceptionOrNull()

        assertTrue(failure is DeviceFirmwareManifestHttpException)
        assertEquals(503, (failure as DeviceFirmwareManifestHttpException).statusCode)
    }

    @Test
    fun `transport failure is not converted to unpublished release`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IOException("offline") }
            .build()

        val failure = DeviceFirmwareManifestHttpSource(client).load(MANIFEST_URL)
            .exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure !is DeviceFirmwareManifestNotPublishedException)
    }

    private fun sourceReturning(statusCode: Int): DeviceFirmwareManifestHttpSource {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("test")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()
        return DeviceFirmwareManifestHttpSource(client)
    }

    private companion object {
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/stable-dosing_dose_pro_2/manifest-stable.json"
    }
}
