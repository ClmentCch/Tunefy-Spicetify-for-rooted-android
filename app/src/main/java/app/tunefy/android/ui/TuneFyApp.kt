package app.tunefy.android.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.tunefy.android.BuildConfig
import app.tunefy.android.core.Constants
import app.tunefy.android.data.model.Category
import app.tunefy.android.ui.components.TuneFyWordmark
import app.tunefy.android.ui.nav.Routes
import app.tunefy.android.ui.screens.AboutScreen
import app.tunefy.android.ui.screens.ApkPatcherScreen
import app.tunefy.android.ui.screens.CatalogScreen
import app.tunefy.android.ui.screens.CompatibilityScreen
import app.tunefy.android.ui.screens.DetailScreen
import app.tunefy.android.ui.screens.DiagnosticsScreen
import app.tunefy.android.ui.screens.GeneratedApksScreen
import app.tunefy.android.ui.screens.ModHistoryScreen
import app.tunefy.android.ui.screens.DownloadsScreen
import app.tunefy.android.ui.screens.HistoryScreen
import app.tunefy.android.ui.screens.HomeScreen
import app.tunefy.android.ui.screens.LibraryScreen
import app.tunefy.android.ui.screens.RootScreen
import app.tunefy.android.ui.screens.SearchScreen
import app.tunefy.android.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private data class DrawerEntry(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val drawerEntries = listOf(
    DrawerEntry(Routes.HOME, "Accueil", Icons.Rounded.Home),
    DrawerEntry(Routes.CATALOG, "Catalogue", Icons.Rounded.Category),
    DrawerEntry(Routes.EXTENSIONS, "Extensions", Icons.Rounded.Extension),
    DrawerEntry(Routes.THEMES, "Thèmes", Icons.Rounded.Palette),
    DrawerEntry(Routes.PATCHES, "APK Patcher", Icons.Rounded.Memory),
    DrawerEntry(Routes.GENERATED, "Mes APK générés", Icons.Rounded.Folder),
    DrawerEntry(Routes.MOD_HISTORY, "Historique des modifications", Icons.Rounded.History),
    DrawerEntry(Routes.COMPATIBILITY, "Compatibilité Spotify", Icons.Rounded.Verified),
    DrawerEntry(Routes.DIAGNOSTICS, "Journaux et diagnostics", Icons.Rounded.BugReport),
    DrawerEntry(Routes.DOWNLOADS, "Téléchargements", Icons.Rounded.Download),
    DrawerEntry(Routes.ROOT, "Outils root", Icons.Rounded.Build),
    DrawerEntry(Routes.HISTORY, "Historique", Icons.Rounded.History),
    DrawerEntry(Routes.SETTINGS, "Paramètres", Icons.Rounded.Settings),
    DrawerEntry(Routes.ABOUT, "À propos", Icons.Rounded.Info)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuneFyApp(viewModel: AppViewModel, startOnDownloads: Boolean = false) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: Routes.HOME
    val isDetail = route.startsWith("detail")
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val activeDownloads = downloads.count { it.status.isActive }
    val installPermission by viewModel.installPermissionRequest.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(startOnDownloads) {
        if (startOnDownloads) {
            navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true }
        }
    }

    if (installPermission) {
        AlertDialog(
            onDismissRequest = viewModel::clearInstallPermissionRequest,
            icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
            title = { Text("Autoriser installations") },
            text = {
                Text(
                    "Android exige une autorisation explicite avant d'installer un APK. " +
                        "TuneFy utilise uniquement l'installateur officiel, ne contourne aucune " +
                        "signature et ne touche jamais aux données privées de Spotify."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::openInstallSettings) { Text("Ouvrir les réglages") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearInstallPermissionRequest) { Text("Plus tard") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(310.dp)) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    TuneFyWordmark()
                }
                Text(
                    text = "Un magasin consacré à Spotify : extensions, thèmes, patches et outils.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    drawerEntries.forEach { entry ->
                        NavigationDrawerItem(
                        label = { Text(entry.label) },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        selected = route == entry.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(entry.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    text = "TuneFy ${BuildConfig.VERSION_NAME} · GPL-3.0\n${Constants.PROJECT_URL.removePrefix("https://")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (route == Routes.HOME) TuneFyWordmark(compact = true)
                        else Text(titleFor(route), style = MaterialTheme.typography.titleLarge)
                    },
                    navigationIcon = {
                        if (isDetail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Rounded.Shield, contentDescription = "Menu")
                            }
                        }
                    },
                    actions = {
                        if (route == Routes.HOME || route == Routes.CATALOG) {
                            IconButton(onClick = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Rechercher")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            bottomBar = {
                if (!isDetail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        TuneFyBottomItem("Accueil", Icons.Rounded.Home, route == Routes.HOME) {
                            navController.navigate(Routes.HOME) { launchSingleTop = true }
                        }
                        TuneFyBottomItem("Catalogue", Icons.Rounded.Category, route == Routes.CATALOG) {
                            navController.navigate(Routes.CATALOG) { launchSingleTop = true }
                        }
                        TuneFyBottomItem(
                            "Téléch.",
                            Icons.Rounded.Download,
                            route == Routes.DOWNLOADS,
                            badge = activeDownloads
                        ) {
                            navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true }
                        }
                        TuneFyBottomItem("Patcher", Icons.Rounded.Memory, route == Routes.PATCHES) {
                            navController.navigate(Routes.PATCHES) { launchSingleTop = true }
                        }
                        TuneFyBottomItem("Réglages", Icons.Rounded.Settings, route == Routes.SETTINGS) {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 16 }
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 16 }
                }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenItem = { navController.navigate(Routes.detail(it)) },
                        onOpenCatalog = { category ->
                            if (category == Category.ALL) navController.navigate(Routes.CATALOG)
                            else navController.navigate(Routes.CATALOG)
                        },
                        onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) },
                        onOpenThemes = { navController.navigate(Routes.THEMES) },
                        onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        onOpenPatches = { navController.navigate(Routes.PATCHES) },
                        onOpenCompatibility = { navController.navigate(Routes.COMPATIBILITY) },
                        onOpenSearch = { navController.navigate(Routes.SEARCH) }
                    )
                }
                composable(Routes.CATALOG) {
                    CatalogScreen(
                        viewModel = viewModel,
                        initialCategory = Category.ALL,
                        onOpenItem = { navController.navigate(Routes.detail(it)) }
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        viewModel = viewModel,
                        onOpenItem = { navController.navigate(Routes.detail(it)) }
                    )
                }
                composable(Routes.EXTENSIONS) {
                    LibraryScreen(
                        viewModel = viewModel,
                        category = Category.EXTENSIONS,
                        onOpenItem = { navController.navigate(Routes.detail(it)) }
                    )
                }
                composable(Routes.THEMES) {
                    LibraryScreen(
                        viewModel = viewModel,
                        category = Category.THEMES,
                        onOpenItem = { navController.navigate(Routes.detail(it)) }
                    )
                }
                composable(Routes.DOWNLOADS) {
                    DownloadsScreen(
                        viewModel = viewModel,
                        onOpenItem = { navController.navigate(Routes.detail(it)) }
                    )
                }
                composable(Routes.PATCHES) {
                    ApkPatcherScreen(
                        viewModel = viewModel,
                        onOpenGenerated = { navController.navigate(Routes.GENERATED) },
                        onOpenHistory = { navController.navigate(Routes.MOD_HISTORY) },
                        onOpenCompatibility = { navController.navigate(Routes.COMPATIBILITY) },
                        onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
                    )
                }
                composable(Routes.GENERATED) {
                    GeneratedApksScreen(viewModel = viewModel)
                }
                composable(Routes.MOD_HISTORY) {
                    ModHistoryScreen(viewModel = viewModel)
                }
                composable(Routes.COMPATIBILITY) {
                    CompatibilityScreen(viewModel = viewModel)
                }
                composable(Routes.DIAGNOSTICS) {
                    DiagnosticsScreen(viewModel = viewModel)
                }
                composable(Routes.ROOT) {
                    RootScreen(viewModel = viewModel)
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(viewModel = viewModel)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenRoot = { navController.navigate(Routes.ROOT) }
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen()
                }
                composable(Routes.DETAIL) { entry ->
                    val itemId = entry.arguments?.getString("itemId").orEmpty()
                    DetailScreen(
                        viewModel = viewModel,
                        itemId = itemId,
                        onOpenItem = { navController.navigate(Routes.detail(it)) },
                        onOpenPatcher = { navController.navigate(Routes.PATCHES) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TuneFyBottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int = 0,
    onClick: () -> Unit
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(21.dp))
            if (badge > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = (-3).dp)
                ) { Text(badge.toString(), style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
        )
    }
}

private fun titleFor(route: String): String = when (route) {
    Routes.CATALOG -> "Catalogue"
    Routes.SEARCH -> "Recherche"
    Routes.EXTENSIONS -> "Extensions"
    Routes.THEMES -> "Thèmes"
    Routes.DOWNLOADS -> "Téléchargements"
    Routes.PATCHES -> "APK Patcher"
    Routes.GENERATED -> "Mes APK générés"
    Routes.MOD_HISTORY -> "Historique des modifications"
    Routes.COMPATIBILITY -> "Compatibilité Spotify"
    Routes.DIAGNOSTICS -> "Journaux et diagnostics"
    Routes.ROOT -> "Outils root"
    Routes.HISTORY -> "Historique"
    Routes.SETTINGS -> "Paramètres"
    Routes.ABOUT -> "À propos"
    else -> if (route.startsWith("detail")) "Fiche application" else "TuneFy"
}
