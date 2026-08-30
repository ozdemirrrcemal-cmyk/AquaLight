package com.aqua.aqualight.smoke

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.i18n.AppLanguageController
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * CI-only verifier for a real Android package replacement using the same signing identity.
 *
 * The lower-version APK seeds supported durable settings plus deliberately stale credential
 * state. The higher-version candidate must preserve supported state, start in a new process,
 * discard staged credentials and remove orphaned committed credentials.
 */
class UpgradeInstallSmokeActivity : AppCompatActivity() {

    private val validationViewModel by viewModels<UpgradeInstallSmokeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        validationViewModel.marker.observe(this, ::render)
    }

    override fun onPostResume() {
        super.onPostResume()
        validationViewModel.start(
            context = applicationContext,
            action = intent.getStringExtra(UpgradeInstallContract.EXTRA_ACTION).orEmpty()
        )
    }

    private fun render(marker: String) {
        setContentView(
            TextView(this).apply {
                text = marker
                contentDescription = marker
                gravity = Gravity.CENTER
                textSize = UpgradeInstallContract.MARKER_TEXT_SIZE_SP
            }
        )
    }
}

internal class UpgradeInstallSmokeViewModel : ViewModel() {
    private val mutableMarker = MutableLiveData(UpgradeInstallContract.RUNNING_MARKER)
    val marker: LiveData<String> = mutableMarker
    private var validationStarted = false

    fun start(context: Context, action: String) {
        if (validationStarted) return
        validationStarted = true
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val evidence = when (action) {
                        UpgradeInstallContract.ACTION_SEED ->
                            UpgradeInstallValidator.seed(appContext)

                        UpgradeInstallContract.ACTION_VERIFY ->
                            UpgradeInstallValidator.verify(appContext)

                        else -> error("Unsupported upgrade-install smoke action.")
                    }
                    writeEvidence(appContext, action, evidence)
                    if (action == UpgradeInstallContract.ACTION_VERIFY) {
                        UpgradeStateStore(appContext).clearValidationState()
                    }
                    evidence
                }
            }
            mutableMarker.value = result.fold(
                onSuccess = {
                    if (action == UpgradeInstallContract.ACTION_SEED) {
                        UpgradeInstallContract.BASELINE_PASS_MARKER
                    } else {
                        UpgradeInstallContract.CANDIDATE_PASS_MARKER
                    }
                },
                onFailure = { error ->
                    "${UpgradeInstallContract.FAIL_MARKER}:" +
                        "${error::class.java.simpleName}:${error.message.orEmpty()}"
                }
            )
        }
    }

    private fun writeEvidence(context: Context, action: String, evidence: JSONObject) {
        val root = context.getExternalFilesDir(null) ?: error(
            "App-specific external storage is unavailable."
        )
        val directory = File(root, UpgradeInstallContract.EVIDENCE_DIRECTORY)
        check(directory.mkdirs() || directory.isDirectory) {
            "Upgrade-install evidence directory could not be created."
        }
        val fileName = if (action == UpgradeInstallContract.ACTION_SEED) {
            UpgradeInstallContract.BASELINE_EVIDENCE_FILE
        } else {
            UpgradeInstallContract.CANDIDATE_EVIDENCE_FILE
        }
        val output = File(directory, fileName)
        output.writeText(evidence.toString(2) + "\n", Charsets.UTF_8)
        check(output.isFile && output.length() > 0L) {
            "Upgrade-install evidence could not be persisted."
        }
    }
}

private object UpgradeInstallValidator {

    suspend fun seed(context: Context): JSONObject {
        val identity = PackageIdentityReader.read(context)
        val stateStore = UpgradeStateStore(context)
        stateStore.awaitStartupAppearanceSync()
        stateStore.reset()
        stateStore.seed(identity)
        UpgradeCredentialProbe(context).seed()

        return JSONObject()
            .put("schemaVersion", UpgradeInstallContract.SCHEMA_VERSION)
            .put("passed", true)
            .put("phase", UpgradeInstallContract.BASELINE_PHASE)
            .put("baselineMode", UpgradeInstallContract.BASELINE_MODE)
            .put("packageName", context.packageName)
            .put("versionName", identity.versionName)
            .put("versionCode", identity.versionCode)
            .put("processId", identity.processId)
            .put("processNonce", identity.processNonce)
            .put("signerSha256", identity.signerSha256)
    }

