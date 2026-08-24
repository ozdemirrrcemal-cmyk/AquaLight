package com.aqua.aqualight.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.main.MainActivity

/**
 * Visual handoff only. AppSessionCoordinator startup remains owned by the process lifecycle while
 * this activity renders the branded splash surface. This activity never resolves authentication or
 * opens owner/device runtime itself.
 */
class SplashActivity : BaseActivity() {

    private var visualAnimationCompleted = false
    private var mainHandoffStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo: ImageView = findViewById(R.id.logoImage)
        val animation = AnimationUtils.loadAnimation(
            this,
            R.anim.logo_fade_scale
        )

        animation.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) = Unit

                override fun onAnimationRepeat(animation: Animation?) = Unit

                override fun onAnimationEnd(animation: Animation?) {
                    visualAnimationCompleted = true
                    completeVisualHandoffIfReady()
                }
            }
        )

        logo.startAnimation(animation)
    }

    override fun onPostResume() {
        super.onPostResume()
        completeVisualHandoffIfReady()
    }

    private fun completeVisualHandoffIfReady() {
        if (!isVisualHandoffReady()) {
            return
        }

        mainHandoffStarted = true
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
        overridePendingTransition(0, 0)
        finish()
    }

    private fun isVisualHandoffReady(): Boolean {
        val visualStateReady = visualAnimationCompleted && !mainHandoffStarted
        val activityStateReady = !isFinishing && !isDestroyed
        val lifecycleReady = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        return visualStateReady && activityStateReady && lifecycleReady
    }
}
