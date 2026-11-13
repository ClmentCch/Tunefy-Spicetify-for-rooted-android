package app.tunefy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.TuneFyApp
import app.tunefy.android.ui.theme.TuneFyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as TunefyApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openDownloads = intent?.getBooleanExtra("open_downloads", false) == true

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            TuneFyTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                TuneFyApp(viewModel = viewModel, startOnDownloads = openDownloads)
            }
        }
    }
}
