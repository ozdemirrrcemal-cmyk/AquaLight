package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject

internal object DeviceLightRefreshTestFixtures {
    fun fixture(
        generation: DeviceRuntimeConnectionGeneration,
        statusGate: CompletableDeferred<Unit>? = null,
        managedPlanInstalled: Boolean = false,
        mutationGate: CompletableDeferred<Unit>? = null
    ): RefreshFixture {
        val gateway = FixtureGateway(generation, statusGate, managedPlanInstalled, mutationGate)
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(deviceUid, generation)
        val operationGate = DeviceLightDeviceOperationGate()
        val runtime = DeviceLightRuntimeRepository(gateway, owner, operationGate)
        val thermal = DeviceLightThermalRuntimeRepository(gateway, owner, operationGate)
        val protection = DeviceLightTemperatureProtectionRuntimeRepository(gateway, owner, operationGate)
        return RefreshFixture(
            gateway = gateway,
            owner = owner,
            runtime = runtime,
            thermal = thermal,
            protection = protection,
            operationGate = operationGate,
            coordinator = DeviceLightRuntimeRefreshCoordinator(
                runtime = runtime,
                thermal = thermal,
                protection = protection
            )
        )
    }

    data class RefreshFixture(
        val gateway: FixtureGateway,
        val owner: DeviceLightRuntimeStateOwner,
        val runtime: DeviceLightRuntimeRepository,
        val thermal: DeviceLightThermalRuntimeRepository,
        val protection: DeviceLightTemperatureProtectionRuntimeRepository,
        val operationGate: DeviceLightDeviceOperationGate,
        val coordinator: DeviceLightRuntimeRefreshCoordinator
    )

    class FixtureGateway(
        var generation: DeviceRuntimeConnectionGeneration,
        private val statusGate: CompletableDeferred<Unit>?,
        private val managedPlanInstalled: Boolean,
        private val mutationGate: CompletableDeferred<Unit>?
    ) : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val commands = mutableListOf<Pair<DeviceUid, String>>()
        val responseGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var beforeResponse: suspend (DeviceUid, String, Long) -> Unit = { _, _, _ -> }

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            // A delayed response belongs to the session at dispatch, never the session at release.
            val dispatchedGeneration = generation
            val messageId = "refresh-${commands.size + 1}"
            actions += command.action
            commands += deviceUid to command.action
            beforeResponse(deviceUid, command.action, timeoutMillis)
            responseGates[command.action]?.await()
            if (command.action == DeviceLightRuntimeContract.Action.STATUS_GET) {
                statusGate?.await()
            }
            if (command.action == DeviceLightRuntimeContract.Action.MANUAL_OFF) {
                mutationGate?.await()
            }
            val response = AqlWsIncomingMessage.Response(
                id = messageId,
                type = "res",
                module = command.module,
                action = command.action,
                data = responseData(command.action),
                ok = true,
                statusCode = HTTP_OK
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = dispatchedGeneration,
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }

