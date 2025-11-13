package com.aqua.aqualight.ui.main  
  
import android.os.Bundle  
import android.view.View  
import androidx.core.view.isVisible  
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
  
        // İçerik hazır olana kadar NavHost'u gizle (ufak flicker'ı azaltır)  
        binding.navHost.visibility = View.INVISIBLE  
  
        val navHost =  
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment  
        val navController = navHost.navController  
  
        if (savedInstanceState == null) {  
            // Grafiği ilk açılışta oturum durumuna göre belirle  
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
  
                // (İsteğe bağlı) Deep link varsa işle  
                navController.handleDeepLink(intent)  
  
                // Bottom bar ↔ nav setup  
                binding.bottomNav.setupWithNavController(navController)  
                hookBottomBarVisibility(navController)  
  
                // Artık graph hazır, içeriği göster  
                binding.navHost.visibility = View.VISIBLE  
            }  
        } else {  
            // Navigation kendi state'ini restore etti; sadece bottom bar'ı bağla  
            binding.bottomNav.setupWithNavController(navController)  
            hookBottomBarVisibility(navController)  
  
            // Zaten restore edilmiş, direkt gösterilebilir  
            binding.navHost.visibility = View.VISIBLE  
        }  
    }  
  
    private fun hookBottomBarVisibility(navController: androidx.navigation.NavController) {  
        // Sadece nav_app hiyerarşisindeyken göster  
        navController.addOnDestinationChangedListener { _, destination, _ ->  
            val inApp = generateSequence(destination) { it.parent }  
                .any { it.id == R.id.nav_app }  
            binding.bottomNav.isVisible = inApp  
        }  
    }  
}  