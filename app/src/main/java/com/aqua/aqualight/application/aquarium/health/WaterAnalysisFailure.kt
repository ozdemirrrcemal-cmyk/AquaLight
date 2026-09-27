package com.aqua.aqualight.application.aquarium.health

enum class WaterAnalysisFailure { CORRUPT_DATA, UNSUPPORTED_SCHEMA, UNSUPPORTED_VALUE, STORE_UNAVAILABLE }

class WaterAnalysisUnavailableException(val failure: WaterAnalysisFailure, cause: Throwable) :
    Exception("Water analysis is unavailable: $failure", cause)
