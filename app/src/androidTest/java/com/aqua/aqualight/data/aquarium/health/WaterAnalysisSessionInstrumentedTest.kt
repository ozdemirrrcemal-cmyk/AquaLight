package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessmentEngine
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionExpiredException
import com.aqua.aqualight.data.auth.OwnerSessionTestFixture
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.UserDataRestoreJournal
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterAnalysisSessionInstrumentedTest {
    @Test
    fun sameOwnerReentryRejectsOldCreateDeleteAndReadsWithoutLosingHistory() = runBlocking {
        withFixture { fixture ->
            val tankId = fixture.addTank(fixture.owner)
            val operations = DefaultWaterAnalysisOperations(
                    fixture.store, fixture.session.lease, evaluationPreparation = null
                )
            val id = operations.saveAnalysis(input(tankId))
            val current = fixture.session.reopen(fixture.owner)

            assertTrue(runCatching { operations.saveAnalysis(input(tankId)) }
                .exceptionOrNull() is OwnerSessionExpiredException)
            assertTrue(runCatching { operations.deleteAnalysis(id) }
                .exceptionOrNull() is OwnerSessionExpiredException)
            assertTrue(runCatching { operations.analysis(id).first() }
                .exceptionOrNull() is OwnerSessionExpiredException)
            val currentOperations = DefaultWaterAnalysisOperations(fixture.store, current, evaluationPreparation = null)
            assertEquals(id, currentOperations.analysis(id).first()?.id)
            currentOperations.deleteAnalysis(id)
            assertTrue(currentOperations.analysesForTank(tankId).first().isEmpty())
        }
    }

    @Test
    fun queuedCreateCannotCommitWhenAQueuedTransitionRunsFirst() = runBlocking {
        withFixture { fixture ->
            coroutineScope {
                val tankId = fixture.addTank(fixture.owner)
                val operations = DefaultWaterAnalysisOperations(
                    fixture.store, fixture.session.lease, evaluationPreparation = null
                )
                val queued = fixture.session.lease.withWrite {
                    val transition = async(start = CoroutineStart.UNDISPATCHED) {
                        fixture.session.reopen(fixture.owner)
                    }
                    val write = async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { operations.saveAnalysis(input(tankId)) }
                    }
                    transition to write
                }
                queued.first.await()
                assertTrue(queued.second.await().exceptionOrNull() is OwnerSessionExpiredException)
                assertTrue(fixture.store.analysesForOwnerFlow(fixture.owner).first().isEmpty())
            }
        }
    }

    @Test
    fun oldCollectorIsCancelledAndExplicitOwnerReadsNeverFollowAmbientAccount() = runBlocking {
        withFixture { fixture ->
            coroutineScope {
                val firstTank = fixture.addTank(fixture.owner)
                val oldOperations = DefaultWaterAnalysisOperations(
                    fixture.store, fixture.session.lease, evaluationPreparation = null
                )
                val firstId = oldOperations.saveAnalysis(input(firstTank))
                val firstEmission = CompletableDeferred<Unit>()
                val cancelled = CompletableDeferred<Throwable?>()
                val received = mutableListOf<Long>()
                val collector = launch {
                    cancelled.complete(runCatching {
                        oldOperations.analysesForTank(firstTank).collect { records ->
                            received += records.map { it.id }
                            firstEmission.complete(Unit)
                        }
                    }.exceptionOrNull())
                }
                firstEmission.await()
                val next = fixture.session.reopen(fixture.otherOwner)
                val secondTank = fixture.addTank(fixture.otherOwner)
                val newOperations = DefaultWaterAnalysisOperations(fixture.store, next, evaluationPreparation = null)
                val secondId = newOperations.saveAnalysis(input(secondTank))
                assertTrue(cancelled.await() is OwnerSessionExpiredException)
                collector.join()
                assertEquals(listOf(firstId), received)
                UserDataScope.withOwnerUid(fixture.otherOwner) {
                    val firstOwnerRecords = fixture.store.analysesForOwnerFlow(fixture.owner).first()
                    assertEquals(listOf(firstId), firstOwnerRecords.map { it.id })
                    assertEquals(listOf(secondId), newOperations.analysesForTank(secondTank).first().map { it.id })
                }
            }
        }
    }

    @Test
    fun savedEvaluationIsAtomicAndRetryDoesNotReadChangedContext() = runBlocking {
        withFixture { fixture ->
            val tankId = fixture.addTank(fixture.owner)
            val input = input(tankId)
            var captures = 0
            val operations = DefaultWaterAnalysisOperations(fixture.store, fixture.session.lease) { sample ->
                captures++
                check(captures == 1) { "Committed retries must not recapture context." }
                val context = AquariumHealthContext(HealthContextCapture(System.currentTimeMillis(), "context-v1"),
                    HealthTankFacts(tankId, "Freshwater Fish", "Freshwater", null, null),
                    HealthCatalogRevisions("plants-v1", "animals-v1"), emptyList(), emptyList(), emptyList())
                WaterEvaluationCodec.encode(sample, context, WaterQualityAssessmentEngine.assess(sample, context))
            }
            val id = operations.saveAnalysis(input)
            assertEquals(id, operations.saveAnalysis(input))
            assertEquals(1, captures)
            val stored = fixture.store.analysesForTankFlow(fixture.owner, tankId).first().single()
            assertTrue(stored.evaluation != null)
            val reopened = operations.analysis(id).first()!!
            assertEquals("context-v1", reopened.assessment?.contextRevision)
            assertEquals(input.measurements.single().value, reopened.measurements.single().canonicalValue!!, 0.0)
        }
    }

    @Test
    fun evaluationFailureDoesNotLeaveARawOnlyRecord() = runBlocking {
        withFixture { fixture ->
            val tankId = fixture.addTank(fixture.owner)
            val operations = DefaultWaterAnalysisOperations(fixture.store, fixture.session.lease) {
                throw IllegalStateException("context read failed")
            }
            assertTrue(runCatching { operations.saveAnalysis(input(tankId)) }.isFailure)
            assertTrue(fixture.store.analysesForTankFlow(fixture.owner, tankId).first().isEmpty())
        }
    }

    @Test
    fun pendingRestoreRejectsNewAnalysisMutationsUntilRecoveryCommits() = runBlocking {
        withFixture { fixture ->
            val tankId = fixture.addTank(fixture.owner)
            val operations = DefaultWaterAnalysisOperations(fixture.store, fixture.session.lease, null)
            val id = operations.saveAnalysis(input(tankId))
            fixture.archive.begin(fixture.owner, setOf(tankId))
            assertTrue(runCatching { operations.saveAnalysis(input(tankId)) }.isFailure)
            assertTrue(runCatching { operations.deleteAnalysis(id) }.isFailure)
            assertEquals(1, operations.analysesForTank(tankId).first().size)
            fixture.archive.markCommitted(fixture.owner)
            operations.deleteAnalysis(id)
            assertTrue(operations.analysesForTank(tankId).first().isEmpty())
        }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) = withTimeout(30_000L) {
        val fixture = Fixture(ApplicationProvider.getApplicationContext())
        try {
            block(fixture)
        } finally {
            withContext(NonCancellable) {
                fixture.session.close()
                listOf(fixture.owner, fixture.otherOwner).forEach { owner ->
                    UserDataScope.withOwnerUid(owner) {
                        fixture.store.clearAllAnalyses(owner)
                        fixture.tanks.clearAllTanks(owner)
                        TankCareIntegrityJournal.clearOwner(owner)
                        fixture.archive.clearOwner(owner)
                    }
                }
            }
        }
    }

    private fun input(tankId: Long) = WaterAnalysisInput(
        tankId = tankId,
        measuredAtMillis = System.currentTimeMillis(),
        temperatureCelsius = null,
        temperatureSource = null,
        measurements = listOf(WaterMeasurementInput(
            parameter = WaterParameter.PH,
            value = 7.0,
            selection = WaterMeasurementSelection(
                WaterMeasurementMethod.MANUAL, null, WaterMeasurementBasis.PH, WaterMeasurementUnit.NONE
            )
        ))
    )

    private class Fixture(context: Context) {
        val owner = "water-session-${UUID.randomUUID()}"
        val otherOwner = "water-other-${UUID.randomUUID()}"
        val session = OwnerSessionTestFixture(owner)
        val archive = UserDataRestoreJournal(context)
        val store = WaterAnalysisDataStoreManager(context)
        val tanks = AquariumTankDataStoreManager(context)

        suspend fun addTank(ownerUid: String): Long = UserDataScope.withOwnerUid(ownerUid) {
            tanks.addTankFromDraft(TankDraft(
                name = "Session Tank", setupDateEpochDay = 20_454L,
                widthCm = 60, lengthCm = 40, heightCm = 40, tankType = "Planted"
            ))
        }
    }
}
