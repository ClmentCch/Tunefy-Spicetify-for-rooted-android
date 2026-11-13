# Fonctionnalités root

Le root est **optionnel**. Toutes les fonctions de boutique, de téléchargement,
de vérification et d'installation standard fonctionnent sans root, sur Android
11 comme sur Android 16/17.

## Activation

**Paramètres → Root et sécurité → Fonctionnalités root** (activé par défaut)
puis **Outils root → Tester l'accès root**. Android/Magisk affiche sa propre
demande d'autorisation `su` ; TuneFy n'obtient jamais l'accès silencieusement.

## Détection

| Élément | Méthode |
| --- | --- |
| Binaire `su` | `/system/bin/su`, `/system/xbin/su`, `/data/adb/ksu/bin/su`, `/data/adb/ap/bin/su`, `/debug_ramdisk/su` |
| Accès effectif | `su -c id` avec timeout de 8 s ; recherche de `uid=0` |
| Version | première ligne de `su -v` |
| Magisk | paquets `com.topjohnwu.magisk` / `io.github.vvb2060.magisk`, dossier `/data/adb/magisk`, mention MAGISK |
| KernelSU | paquet `me.weishu.kernelsu`, dossier `/data/adb/ksu`, mention KernelSU |
| APatch | paquet `me.bmax.apatch`, dossier `/data/adb/ap`, mention APatch |

Les commandes sont bornées dans le temps et leurs flux sont drainés pour éviter
tout blocage de l'interface.

## Inspection de Spotify

`SpotifyManager.status()` lit, sans root :

- version et code de version ;
- empreinte SHA-256 du certificat de signature ;
- nombre de splits installés ;
- permissions demandées ;
- lisibilité du dossier de données privées.

Avec root, la taille de `/data/data/com.spotify.music` peut être mesurée
(`du -sb`).

## Sauvegarde

```
tar -czf - -C /data/data com.spotify.music  >  filesDir/backups/spotify-<ts>.tar.gz
```

- **Lecture seule** : TuneFy ne réécrit jamais cette archive dans Spotify.
- Stockage privé de TuneFy, exclu des sauvegardes cloud Android.
- Export possible vers un emplacement choisi via le sélecteur de documents.
- Suppression possible depuis l'écran Outils root.
- La restauration des données Spotify **n'est pas implémentée**, précisément
  pour éviter d'écraser les données privées d'une autre application.

## Installation root

Sur action explicite et après vérification :

1. empreinte SHA-256 du fichier ;
2. cohérence des signatures entre splits ;
3. correspondance du package ;
4. comparaison avec la signature de la version déjà installée.

Puis :

```bash
su -c "pm install -r -d <apk>"
su -c "pm install-multiple -r -d <base> <split>…"
```

Les chemins sont échappés ; aucune commande n'est construite à partir d'une URL
ou d'un champ de dépôt non vérifié.

## Ce que TuneFy ne fait pas

- Pas d'écriture dans `/data/data/com.spotify.music`.
- Pas de désactivation de vérification de signature Android.
- Pas d'installation silencieuse : `pm install` peut encore être refusé par le
  système ou le gestionnaire root.
- Pas de contournement d'abonnement, de publicité audio ou de DRM.
- Pas de script shell fourni par le dépôt : seules des commandes fixes existent
  dans le code.
