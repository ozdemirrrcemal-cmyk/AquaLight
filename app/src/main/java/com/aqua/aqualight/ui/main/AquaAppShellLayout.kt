package com.aqua.aqualight.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.aqua.aqualight.R

/**
 * Owns the system-bar contract for the root application shell.
 *
 * Normal destinations remain inside safe drawing insets. A destination with a visual backdrop can
 * temporarily request full-bleed content: the NavHost then reaches the physical display edges while
 * that destination remains responsible for insetting only its interactive foreground content.
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

    private data class SystemBarAppearance(
        val statusBarColor: Int,
        val navigationBarColor: Int,
        val navigationBarDividerColor: Int?,
        val lightStatusBars: Boolean,
        val lightNavigationBars: Boolean,
        val statusBarContrastEnforced: Boolean?,
        val navigationBarContrastEnforced: Boolean?
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

    private var contentDrawsBehindSystemBars = false
    private var defaultSystemBarAppearance: SystemBarAppearance? = null

    private val safeDrawingTypes =
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()

    init {
        enableEdgeToEdgeWindow()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            safeDrawingInsets =
                windowInsets.getInsets(
                    safeDrawingTypes
                )

            applyInsetsToShellChildren()

            if (contentDrawsBehindSystemBars) {
                // A full-bleed destination must receive the original insets so it can protect only
                // its foreground controls without shrinking its visual backdrop.
                windowInsets
            } else {
                WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(
                        safeDrawingTypes,
                        Insets.NONE
                    )
                    .build()
            }
        }
    }

    /**
     * Enables a destination-owned full-bleed backdrop without changing the inset contract of any
     * other screen. The default system-bar appearance is restored when this mode is disabled.
     */
    fun setContentDrawsBehindSystemBars(enabled: Boolean) {
        if (contentDrawsBehindSystemBars == enabled) return

        contentDrawsBehindSystemBars = enabled
        captureDefaultSystemBarAppearanceIfNeeded()
        applySystemBarAppearance()
        applyInsetsToShellChildren()
        ViewCompat.requestApplyInsets(this)
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

        bottomNavigation?.let { bottomNavigationView ->
            ViewCompat.setOnApplyWindowInsetsListener(
                bottomNavigationView
            ) { _, childInsets ->
                applyInsetsToBottomNavigation(
                    bottomNavigationView
                )

                childInsets
            }
        }

        applyInsetsToShellChildren()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        enableEdgeToEdgeWindow()
        captureDefaultSystemBarAppearanceIfNeeded()
        applySystemBarAppearance()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        if (contentDrawsBehindSystemBars) {
            contentDrawsBehindSystemBars = false
            applySystemBarAppearance()
        }

        super.onDetachedFromWindow()
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

        val contentInsets = if (contentDrawsBehindSystemBars) {
            Insets.NONE
        } else {
            Insets.of(
                safeDrawingInsets.left,
                safeDrawingInsets.top,
                safeDrawingInsets.right,
                if (bottomNavigationVisible) {
                    0
                } else {
                    safeDrawingInsets.bottom
                }
            )
        }

        navHostView.updatePadding(
            left =
                navHostBasePadding.left +
                    contentInsets.left,
            top =
                navHostBasePadding.top +
                    contentInsets.top,
            right =
                navHostBasePadding.right +
                    contentInsets.right,
            bottom =
                navHostBasePadding.bottom +
                    contentInsets.bottom
        )

        applyInsetsToBottomNavigation(
            bottomNavigationView
        )
    }

    private fun applyInsetsToBottomNavigation(
        bottomNavigationView: View
    ) {
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

    private fun enableEdgeToEdgeWindow() {
        context.findActivity()?.let { activity ->
            WindowCompat.setDecorFitsSystemWindows(
                activity.window,
                false
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun captureDefaultSystemBarAppearanceIfNeeded() {
        if (defaultSystemBarAppearance != null) return

        val activity = context.findActivity() ?: return
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, this)

        defaultSystemBarAppearance = SystemBarAppearance(
            statusBarColor = window.statusBarColor,
            navigationBarColor = window.navigationBarColor,
            navigationBarDividerColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor
            } else {
                null
            },
            lightStatusBars = controller.isAppearanceLightStatusBars,
            lightNavigationBars = controller.isAppearanceLightNavigationBars,
            statusBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced
            } else {
                null
            },
            navigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced
            } else {
                null
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarAppearance() {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, this)

        if (contentDrawsBehindSystemBars) {
            val transparentColor = ContextCompat.getColor(
                context,
                R.color.aqua_color_transparent
            )

            window.statusBarColor = transparentColor
            window.navigationBarColor = transparentColor
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = transparentColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            return
        }

        val appearance = defaultSystemBarAppearance ?: return
        window.statusBarColor = appearance.statusBarColor
        window.navigationBarColor = appearance.navigationBarColor
        controller.isAppearanceLightStatusBars = appearance.lightStatusBars
        controller.isAppearanceLightNavigationBars = appearance.lightNavigationBars

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appearance.navigationBarDividerColor?.let { color ->
                window.navigationBarDividerColor = color
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appearance.statusBarContrastEnforced?.let { enforced ->
                window.isStatusBarContrastEnforced = enforced
            }
            appearance.navigationBarContrastEnforced?.let { enforced ->
                window.isNavigationBarContrastEnforced = enforced
            }
        }
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