        fun responseData(action: String): JSONObject = when (action) {
            DeviceLightRuntimeContract.Action.STATUS_GET -> status(managedPlanInstalled)
            DeviceLightRuntimeContract.Action.CUSTOM_GET -> customDocument()
            DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET -> automaticPrograms()
            DeviceLightRuntimeContract.Action.AUTO_PLAN_GET -> {
                check(managedPlanInstalled)
                managedAutoPlan()
            }
            DeviceLightRuntimeContract.Action.GRAPH_GET -> DeviceLightRuntimeFixtures.graph()
            DeviceLightThermalV1Contract.Action.STATUS_GET -> thermalStatus()
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET ->
                temperatureProtectionStatus()
            DeviceLightThermalV1Contract.Action.CONFIG_APPLY -> thermalConfigApply()
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET -> protectionSetResult()
            DeviceLightRuntimeContract.Action.CONTROL_SET -> JSONObject().put("mode", "MANUAL")
            DeviceLightRuntimeContract.Action.MANUAL_SET,
            DeviceLightRuntimeContract.Action.MANUAL_OFF ->
                JSONObject().put("scene", DeviceLightScene.wrgb(0, 0, 0, 0).toJson())
            in lightMutationActions -> JSONObject()
            else -> error("Unexpected Light refresh action: $action")
        }
    }

    val lightMutationActions = listOf(
        DeviceLightRuntimeContract.Action.CONTROL_SET,
        DeviceLightRuntimeContract.Action.MANUAL_SET,
        DeviceLightRuntimeContract.Action.MANUAL_OFF,
        DeviceLightRuntimeContract.Action.AUTO_PROGRAM_CREATE,
        DeviceLightRuntimeContract.Action.AUTO_PROGRAM_UPDATE,
        DeviceLightRuntimeContract.Action.AUTO_PROGRAM_ENABLED_SET,
        DeviceLightRuntimeContract.Action.AUTO_PROGRAM_DELETE,
        DeviceLightRuntimeContract.Action.AUTO_PLAN_APPLY,
        DeviceLightRuntimeContract.Action.AUTO_PLAN_DELETE,
        DeviceLightRuntimeContract.Action.CUSTOM_INSTALL,
        DeviceLightRuntimeContract.Action.CUSTOM_CLEAR,
        DeviceLightRuntimeContract.Action.ACCLIMATION_START,
        DeviceLightRuntimeContract.Action.ACCLIMATION_STOP,
        DeviceLightRuntimeContract.Action.PREVIEW_SET,
        DeviceLightRuntimeContract.Action.PREVIEW_CLEAR
    )

    fun thermalConfigApply(): JSONObject = JSONObject()
        .put("schema", DeviceLightThermalV1Contract.SCHEMA)
        .put("schemaVersion", 1)
        .put("operation", "configApply")
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("command", "light.thermal.config.apply")
        .put("event", DeviceLightThermalV1Contract.Event.STATUS_CHANGED)
        .put("status", thermalStatus())

    fun protectionSetResult(): JSONObject = JSONObject()
        .put("operation", "temperatureProtectionSet")
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("runtimeTransport", "websocket")
        .put("command", "light.temperature-protection.set")
        .put("event", "light.thermal.status.changed")
        .put("status", temperatureProtectionStatus())

    val deviceUid = DeviceUid("central-light-refresh")
    val generationOne = DeviceRuntimeConnectionGeneration(1L)
    val generationTwo = DeviceRuntimeConnectionGeneration(2L)
    const val HTTP_OK = 200
    const val LIGHT_SURFACE_PART_COUNT = 4
    val expectedRefreshActions = listOf(
        DeviceLightRuntimeContract.Action.STATUS_GET,
        DeviceLightRuntimeContract.Action.CUSTOM_GET,
        DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET,
        DeviceLightRuntimeContract.Action.GRAPH_GET
    )
    val expectedAllRefreshActions = listOf(
        DeviceLightRuntimeContract.Action.STATUS_GET,
        DeviceLightRuntimeContract.Action.GRAPH_GET,
        DeviceLightRuntimeContract.Action.CUSTOM_GET,
        DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET,
        DeviceLightThermalV1Contract.Action.STATUS_GET,
        DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET
    )
    val expectedManagedPlanRefreshActions = listOf(
        DeviceLightRuntimeContract.Action.STATUS_GET,
        DeviceLightRuntimeContract.Action.CUSTOM_GET,
        DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET,
        DeviceLightRuntimeContract.Action.AUTO_PLAN_GET,
        DeviceLightRuntimeContract.Action.GRAPH_GET
    )

    fun status(managedPlanInstalled: Boolean): JSONObject =
        DeviceLightRuntimeFixtures.status().also { status ->
            if (managedPlanInstalled) {
                status.getJSONObject("auto")
                    .put("scheduleSource", "MANAGED_PLAN")
                    .put("planRevision", 4)
                    .put("planInstalled", true)
                    .put("planId", "lp-00000001")
                    .put("planRuntimeState", "NOT_SELECTED")
            }
        }

    fun customDocument(): JSONObject = JSONObject()
        .put("revision", 1)
        .put("installed", false)
        .put("weekdaysMask", 0)
        .put("pointCount", 0)
        .put("points", JSONArray())

    fun automaticPrograms(): JSONObject = JSONObject()
        .put("revision", 1)
        .put("capacity", DeviceLightRuntimeContract.Limit.AUTO_PROGRAM_CAPACITY)
        .put("programCount", 0)
        .put("enabledCount", 0)
        .put("programs", JSONArray())

    fun thermalStatus(): JSONObject = JSONObject(
        """
        {
          "schema":"aql.light-thermal.v1","schemaVersion":1,
          "productKey":"LIGHT_WRGB_PRO_ELITE","uptimeMs":1000,
          "topology":{"fanOutputCount":2,"temperatureSensorCount":1},
          "config":{"mode":"Auto","minTemperatureC":30.0,"maxTemperatureC":50.0},
          "temperature":{
            "sensorKey":"fixture","sensorIndex":0,"readingValid":true,
            "temperatureC":35.0,"sampledAtMs":1000
          },
          "lightProtection":{"enabled":true,"active":false,"thresholdC":60.0},
          "fans":[
            {
              "fanKey":"fan1","index":0,"name":"Fan 1","regime":"Auto",
              "valueNow":0.25,"valueAuto":0.25,"percentNow":25.0,"percentAuto":25.0,
              "hardware":{
                "editable":false,"gpio":15,"ledcChannel":4,
                "pwmFrequencyHz":25000,"pwmResolutionBits":10,"invert":false,
                "pwmOutputHealth":"OK","health":"UNVERIFIED","physicalFeedbackAvailable":false
              }
            },
            {
              "fanKey":"fan2","index":1,"name":"Fan 2","regime":"Auto",
              "valueNow":0.25,"valueAuto":0.25,"percentNow":25.0,"percentAuto":25.0,
              "hardware":{
                "editable":false,"gpio":16,"ledcChannel":5,
                "pwmFrequencyHz":25000,"pwmResolutionBits":10,"invert":false,
                "pwmOutputHealth":"OK","health":"UNVERIFIED","physicalFeedbackAvailable":false
              }
            }
          ],
          "runtime":{
            "event":"light.thermal.telemetry.changed","statusEvent":"light.thermal.status.changed",
            "sensorFailSafeActive":false,"automaticOutputCycleHealthy":true,
            "hardwareEditable":false,"fanMappingEditable":false,"sensorMappingEditable":false
          }
        }
        """.trimIndent()
    )

    fun temperatureProtectionStatus(): JSONObject = JSONObject(
        """
        {
          "supported":true,
          "temperatureProtection":{
            "supported":true,"active":false,"thresholdEditable":true,
            "thresholdC":60.0,"minimumC":50.0,"maximumC":70.0
          },
          "runtime":{
            "module":"light","readOnly":false,"supportsStatusGet":true,"supportsSet":true,
            "event":"light.thermal.status.changed"
          }
        }
        """.trimIndent()
    )

    fun managedAutoPlan(): JSONObject = JSONObject()
        .put("storageGeneration", 12)
        .put("revision", 4)
        .put("installed", true)
        .put("planId", "lp-00000001")
        .put(
            "initialStartPercent",
            DeviceLightRuntimeContract.Limit.MANAGED_PLAN_INITIAL_START_PERCENT_DEFAULT
        )
        .put("phaseCount", 1)
        .put(
            "phases",
            JSONArray().put(
                DeviceLightManagedPlanPhase(
                    validFromEpochDay = 20_000,
                    validUntilEpochDayExclusive = null,
                    transitionDays = 7,
                    weekdaysMask = 127,
                    startTimeMs = 28_800_000,
                    endTimeMs = 64_800_000,
                    rampDurationMs = 1_800_000,
                    scene = DeviceLightScene.wrgb(10, 20, 30, 40)
                ).toJson()
            )
        )
        .put(
            "runtime",
            JSONObject()
                .put("clockReady", true)
                .put("state", "NOT_SELECTED")
                .put("activePhaseIndex", JSONObject.NULL)
                .put("transitionPermille", JSONObject.NULL)
                .put("nextTransitionEpochDay", JSONObject.NULL)
        )

    fun DeviceLightRuntimeRepository.currentAuthoritativeSurface(): List<Any>? {
        val surface = listOfNotNull(
            currentStatus(deviceUid),
            currentLibrary(deviceUid, DeviceLightLibraryReadAuthority.AUTHORITATIVE),
            currentAutomatic(deviceUid, DeviceLightAutomaticReadAuthority.AUTHORITATIVE),
            currentDashboard(deviceUid, DeviceLightDashboardReadAuthority.AUTHORITATIVE)
        )
        return surface.takeIf { entries -> entries.size == LIGHT_SURFACE_PART_COUNT }
    }

    fun DeviceLightRuntimeRefreshResult.isSuccess(): Boolean =
        this is DeviceLightRuntimeRefreshResult.Success
}