    suspend fun verify(context: Context): JSONObject {
        val candidate = PackageIdentityReader.read(context)
        val stateStore = UpgradeStateStore(context)
        stateStore.awaitStartupAppearanceSync()
        val baseline = stateStore.load()
        val credentialEvidence = UpgradeCredentialProbe(context).verifyAndCleanup()
        val checks = linkedMapOf(
            "versionCodeIncreased" to (candidate.versionCode > baseline.versionCode),
            "signerUnchanged" to (candidate.signerSha256 == baseline.signerSha256),
            "processRecreated" to (candidate.processNonce != baseline.processNonce),
            "preferenceMarkerPreserved" to baseline.preferenceMarkerPreserved,
            "fileMarkerPreserved" to baseline.fileMarkerPreserved,
            "appearancePreserved" to baseline.appearancePreserved,
            "committedCredentialPreservedBeforeCleanup" to
                credentialEvidence.committedPreserved,
            "stagedCredentialPresentBeforeCleanup" to
                credentialEvidence.stagedPresent,
            "stagedCredentialDiscarded" to credentialEvidence.stagedDiscarded,
            "orphanCredentialRemoved" to credentialEvidence.orphanRemoved,
            "credentialAbsentAfterCleanup" to credentialEvidence.absentAfterCleanup
        )
        val failedChecks = checks.filterValues { passed -> !passed }.keys
        check(failedChecks.isEmpty()) {
            "Upgrade-install checks failed: ${failedChecks.sorted()}"
        }

        return JSONObject()
            .put("schemaVersion", UpgradeInstallContract.SCHEMA_VERSION)
            .put("passed", true)
            .put("phase", UpgradeInstallContract.CANDIDATE_PHASE)
            .put("baselineMode", UpgradeInstallContract.BASELINE_MODE)
            .put("packageName", context.packageName)
            .put("baseline", baseline.toJson())
            .put("candidate", candidate.toJson())
            .put("checks", UpgradeInstallJson.fromMap(checks))
            .put("credentialCleanup", credentialEvidence.toJson())
    }
}

private class UpgradeStateStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val application = appContext as AquaApp
    private val state = appContext.getSharedPreferences(
        UpgradeInstallContract.STATE_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val appearance = application.appContainer.startupAppearanceCache
    private val userPreferences = application.appContainer.userPreferencesManager
    private val userSettings = application.appContainer.userSettingsOperations
    private val markerFile = File(
        appContext.filesDir,
        UpgradeInstallContract.MARKER_FILE
    )

    suspend fun awaitStartupAppearanceSync() {
        application.awaitStartupAppearanceSyncForProcess()
    }

    fun reset() {
        state.edit().clear().commitOrThrow(
            "Upgrade validation preferences could not be reset."
        )
        check(!markerFile.exists() || markerFile.delete()) {
            "Upgrade validation marker file could not be reset."
        }
    }

    suspend fun seed(identity: PackageIdentity) {
        state.edit()
            .putString(
                UpgradeInstallContract.KEY_MARKER,
                UpgradeInstallContract.PREFERENCE_MARKER
            )
            .putString(UpgradeInstallContract.KEY_VERSION_NAME, identity.versionName)
            .putLong(UpgradeInstallContract.KEY_VERSION_CODE, identity.versionCode)
            .putInt(UpgradeInstallContract.KEY_PROCESS_ID, identity.processId)
            .putString(UpgradeInstallContract.KEY_PROCESS_NONCE, identity.processNonce)
            .putString(UpgradeInstallContract.KEY_SIGNER, identity.signerSha256)
            .commitOrThrow("Upgrade validation preferences could not be seeded.")
        markerFile.parentFile?.let { parent ->
            check(parent.mkdirs() || parent.isDirectory) {
                "Upgrade validation marker directory could not be created."
            }
        }
        markerFile.writeText(
            UpgradeInstallContract.FILE_MARKER + "\n",
            Charsets.UTF_8
        )
        userSettings.updateThemeMode(UpgradeInstallContract.APPEARANCE_THEME)
        userSettings.updateLanguage(UpgradeInstallContract.APPEARANCE_LANGUAGE)
        awaitAppearancePreserved()
    }

    suspend fun load(): SeededUpgradeState {
        val seededIdentity = PackageIdentity(
            versionName = state.getString(
                UpgradeInstallContract.KEY_VERSION_NAME,
                null
            ).orEmpty(),
            versionCode = state.getLong(UpgradeInstallContract.KEY_VERSION_CODE, 0L),
            processId = state.getInt(UpgradeInstallContract.KEY_PROCESS_ID, 0),
            processNonce = state.getString(
                UpgradeInstallContract.KEY_PROCESS_NONCE,
                null
            ).orEmpty(),
            signerSha256 = state.getString(
                UpgradeInstallContract.KEY_SIGNER,
                null
            ).orEmpty()
        )
        check(seededIdentity.isComplete()) {
            "Upgrade baseline identity is missing or malformed."
        }
        val currentAppearance = appearance.read()
        return SeededUpgradeState(
            identity = seededIdentity,
            preferenceMarkerPreserved = state.getString(
                UpgradeInstallContract.KEY_MARKER,
                null
            ) == UpgradeInstallContract.PREFERENCE_MARKER,
            fileMarkerPreserved = markerFile.readText(Charsets.UTF_8).trim() ==
                UpgradeInstallContract.FILE_MARKER,
            appearancePreserved = isAppearancePreserved(currentAppearance)
        )
    }

    private suspend fun isAppearancePreserved(
        currentAppearance: StartupAppearanceCache.Appearance = appearance.read()
    ): Boolean {
        return appearanceFailures(currentAppearance).isEmpty()
    }

    private suspend fun awaitAppearancePreserved() {
        repeat(UpgradeInstallContract.APPEARANCE_SYNC_ATTEMPTS) {
            if (appearanceFailures().isEmpty()) return
            delay(UpgradeInstallContract.APPEARANCE_SYNC_DELAY_MILLIS)
        }
        val failures = appearanceFailures()
        error(
            "Upgrade appearance baseline could not be seeded through production settings; " +
                "failed checks: ${failures.sorted()}."
        )
    }

    private suspend fun appearanceFailures(
        currentAppearance: StartupAppearanceCache.Appearance = appearance.read()
    ): List<String> {
        val durablePreferences = userPreferences.userPrefsFlow.first()
        val failures = mutableListOf<String>()
        if (
            currentAppearance.themeMode != UpgradeInstallContract.APPEARANCE_THEME ||
            currentAppearance.languageCode != UpgradeInstallContract.APPEARANCE_LANGUAGE
        ) {
            failures += "startupCache"
        }
        if (
            durablePreferences.themeMode != UpgradeInstallContract.APPEARANCE_THEME ||
            durablePreferences.languageCode != UpgradeInstallContract.APPEARANCE_LANGUAGE
        ) {
            failures += "durablePreferences"
        }
        if (AppLanguageController.current() != UpgradeInstallContract.APPEARANCE_LANGUAGE) {
            failures += "runtimeLanguage"
        }
        return failures
    }

    fun clearValidationState() {
        state.edit().clear().commitOrThrow(
            "Upgrade validation preferences could not be cleared."
        )
        check(!markerFile.exists() || markerFile.delete()) {
            "Upgrade validation marker file could not be cleared."
        }
    }
}

