# Changelog

Toutes les évolutions notables de TuneFy sont documentées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
versionnage sémantique.

## [2.0.0] - 2026-11-13

### Corrigé

- **Crash « Application des patches » avec les patches Spicetify** : les
  empreintes ABI du bundle 1.2.0 hashaient les octets DEX d'anciennes classes.
  Le dexlib2 récent de Morphe Patcher 2.x ne retrouvait plus les pins Settings
  et Extensions, et `playerBridgePatch` levait une exception avant même de
  patcher. Le bundle est reconstruit depuis le correctif upstream
  `spicetify/morphe-patches@6741682`, qui décrit la forme sémantique des
  classes. Les fingerprints réels restent vérifiés.
- Le bundle est exécuté sur l'APK officiel 9.1.80.2221 dans un harnais JVM :
  **8/8 patches appliqués**, APK reconstruit (154 529 679 octets). Aucun
  appareil Android n'était connecté pour tester l'installation finale.

### Modifié

- **Écran APK Patcher** : barre d'action fixe avec le nombre de patches et le
  bouton de génération toujours accessible, sélection rapide « Tout » /
  « Défaut », compteur de patches compatibles et carte d'erreur complète avec
  accès direct aux journaux. Les échecs de patch ne sont plus masqués par un
  toast tronqué.
- Version `2.0.0` (versionCode 5).

### Limite

- Installation et lancement de l'APK patché sur un Spotify officiel non testés
  sans appareil Android dans cet environnement.

[2.0.0]: https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/tag/v2.0.0

## [1.3.0] - 2026-11-13

### Corrigé

- **Chargement du bundle de patches sous Android 14+** : ART refusait le DEX
  `patches-1.2.0-tunefy.mpp` avec « Writable dex file ... is not allowed ».
  `PatchBundleStore` vérifie désormais le SHA-256 puis force le bundle en
  lecture seule (`0444`) avant de le confier à dexlib2 et `DexClassLoader`.
  Le fichier n'est jamais modifié après vérification.
- L'écran À propos et les Paramètres affichent la version issue de
  `BuildConfig`, au lieu d'une valeur figée.

### Ajouté

- Section **Paramètres → Accès aux fichiers** : état de l'accès global,
  ouverture du réglage Android « tous les fichiers » et accès direct aux
  réglages de l'application. Cet accès reste optionnel : le sélecteur Android
  suffit pour choisir un APK.
- Version `1.3.0` (versionCode 4).

### Limite

- Le correctif supprime le blocage ART au chargement du bundle. L'exécution
  complète des patches sur un Spotify 9.1.80.2221 installé n'a pas pu être
  testée sans appareil Android dans cet environnement.

[1.3.0]: https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/tag/v1.3.0

## [1.0.2] - 2026-11-13

### Corrigé

- **Import d'un APK Spotify valide signalé comme corrompu** : `DexIndexer`
  passait à dexlib2 le flux d'une entrée `ZipFile`, qui ne supporte pas `mark`.
  Chaque DEX est maintenant lu dans un `ByteArrayInputStream`. L'APK officiel
  9.1.80.2221 (150 486 785 octets, 17 DEX, 97 135 classes) est analysé et
  déclaré compatible.
- Reconstruction ARSCLib vérifiée sur cet APK réel : 17 DEX et 48 bibliothèques
  natives conservés, manifeste `com.spotify.music` / versionCode `145767611`
  préservé.

### Ajouté

- **Deux boutons dans APK Patcher** : « Choisir mon propre APK » et « Utiliser
  l'APK officiel 9.1.80.2221 ».
- Téléchargement intégré de l'APK officiel depuis la release GitHub
  `Spotify-Apk`, avec progression, vérification SHA-256
  (`f4826f9a9148b15d4a7adeb10a01603a6eca2ff29d16c9d59a320439b4ff17da`) et
  réutilisation du fichier déjà téléchargé.
- Version `1.0.2` (versionCode 3) pour remplacer la build 1.1.0 (versionCode 2).

