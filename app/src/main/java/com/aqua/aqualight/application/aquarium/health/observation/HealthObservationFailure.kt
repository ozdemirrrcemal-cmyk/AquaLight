package com.aqua.aqualight.application.aquarium.health.observation

enum class HealthObservationRequestFailure { PAYLOAD_CHANGED, RECORD_REMOVED }
class HealthObservationRequestException(val failure: HealthObservationRequestFailure) :
    IllegalStateException(failure.name)