private class UpgradeCredentialProbe(
    context: Context
) {
    private val store = DeviceCredentialStore(
        context = context.applicationContext,
        ownerUid = UpgradeInstallContract.OWNER_UID
    )
    private val deviceUid = DeviceUid(UpgradeInstallContract.DEVICE_UID)

    suspend fun seed() {
        store.clearOwner()
        store.saveToken(deviceUid, UpgradeInstallContract.committedToken)
        store.stageToken(deviceUid, UpgradeInstallContract.stagedToken)
        check(store.getCommittedToken(deviceUid) == UpgradeInstallContract.committedToken)
        check(store.getToken(deviceUid) == UpgradeInstallContract.stagedToken)
    }

    suspend fun verifyAndCleanup(): CredentialCleanupEvidence {
        val committedPreserved =
            store.getCommittedToken(deviceUid) == UpgradeInstallContract.committedToken
        val stagedPresent =
            store.getToken(deviceUid) == UpgradeInstallContract.stagedToken
        val discardedStagedCount = store.discardStagedTokens()
        val stagedDiscarded =
            discardedStagedCount == 1 &&
                store.getToken(deviceUid) == UpgradeInstallContract.committedToken
        val removedOrphanCount = store.retainTokensFor(emptyList())
        val orphanRemoved = removedOrphanCount == 1
        val absentAfterCleanup = store.getToken(deviceUid) == null
        return CredentialCleanupEvidence(
            committedPreserved = committedPreserved,
            stagedPresent = stagedPresent,
            stagedDiscarded = stagedDiscarded,
            orphanRemoved = orphanRemoved,
            absentAfterCleanup = absentAfterCleanup,
            discardedStagedCount = discardedStagedCount,
            removedOrphanCount = removedOrphanCount
        )
    }
}

private object PackageIdentityReader {

    fun read(context: Context): PackageIdentity {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            flags
        )
        val signatures = signatures(packageInfo)
        check(signatures.size == 1) {
            "Upgrade validation requires exactly one APK signer."
        }
        return PackageIdentity(
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = versionCode(packageInfo),
            processId = Process.myPid(),
            processNonce = UpgradeProcessIdentity.nonce,
            signerSha256 = sha256(signatures.single().toByteArray())
        )
    }

    private fun signatures(packageInfo: PackageInfo): List<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.toList().orEmpty()
        }
    }

    private fun versionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { byte -> "%02x".format(byte) }
}

