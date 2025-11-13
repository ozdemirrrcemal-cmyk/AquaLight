package com.aqua.aqualight.ui.main

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val userPrefs by lazy { UserPreferencesManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        if (savedInstanceState == null) {
            // 🔹 Uygulama ilk açılış: graph'i login durumuna göre *ilk kez* set et
            lifecycleScope.launch {
                val loggedIn = try {
                    val prefs = userPrefs.userPrefsFlow.first()
                    prefs.isLoggedIn && prefs.idToken.isNotEmpty()
                } catch (e: Exception) {
                    // prefs okunamazsa fail-safe: login ekranına gönder
                    false
                }

                val graph = navController.navInflater.inflate(R.navigation.nav_root).apply {
                    setStartDestination(
                        if (loggedIn) R.id.nav_app
                        else R.id.authContainerFragment
                    )
                }
                navController.graph = graph

                setupBottomBar(navController)

                // ✅ Artık doğru graph set edildi, kullanıcıya göster
                binding.navHost.isVisible = true
            }
        } else {
            // 🔹 Rotate / tema değişimi sonrası:
            // Navigation kendi graph + backstack’ini restore eder
            setupBottomBar(navController)
            binding.navHost.isVisible = true
        }
    }

    private fun setupBottomBar(navController: NavController) {
        // Bottom bar ↔ nav bağla
        binding.bottomNav.setupWithNavController(navController)

        // Hangi destination'larda bottom bar görünsün?
        fun isInAppDest(destinationId: Int): Boolean {
            return when (destinationId) {
                R.id.aquariumFragment,
                R.id.devicesFragment,
                R.id.settingsFragment -> true
                else -> false
            }
        }

        // Her destination değiştiğinde çağrılır
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = isInAppDest(destination.id)
        }

        // Recreate / tema değişimi sonrası ilk state'i de elle tetikle
        navController.currentDestination?.let { dest ->
            binding.bottomNav.isVisible = isInAppDest(dest.id)
        }
    }
} 