package com.aqua.aqualight.data.devices.light.programs.model

enum class LightProgramRepeatMode {
    EVERY,
    WEEK,
    WEEKEND,
    CUSTOM;

    companion object {
        fun fromStorage(
            value: String
        ): LightProgramRepeatMode {
            return entries.firstOrNull { mode ->
                mode.name.equals(value, ignoreCase = true)
            } ?: EVERY
        }
    }
}

enum class LightProgramTransitionMode {
    LINEAR,
    SMOOTH,
    NATURAL;

    companion object {
        fun fromStorage(
            value: String
        ): LightProgramTransitionMode {
            return entries.firstOrNull { mode ->
                mode.name.equals(value, ignoreCase = true)
            } ?: NATURAL
        }
    }
}

enum class LightProgramSyncState {
    LOCAL_ONLY,
    ACTIVE_SYNCED,
    ACTIVE_DIRTY,
    SYNC_FAILED;

    companion object {
        fun fromStorage(
            value: String
        ): LightProgramSyncState {
            return entries.firstOrNull { state ->
                state.name.equals(value, ignoreCase = true)
            } ?: LOCAL_ONLY
        }
    }
}
