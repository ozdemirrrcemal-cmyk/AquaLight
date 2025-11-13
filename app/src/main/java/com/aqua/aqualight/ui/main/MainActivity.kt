package com.aqua.aqualight.ui.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
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

        // İstersen kalsın, istersen kaldır; crash ile alakası yok
        binding.navHost.visibility = View.INVISIBLE

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        // 🧩 Alt menüyü navController'a bağla (her durumda)
        binding.bottomNav.setupWithNavController(navController)

        if (savedInstanceState == null) {
            // ⬅ Uygulama ilk kez açılıyor → login durumuna göre startDest seç
            lifecycleScope.launch {
                val prefs = userPrefs.userPrefsFlow.first()
                val loggedIn = prefs.isLoggedIn && prefs.idToken.isNotEmpty()

                val graph = navController.navInflater.inflate(R.navigation.nav_root).apply {
                    setStartDestination(
                        if (loggedIn) R.id.nav_app
                        else R.id.authContainerFragment
                    )
                }
                navController.graph = graph

                binding.navHost.visibility = View.VISIBLE
            }
        } else {
            // 🔁 Tema değişimi / rotate vb → Navigation kendi backstack'ini restore ediyor
            binding.navHost.visibility = View.VISIBLE
        }
    }
}