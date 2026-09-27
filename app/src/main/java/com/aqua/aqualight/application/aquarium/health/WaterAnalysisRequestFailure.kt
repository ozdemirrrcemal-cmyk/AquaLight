package com.aqua.aqualight.application.aquarium.health

enum class WaterAnalysisRequestFailure { PAYLOAD_CHANGED, RECORD_DELETED }

class WaterAnalysisRequestException(val failure: WaterAnalysisRequestFailure) :
    IllegalStateException("Water analysis request could not be replayed: $failure")
