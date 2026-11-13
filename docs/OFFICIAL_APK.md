# APK Spotify officiel — inspection et reconstruction

Fichier publié dans la release TuneFy [`Spotify-Apk`](https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/tag/Spotify-Apk) :

| Propriété | Valeur |
| --- | --- |
| URL | `https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android/releases/download/Spotify-Apk/Spotify.apk` |
| Taille | 150 486 785 octets |
| SHA-256 | `f4826f9a9148b15d4a7adeb10a01603a6eca2ff29d16c9d59a320439b4ff17da` |
| Package | `com.spotify.music` |
| Version | 9.1.80.2221 |
| versionCode | 145767611 |
| Signature | `CN=Spotify, OU=Android, O=Spotify, L=Stockholm, ST=Stockholm, C=SE` |
| Schémas de signature | v2 + v3, SourceStamp Google présent |
| ABI | arm64-v8a, armeabi-v7a, x86, x86_64 |
| DEX | 17 fichiers, 97 135 classes indexées |
| Bibliothèques natives | 48 fichiers `.so` |
| minSdk / targetSdk | 24 / 37 |

## Vérifications effectuées

- `unzip -t` : aucune erreur ZIP.
- `aapt2 dump badging` : manifeste lisible, package/version conformes.
- `apksigner verify` : signature Spotify valide.
- `DexIndexer` : 97 135 classes lues, profil 9.1.80.2221 compatible.
- Les huit patches du bundle 1.2.0 ont leurs classes requises dans ce DEX et
  s'appliquent tous dans le harnais JVM de vérification de la 2.0.0.
- Reconstruction ARSCLib : 17 DEX et 48 bibliothèques conservés, manifeste
  `com.spotify.music` / versionCode `145767611` préservé, 150 486 785 octets.

## Cause du faux « APK corrompu »

`DexIndexer` passait à `DexBackedDexFile.fromInputStream` le flux retourné par
`ZipFile.getInputStream`. Ce flux ne supporte pas `mark()`, alors que dexlib2
l'exige pour vérifier l'en-tête. Le fichier était donc valide, mais l'analyse
échouait avant même de lire les classes. La correction lit chaque entrée DEX
dans un `ByteArrayInputStream` ; un test de régression construit un ZIP avec un
petit DEX et vérifie l'indexation.

## Limite

Cette inspection prouve la validité structurelle de la base et la capacité du
moteur à l'analyser. Elle ne prouve pas l'exécution des patches ART ni
l'installation sur un appareil physique, qui n'étaient pas disponibles dans cet
environnement.
