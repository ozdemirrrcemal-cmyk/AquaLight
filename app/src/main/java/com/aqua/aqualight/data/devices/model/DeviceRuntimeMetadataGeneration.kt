package com.aqua.aqualight.data.devices.model

@JvmInline
value class DeviceRuntimeMetadataGeneration(val value: Long) {
    init { require(value > 0L) { "Runtime metadata generation must be greater than zero." } }

    internal fun next(): DeviceRuntimeMetadataGeneration {
        check(value < Long.MAX_VALUE) { "Runtime metadata generation space is exhausted." }
        return DeviceRuntimeMetadataGeneration(value + 1L)
    }

    companion object { val FIRST = DeviceRuntimeMetadataGeneration(1L) }
}

enum class DeviceRuntimeMetadataFailureCode {
    IDENTITY_PARSE_FAILED,
    CAPABILITIES_PARSE_FAILED,
    MODULES_PARSE_FAILED,
    BOOTSTRAP_DISPATCH_FAILED,
    BOOTSTRAP_RESPONSE_FAILED,
    BOOTSTRAP_RESPONSE_MISMATCH,
    BOOTSTRAP_TIMEOUT,
    DEVICE_UID_MISMATCH,
    STATUS_IDENTITY_MISMATCH,
    CATALOG_VALIDATION_FAILED,
    CONFLICTING_IDENTITY,
    CONFLICTING_CAPABILITIES,
    CONFLICTING_MODULES,
    RUNTIME_UNAVAILABLE
}

data class DeviceRuntimeMetadataFailure(
    val code: DeviceRuntimeMetadataFailureCode,
    val field: String?
)

sealed interface DeviceRuntimeMetadataGenerationState {
    val deviceUid: DeviceUid
    val generation: DeviceRuntimeMetadataGeneration
    val publishedMetadata: DeviceRuntimeMetadata?

    data class Collecting(
        override val deviceUid: DeviceUid,
        override val generation: DeviceRuntimeMetadataGeneration,
        val identity: DeviceRuntimeIdentityEnvelope?,
        val capabilities: DeviceRuntimeCapabilities?,
        val moduleStatus: DeviceRuntimeModuleStatus?
    ) : DeviceRuntimeMetadataGenerationState {
        override val publishedMetadata: DeviceRuntimeMetadata? = null
    }

    data class Ready(
        override val deviceUid: DeviceUid,
        override val generation: DeviceRuntimeMetadataGeneration,
        val identityEnvelope: DeviceRuntimeIdentityEnvelope,
        val moduleStatus: DeviceRuntimeModuleStatus,
        val metadata: DeviceRuntimeMetadata
    ) : DeviceRuntimeMetadataGenerationState {
        override val publishedMetadata: DeviceRuntimeMetadata = metadata
    }

    data class Rejected(
        override val deviceUid: DeviceUid,
        override val generation: DeviceRuntimeMetadataGeneration,
        val failure: DeviceRuntimeMetadataFailure
    ) : DeviceRuntimeMetadataGenerationState {
        override val publishedMetadata: DeviceRuntimeMetadata? = null
    }
}

sealed interface DeviceRuntimeMetadataFragment {
    val generation: DeviceRuntimeMetadataGeneration

    data class Identity(
        override val generation: DeviceRuntimeMetadataGeneration,
        val value: DeviceRuntimeIdentityEnvelope
    ) : DeviceRuntimeMetadataFragment

    data class Capabilities(
        override val generation: DeviceRuntimeMetadataGeneration,
        val value: DeviceRuntimeCapabilities
    ) : DeviceRuntimeMetadataFragment

    data class Modules(
        override val generation: DeviceRuntimeMetadataGeneration,
        val value: DeviceRuntimeModuleStatus
    ) : DeviceRuntimeMetadataFragment
}

sealed interface DeviceRuntimeMetadataReduction {
    val state: DeviceRuntimeMetadataGenerationState

    data class Accepted(
        override val state: DeviceRuntimeMetadataGenerationState
    ) : DeviceRuntimeMetadataReduction

    data class IgnoredStale(
        override val state: DeviceRuntimeMetadataGenerationState,
        val staleGeneration: DeviceRuntimeMetadataGeneration
    ) : DeviceRuntimeMetadataReduction

    data class Rejected(
        override val state: DeviceRuntimeMetadataGenerationState.Rejected
    ) : DeviceRuntimeMetadataReduction
}
