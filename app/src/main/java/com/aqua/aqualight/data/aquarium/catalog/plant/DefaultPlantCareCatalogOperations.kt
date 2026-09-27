package com.aqua.aqualight.data.aquarium.catalog.plant

import android.content.Context
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogFailure
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogOperations
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogResult
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogSnapshot
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Composition owns one instance. Failed loads remain retryable; no second UI cache exists. */
internal class DefaultPlantCareCatalogOperations(
    private val readAsset: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PlantCareCatalogOperations {
    private val mutex = Mutex()
    private var cached: PlantCareCatalogSnapshot? = null

    override suspend fun snapshot(): PlantCareCatalogResult = withContext(ioDispatcher) {
        mutex.withLock {
            cached?.let { return@withLock PlantCareCatalogResult.Available(it) }
            try {
                val snapshot = PlantCatalogParser.parse(readAsset())
                cached = snapshot
                PlantCareCatalogResult.Available(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                PlantCareCatalogResult.Unavailable(PlantCareCatalogFailure.UNREADABLE)
            } catch (_: RuntimeException) {
                PlantCareCatalogResult.Unavailable(PlantCareCatalogFailure.INVALID_CONTENT)
            }
        }
    }

    companion object {
        fun create(context: Context): DefaultPlantCareCatalogOperations {
            val assets = context.applicationContext.assets
            return DefaultPlantCareCatalogOperations(
                readAsset = { assets.open("aqualight_plant_catalog.json").bufferedReader().use { it.readText() } }
            )
        }
    }
}
