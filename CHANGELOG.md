# Changelog

Toutes les évolutions notables de TuneFy sont documentées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
versionnage sémantique.

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
