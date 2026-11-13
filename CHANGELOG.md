# Changelog

Toutes les évolutions notables de TuneFy sont documentées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
versionnage sémantique.

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
