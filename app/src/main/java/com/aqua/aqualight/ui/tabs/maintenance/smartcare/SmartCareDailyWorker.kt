package com.aqua.aqualight.ui.tabs.maintenance.smartcare

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

      if (inputData.getBoolean(KEY_TEST_MODE, false)) {
        showTestToast(
          "SmartCare worker çalıştı: ${generatedTasks.size} görev kontrol edildi"
        )
      }

      Result.success()
    } catch (exception: Exception) {
      exception.printStackTrace()

      if (inputData.getBoolean(KEY_TEST_MODE, false)) {
        showTestToast(
          "SmartCare worker hata verdi"
        )
      }

      Result.retry()
    }
  }

  private fun showTestToast(
    message: String
  ) {
    Handler(
      Looper.getMainLooper()
    ).post {
      Toast.makeText(
        applicationContext,
        message,
        Toast.LENGTH_LONG
      ).show()
    }
  }

  companion object {
    private const val WORK_NAME = "smart_care_daily_worker"
    private const val TEST_WORK_NAME = "smart_care_daily_worker_test"
    private const val KEY_TEST_MODE = "test_mode"

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

    fun runOnceForTest(
      context: Context
    ) {
      val request = OneTimeWorkRequestBuilder<SmartCareDailyWorker>()
        .setInputData(
          workDataOf(
            KEY_TEST_MODE to true
          )
        )
        .build()

      WorkManager.getInstance(
        context.applicationContext
      ).enqueueUniqueWork(
        TEST_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
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