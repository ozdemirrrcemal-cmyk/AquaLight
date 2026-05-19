package com.aqua.aqualight.ui.tabs.maintenance.smartcare

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aqua.aqualight.data.CareTaskDataStoreManager
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SmartCareDailyWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(
  appContext,
  workerParams
) {

  override suspend fun doWork(): Result {
    return try {
      val tankDataStoreManager = AquariumTankDataStoreManager(
        applicationContext
      )

      val careTaskDataStoreManager = CareTaskDataStoreManager.create(
        applicationContext
      )

      val tanks = tankDataStoreManager.tanksFlow.first()

      val generatedTasks = SmartCareTaskGenerator.generateForTanks(
        tanks = tanks
      )

      careTaskDataStoreManager.syncAutomaticTasks(
        generatedTasks = generatedTasks
      )

      Result.success()
    } catch (exception: Exception) {
      exception.printStackTrace()
      Result.retry()
    }
  }

  companion object {
    private const val WORK_NAME = "smart_care_daily_worker"

    fun schedule(
      context: Context
    ) {
      val request = PeriodicWorkRequestBuilder<SmartCareDailyWorker>(
        1,
        TimeUnit.DAYS
      )
        .setInitialDelay(
          calculateInitialDelayMillis(),
          TimeUnit.MILLISECONDS
        )
        .build()

      WorkManager.getInstance(
        context.applicationContext
      ).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
      )
    }

    private fun calculateInitialDelayMillis(): Long {
      val now = Calendar.getInstance()

      val nextRun = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 6)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        if (before(now)) {
          add(Calendar.DAY_OF_YEAR, 1)
        }
      }

      return nextRun.timeInMillis - now.timeInMillis
    }
  }
}