package com.aqua.aqualight.smoke

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentsStore
import com.aqua.aqualight.data.aquarium.store.AquariumTanksStore
import com.aqua.aqualight.data.care.CareTasksStore
import com.aqua.aqualight.data.devices.store.KnownDevicesStore
import com.aqua.aqualight.data.notifications.NotificationPreferencesStore
import com.aqua.aqualight.data.notifications.NotificationScheduleStateStore
import com.aqua.aqualight.data.user.UserPreferences
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * First-launch validator packaged only in the minified releaseSmoke variant.
 *
 * The host runner installs the APK without `-r` after proving the package is absent. This Activity
 * then verifies the logical application state from inside the non-debuggable sandbox and emits
 * machine-readable evidence to app-specific external storage.
 */
class CleanInstallSmokeActivity : Activity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(CleanInstallContract.RUNNING_MARKER)

        activityScope.launch {
            val evidence = runCatching {
                withContext(Dispatchers.IO) {
                    CleanInstallEvidenceCollector(this@CleanInstallSmokeActivity).collect()
                }
            }.getOrElse { error ->
                JSONObject()
                    .put("schemaVersion", CleanInstallContract.SCHEMA_VERSION)
                    .put("passed", false)
                    .put("packageName", packageName)
                    .put("failureType", error::class.java.name)
                    .put("failure", error.message.orEmpty())
            }
            writeEvidence(evidence)
            if (evidence.optBoolean("passed", false)) {
                render(CleanInstallContract.PASS_MARKER)
            } else {
                render(
                    "${CleanInstallContract.FAIL_MARKER}:" +
                        evidence.optString("failureType")
                )
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun writeEvidence(evidence: JSONObject) {
        val root = getExternalFilesDir(null) ?: error(
            "App-specific external storage is unavailable."
        )
        val directory = File(root, CleanInstallContract.EVIDENCE_DIRECTORY)
        check(directory.mkdirs() || directory.isDirectory) {
            "Clean-install evidence directory could not be created."
        }
        val output = File(directory, CleanInstallContract.EVIDENCE_FILE)
        output.writeText(evidence.toString(2) + "\n", Charsets.UTF_8)
        check(output.isFile && output.length() > 0L) {
            "Clean-install evidence could not be persisted."
        }
    }

    private fun render(marker: String) {
        setContentView(
            TextView(this).apply {
                text = marker
                contentDescription = marker
                gravity = Gravity.CENTER
                textSize = CleanInstallContract.MARKER_TEXT_SIZE_SP
            }
        )
    }
}

private class CleanInstallEvidenceCollector(
    private val activity: Activity
) {

    suspend fun collect(): JSONObject {
        val application = activity.application as AquaApp
        val preferences = application.appContainer
            .userPreferencesManager
            .userPrefsFlow
            .first()
        val counts = linkedMapOf<String, Int>().apply {
            putAll(protoStateCounts())
            putAll(privateStateCounts(preferences))
        }
        val checks = buildChecks(preferences, counts)
        CountEvidence.requirePassingState(checks, counts)
        val packageInfo = activity.packageManager.getPackageInfo(
            activity.packageName,
            0
        )

        return JSONObject()
            .put("schemaVersion", CleanInstallContract.SCHEMA_VERSION)
            .put("passed", true)
            .put("packageName", activity.packageName)
            .put("versionName", packageInfo.versionName.orEmpty())
            .put("versionCode", PackageVersionReader.versionCode(packageInfo))
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("checks", CleanInstallJson.fromMap(checks))
            .put("counts", CleanInstallJson.fromMap(counts))
    }

    private fun protoStateCounts(): Map<String, Int> {
        val knownDevices = readProto(
            CleanInstallContract.KNOWN_DEVICES_FILE,
            { bytes -> KnownDevicesStore.parseFrom(bytes) },
            KnownDevicesStore::getDefaultInstance
        )
        return linkedMapOf(
            "knownDevices" to knownDevices.devicesCount,
            "ignoredDevices" to knownDevices.ignoredDevicesCount,
            "tanks" to protoCount(
                CleanInstallContract.AQUARIUM_TANKS_FILE,
                { bytes -> AquariumTanksStore.parseFrom(bytes) },
                AquariumTanksStore::getDefaultInstance
            ) { store -> store.tanksCount },
            "assignments" to protoCount(
                CleanInstallContract.TANK_DEVICE_ASSIGNMENTS_FILE,
                { bytes -> TankDeviceAssignmentsStore.parseFrom(bytes) },
                TankDeviceAssignmentsStore::getDefaultInstance
            ) { store -> store.assignmentsCount },
            "careTasks" to protoCount(
                CleanInstallContract.CARE_TASKS_FILE,
                { bytes -> CareTasksStore.parseFrom(bytes) },
                CareTasksStore::getDefaultInstance
            ) { store -> store.tasksCount },
            "notificationPreferences" to protoCount(
                CleanInstallContract.NOTIFICATION_PREFERENCES_FILE,
                { bytes -> NotificationPreferencesStore.parseFrom(bytes) },
                NotificationPreferencesStore::getDefaultInstance
            ) { store -> store.ownerPreferencesCount },
            "notificationSchedules" to protoCount(
                CleanInstallContract.NOTIFICATION_SCHEDULES_FILE,
                { bytes -> NotificationScheduleStateStore.parseFrom(bytes) },
                NotificationScheduleStateStore::getDefaultInstance
            ) { store -> store.ownerSchedulesCount }
        )
    }

    private fun privateStateCounts(preferences: UserPreferences): Map<String, Int> {
        return linkedMapOf(
            "activeProfileCaches" to preferences.profileCachesCount,
            "userPrivateProjectionFields" to userPrivateFieldCount(preferences),
            "encryptedOwnerEntries" to encryptedOwnerEntryCount(),
            "tankCareIntegrityEntries" to sharedStringSetSize(
                CleanInstallContract.TANK_CARE_INTEGRITY_PREFERENCES,
                CleanInstallContract.TANK_CARE_INTEGRITY_KEY
            ),
            "recoveryMarkers" to sharedStringSetSize(
                CleanInstallContract.RECOVERY_PREFERENCES,
                CleanInstallContract.RECOVERY_KEY
            ),
            "ownerMediaFiles" to ownerMediaFileCount()
        )
    }

    private fun buildChecks(
        preferences: UserPreferences,
        counts: Map<String, Int>
    ): Map<String, Boolean> {
        return linkedMapOf(
            "nonDebuggable" to (
                (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0
                ),
            "backupDisabled" to (
                (activity.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0
                ),
            "firebaseSignedOut" to (FirebaseAuth.getInstance().currentUser == null),
            "userSessionEmpty" to (
                !preferences.isLoggedIn &&
                    preferences.uid.isBlank() &&
                    preferences.email.isBlank()
                ),
            "userPrivateProjectionEmpty" to CountEvidence.isZero(
                counts,
                "userPrivateProjectionFields"
            ),
            "profileCacheEmpty" to CountEvidence.isZero(
                counts,
                "activeProfileCaches"
            ),
            "knownDevicesEmpty" to (
                CountEvidence.isZero(counts, "knownDevices") &&
                    CountEvidence.isZero(counts, "ignoredDevices")
                ),
            "tanksEmpty" to CountEvidence.isZero(counts, "tanks"),
            "assignmentsEmpty" to CountEvidence.isZero(counts, "assignments"),
            "careTasksEmpty" to CountEvidence.isZero(counts, "careTasks"),
            "notificationPreferencesEmpty" to CountEvidence.isZero(
                counts,
                "notificationPreferences"
            ),
            "notificationSchedulesEmpty" to CountEvidence.isZero(
                counts,
                "notificationSchedules"
            ),
            "encryptedOwnerStateEmpty" to CountEvidence.isZero(
                counts,
                "encryptedOwnerEntries"
            ),
            "tankCareIntegrityJournalEmpty" to CountEvidence.isZero(
                counts,
                "tankCareIntegrityEntries"
            ),
            "recoveryMarkersEmpty" to CountEvidence.isZero(
                counts,
                "recoveryMarkers"
            ),
            "ownerMediaEmpty" to CountEvidence.isZero(counts, "ownerMediaFiles")
        )
    }

    private fun userPrivateFieldCount(preferences: UserPreferences): Int {
        val textFields = listOf(
            preferences.uid,
            preferences.email,
            preferences.username,
            preferences.profilePhotoUrl,
            preferences.fullName,
            preferences.firstName,
            preferences.lastName,
            preferences.city,
            preferences.addressLine,
            preferences.postCode,
            preferences.phoneNumber,
            preferences.country,
            preferences.lastEventDescription,
            preferences.lastUsageDayKey,
            preferences.lastUsageWeekKey
        )
        val numericFields = listOf(
            preferences.weeklyAutomationCount.toLong(),
            preferences.weeklyAlertCount.toLong(),
            preferences.todayAutomationCount.toLong(),
            preferences.todayManualActionCount.toLong(),
            preferences.lastEventTimeMillis
        )
        return textFields.count(String::isNotBlank) +
            numericFields.count { value -> value != 0L } +
            listOf(
                preferences.isLoggedIn,
                preferences.loginAlertsEnabled,
                preferences.twoFactorEnabled
            ).count { value -> value }
    }

    private fun encryptedOwnerEntryCount(): Int {
        val masterKey = MasterKey.Builder(activity.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return CleanInstallContract.encryptedOwnerPreferenceFiles.sumOf { fileName ->
            EncryptedSharedPreferences.create(
                activity.applicationContext,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).all.size
        }
    }

    private fun ownerMediaFileCount(): Int {
        return CleanInstallContract.ownerMediaDirectories.sumOf { directoryName ->
            val directory = File(activity.filesDir, directoryName)
            directory
                .takeIf(File::exists)
                ?.walkTopDown()
                ?.count(File::isFile)
                ?: 0
        }
    }

    private fun sharedStringSetSize(fileName: String, key: String): Int {
        return activity.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .getStringSet(key, emptySet())
            .orEmpty()
            .size
    }

    private fun <T> protoCount(
        fileName: String,
        parse: (ByteArray) -> T,
        defaultValue: () -> T,
        count: (T) -> Int
    ): Int = count(readProto(fileName, parse, defaultValue))

    private fun <T> readProto(
        fileName: String,
        parse: (ByteArray) -> T,
        defaultValue: () -> T
    ): T {
        val file = File(activity.filesDir, "datastore/$fileName")
        return if (file.exists()) parse(file.readBytes()) else defaultValue()
    }

}

private object PackageVersionReader {
    fun versionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
}

private object CleanInstallJson {
    fun fromMap(values: Map<String, *>): JSONObject {
        return JSONObject().also { output ->
            values.forEach { (key, value) -> output.put(key, value) }
        }
    }
}

private object CountEvidence {
    fun isZero(counts: Map<String, Int>, key: String): Boolean =
        counts.getValue(key) == 0

    fun requirePassingState(
        checks: Map<String, Boolean>,
        counts: Map<String, Int>
    ) {
        val failedChecks = checks.filterValues { passed -> !passed }.keys
        check(failedChecks.isEmpty()) {
            "Clean-install state checks failed: ${failedChecks.sorted()}"
        }
        val nonZeroCounts = counts.filterValues { count -> count != 0 }.keys
        check(nonZeroCounts.isEmpty()) {
            "Clean-install state counts were non-zero: ${nonZeroCounts.sorted()}"
        }
    }
}

private object CleanInstallContract {
    const val SCHEMA_VERSION = 1
    const val RUNNING_MARKER = "CLEAN_INSTALL_RUNNING"
    const val PASS_MARKER = "CLEAN_INSTALL_PASS"
    const val FAIL_MARKER = "CLEAN_INSTALL_FAIL"
    const val EVIDENCE_DIRECTORY = "stage14"
    const val EVIDENCE_FILE = "clean-install-activity.json"
    const val MARKER_TEXT_SIZE_SP = 18f

    const val KNOWN_DEVICES_FILE = "known_devices.pb"
    const val AQUARIUM_TANKS_FILE = "aquarium_tanks.pb"
    const val TANK_DEVICE_ASSIGNMENTS_FILE = "tank_device_assignments.pb"
    const val CARE_TASKS_FILE = "care_tasks.pb"
    const val NOTIFICATION_PREFERENCES_FILE = "notification_preferences.pb"
    const val NOTIFICATION_SCHEDULES_FILE = "notification_schedule_state.pb"
    const val TANK_CARE_INTEGRITY_PREFERENCES = "tank_care_integrity_journal"
    const val TANK_CARE_INTEGRITY_KEY = "pending_deletions"
    const val RECOVERY_PREFERENCES = "local_data_recovery"
    const val RECOVERY_KEY = "recovered_areas"

    val encryptedOwnerPreferenceFiles = listOf(
        "device_credentials",
        "account_deletion_recovery",
        "aql_provisioning_commit_recovery",
        "aql_provisioning_sessions",
        "aql_provisioning_qr_secrets"
    )
    val ownerMediaDirectories = listOf(
        "profile_photos",
        "tank_photos"
    )
}
