package ps.palpay.tracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ps.palpay.tracker.service.TrackingForegroundService
import ps.palpay.tracker.ui.screens.MainScreen
import ps.palpay.tracker.ui.screens.SettingsScreen
import ps.palpay.tracker.ui.theme.PalPayTrackerAppTheme
import ps.palpay.tracker.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val repository = (application as PalPayApplication).repository
        
        setContent {
            PalPayTrackerAppTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(application, repository)
                )
                mainViewModel = viewModel
                
                val uiState by viewModel.uiState.collectAsState()
                val navController = rememberNavController()

                // Manage Foreground Service
                LaunchedEffect(uiState.trackingEnabled) {
                    val serviceIntent = Intent(this@MainActivity, TrackingForegroundService::class.java)
                    if (uiState.trackingEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    } else {
                        stopService(serviceIntent)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController, 
                        startDestination = "main",
                        enterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400)) { it } },
                        exitTransition = { fadeOut(animationSpec = tween(400)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(400)) },
                        popExitTransition = { slideOutHorizontally(animationSpec = tween(400)) { it } }
                    ) {
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // تحديث حالة البطارية فوراً عند العودة للتطبيق من الإعدادات
        mainViewModel?.refreshBatteryStatus()
    }
}
