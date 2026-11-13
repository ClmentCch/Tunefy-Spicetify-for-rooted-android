# Architecture technique

## Vue d'ensemble

```
┌──────────────────────────── UI (Jetpack Compose) ───────────────────────────┐
│ Home · Catalog · Detail · Downloads · Patches · Root · Settings · History  │
│                              AppViewModel                                   │
└───────────────▲───────────────────────────────▲────────────────────────────┘
                │ StateFlow                     │ actions
┌───────────────┴───────────────┐  ┌────────────┴───────────────┐
│ CatalogRepository             │  │ DownloadManager            │
│ PatchRepository               │  │ DownloadService (FGS)      │
│ LibraryRepository (DataStore) │  │ InstallCoordinator         │
│ SettingsRepository (DataStore)│  │ RootManager / SpotifyManager│
└───────────────▲───────────────┘  └────────────▲───────────────┘
                │                               │
        assets/index.json                 PackageInstaller
        HTTPS index + packages            OkHttp + SHA-256
```

Le conteneur `AppContainer` (dans `TunefyApplication`) instancie une seule fois
les dépôts et services. Aucune annotation de DI, aucun singleton caché : le
câblage est lisible dans un fichier.

## Modules et responsabilités

| Fichier | Rôle |
| --- | --- |
| `data/model/Models.kt` | Modèles sérialisables du dépôt, téléchargements, patches, réglages |
| `data/repo/CatalogRepository.kt` | Index embarqué, cache, rafraîchissement HTTPS, recherche |
| `data/repo/PatchRepository.kt` | Liste morphe-patches, cache, sélection, compatibilité |
| `data/repo/LibraryRepository.kt` | Installations, historique, sauvegardes (DataStore) |
| `data/settings/SettingsRepository.kt` | Préférences utilisateur (DataStore) |
| `download/DownloadManager.kt` | Transferts, reprise, vitesse, SHA-256, reprise après pause |
| `download/DownloadService.kt` | Service de premier plan + notification de progression |
| `install/ApkInspector.kt` | Métadonnées APK, signatures, extraction et choix des splits |
| `install/ApkInstaller.kt` | Sessions `PackageInstaller`, suivi des résultats |
| `install/InstallCoordinator.kt` | APK vs paquet de bibliothèque, contrôles de sécurité |
| `root/RootManager.kt` | Détection root, exécution `su` bornée dans le temps |
| `root/SpotifyManager.kt` | État Spotify, sauvegarde en lecture seule |
| `ui/AppViewModel.kt` | Orchestration, états exposés, messages, actions |
| `ui/TuneFyApp.kt` | Tiroir, barre inférieure, NavHost, transitions |
| `ui/components/*` | Marque, loaders animés, cartes, badges, méta-données |
| `ui/screens/*` | Un écran par flux, sans logique réseau directe |

## Flux de démarrage

1. `MainActivity` installe le splash Android puis active l'edge-to-edge.
2. `AppViewModel` collecte les réglages (thème appliqué immédiatement).
3. `CatalogRepository.bootstrap()` lit le cache local, sinon l'index embarqué.
4. Un rafraîchissement HTTPS de l'index est lancé en arrière-plan ; en cas
   d'échec, l'index local reste affiché et un bandeau explique pourquoi.
5. `PatchRepository.load()` lit le cache/snapshot puis rafraîchit
   `patches-list.json`.
6. La présence de Spotify est vérifiée via `PackageManager`.

## Flux de téléchargement / installation

```
Install (UI)
  └─ DownloadManager.enqueue(item)
       ├─ DownloadService.ensureRunning()
       ├─ GET HTTPS (Range si .part existant)
       ├─ updates StateFlow (bytes, total, vitesse, ETA) toutes ~300 ms
       ├─ vérification SHA-256
       └─ installAfterDownload ? InstallCoordinator.install()
            ├─ APK / archive → ApkInspector.expand()
            │     ├─ signatures cohérentes ? package attendu ?
            │     ├─ version installée de même signature ?
            │     └─ PackageInstaller session + PendingIntent
            │           └─ InstallResultReceiver → historique + notification
            └─ JSON / .mpp → validation → filesDir/library/<id>
```

La sélection des splits d'un APKM retient la base, l'ABI compatible, la densité
la plus proche et la langue courante, plus les modules fonctionnels sans
spécificité d'appareil. Si aucun split ne correspond, tout est installé plutôt
que de risquer une base incomplète.

## Compatibilité des patches

```kotlin
supported = patch.spotifyPackage?.targets?.map { it.version }
installed = PackageInfo("com.spotify.music").versionName

when {
  spotify absent          -> UNKNOWN
  installed in supported  -> EXPERIMENTAL si target.isExperimental sinon COMPATIBLE
  sinon                   -> INCOMPATIBLE
}
```

L'UI désactive le commutateur et affiche la raison. L'export JSON est purement
descriptif : Morphe reste seul applicateur.

## Root

- Détection : présence de binaires `su`, `su -c id` (timeout 8 s), `su -v`,
  paquets gestionnaires et répertoires `/data/adb/*`.
- Sauvegarde : `tar -czf - -C /data/data com.spotify.music` streamé vers un
  fichier privé TuneFy. Aucune écriture inverse.
- Installation root : vérification locales des signatures puis
  `pm install -r -d` ou `pm install-multiple`, uniquement sur action explicite.

## Persistance

| Donnée | Emplacement | Format |
| --- | --- | --- |
| Réglages | DataStore `tunefy_settings` | préférences typées |
| Bibliothèque / historique / sauvegardes | DataStore `tunefy_library` | JSON sérialisé |
| Sélection de patches | DataStore `tunefy_patches` | `Set<String>` |
| Index distant en cache | `filesDir/repository/index.json` | JSON |
| Patches en cache | `filesDir/morphe/patches-list.json` | JSON |
| Téléchargements | `filesDir/downloads/` | fichier + `.part` + `tasks.json` |
| Paquets installés | `filesDir/library/<itemId>/` | fichier d'origine |
| Sauvegardes | `filesDir/backups/` | `.tar.gz` |

Rien n'est écrit dans le stockage partagé sans action de l'utilisateur (export
via le sélecteur de documents Android).

## Threading

- Compose sur le thread principal, états exposés en `StateFlow`.
- Réseau, fichiers, hash et root sur `Dispatchers.IO`.
- Le `DownloadManager` possède son propre `CoroutineScope` (SupervisorJob) : une
  tâche en échec n'annule pas les autres.
- `RootManager.exec` borne chaque commande et draine stdout/stderr dans des
  threads dédiés pour éviter les blocages.

## Animations

- `WaveLoadingIndicator` : trois anneaux sinusoïdaux dessinés au Canvas, rotation
  et pulsation en `rememberInfiniteTransition`.
- `AnimatedCheck` : chemin tracé via `PathMeasure` après installation.
- Transitions de navigation : fondu + glissement court, 140–280 ms.
- Pression des cartes : `animateFloatAsState` avec ressort.
- Chargements et skeletons : alpha animé, aucun bitmap.

Ces animations restent sur le thread UI et ne font aucune allocation lourde par
frame.

## Choix assumés

- Pas de base de données : le volume (catalogue, historique) tient dans DataStore
  et des fichiers JSON, ce qui simplifie la sauvegarde et l'audit.
- Pas de DI framework : le conteneur manuel reste lisible pour un projet de cette
  taille.
- Pas d'updater intégré : les releases GitHub sont la source de vérité.
