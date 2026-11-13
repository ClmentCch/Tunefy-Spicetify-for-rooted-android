# Notice et attributions

TuneFy est un projet indépendant sous GPL-3.0. Il n'est affilié ni à Spotify AB,
ni à F-Droid, ni à Spicetify, ni à Morphe.

## Inspiration

- **F-Droid** (<https://f-droid.org>, code source GPL-3.0) : TuneFy reprend des
  principes d'organisation (accueil éditorialisé, catalogue par catégories,
  fiches détaillées, gestionnaire de téléchargements, dépôt versionné) sans
  copier de code source, de logo ou d'identité de marque.
- **Material Design 3 / Jetpack Compose** : composants et guidelines Android.

## Composants intégrés et code source correspondant

TuneFy intègre le moteur de patch **Morphe Patcher 2.3.0** sous GPL-3.0,
chargé comme bibliothèque Android/ART. Le binaire non modifié est vendored dans
`libs/m2/app/morphe/morphe-patcher/2.3.0/` ; son code source correspondant est
publié par le projet Morphe (GPL-3.0). TuneFy conserve les mentions de copyright
et ne revendique aucune marque Morphe.

- **Morphe** (<https://morphe.software>, <https://github.com/MorpheApp>) —
  patcheur Android, Morphe Patcher 2.3.0, GPL-3.0.
- **Spicetify Android patches** (<https://github.com/spicetify/morphe-patches>) —
  patches et bundle 1.2.0, GPL-3.0. TuneFy redistribue une version modifiée du
  bundle (`app/src/main/assets/patches/patches-1.2.0-tunefy.mpp`) pour supprimer
  un digest obsolète et résoudre `SpotifyApplication.onCreate` via les
  superclasses. Le diff source correspondant est conservé dans
  `third_party/spicetify-settings-abi.patch`.
- **Spicetify** (<https://github.com/spicetify>) — patches, thèmes et extensions
  sous GPL-3.0 / LGPL-2.1 selon les dépôts.
- **Spicetify Themes** (<https://github.com/spicetify/spicetify-themes>) —
  palettes et color.ini sous GPL-3.0. Les paquets TuneFy contiennent des palettes
  dérivées, avec mention de la source.
- **ARSCLib** (<https://github.com/REAndroid/ARSCLib>) — Apache 2.0, fusion des
  splits et reconstruction APK.
- **smali/dexlib2** (<https://github.com/google/smali>) — BSD 3-Clause, lecture
  des classes DEX.
- **Bouncy Castle** — licence Bouncy Castle, génération de la clé BKS locale.
- **Spotify** est une marque de Spotify AB. Le nom est utilisé à titre
  descriptif pour désigner les ressources compatibles ; TuneFy n'est pas
  approuvé par Spotify AB.

## Polices et icônes

- Icônes : **Material Icons** (Apache 2.0), via `androidx.compose.material`.
- Typographie : police système Android.
- Marque TuneFy : dessin vectoriel original, aucune reprise du robot F-Droid.

## Licences des dépendances principales

| Dépendance | Licence |
| --- | --- |
| Kotlin / kotlinx.coroutines | Apache 2.0 |
| Jetpack Compose / AndroidX | Apache 2.0 |
| OkHttp | Apache 2.0 |
| kotlinx.serialization | Apache 2.0 |
| Morphe Patcher | GPL-3.0 |
| ARSCLib | Apache 2.0 |
| smali / dexlib2 | BSD 3-Clause |
| Bouncy Castle | Bouncy Castle License |

Les textes complets sont disponibles aux adresses officielles de chaque projet.
