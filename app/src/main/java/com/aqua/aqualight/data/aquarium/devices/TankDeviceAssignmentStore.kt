package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class TankDeviceAssignment(
    val tankId: Long,
    val deviceUid: DeviceUid,
    val assignedAtMillis: Long
)

class TankDeviceAssignmentStore private constructor(
    context: Context
) {
    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    private val _assignments = MutableStateFlow(loadAssignments())
    val assignments: StateFlow<List<TankDeviceAssignment>> = _assignments.asStateFlow()

    @Synchronized
    fun assignDeviceToTank(
        tankId: Long,
        deviceUid: DeviceUid
    ) {
        if (tankId <= 0L || deviceUid.value.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()

        val next = _assignments.value
            .filterNot { assignment ->
                assignment.deviceUid == deviceUid
            }
            .plus(
                TankDeviceAssignment(
                    tankId = tankId,
                    deviceUid = deviceUid,
                    assignedAtMillis = now
                )
            )

        saveAssignments(next)
        _assignments.value = next
    }

    @Synchronized
    fun removeDeviceFromTank(
        tankId: Long,
        deviceUid: DeviceUid
    ) {
        val next = _assignments.value.filterNot { assignment ->
            assignment.tankId == tankId && assignment.deviceUid == deviceUid
        }

        saveAssignments(next)
        _assignments.value = next
    }

    @Synchronized
    fun removeDeviceFromAnyTank(
        deviceUid: DeviceUid
    ) {
        val next = _assignments.value.filterNot { assignment ->
            assignment.deviceUid == deviceUid
        }

        saveAssignments(next)
        _assignments.value = next
    }

    private fun loadAssignments(): List<TankDeviceAssignment> {
        val raw = prefs.getString(KEY_ASSIGNMENTS_JSON, "").orEmpty()

        if (raw.isBlank()) {
            return emptyList()
        }

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val tankId = item.optLong(KEY_TANK_ID, 0L)
                    val uid = item.optString(KEY_DEVICE_UID, "")
                    val assignedAt = item.optLong(KEY_ASSIGNED_AT, 0L)

                    if (tankId > 0L && uid.isNotBlank()) {
                        add(
                            TankDeviceAssignment(
                                tankId = tankId,
                                deviceUid = DeviceUid(uid),
                                assignedAtMillis = assignedAt.takeIf { value -> value > 0L }
                                    ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun saveAssignments(
        assignments: List<TankDeviceAssignment>
    ) {
        val array = JSONArray()

        assignments.forEach { assignment ->
            array.put(
                JSONObject()
                    .put(KEY_TANK_ID, assignment.tankId)
                    .put(KEY_DEVICE_UID, assignment.deviceUid.value)
                    .put(KEY_ASSIGNED_AT, assignment.assignedAtMillis)
            )
        }

        prefs.edit()
            .putString(KEY_ASSIGNMENTS_JSON, array.toString())
            .apply()
    }

    companion object {
        private const val PREF_NAME = "tank_device_assignments_v2"
        private const val KEY_ASSIGNMENTS_JSON = "assignments_json"
        private const val KEY_TANK_ID = "tankId"
        private const val KEY_DEVICE_UID = "deviceUid"
        private const val KEY_ASSIGNED_AT = "assignedAtMillis"

        @Volatile
        private var INSTANCE: TankDeviceAssignmentStore? = null

        fun get(
            context: Context
        ): TankDeviceAssignmentStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TankDeviceAssignmentStore(
                    context = context.applicationContext
                ).also { store ->
                    INSTANCE = store
                }
            }
        }
    }
}
