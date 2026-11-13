package app.tunefy.android

import android.app.Application
import android.content.Context
import app.tunefy.android.core.Notifications
import app.tunefy.android.download.DownloadManager
import app.tunefy.android.data.repo.CatalogRepository
import app.tunefy.android.data.repo.LibraryRepository
import app.tunefy.android.data.repo.PatchRepository
import app.tunefy.android.data.settings.SettingsRepository
import app.tunefy.android.install.ApkInstaller
import app.tunefy.android.install.InstallCoordinator
import app.tunefy.android.root.RootManager
import app.tunefy.android.root.SpotifyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Tiny manual dependency container. No DI framework, no hidden magic:
 * every repository is created once and shared by the ViewModels.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settingsRepository = SettingsRepository(appContext)
    val libraryRepository = LibraryRepository(appContext)
    val catalogRepository = CatalogRepository(appContext, settingsRepository)
    val patchRepository = PatchRepository(appContext)
    val rootManager = RootManager(appContext)
    val spotifyManager = SpotifyManager(appContext, rootManager)
    val apkInstaller = ApkInstaller(appContext, libraryRepository)
    val installCoordinator = InstallCoordinator(appContext, libraryRepository, apkInstaller)
    val downloadManager = DownloadManager(
        appContext,
        catalogRepository,
        settingsRepository,
        libraryRepository,
        installCoordinator
    )
}

class TunefyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
