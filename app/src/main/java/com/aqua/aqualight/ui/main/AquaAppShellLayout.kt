package com.aqua.aqualight.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.aqua.aqualight.R

/**
 * Owns the system-bar contract for the authenticated app shell.
 *
 * The shell renders edge-to-edge, keeps fragment content outside status bars
 * and display cutouts, and lets the bottom-navigation background extend behind
 * the gesture/navigation area without duplicating insets in child fragments.
 */
class AquaAppShellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(
    context,
    attrs,
    defStyleAttr
) {

    private data class ViewPadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private var navHost: View? = null
    private var bottomNavigation: View? = null

    private var navHostBasePadding =
        ViewPadding(
            left = 0,
            top = 0,
            right = 0,
            bottom = 0
        )

    private var bottomNavigationBasePadding =
        ViewPadding(
            left = 0,
            top = 0,
            right = 0,
            bottom = 0
        )

    private var safeDrawingInsets: Insets =
        Insets.NONE

    private var lastBottomNavigationVisibility =
        View.GONE

    private val safeDrawingTypes =
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()

    init {
        context.findActivity()?.let { activity ->
            WindowCompat.setDecorFitsSystemWindows(
                activity.window,
                false
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            safeDrawingInsets =
                windowInsets.getInsets(
                    safeDrawingTypes
                )

            applyInsetsToShellChildren()

            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(
                    safeDrawingTypes,
                    Insets.NONE
                )
                .build()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        navHost =
            findViewById(
                R.id.nav_host
            )

        bottomNavigation =
            findViewById(
                R.id.bottomNav
            )

        navHostBasePadding =
            navHost.capturePadding()

        bottomNavigationBasePadding =
            bottomNavigation.capturePadding()

        lastBottomNavigationVisibility =
            bottomNavigation?.visibility
                ?: View.GONE

        applyInsetsToShellChildren()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        context.findActivity()?.let { activity ->
            WindowCompat.setDecorFitsSystemWindows(
                activity.window,
                false
            )
        }

        ViewCompat.requestApplyInsets(this)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onLayout(
            changed,
            left,
            top,
            right,
            bottom
        )

        val currentVisibility =
            bottomNavigation?.visibility
                ?: View.GONE

        if (
            currentVisibility !=
            lastBottomNavigationVisibility
        ) {
            lastBottomNavigationVisibility =
                currentVisibility

            post {
                applyInsetsToShellChildren()
            }
        }
    }

    private fun applyInsetsToShellChildren() {
        val navHostView =
            navHost
                ?: return

        val bottomNavigationView =
            bottomNavigation
                ?: return

        val bottomNavigationVisible =
            bottomNavigationView.visibility ==
                View.VISIBLE

        navHostView.updatePadding(
            left =
                navHostBasePadding.left +
                    safeDrawingInsets.left,
            top =
                navHostBasePadding.top +
                    safeDrawingInsets.top,
            right =
                navHostBasePadding.right +
                    safeDrawingInsets.right,
            bottom =
                navHostBasePadding.bottom +
                    if (bottomNavigationVisible) {
                        0
                    } else {
                        safeDrawingInsets.bottom
                    }
        )

        bottomNavigationView.updatePadding(
            left =
                bottomNavigationBasePadding.left +
                    safeDrawingInsets.left,
            top =
                bottomNavigationBasePadding.top,
            right =
                bottomNavigationBasePadding.right +
                    safeDrawingInsets.right,
            bottom =
                bottomNavigationBasePadding.bottom +
                    safeDrawingInsets.bottom
        )
    }

    private fun View?.capturePadding(): ViewPadding {
        return if (this == null) {
            ViewPadding(
                left = 0,
                top = 0,
                right = 0,
                bottom = 0
            )
        } else {
            ViewPadding(
                left = paddingLeft,
                top = paddingTop,
                right = paddingRight,
                bottom = paddingBottom
            )
        }
    }

    private fun Context.findActivity(): Activity? {
        var currentContext: Context? =
            this

        while (currentContext != null) {
            when (currentContext) {
                is Activity -> {
                    return currentContext
                }

                is ContextWrapper -> {
                    val baseContext =
                        currentContext.baseContext

                    if (baseContext === currentContext) {
                        return null
                    }

                    currentContext =
                        baseContext
                }

                else -> {
                    return null
                }
            }
        }

        return null
    }
}
