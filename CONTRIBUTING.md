# Contribuer à TuneFy

Merci de votre intérêt ! TuneFy est un projet open source (GPL-3.0) consacré à
l'écosystème Spotify sur Android.

## Avant de commencer

- Recherchez une issue existante avant d'en ouvrir une nouvelle.
- Pour une fonctionnalité importante, ouvrez d'abord une discussion/issue afin
  d'éviter un travail perdu.
- Les contributions doivent respecter le [Code de conduite](CODE_OF_CONDUCT.md).

## Ce que TuneFy n'accepte pas

- Du code qui contourne les abonnements Spotify, la publicité audio ou les DRM.
- Des APK propriétaires ou des ressources sous copyright redistribuées.
- Des contournements de sécurité Android (installation silencieuse, écriture
  directe dans `/data/data` d'une autre application).
- De la télémétrie, des publicités ou des SDK propriétaires obligatoires.

## Environnement

```bash
git clone https://github.com/ClmentCch/Tunefy-Spicetify-for-rooted-android.git
cd Tunefy-Spicetify-for-rooted-android
./gradlew :app:assembleDebug
```

Prérequis détaillés : [docs/BUILDING.md](docs/BUILDING.md).

## Style de code

- Kotlin idiomatique, **Compose** pour toute l'UI.
- Architecture MVVM : les écrans ne parlent pas directement aux APIs réseau ;
  passez par un dépôt et exposez des `StateFlow`.
- Pas de dépendance ajoutée sans nécessité ; justifiez-la dans la pull request.
- Chaînes d'interface en français, ton direct et factuel.
- Les commentaires expliquent le *pourquoi*, pas le *quoi*.
- Utilisez `Modifier` et les composants existants avant d'en créer de nouveaux.

## Ajouter ou corriger une entrée du catalogue

Les données vivent dans `repository/index.json` et les paquets dans
`repository/packages/`. Après modification :

```bash
python3 scripts/update-repository.py
```

Cela recalcule les SHA-256, rafraîchit le snapshot des patches et copie l'index
dans les assets de l'application. Le format est documenté dans
[docs/REPOSITORY_FORMAT.md](docs/REPOSITORY_FORMAT.md).

Une entrée doit :

- être liée à Spotify (application, extension, thème, patch ou outil) ;
- déclarer une licence, une source et une URL de téléchargement HTTPS vérifiée ;
- préciser les versions Spotify prises en charge si elle dépend d'un patch ;
- ne jamais pointer vers un APK modifié distribué sans autorisation.

## Processus de pull request

1. Forkez le dépôt et créez une branche descriptive.
2. Limitez la PR à un sujet.
3. Vérifiez la compilation (`./gradlew :app:assembleDebug`).
4. Décrivez le comportement avant/après et les vérifications effectuées.
5. Mettez à jour la documentation si vous changez un format, une permission ou
   une garantie de sécurité.

Les mainteneurs peuvent demander des changements de sécurité avant tout merge.
