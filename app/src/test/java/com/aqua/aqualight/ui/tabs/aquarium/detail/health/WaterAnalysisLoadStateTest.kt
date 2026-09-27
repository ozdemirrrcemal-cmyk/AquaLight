package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisFailure
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisUnavailableException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterAnalysisLoadStateTest {
    @Test
    fun `empty content remains different from load and storage failure`() = runBlocking {
        assertEquals(listOf(WaterAnalysisLoadState.Loading, WaterAnalysisLoadState.Content(emptyList<Int>())),
            flowOf(emptyList<Int>()).asWaterLoadState().toList())
        val failed = flow<List<Int>> { throw IOException("unreadable") }.asWaterLoadState().toList()
        assertEquals(listOf(WaterAnalysisLoadState.Loading,
            WaterAnalysisLoadState.Error(WaterAnalysisFailure.STORE_UNAVAILABLE)), failed)
    }

    @Test
    fun `typed read failures keep their cause category instead of becoming an empty history`() = runBlocking {
        WaterAnalysisFailure.entries.forEach { failure ->
            val source = flow<Int> { throw WaterAnalysisUnavailableException(failure, IOException()) }
            assertEquals(WaterAnalysisLoadState.Error(failure), source.asWaterLoadState().toList().last())
        }
    }

    @Test
    fun `owner and lifecycle cancellation never emit a storage error`() = runBlocking {
        val source = flow<Int> { throw CancellationException("expired owner") }
        val seen = mutableListOf<WaterAnalysisLoadState<Int>>()
        val failure = runCatching { source.asWaterLoadState().toList(seen) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(listOf(WaterAnalysisLoadState.Loading), seen)
    }
}
