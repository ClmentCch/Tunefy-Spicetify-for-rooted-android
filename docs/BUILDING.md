# Compilation et publication

## Prérequis

| Outil | Version |
| --- | --- |
| JDK | 17 (Temurin recommandé) |
| Android SDK Platform | 36 |
| Android Build Tools | 36.0.0 |
| Gradle | 8.11.1 (wrapper inclus) |
| Python (maintenance du dépôt) | 3.9+ |

`local.properties` doit pointer vers le SDK :

```properties
sdk.dir=/chemin/vers/Android/Sdk
```

Variables utiles :

```bash
export JAVA_HOME=/chemin/vers/jdk-17
export ANDROID_HOME=/chemin/vers/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

## Build debug

```bash
./gradlew :app:assembleDebug
```

Sortie : `app/build/outputs/apk/debug/app-debug.apk` (applicationId suffixé
`.debug`, signé avec la clé debug locale).

## Build release

1. Générez une clé de release (à conserver précieusement) :

```bash
mkdir -p local-signing
keytool -genkeypair -v -keystore local-signing/tunefy-release.jks \
  -alias tunefy -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=TuneFy, OU=Open Source, O=TuneFy, L=Paris, C=FR"
```

2. Créez `keystore.properties` à la racine (ignoré par Git) :

```properties
storeFile=local-signing/tunefy-release.jks
storePassword=...
keyAlias=tunefy
keyPassword=...
```

3. Compilez :

```bash
./gradlew :app:assembleRelease
```

Sortie : `app/build/outputs/apk/release/app-release.apk`, minifié par R8 et
signé (`v1` + `v2` + `v3` selon l'outil).

En CI, utilisez plutôt les variables d'environnement :

- `TUNEFY_KEYSTORE_FILE`
- `TUNEFY_KEYSTORE_PASSWORD`
- `TUNEFY_KEY_ALIAS`
- `TUNEFY_KEY_PASSWORD`

Sans ces valeurs, le build release produit un APK **non signé** : `assembleRelease`
réussit mais l'APK n'est pas installable.

## Vérifications avant publication

```bash
# Signature et certificat
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk

# Manifeste, permissions, min/target SDK
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging \
  app/build/outputs/apk/release/app-release.apk

# Empreinte à publier dans la release
sha256sum app/build/outputs/apk/release/app-release.apk
```

Installation de contrôle :

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n app.tunefy.android/.MainActivity
adb logcat -s AndroidRuntime:E
```

## Mise à jour du dépôt

Après toute modification de `repository/index.json` ou des paquets :

```bash
python3 scripts/update-repository.py            # réseau : hashes + snapshot
python3 scripts/update-repository.py --skip-remote  # hors ligne
```

Le script :

1. calcule les SHA-256 et tailles des fichiers de `repository/packages/` ;
2. télécharge le bundle `.mpp` pour vérifier son empreinte (`AUTO_REMOTE`) ;
3. rafraîchit `assets/patches/patches-list.json` ;
4. copie l'index dans `app/src/main/assets/repository/index.json`.

## Checklist de release

1. `python3 scripts/update-repository.py`
2. `./gradlew :app:assembleRelease`
3. `apksigner verify` + `aapt2 dump badging`
4. `sha256sum` de l'APK
5. Tag `vX.Y.Z` sur `main`
6. GitHub Release avec :
   - l'APK signé,
   - son SHA-256,
   - les notes d'installation,
   - la liste des limites connues,
   - l'empreinte du certificat de signature.
7. Vérifier que l'asset est téléchargeable sans authentification.

## Dépannage

| Symptôme | Cause probable | Solution |
| --- | --- | --- |
| `SDK location not found` | `local.properties` absent | créer le fichier avec `sdk.dir` |
| `Unsupported class file major version` | JDK trop récent | utiliser JDK 17 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | signature différente | désinstaller l'ancienne version |
| `INSTALL_FAILED_INVALID_APK` (splits) | splits incomplets | utiliser l'APKM complet ou le mode root |
| `Cleartext HTTP not permitted` | URL du dépôt en HTTP | utiliser HTTPS |
| Build release non installable | pas de signature | fournir `keystore.properties` ou les variables d'environnement |
