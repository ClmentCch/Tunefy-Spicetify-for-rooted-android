# Format du dépôt TuneFy

Le dépôt TuneFy est un fichier JSON statique, servi en HTTPS, sans API ni
serveur obligatoire. L'application embarque le même schéma dans
`app/src/main/assets/repository/index.json`.

## Emplacement

- Dépôt par défaut :
  `https://raw.githubusercontent.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/main/repository/index.json`
- Modifiable dans **Paramètres → Dépôt TuneFy**.
- Cache : `filesDir/repository/index.json`.

## Racine

```json
{
  "schema": 1,
  "name": "TuneFy Repository",
  "version": 1,
  "updated": "2026-11-13",
  "apps": []
}
```

| Champ | Type | Rôle |
| --- | --- | --- |
| `schema` | int | Version du schéma (1) |
| `name` | string | Nom affiché |
| `version` | int | Version de l'index |
| `updated` | string | Date ISO de mise à jour |
| `apps` | array | Éléments du catalogue |

## Élément de catalogue

```json
{
  "id": "theme-blossom",
  "name": "Blossom",
  "packageId": "",
  "category": "themes",
  "developer": "Spicetify community",
  "summary": "Rose poudré et violet profond.",
  "description": "Texte long…",
  "version": "1.0.0",
  "versionCode": 0,
  "architecture": "universal",
  "sizeBytes": 899,
  "minSdk": 30,
  "publishedAt": "2026-11-10",
  "license": "GPL-3.0",
  "sourceUrl": "https://github.com/spicetify/spicetify-themes",
  "downloadUrl": "https://raw.githubusercontent.com/.../theme-blossom.json",
  "downloadType": "direct",
  "fileName": "theme-blossom.json",
  "sha256": "…64 caractères hex…",
  "signatureSha256": null,
  "spicetifyCompatible": true,
  "morpheCompatible": true,
  "supportedSpotifyVersions": ["9.1.80.2221"],
  "tags": ["thème", "rose"],
  "accentColor": "#E5A9C4",
  "previewColors": ["#2A1838", "#7B4B8A", "#E5A9C4"],
  "permissions": [],
  "versions": [],
  "featured": true,
  "downloadCount": 9400,
  "warnings": [],
  "notes": ""
}
```

### Champs essentiels

| Champ | Contrainte |
| --- | --- |
| `id` | unique, stable, `[a-z0-9-]` |
| `category` | `spotify`, `extensions`, `themes`, `patches`, `tools` |
| `version` | version affichée et comparée |
| `downloadType` | `direct`, `external` ou `recipe` |
| `sha256` | **obligatoire** pour `direct`, 64 hex minuscules |
| `sizeBytes` | taille exacte pour `direct`, 0 sinon |
| `supportedSpotifyVersions` | versions Spotify réellement prises en charge |
| `previewColors` | 2 à 5 couleurs hex pour l'aperçu des thèmes |

### Types de téléchargement

- `direct` : fichier HTTPS téléchargeable, vérifié par `sha256`, installé par
  TuneFy (APK ou paquet de bibliothèque).
- `external` : la fiche renvoie vers une source officielle (Play Store, site
  d'un outil).
- `recipe` : recette de patchage appliquée par Morphe, aucun fichier distribué.

### Catégories

| Clé | Contenu |
| --- | --- |
| `spotify` | Application officielle, recettes de patch |
| `extensions` | Extensions Spicetify Android |
| `themes` | Palettes et paquets de thème |
| `patches` | Bundles de patches Morphe |
| `tools` | Outils de personnalisation et patcheurs |

## Sécurité et vérification

- L'application exige HTTPS (le trafic en clair est refusé par Android).
- Un fichier `direct` sans `sha256` valide est refusé à la vérification.
- Un fichier dont l'empreinte diffère est supprimé et l'installation annulée.
- Les APK déclarent `packageId` ; un fichier contenant un autre package est
  refusé.
- `signatureSha256` est optionnel : s'il est présent, la signature du fichier
  doit correspondre exactement.

## Ajouter une entrée

1. Ajoutez le paquet dans `repository/packages/` si nécessaire.
2. Ajoutez l'objet dans `repository/index.json` (`"sha256": "AUTO"`).
3. Lancez `python3 scripts/update-repository.py`.
4. Testez l'installation dans l'application.
5. Ouvrez une pull request avec la source upstream et la licence.

## Auto-hébergement

N'importe qui peut forker `repository/`, servir `index.json` sur un hébergement
statique HTTPS et modifier l'URL dans les paramètres. Aucune signature de dépôt
ni clé propriétaire n'est requise. Un dépôt alternatif est donc de confiance
équivalente à son hébergeur : vérifiez les empreintes et les sources.
