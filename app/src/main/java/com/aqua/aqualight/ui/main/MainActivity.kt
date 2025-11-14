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

        // Daha önce oluşturulmuş bir NavHost var mı? (tema değişimi vb.)
        val existingHost =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment?

        if (existingHost == null) {
            // 🔹 Uygulama ilk açılış (Splash'tan gelinen an)
            binding.navHost.isVisible = false
            binding.bottomNav.isVisible = false

            lifecycleScope.launch {
                val loggedIn = try {
                    val prefs = userPrefs.userPrefsFlow.first()
                    prefs.isLoggedIn && prefs.idToken.isNotEmpty()
                } catch (_: Exception) {
                    false // prefs okunamazsa login ekranı
                }

                // 1) Boş bir NavHostFragment oluştur
                val navHost = NavHostFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host, navHost)
                    .setPrimaryNavigationFragment(navHost) // defaultNavHost = true
                    .commitNow()

                // 2) Graph'i login durumuna göre hazırla
                val navController = navHost.navController
                val graph = navController.navInflater.inflate(R.navigation.nav_root).apply {
                    setStartDestination(
                        if (loggedIn) R.id.nav_app
                        else R.id.authContainerFragment
                    )
                }
                navController.graph = graph

                // 3) Bottom bar'ı bağla
                setupBottomBar(navController)

                // 4) İlk destination'a göre bottom bar görünürlüğü
                navController.currentDestination?.let { dest ->
                    binding.bottomNav.isVisible = isInAppDest(dest.id)
                }

                // 5) Artık her şey hazır, kullanıcıya göster
                binding.navHost.isVisible = true
            }
        } else {
            // 🔹 Tema değişimi / rotate sonrası:
            // NavHostFragment ve graph zaten restore edildi, sadece yeniden bağlan
            val navController = existingHost.navController

            setupBottomBar(navController)

            navController.currentDestination?.let { dest ->
                binding.bottomNav.isVisible = isInAppDest(dest.id)
            }

            binding.navHost.isVisible = true
        }
    }

    // App içi tab ekranlarını temsil eden id'ler
    private fun isInAppDest(destinationId: Int): Boolean {
        return when (destinationId) {
            R.id.nav_app,              // nested graph
            R.id.aquariumFragment,
            R.id.devicesFragment,
            R.id.settingsFragment -> true
            else -> false
        }
    }

    private fun setupBottomBar(navController: NavController) {
        // Bottom bar ↔ nav bağla
        binding.bottomNav.setupWithNavController(navController)

        // Destination değişince alt bar görünürlüğünü güncelle
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = isInAppDest(destination.id)
        }
    }
}