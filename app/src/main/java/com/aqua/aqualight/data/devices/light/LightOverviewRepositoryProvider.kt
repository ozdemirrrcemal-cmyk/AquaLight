package com.aqua.aqualight.data.devices.light

object LightOverviewRepositoryProvider {

    private val repository: LightOverviewRepository by lazy {
        LightOverviewRepositoryImpl()
    }

    fun get(): LightOverviewRepository {
        return repository
    }
}