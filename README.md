# TuneFy

**Le magasin Android open source, uniquement consacré à Spotify.**

TuneFy est une application Android native qui rassemble l'application Spotify, les
extensions Spicetify, les thèmes, les patches Spicetify et un moteur de patch
intégré dans une boutique unique, inspirée de l'organisation de F-Droid et des
guidelines Material 3 d'Android 16. TuneFy ne dépend d'aucun gestionnaire de
patches externe : l'analyse, l'application, la reconstruction et la signature se
font dans l'application.

> TuneFy n'est affilié ni à Spotify AB, ni à F-Droid, ni à Spicetify. Aucun APK
> Spotify propriétaire n'est hébergé dans ce dépôt. TuneFy ne contourne ni les
> abonnements ni les protections DRM.

---

## Sommaire

- [Aperçu](#aperçu)
- [Fonctionnalités](#fonctionnalités)
- [Captures](#captures)
- [Installation de l'APK](#installation-de-lapk)
- [Compiler depuis les sources](#compiler-depuis-les-sources)
- [Structure du dépôt](#structure-du-dépôt)
- [Architecture](#architecture)
- [Sécurité](#sécurité)
- [Patches Spicetify](#patches-spicetify)
- [Fonctionnalités root](#fonctionnalités-root)
- [Format du dépôt](#format-du-dépôt)
- [Limites connues](#limites-connues)
- [Contribuer](#contribuer)
- [Licence](#licence)

## Aperçu

TuneFy fonctionne comme un vrai magasin d'applications :

- un **accueil** avec Spotify en vedette, les dernières versions, les extensions
  populaires, les thèmes recommandés et les dernières mises à jour ;
- un **catalogue** filtrable (Spotify, extensions, thèmes, patches, outils) avec
  recherche instantanée ;
- de **vraies fiches** : version, architecture, taille, Android minimum,
  compatibilité Spicetify, signatures, historique des versions ;
- un **gestionnaire de téléchargements** avec progression, vitesse, temps
  restant, pause, reprise, annulation et vérification SHA-256 ;
- une **bibliothèque** d'extensions et de thèmes installables/désinstallables ;
- un **APK Patcher** autonome : import APK/APKS/XAPK, analyse de la version,
  de l'ABI et des classes DEX, choix de patches, reconstruction et signature
  TuneFy, vérification SHA-256 ;
- des pages **Mes APK générés**, **Historique des modifications**,
  **Compatibilité Spotify** et **Journaux et diagnostics** ;
- des **outils root optionnels** : détection Magisk/KernelSU/APatch, inspection
  de Spotify, sauvegarde en lecture seule, installation root contrôlée.

L'application est écrite en **Kotlin**, **Jetpack Compose**, **Material 3**, avec
une architecture **MVVM**, des **coroutines** et un dépôt JSON versionné.

## Fonctionnalités

### Boutique

| Fonction | État |
| --- | --- |
| Accueil éditorialisé (Spotify, nouveautés, extensions, thèmes) | ✅ |
| Catalogue Spotify uniquement, 5 catégories | ✅ |
| Recherche plein texte + filtres | ✅ |
| Fiches détaillées (version, code, arch, taille, Android min, licence, package) | ✅ |
| Historique des versions | ✅ |
| Compatibilité Spicetify calculée sur la version installée | ✅ |
| Dépôt local embarqué + dépôt distant configurable | ✅ |
| Mise à jour de l'index avec vérification de schéma | ✅ |
| Thème clair/sombre/système + Material You optionnel | ✅ |
| Animations : vagues de chargement, transitions, coche animée, boutons pressés | ✅ |
| Aucune publicité, aucun compte, aucun traqueur | ✅ |

### Téléchargements et installation

| Fonction | État |
| --- | --- |
| Téléchargement HTTP réel avec reprise (Range) | ✅ |
| Vitesse instantanée et temps restant | ✅ |
| Notification de progression (service de premier plan) | ✅ |
| Vérification SHA-256 et suppression des fichiers corrompus | ✅ |
| Installation d'APK via `PackageInstaller` | ✅ |
| Import d'APKM/XAPK/APKS avec sélection des splits pour l'appareil | ✅ |
| Contrôle de cohérence des signatures et du package | ✅ |
| Installation root via `pm install` / `pm install-multiple` (optionnelle) | ✅ |
| Bibliothèque privée pour thèmes, extensions et bundles de patches | ✅ |
| Désinstallation propre des paquets de la bibliothèque | ✅ |
| Journal local des opérations (historique + empreintes) | ✅ |

### APK Patcher intégré

| Fonction | État |
| --- | --- |
| Moteur de patch embarqué (bibliothèque Morphe Patcher 2.3.0, GPL-3.0) | ✅ |
| Import APK, APKS et XAPK avec fusion des splits | ✅ |
| Deux sources : fichier personnel ou APK officiel 9.1.80.2221 téléchargé et vérifié dans TuneFy | ✅ |
| Analyse version, versionCode, ABI, signature et classes DEX | ✅ |
| Profils de compatibilité extensibles (`spotify-profiles.json`) | ✅ |
| Catalogue de 8 patches Spicetify Android 1.2.0 | ✅ |
| Sélection multi-patches avec dépendances et disponibilité par patch | ✅ |
| Application dans TuneFy, sans Morphe Manager | ✅ |
| Reconstruction APK, signature TuneFy persistante, vérification apksig | ✅ |
| Progression en 7 étapes et journal local | ✅ |
| Installation via `PackageInstaller` ou enregistrement de l'APK | ✅ |
| Test sur appareil physique et APK Spotify réel | Non vérifié dans cet environnement |

### Root (optionnel)

| Fonction | État |
| --- | --- |
| Détection Magisk / KernelSU / APatch | ✅ |
| Vérification de l'accès `su` et version du gestionnaire | ✅ |
| Détection Spotify : version, signature, splits, permissions | ✅ |
| Sauvegarde `tar.gz` des données Spotify (lecture seule) | ✅ |
| Export de la sauvegarde vers un emplacement choisi | ✅ |
| Installation d'APK vérifié par le gestionnaire de paquets root | ✅ |
| Restauration des données privées Spotify | ❌ volontairement non implémentée |

## Captures

Les captures seront ajoutées aux releases GitHub. L'interface reprend les
principes de navigation de F-Droid avec l'identité visuelle propre à TuneFy :

- bleu pastel `#B9D7FF`, bleu principal `#78AFFF`, bleu profond `#315B91` ;
- fond clair `#F4F7FC`, fond sombre `#111923` ;
- cartes arrondies, icônes minimalistes, typographie lisible.

## Installation de l'APK

1. Téléchargez `TuneFy-1.0.2-release.apk` depuis la page
   [Releases](https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases).
2. Vérifiez l'empreinte SHA-256 publiée dans les notes de version.
3. Autorisez l'installation depuis cette source si Android le demande.
4. Installez, puis ouvrez TuneFy.

Prérequis : Android 11 (API 30) minimum, ARM64 recommandé, Android 16/17 pris en
charge (target API 36).

> L'APK de release est signé avec une clé de build conservée hors du dépôt.
> Pour recevoir une future mise à jour par-dessus, elle devra être signée avec la
> même clé ; sinon, désinstallez TuneFy avant d'installer une build signée
> différemment (Android refuse les mises à jour entre signatures distinctes).

## Compiler depuis les sources

### Outils requis

- JDK 17 ;
- Android SDK Platform 36 + Build Tools 36.0.0 ;
- Gradle 8.11.1 (fourni par le wrapper).

### Debug

```bash
git clone https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android.git
cd Tunefy-Spicetify-for-rooted-android
./gradlew :app:assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

### Release signée

Créez une clé (une seule fois) et un fichier `keystore.properties` à la racine,
ignoré par Git :

```bash
keytool -genkeypair -v -keystore local-signing/tunefy-release.jks \
  -alias tunefy -keyalg RSA -keysize 4096 -validity 10000
```

```properties
# keystore.properties
storeFile=local-signing/tunefy-release.jks
storePassword=********
keyAlias=tunefy
keyPassword=********
```

```bash
./gradlew :app:assembleRelease
# APK : app/build/outputs/apk/release/app-release.apk
```

Les mêmes valeurs peuvent être fournies par variables d'environnement
`TUNEFY_KEYSTORE_FILE`, `TUNEFY_KEYSTORE_PASSWORD`, `TUNEFY_KEY_ALIAS`,
`TUNEFY_KEY_PASSWORD` (utile en CI).

### Mettre à jour l'index et les empreintes

```bash
python3 scripts/update-repository.py
```

Le script recalcule les SHA-256 des paquets, télécharge l'empreinte du bundle
`.mpp`, rafraîchit le snapshot des patches et copie l'index dans
`app/src/main/assets/repository/index.json` (copie utilisée hors ligne par
l'application). Utilisez `--skip-remote` pour travailler hors réseau.

## Structure du dépôt

```
app/                       Application Android (Kotlin, Compose)
  src/main/java/app/tunefy/android/
    data/                  Modèles, dépôts, DataStore, HTTP
    download/              DownloadManager + service de premier plan
    install/               Inspection APK, PackageInstaller, coordination
    patch/                 Moteur intégré : analyse, profils, DEX, bundle,
                           signature TuneFy, stockage, fusion des splits
    root/                  Détection root, gestion Spotify
    ui/                    Thème, composants, écrans, ViewModel
  src/main/assets/
    repository/index.json  Index embarqué
    patches/               Bundle 1.2.0 corrigé + profils de compatibilité
  src/test/                Tests JVM du moteur, des profils, du bundle et
                           de la signature
repository/                Dépôt TuneFy public (index + paquets JSON)
scripts/                   Outils de maintenance
docs/                      Documentation technique
third_party/               Diff source de la correction Spicetify settings
```

## Architecture

- **UI** : Jetpack Compose, Material 3, navigation par `NavHost`, tiroir de
  navigation et barre inférieure, transitions légères.
- **MVVM** : un `AppViewModel` expose des `StateFlow` issus des dépôts ;
  les écrans collectent avec `collectAsStateWithLifecycle`.
- **Données** : `CatalogRepository` (index local + distant),
  `PatchRepository` (catalogue + compatibilité d'affichage), `LibraryRepository`
  (installation, historique, sauvegardes), `SettingsRepository` (DataStore).
- **Moteur de patch** : `PatchEngine` (orchestration en 7 étapes),
  `SpotifyCompatibility` (profils et contrôles ABI/DEX), `DexIndexer`,
  `PatchBundleStore` (bundle vérifié par SHA-256), `SplitMerger` (ARSCLib),
  `TuneFySigningKey` (clé BKS privée réutilisée).
- **Téléchargements** : `DownloadManager` (OkHttp, Range, reprise, vitesse, ETA,
  SHA-256) + `DownloadService` pour la notification persistante.
- **Installation** : `ApkInspector` (métadonnées, signatures, splits,
  sélection ABI/densité/langue), `ApkInstaller` (sessions Android),
  `InstallCoordinator` (APK vs paquets de bibliothèque).
- **Root** : `RootManager` (binaire `su`, `su -c id`, `su -v`, paquets
  gestionnaires) et `SpotifyManager` (version, signature, sauvegarde).

Voir [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) pour le détail des flux.

## Sécurité

- Tout fichier téléchargé depuis le dépôt TuneFy est vérifié par **SHA-256**
  avant d'être installé ; un fichier non conforme est supprimé.
- Les APK passent par `PackageInstaller` (ou `pm install` en mode root
  explicite), jamais par une copie dans les données d'une autre application.
- Les signatures des splits sont comparées entre elles, au package déclaré et à
  la version déjà installée.
- Les permissions sont minimales : réseau, notifications, service de premier
  plan, installation de paquets. Pas de `QUERY_ALL_PACKAGES`, pas de stockage
  externe global, pas d'accessibilité.
- Aucune télémétrie. Les sauvegardes restent dans le stockage privé de TuneFy.
- Signalement : voir [SECURITY.md](SECURITY.md).

## APK Patcher et patches Spicetify

Source des patches : <https://github.com/spicetify/morphe-patches> (GPL-3.0).
TuneFy embarque le bundle 1.2.0 corrigé et le charge directement dans son propre
processus Android via `loadPatchesFromDex`. Aucun Morphe Manager, aucune
installation externe et aucune ouverture d'application tierce ne sont requis.

### Deux sources dans APK Patcher

1. **Choisir mon propre APK** : sélecteur Android, pour un APK, APKS ou XAPK
   téléchargé ou fourni par vous.
2. **Utiliser l'APK officiel 9.1.80.2221** : TuneFy télécharge l'APK publié dans
   la release [`Spotify-Apk`](https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/tag/Spotify-Apk),
   affiche la progression, vérifie son SHA-256
   (`f4826f9a9148b15d4a7adeb10a01603a6eca2ff29d16c9d59a320439b4ff17da`) puis
   l'analyse. Le fichier est conservé dans le stockage privé et réutilisé lors
   des ouvertures suivantes.

L'APK officiel pèse 150 486 785 octets (environ 144 Mo). Il n'est pas embarqué
 dans TuneFy : le téléchargement se fait depuis GitHub, à votre initiative.
Détails de l'inspection et de la reconstruction : [docs/OFFICIAL_APK.md](docs/OFFICIAL_APK.md).

### Versions prises en charge

- **Spotify 9.1.80.2221**, versionCode `145767611`, **arm64-v8a** uniquement.
- Android 16 / API 36 est la cible de test déclarée.
- Toute autre version est refusée avant le moindre patch. En particulier,
  **9.2.0.26 est explicitement non supportée** : le patch « Hide Home and Browse
  ads » y échoue avec `Spotify advertising ABI changed: Lp/jb20;`. Le message
  exact est affiché, sans prétendre que le patch a réussi.

### Correction de l'import « APK corrompu »

Un APK Spotify pourtant valide pouvait être refusé avec « L'APK est corrompu ».
La cause était dans `DexIndexer` : le flux d'une entrée `ZipFile` ne supporte pas
`mark()`, exigé par dexlib2. Chaque DEX est désormais lu dans un
`ByteArrayInputStream`. L'APK officiel 9.1.80.2221 (17 DEX, 97 135 classes) est
analysé et déclaré compatible ; une reconstruction ARSCLib conserve ses 17 DEX,
ses 48 bibliothèques natives et son manifeste.

### Correction `SettingsPatch.settingsPatch`

Les erreurs `Spotify settings ABI changed: Lcom/spotify/music/SpotifyApplication;`
et `missing Lp/rih1;` venaient d'un digest de classes obsolète dans le bundle
1.1.1 et d'une résolution directe de `onCreate`. Le bundle TuneFy 1.2.0 :

1. ne bloque plus sur le digest obsolète ;
2. résout `SpotifyApplication.onCreate` via la chaîne de superclasses ;
3. conserve les vérifications ABI/DEX réelles et échoue explicitement si une
   classe ou méthode manque.

Le diff source est conservé dans
[`third_party/spicetify-settings-abi.patch`](third_party/spicetify-settings-abi.patch),
conformément à la GPL-3.0. Les empreintes du bundle sont vérifiées avant chaque
utilisation (`bdc38eadd482228c5c520765f7027d2c68d1d903e8d76c4536d225d2b3470977`).

### Pipeline

1. Analyse de l'APK ;
2. vérification de compatibilité (version, versionCode, ABI, classes/méthodes) ;
3. préparation des fichiers et fusion des splits ;
4. application des patches sélectionnés ;
5. reconstruction de l'APK ;
6. signature avec la clé TuneFy ;
7. vérification de la signature et du SHA-256.

TuneFy ne contourne ni les vérifications ABI, ni les fingerprints du patcheur. Un
patch qui échoue est signalé comme un échec et aucun APK n'est enregistré. Aucun
contournement de DRM, d'abonnement ou de publicité audio n'est fourni.

## Fonctionnalités root

Le root est **entièrement optionnel** et désactivable dans les paramètres.
TuneFy demande l'accès `su` uniquement lorsque vous lancez une analyse ou une
sauvegarde. Détails et garanties : [docs/ROOT_FEATURES.md](docs/ROOT_FEATURES.md).

## Format du dépôt

Le dépôt est un simple fichier JSON versionné, auto-hébergeable. Schéma complet
et guide de publication : [docs/REPOSITORY_FORMAT.md](docs/REPOSITORY_FORMAT.md).

## Limites connues

- Aucun APK Spotify n'est distribué : la fiche officielle renvoie vers Google
  Play ou l'import d'un fichier personnel vérifié.
- La restauration des données privées Spotify n'est pas proposée : sauvegarde en
  lecture seule uniquement, pour ne jamais écraser les données de l'application.
- Les thèmes sont des palettes/color.ini prêts à appliquer via le patch
  « Theme colors » ; TuneFy n'injecte pas de CSS dans Spotify.
- Le patchage réel de Spotify n'a pas pu être exécuté dans cet environnement :
  aucun APK Spotify 9.1.80.2221 n'était disponible et aucun appareil/émulateur
  Android n'était attaché. Les tests JVM couvrent les profils, l'ABI, le bundle,
  la reconstruction ARSCLib et la signature apksig, mais pas l'installation sur
  appareil ni les fingerprints ART du patcheur.
- La fusion XAPK/APKS est implémentée avec ARSCLib et compilée ; elle n'a pas été
  validée sur un véritable ensemble de splits Spotify dans cet environnement.
- Toute build TuneFy est signée par la clé locale TuneFy : Android refusera une
  mise à jour directe par-dessus Spotify officiel (signature différente). TuneFy
  ne désinstalle jamais Spotify et ne supprime jamais ses données.
- Certains fabricants restreignent l'installation de splits (APKM) par des
  applications tierces ; le mode root est alors un recours explicite.
- L'application n'a pas été validée sur un appareil physique dans cet
  environnement de build : voir les notes de release pour les vérifications
  réellement effectuées.
- Pas de mise à jour automatique in-app : les releases GitHub sont la source.

## Contribuer

Les contributions sont bienvenues. Lisez [CONTRIBUTING.md](CONTRIBUTING.md) et
le [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Les données du catalogue vivent
dans `repository/` et se corrigent via une pull request.

## Licence

TuneFy est distribué sous **GNU General Public License v3.0** — voir
[LICENSE](LICENSE). Les ressources tierces (patches, thèmes, extensions)
restent sous leurs licences respectives ; voir [NOTICE.md](NOTICE.md).

L'organisation de navigation s'inspire de F-Droid (GPL-3.0) ; aucun logo ni
élément d'identité de marque de F-Droid n'est réutilisé.
