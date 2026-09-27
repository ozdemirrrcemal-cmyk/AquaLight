package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContextProvider
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContextResult
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationOperations
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPolicy
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPreparation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationQuery
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterEvidencePolicy
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** Owner-bound use cases; feature UI neither constructs persistence nor discovers an ambient owner. */
internal class DefaultHealthObservationOperations(
    private val access: HealthObservationWriteAccess,
    private val contextProvider: AquariumHealthContextProvider,
    private val water: WaterAnalysisOperations,
    private val clock: () -> Long = System::currentTimeMillis
) : HealthObservationOperations {
    private val media = HealthObservationMedia(access.appContext)
    override suspend fun prepare(tankId: Long, observedAtMillis: Long): HealthObservationPreparation {
        access.session.requireCurrent()
        val source = water.analysisAtOrBefore(tankId, observedAtMillis).first()
        val context = context(tankId)
        return HealthObservationPreparation(context,
            ObservationWaterEvidencePolicy.resolve(tankId, observedAtMillis, source, RECENCY))
    }

    override fun history(query: HealthObservationQuery) = access.queries.history(access.owner, query)
        .onStart { access.session.requireCurrent() }.onEach { access.session.requireCurrent() }

    override fun observation(tankId: Long, observationId: Long) =
        access.queries.record(access.owner, tankId, observationId)
            .onStart { access.session.requireCurrent() }.onEach { access.session.requireCurrent() }

    override suspend fun save(input: HealthObservationInput): Long {
        val frozen = HealthObservationPolicy.validate(input, clock())
        val replay = access.withTankWrite(frozen.identity.tankId) {
            withContext(Dispatchers.IO) { access.commits.replay(access.owner, frozen) }
        }
        if (replay != null) return replay
        // Reads may activate Water history using the session barrier, so obtain them before writer admission.
        val source = water.analysisAtOrBefore(frozen.identity.tankId, frozen.identity.observedAtMillis).first()
        return access.withTankWrite(frozen.identity.tankId) {
            withContext(Dispatchers.IO) { access.commits.replay(access.owner, frozen) } ?: run {
                val prepared = prepareHealthObservation(frozen, HealthObservationPreparation(
                    context(frozen.identity.tankId), ObservationWaterEvidencePolicy.resolve(frozen.identity.tankId,
                        frozen.identity.observedAtMillis, source, RECENCY)))
                access.awaitCommit {
                    media.requireCandidate(access.owner, frozen.notes.photoUri)
                    val id = access.commits.create(access.owner, prepared, clock()) {
                        access.requireAuthority(frozen.identity.tankId)
                    }
                    media.committed(frozen.notes.photoUri)
                    id
                }
            }
        }
    }

    override suspend fun delete(tankId: Long, observationId: Long) = access.withTankWrite(tankId) {
        access.awaitCommit {
            val photo = access.database.observations().record(access.owner, tankId, observationId)
                ?.toStored()?.input?.photoUri
            access.commits.delete(access.owner, tankId, observationId) { access.requireAuthority(tankId) }
            media.deleted(access.owner, photo)
        }
    }

    private suspend fun context(tankId: Long): AquariumHealthContext {
        val result = contextProvider.capture(tankId)
        access.session.requireCurrent()
        return when (result) {
            is AquariumHealthContextResult.Available -> result.context
            AquariumHealthContextResult.TankMissing -> error("The selected aquarium is no longer available.")
            is AquariumHealthContextResult.Unavailable ->
                error("Aquarium health context is unavailable: ${result.reason}")
        }
    }

    private companion object {
        // A dated UI linking convention, never a claim that a sample remains biologically safe for a day.
        val RECENCY = ObservationWaterWindow(24L * 60L * 60L * 1000L, "observation-link-24h-v1")
    }
}
