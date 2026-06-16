package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.runtime.light.LightLocalOverrideState
import com.aqua.aqualight.data.devices.runtime.light.LightLocalOverrideType
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSnapshot

/**
 * Captures the controller-owned state that existed before live preview started.
 *
 * Program preview uses short-lived temporary manual frames. Stopping preview must
 * not blindly force AUTO, because the user may have been in MANUAL or SCENE
 * before opening the editor. This target lets the preview layer restore the same
 * visible runtime mode without depending on the future program data layer.
 */
internal sealed class LightProgramPreviewRestoreTarget {

    data object ControllerManaged : LightProgramPreviewRestoreTarget()

    data class Manual(
        val channels: LightChannelValues
    ) : LightProgramPreviewRestoreTarget()

    data class Scene(
        val channels: LightChannelValues,
        val sceneName: String,
        val sceneSource: String?
    ) : LightProgramPreviewRestoreTarget()

    data object Unknown : LightProgramPreviewRestoreTarget()

    companion object {
        fun fromSnapshot(
            snapshot: LightRuntimeSnapshot?
        ): LightProgramPreviewRestoreTarget {
            if (snapshot == null) {
                return Unknown
            }

            snapshot.localOverride?.let { localOverride ->
                return fromLocalOverride(
                    localOverride = localOverride,
                    sceneNameFallback = snapshot.activeSceneName,
                    sceneSourceFallback = snapshot.activeSceneSource
                )
            }

            return when (snapshot.mode) {
                LightMode.MANUAL -> Manual(
                    channels = snapshot.channels.normalized()
                )

                LightMode.SCENE -> Scene(
                    channels = snapshot.channels.normalized(),
                    sceneName = snapshot.activeSceneName ?: FALLBACK_SCENE_NAME,
                    sceneSource = snapshot.activeSceneSource
                )

                LightMode.AUTO,
                LightMode.MOONLIGHT,
                LightMode.IDLE -> ControllerManaged

                LightMode.UNKNOWN -> Unknown
            }
        }

        fun fromLocalOverride(
            localOverride: LightLocalOverrideState,
            sceneNameFallback: String? = null,
            sceneSourceFallback: String? = null
        ): LightProgramPreviewRestoreTarget {
            return when (localOverride.type) {
                LightLocalOverrideType.MANUAL -> Manual(
                    channels = localOverride.channels.normalized()
                )

                LightLocalOverrideType.SCENE -> Scene(
                    channels = localOverride.channels.normalized(),
                    sceneName = localOverride.sceneName
                        ?: sceneNameFallback
                        ?: FALLBACK_SCENE_NAME,
                    sceneSource = localOverride.sceneSource
                        ?: sceneSourceFallback
                )
            }
        }

        private const val FALLBACK_SCENE_NAME = "Scene"
    }
}
