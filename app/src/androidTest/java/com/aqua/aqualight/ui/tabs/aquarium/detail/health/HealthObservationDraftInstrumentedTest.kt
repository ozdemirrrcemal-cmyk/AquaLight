package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationOperations
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPage
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationQuery
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthObservationDraftInstrumentedTest {
    @Test
    fun requestAndLocalSubjectAndPhotoSurviveParcelableDraftRestoration() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val original = HealthObservationViewModel(UnusedObservationOperations, route())
                original.draft = Bundle().apply {
                    putLong("observed", 1_780_000_000_000)
                    putLong("subject", 31)
                    putString("notes", "Recorded leaf change")
                    putString("photo", "content://owned-health-candidate")
                    putStringArrayList("findings", arrayListOf(PlantFinding.LEAF_DAMAGE.name))
                }
                val before = original.formInput(activity)
                val restoredState = route().apply {
                    set("health_request", original.requestId)
                    set("health_draft", parcelCopy(original.draft))
                }
                val restored = HealthObservationViewModel(UnusedObservationOperations, restoredState)
                assertEquals(before, restored.formInput(activity))
                assertEquals(31L, (before.observation as HealthObservation.Plant).plantId)
                assertNotEquals(original.requestId,
                    HealthObservationViewModel(UnusedObservationOperations, route()).requestId)
            }
        }
    }

    private fun route() = SavedStateHandle(mapOf("tankId" to 2L, "healthKind" to "PLANT"))

    private fun parcelCopy(input: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(input)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(Bundle::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }
}

private object UnusedObservationOperations : HealthObservationOperations {
    override suspend fun prepare(tankId: Long, observedAtMillis: Long) = error("Unused in draft test")
    override fun history(query: HealthObservationQuery) = flowOf(HealthObservationPage(emptyList(), 0, null))
    override fun observation(tankId: Long, observationId: Long) = flowOf<HealthObservationSnapshot?>(null)
    override suspend fun save(input: HealthObservationInput): Long = error("Unused in draft test")
    override suspend fun delete(tankId: Long, observationId: Long) = error("Unused in draft test")
}
