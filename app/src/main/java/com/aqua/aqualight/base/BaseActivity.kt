package com.aqua.aqualight.base

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class BaseActivity : AppCompatActivity() {

    // Activity’ye özel coroutine scope
    private val activityJob = SupervisorJob()
    protected val uiScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate + activityJob)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DİKKAT: Burada system bars ile oynama yok! (decorView daha oluşmadı)
    }

    // İçerik yüklendikten hemen sonra immersive mode uygula
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyEdgeToEdge()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        applyEdgeToEdge()
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(view, params)
        applyEdgeToEdge()
    }

    protected fun applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    protected fun logError(tag: String, message: String?, throwable: Throwable? = null) {
        android.util.Log.e(tag, message, throwable)
    }

    override fun onDestroy() {
        activityJob.cancel()
        super.onDestroy()
    }
}