[1.0.2]: https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/tag/v1.0.2

## [1.1.0] - 2026-11-13

### Ajouté

- **Moteur de patch intégré** : TuneFy charge le bundle Spicetify dans son propre
  processus Android avec `loadPatchesFromDex` et n'ouvre plus Morphe Manager.
- **APK Patcher** : import APK/APKS/XAPK, analyse version/versionCode/ABI/DEX,
  sélection multi-patches, progression en 7 étapes, reconstruction, signature
  TuneFy et vérification apksig/SHA-256.
- Pages **Mes APK générés**, **Historique des modifications**, **Compatibilité
  Spotify** et **Journaux et diagnostics**.
- Profils de compatibilité extensibles dans `spotify-profiles.json`.
- Bundle Spicetify Android 1.2.0 corrigé pour TuneFy, avec suppression du digest
  obsolète et résolution de `SpotifyApplication.onCreate` via les superclasses.
- Clé de signature BKS locale, persistante et réutilisée entre les builds TuneFy.
- Fusion des splits APKS/XAPK via ARSCLib, sans Morphe Manager.
- Tests JVM : profils, erreurs ABI, méthodes DEX héritées, détection ABI, bundle,
  reconstruction ARSCLib et signature/vérification apksig.

### Modifié

- Kotlin 2.4.21, patcher Morphe 2.3.0 vendored dans `libs/m2`, JitPack et dépôt
  local pour les dépendances smali/ARSCLib.
- L'ancien écran de patches Morphe est remplacé par l'APK Patcher autonome.
- La fiche application ouvre APK Patcher au lieu de Morphe.

### Sécurité

- Bundle vérifié par SHA-256 avant chargement ; APK final vérifié après signature.
- Aucune désinstallation automatique de Spotify, aucune suppression de données.
- Les erreurs de fingerprint ou de classe manquante restent des échecs explicites.

### Limites vérifiées

- Compatibilité testée structurellement pour Spotify 9.1.80.2221 (arm64-v8a) ;
  aucune exécution sur appareil ni sur APK Spotify réel dans cet environnement.
- Spotify 9.2.0.26 est refusé avec le message exact `Lp/jb20;`.

[1.1.0]: https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/tag/v1.1.0

## [1.0.0] - 2026-11-13

### Ajouté

- Boutique Spotify complète : accueil, catalogue à 5 catégories, recherche,
  fiches détaillées, historique des versions.
- Dépôt TuneFy JSON embarqué + mise à jour distante configurable, 15 entrées
  (Spotify, 3 extensions, 6 thèmes, 1 bundle de patches, 3 outils).
- Gestionnaire de téléchargements réel : reprise Range, pause, vitesse, ETA,
  notification de premier plan, vérification SHA-256.
- Installation d'APK et de bundles APKM/XAPK/APKS via `PackageInstaller`, avec
  sélection des splits compatibles (ABI, densité, langue).
- Contrôle de signature entre splits, package attendu et version installée.
- Bibliothèque d'extensions et de thèmes : installation, désinstallation,
  aperçu visuel, activation documentée.
- Écran Patches alimenté par `spicetify/morphe-patches` : compatibilité calculée,
  blocage des patches incompatibles, sélection, export JSON, ouverture de Morphe.
- Détection root Magisk / KernelSU / APatch, inspection de Spotify, sauvegarde
  en lecture seule et export, installation root vérifiée.
- Historique local des installations et empreintes.
- Thèmes clair/sombre/système, Material You optionnel, animations (vagues,
  transitions, coche, pression), notifications.
- Documentation : README, ARCHITECTURE, BUILDING, REPOSITORY_FORMAT,
  ROOT_FEATURES, SECURITY, CONTRIBUTING, CODE_OF_CONDUCT, NOTICE.

### Notes

- Aucun APK Spotify n'est distribué.
- La restauration des données privées Spotify n'est pas implémentée, par choix
  de sécurité.
- L'APK de release est signé avec une clé de build conservée hors du dépôt.

[1.0.0]: https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/tag/v1.0.0
