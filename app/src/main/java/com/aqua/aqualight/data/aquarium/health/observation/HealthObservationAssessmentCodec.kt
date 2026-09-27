package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.observation.ObservationAction
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessment
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessmentState
import com.aqua.aqualight.application.aquarium.health.observation.ObservationGap
import com.aqua.aqualight.data.aquarium.health.WaterEvaluationFindingCodec
import java.util.Collections

internal object HealthObservationAssessmentCodec {
    fun encode(result: ObservationAssessment): StoredHealthObservationAssessment =
        StoredHealthObservationAssessment.newBuilder().setEngineRevision(result.engineRevision)
            .setState(result.state.name).addAllGaps(result.gaps.map { it.name }.sorted())
            .addAllActions(result.actions.map { it.name }.sorted())
            .addAllWaterFindings(result.waterFindings.map(WaterEvaluationFindingCodec::encode)).build()

    fun decode(value: StoredHealthObservationAssessment): ObservationAssessment {
        require(value.engineRevision.isNotBlank())
        require(value.gapsList.distinct().size == value.gapsCount)
        require(value.actionsList.distinct().size == value.actionsCount)
        return ObservationAssessment(value.engineRevision, enumValueOf<ObservationAssessmentState>(value.state),
            Collections.unmodifiableSet(value.gapsList.map { enumValueOf<ObservationGap>(it) }.toSet()),
            Collections.unmodifiableSet(value.actionsList.map { enumValueOf<ObservationAction>(it) }.toSet()),
            Collections.unmodifiableList(value.waterFindingsList.map(WaterEvaluationFindingCodec::decode)))
    }
}
