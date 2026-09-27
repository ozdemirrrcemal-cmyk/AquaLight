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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterAnalysisSessionInstrumentedTest {
    @Test
    fun mismatchedTankCannotReadOrDeleteAnExistingAnalysis() = runBlocking {
        withFixture { fixture ->
            val tankId = fixture.addTank(fixture.owner)
            val otherTankId = fixture.addTank(fixture.owner)
            val operations = DefaultWaterAnalysisOperations(fixture.store, fixture.session.lease, null)
            val id = operations.saveAnalysis(input(tankId))
            assertNull(operations.analysis(otherTankId, id).first())
            assertNull(operations.latestAnalysis(otherTankId).first())
            operations.deleteAnalysis(otherTankId, id)
            assertEquals(id, operations.analysis(tankId, id).first()?.id)
            assertEquals(id, operations.latestAnalysis(tankId).first()?.id)
            operations.deleteAnalysis(tankId, id)
            assertNull(operations.analysis(tankId, id).first())
            assertNull(operations.latestAnalysis(tankId).first())
        }
    }

    @Test
    fun latestUsesObservationBeforeCommitOrderAndKeepsOtherTanksSeparate() = runBlocking {
        withFixture { fixture ->
            val tankId = fixture.addTank(fixture.owner)
            val otherTankId = fixture.addTank(fixture.owner)
            val operations = DefaultWaterAnalysisOperations(fixture.store, fixture.session.lease, null)
            val sample = input(tankId)
            val first = operations.saveAnalysis(sample)
            val backdated = operations.saveAnalysis(sample.copy(
                measuredAtMillis = sample.measuredAtMillis - 60_000L, requestId = UUID.randomUUID().toString()))
            assertEquals(first, operations.latestAnalysis(tankId).first()?.id)
            operations.saveAnalysis(input(otherTankId))
            assertEquals(first, operations.latestAnalysis(tankId).first()?.id)
            val tied = operations.saveAnalysis(sample.copy(requestId = UUID.randomUUID().toString()))
            assertEquals(tied, operations.latestAnalysis(tankId).first()?.id)
            operations.deleteAnalysis(tankId, tied)
            assertEquals(first, operations.latestAnalysis(tankId).first()?.id)
            operations.deleteAnalysis(tankId, first)
            assertEquals(backdated, operations.latestAnalysis(tankId).first()?.id)
        }
    }

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
            assertTrue(runCatching { operations.deleteAnalysis(tankId, id) }
                .exceptionOrNull() is OwnerSessionExpiredException)
            assertTrue(runCatching { operations.analysis(tankId, id).first() }
                .exceptionOrNull() is OwnerSessionExpiredException)
            val currentOperations = DefaultWaterAnalysisOperations(fixture.store, current, evaluationPreparation = null)
            assertEquals(id, currentOperations.analysis(tankId, id).first()?.id)
            currentOperations.deleteAnalysis(tankId, id)
            assertTrue(currentOperations.historyPage(tankId).first().records.isEmpty())
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
                assertTrue(waterHistoryTestRows(fixture.context, fixture.owner).isEmpty())
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
                        oldOperations.historyPage(firstTank).collect { records ->
                            received += records.records.map { it.id }
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
                    val firstOwnerRecords = waterHistoryTestRows(fixture.context, fixture.owner)
                    assertEquals(listOf(firstId), firstOwnerRecords.map { it.id })
                    assertEquals(listOf(secondId), newOperations.historyPage(secondTank).first().records.map { it.id })
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
            val stored = waterHistoryTestRows(fixture.context, fixture.owner, tankId).single()
            assertTrue(stored.evaluation != null)
            val reopened = operations.analysis(tankId, id).first()!!
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
            assertTrue(waterHistoryTestRows(fixture.context, fixture.owner, tankId).isEmpty())
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
            assertTrue(runCatching { operations.deleteAnalysis(tankId, id) }.isFailure)
            assertEquals(1, operations.historyPage(tankId).first().records.size)
            fixture.archive.markCommitted(fixture.owner)
            operations.deleteAnalysis(tankId, id)
            assertTrue(operations.historyPage(tankId).first().records.isEmpty())
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

    private class Fixture(val context: Context) {
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
