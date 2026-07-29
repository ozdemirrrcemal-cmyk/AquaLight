package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailure
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
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
    ): DeviceRuntimeMetadataReduction = when {
        fragment.generation != current.generation -> DeviceRuntimeMetadataReduction.IgnoredStale(
            state = current,
            staleGeneration = fragment.generation
        )

        current is DeviceRuntimeMetadataGenerationState.Rejected ->
            DeviceRuntimeMetadataReduction.Rejected(current)

        else -> reduceCurrent(current = current, fragment = fragment)
    }

    fun reject(
        current: DeviceRuntimeMetadataGenerationState,
        code: DeviceRuntimeMetadataFailureCode,
        field: String?
    ): DeviceRuntimeMetadataReduction.Rejected {
        return DeviceRuntimeMetadataReduction.Rejected(
            DeviceRuntimeMetadataGenerationState.Rejected(
                deviceUid = current.deviceUid,
                generation = current.generation,
                failure = DeviceRuntimeMetadataFailure(
                    code = code,
                    field = field
                )
            )
        )
    }

    private fun reduceCurrent(
        current: DeviceRuntimeMetadataGenerationState,
        fragment: DeviceRuntimeMetadataFragment
    ): DeviceRuntimeMetadataReduction {
        val merge = current.fragments().merge(
            deviceUid = current.deviceUid,
            fragment = fragment
        )
        return when (merge) {
            is FragmentMerge.Accepted -> DeviceRuntimeMetadataReduction.Accepted(
                merge.fragments.toState(
                    deviceUid = current.deviceUid,
                    generation = current.generation
                )
            )

            is FragmentMerge.Conflict -> reject(
                current = current,
                code = merge.code,
                field = merge.field
            )
        }
    }

    private fun Fragments.merge(
        deviceUid: DeviceUid,
        fragment: DeviceRuntimeMetadataFragment
    ): FragmentMerge = when (fragment) {
        is DeviceRuntimeMetadataFragment.Identity -> mergeIdentity(
            deviceUid = deviceUid,
            incoming = fragment.value
        )

        is DeviceRuntimeMetadataFragment.Capabilities -> mergeCapabilities(fragment.value)
        is DeviceRuntimeMetadataFragment.Modules -> mergeModules(fragment.value)
    }

    private fun Fragments.mergeIdentity(
        deviceUid: DeviceUid,
        incoming: DeviceRuntimeIdentity
    ): FragmentMerge = when {
        incoming.deviceUid != deviceUid -> FragmentMerge.Conflict(
            code = DeviceRuntimeMetadataFailureCode.DEVICE_UID_MISMATCH,
            field = "deviceUid"
        )

        identity == null || identity == incoming -> FragmentMerge.Accepted(
            copy(identity = incoming)
        )

        else -> FragmentMerge.Conflict(
            code = DeviceRuntimeMetadataFailureCode.CONFLICTING_IDENTITY,
            field = "identity"
        )
    }

    private fun Fragments.mergeCapabilities(
        incoming: DeviceRuntimeCapabilities
    ): FragmentMerge = if (capabilities == null || capabilities == incoming) {
        FragmentMerge.Accepted(copy(capabilities = incoming))
    } else {
        FragmentMerge.Conflict(
            code = DeviceRuntimeMetadataFailureCode.CONFLICTING_CAPABILITIES,
            field = "capabilities"
        )
    }

    private fun Fragments.mergeModules(
        incoming: DeviceRuntimeModules
    ): FragmentMerge = if (modules == null || modules == incoming) {
        FragmentMerge.Accepted(copy(modules = incoming))
    } else {
        FragmentMerge.Conflict(
            code = DeviceRuntimeMetadataFailureCode.CONFLICTING_MODULES,
            field = "modules"
        )
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

    private sealed interface FragmentMerge {
        data class Accepted(val fragments: Fragments) : FragmentMerge

        data class Conflict(
            val code: DeviceRuntimeMetadataFailureCode,
            val field: String
        ) : FragmentMerge
    }

    private data class Fragments(
        val identity: DeviceRuntimeIdentity?,
        val capabilities: DeviceRuntimeCapabilities?,
        val modules: DeviceRuntimeModules?
    )
}
