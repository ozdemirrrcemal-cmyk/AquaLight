package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentityEnvelope
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailure
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleStatus
import com.aqua.aqualight.data.devices.model.DeviceUid

/** Pure reducer for one authenticated runtime-metadata generation. */
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
            moduleStatus = null
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
        else -> reduceCurrent(current, fragment)
    }

    fun reject(
        current: DeviceRuntimeMetadataGenerationState,
        code: DeviceRuntimeMetadataFailureCode,
        field: String?
    ): DeviceRuntimeMetadataReduction.Rejected = DeviceRuntimeMetadataReduction.Rejected(
        DeviceRuntimeMetadataGenerationState.Rejected(
            deviceUid = current.deviceUid,
            generation = current.generation,
            failure = DeviceRuntimeMetadataFailure(code = code, field = field)
        )
    )

    private fun reduceCurrent(
        current: DeviceRuntimeMetadataGenerationState,
        fragment: DeviceRuntimeMetadataFragment
    ): DeviceRuntimeMetadataReduction {
        return when (val merge = current.fragments().merge(current.deviceUid, fragment)) {
            is FragmentMerge.Accepted -> DeviceRuntimeMetadataReduction.Accepted(
                merge.fragments.toState(current.deviceUid, current.generation)
            )
            is FragmentMerge.Conflict -> reject(current, merge.code, merge.field)
        }
    }

    private fun Fragments.merge(
        deviceUid: DeviceUid,
        fragment: DeviceRuntimeMetadataFragment
    ): FragmentMerge = when (fragment) {
        is DeviceRuntimeMetadataFragment.Identity -> mergeIdentity(deviceUid, fragment.value)
        is DeviceRuntimeMetadataFragment.Capabilities -> mergeCapabilities(fragment.value)
        is DeviceRuntimeMetadataFragment.Modules -> mergeModuleStatus(fragment.value)
    }

    private fun Fragments.mergeIdentity(
        deviceUid: DeviceUid,
        incoming: DeviceRuntimeIdentityEnvelope
    ): FragmentMerge {
        if (incoming.identity.deviceUid != deviceUid) {
            return FragmentMerge.Conflict(
                DeviceRuntimeMetadataFailureCode.DEVICE_UID_MISMATCH,
                "deviceUid"
            )
        }
        moduleStatus?.mismatchField(incoming.identity)?.let { field ->
            return FragmentMerge.Conflict(
                DeviceRuntimeMetadataFailureCode.STATUS_IDENTITY_MISMATCH,
                field
            )
        }
        return if (identity == null || identity == incoming) {
            FragmentMerge.Accepted(copy(identity = incoming))
        } else {
            FragmentMerge.Conflict(
                DeviceRuntimeMetadataFailureCode.CONFLICTING_IDENTITY,
                "identity"
            )
        }
    }

    private fun Fragments.mergeCapabilities(
        incoming: DeviceRuntimeCapabilities
    ): FragmentMerge = if (capabilities == null || capabilities == incoming) {
        FragmentMerge.Accepted(copy(capabilities = incoming))
    } else {
        FragmentMerge.Conflict(
            DeviceRuntimeMetadataFailureCode.CONFLICTING_CAPABILITIES,
            "capabilities"
        )
    }

    private fun Fragments.mergeModuleStatus(
        incoming: DeviceRuntimeModuleStatus
    ): FragmentMerge {
        identity?.identity?.let(incoming::mismatchField)?.let { field ->
            return FragmentMerge.Conflict(
                DeviceRuntimeMetadataFailureCode.STATUS_IDENTITY_MISMATCH,
                field
            )
        }
        return if (moduleStatus == null || moduleStatus == incoming) {
            FragmentMerge.Accepted(copy(moduleStatus = incoming))
        } else {
            FragmentMerge.Conflict(
                DeviceRuntimeMetadataFailureCode.CONFLICTING_MODULES,
                "modules"
            )
        }
    }

    private fun DeviceRuntimeMetadataGenerationState.fragments(): Fragments = when (this) {
        is DeviceRuntimeMetadataGenerationState.Collecting -> Fragments(
            identity = identity,
            capabilities = capabilities,
            moduleStatus = moduleStatus
        )
        is DeviceRuntimeMetadataGenerationState.Ready -> Fragments(
            identity = identityEnvelope,
            capabilities = metadata.capabilities,
            moduleStatus = moduleStatus
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
        val readyModuleStatus = moduleStatus
        return if (readyIdentity != null && readyCapabilities != null && readyModuleStatus != null) {
            DeviceRuntimeMetadataGenerationState.Ready(
                deviceUid = deviceUid,
                generation = generation,
                identityEnvelope = readyIdentity,
                moduleStatus = readyModuleStatus,
                metadata = DeviceRuntimeMetadata(
                    identity = readyIdentity.identity,
                    capabilities = readyCapabilities,
                    modules = readyModuleStatus.modules
                )
            )
        } else {
            DeviceRuntimeMetadataGenerationState.Collecting(
                deviceUid = deviceUid,
                generation = generation,
                identity = readyIdentity,
                capabilities = readyCapabilities,
                moduleStatus = readyModuleStatus
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
        val identity: DeviceRuntimeIdentityEnvelope?,
        val capabilities: DeviceRuntimeCapabilities?,
        val moduleStatus: DeviceRuntimeModuleStatus?
    )
}