private data class PackageIdentity(
    val versionName: String,
    val versionCode: Long,
    val processId: Int,
    val processNonce: String,
    val signerSha256: String
) {
    fun isComplete(): Boolean =
        versionName.endsWith("-smoke") &&
            versionCode > 0L &&
            processId > 0 &&
            processNonce.matches(UpgradeInstallContract.PROCESS_NONCE_PATTERN) &&
            signerSha256.matches(Regex("^[0-9a-f]{64}$"))

    fun toJson(): JSONObject = JSONObject()
        .put("versionName", versionName)
        .put("versionCode", versionCode)
        .put("processId", processId)
        .put("processNonce", processNonce)
        .put("signerSha256", signerSha256)
}

private data class SeededUpgradeState(
    val identity: PackageIdentity,
    val preferenceMarkerPreserved: Boolean,
    val fileMarkerPreserved: Boolean,
    val appearancePreserved: Boolean
) {
    val versionName: String
        get() = identity.versionName
    val versionCode: Long
        get() = identity.versionCode
    val processId: Int
        get() = identity.processId
    val processNonce: String
        get() = identity.processNonce
    val signerSha256: String
        get() = identity.signerSha256

    fun toJson(): JSONObject = identity.toJson()
}

private data class CredentialCleanupEvidence(
    val committedPreserved: Boolean,
    val stagedPresent: Boolean,
    val stagedDiscarded: Boolean,
    val orphanRemoved: Boolean,
    val absentAfterCleanup: Boolean,
    val discardedStagedCount: Int,
    val removedOrphanCount: Int
) {
    fun toJson(): JSONObject = JSONObject()
        .put("discardedStagedCount", discardedStagedCount)
        .put("removedOrphanCount", removedOrphanCount)
}

private object UpgradeInstallJson {
    fun fromMap(values: Map<String, *>): JSONObject {
        return JSONObject().also { output ->
            values.forEach { (key, value) -> output.put(key, value) }
        }
    }
}

private object UpgradeProcessIdentity {
    val nonce: String = UUID.randomUUID().toString()
}

@SuppressLint("ApplySharedPref")
private fun SharedPreferences.Editor.commitOrThrow(message: String) {
    check(commit()) { message }
}

private object UpgradeInstallContract {
    const val SCHEMA_VERSION = 1
    const val EXTRA_ACTION = "aqua_upgrade_install_action"
    const val ACTION_SEED = "seed"
    const val ACTION_VERIFY = "verify"
    const val BASELINE_PHASE = "baseline-seed"
    const val CANDIDATE_PHASE = "candidate-verify"
    const val BASELINE_MODE = "same-commit-lower-version-code"

    const val RUNNING_MARKER = "UPGRADE_INSTALL_RUNNING"
    const val BASELINE_PASS_MARKER = "UPGRADE_INSTALL_BASELINE_PASS"
    const val CANDIDATE_PASS_MARKER = "UPGRADE_INSTALL_CANDIDATE_PASS"
    const val FAIL_MARKER = "UPGRADE_INSTALL_FAIL"
    const val EVIDENCE_DIRECTORY = "stage14"
    const val BASELINE_EVIDENCE_FILE = "upgrade-install-baseline.json"
    const val CANDIDATE_EVIDENCE_FILE = "upgrade-install-candidate.json"
    const val MARKER_TEXT_SIZE_SP = 18f

    const val STATE_PREFERENCES = "stage14_upgrade_install"
    const val KEY_MARKER = "marker"
    const val KEY_VERSION_NAME = "version_name"
    const val KEY_VERSION_CODE = "version_code"
    const val KEY_PROCESS_ID = "process_id"
    const val KEY_PROCESS_NONCE = "process_nonce"
    const val KEY_SIGNER = "signer_sha256"
    const val PREFERENCE_MARKER = "aqualight-stage14-upgrade-preference-v1"
    const val MARKER_FILE = "stage14/upgrade-install-marker.txt"
    const val FILE_MARKER = "aqualight-stage14-upgrade-file-v1"

    const val APPEARANCE_THEME = "light"
    const val APPEARANCE_LANGUAGE = "tr"
    const val APPEARANCE_SYNC_ATTEMPTS = 50
    const val APPEARANCE_SYNC_DELAY_MILLIS = 100L
    const val OWNER_UID = "stage14-upgrade-owner"
    const val DEVICE_UID = "stage14-upgrade-orphan-device"
    val PROCESS_NONCE_PATTERN = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-" +
            "[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    )
    private const val TOKEN_BYTE_LENGTH = 32
    val committedToken = "11".repeat(TOKEN_BYTE_LENGTH)
    val stagedToken = "22".repeat(TOKEN_BYTE_LENGTH)
}
