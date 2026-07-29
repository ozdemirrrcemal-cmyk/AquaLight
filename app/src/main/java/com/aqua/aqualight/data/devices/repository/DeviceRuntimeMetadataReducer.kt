package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailure
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceUid

/**
 * Pure reducer for one authenticated runtime-metadata generation.
 *
 * The reducer never parses wire JSON and never merges with metadata from an older authentication.
 * A publication exists only when identity, capabilities and modules from the same generation have
 * all been accepted. Stale fragments are ignored; contradictory fragments reject the generation.
 */
class DeviceRuntimeMetadataReducer {

    fun begin(
        deviceUid: DeviceUid,
        previous: DeviceRuntimeMetadataGenerationState?
    ): DeviceRuntimeMetadataGenerationState.Collecting {
        require(previous == null || previous.deviceUid == deviceUid) {
            "Runtime metadata generation cannot move between devices."
        }
        val generation = previous?.generation?.next() ?: DeviceRuntimeMetadataGeneration.FIRST
        return DeviceRuntimeMetadataGenerationState.Collecting(
            deviceUid = deviceUid,
            generation = generation,
            identity = null,
            capabilities = null,
            modules = null
        )
    }

    fun reduce(
        current: DeviceRuntimeMetadataGenerationState,
        fragment: DeviceRuntimeMetadataFragment
    ): DeviceRuntimeMetadataReduction {
        if (fragment.generation != current.generation) {
            return DeviceRuntimeMetadataReduction.IgnoredStale(
                state = current,
                staleGeneration = fragment.generation
            )
        }
        if (current is DeviceRuntimeMetadataGenerationState.Rejected) {
            return DeviceRuntimeMetadataReduction.Rejected(current)
        }

        val existing = current.fragments()
        val updated = when (fragment) {
            is DeviceRuntimeMetadataFragment.Identity -> {
                if (fragment.value.deviceUid != current.deviceUid) {
                    return reject(
                        current = current,
                        code = DeviceRuntimeMetadataFailureCode.DEVICE_UID_MISMATCH,
                        field = "deviceUid"
                    )
                }
                val identity = acceptUnique(
                    previous = existing.identity,
                    incoming = fragment.value,
                    conflictCode = DeviceRuntimeMetadataFailureCode.CONFLICTING_IDENTITY,
                    current = current
                ) ?: return reject(
                    current = current,
                    code = DeviceRuntimeMetadataFailureCode.CONFLICTING_IDENTITY,
                    field = "identity"
                )
                existing.copy(identity = identity)
            }

            is DeviceRuntimeMetadataFragment.Capabilities -> {
                val capabilities = acceptUnique(
                    previous = existing.capabilities,
                    incoming = fragment.value,
                    conflictCode = DeviceRuntimeMetadataFailureCode.CONFLICTING_CAPABILITIES,
                    current = current
                ) ?: return reject(
                    current = current,
                    code = DeviceRuntimeMetadataFailureCode.CONFLICTING_CAPABILITIES,
                    field = "capabilities"
                )
                existing.copy(capabilities = capabilities)
            }

            is DeviceRuntimeMetadataFragment.Modules -> {
                val modules = acceptUnique(
                    previous = existing.modules,
                    incoming = fragment.value,
                    conflictCode = DeviceRuntimeMetadataFailureCode.CONFLICTING_MODULES,
                    current = current
                ) ?: return reject(
                    current = current,
                    code = DeviceRuntimeMetadataFailureCode.CONFLICTING_MODULES,
                    field = "modules"
                )
                existing.copy(modules = modules)
            }
        }

        val next = updated.toState(
            deviceUid = current.deviceUid,
            generation = current.generation
        )
        return DeviceRuntimeMetadataReduction.Accepted(next)
    }

    fun reject(
        current: DeviceRuntimeMetadataGenerationState,
        code: DeviceRuntimeMetadataFailureCode,
        field: String?
    ): DeviceRuntimeMetadataReduction.Rejected {
        val rejected = DeviceRuntimeMetadataGenerationState.Rejected(
            deviceUid = current.deviceUid,
            generation = current.generation,
            failure = DeviceRuntimeMetadataFailure(
                code = code,
                field = field
            )
        )
        return DeviceRuntimeMetadataReduction.Rejected(rejected)
    }

    private fun DeviceRuntimeMetadataGenerationState.fragments(): Fragments = when (this) {
        is DeviceRuntimeMetadataGenerationState.Collecting -> Fragments(
            identity = identity,
            capabilities = capabilities,
            modules = modules
        )

        is DeviceRuntimeMetadataGenerationState.Ready -> Fragments(
            identity = metadata.identity,
            capabilities = metadata.capabilities,
            modules = metadata.modules
        )

        is DeviceRuntimeMetadataGenerationState.Rejected -> error(
            "Rejected metadata generations do not expose reusable fragments."
        )
    }

    private fun Fragments.toState(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeMetadataGeneration
    ): DeviceRuntimeMetadataGenerationState {
        val readyIdentity = identity
        val readyCapabilities = capabilities
        val readyModules = modules
        return if (
            readyIdentity != null &&
            readyCapabilities != null &&
            readyModules != null
        ) {
            DeviceRuntimeMetadataGenerationState.Ready(
                deviceUid = deviceUid,
                generation = generation,
                metadata = DeviceRuntimeMetadata(
                    identity = readyIdentity,
                    capabilities = readyCapabilities,
                    modules = readyModules
                )
            )
        } else {
            DeviceRuntimeMetadataGenerationState.Collecting(
                deviceUid = deviceUid,
                generation = generation,
                identity = readyIdentity,
                capabilities = readyCapabilities,
                modules = readyModules
            )
        }
    }

    private fun <T> acceptUnique(
        previous: T?,
        incoming: T,
        conflictCode: DeviceRuntimeMetadataFailureCode,
        current: DeviceRuntimeMetadataGenerationState
    ): T? {
        @Suppress("UNUSED_VARIABLE")
        val guardedConflictCode = conflictCode
        @Suppress("UNUSED_VARIABLE")
        val guardedCurrent = current
        return when {
            previous == null -> incoming
            previous == incoming -> previous
            else -> null
        }
    }

    private data class Fragments(
        val identity: com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentity?,
        val capabilities: com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities?,
        val modules: com.aqua.aqualight.data.devices.model.DeviceRuntimeModules?
    )
}
