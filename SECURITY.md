# Sécurité

## Signalement

Ouvrez un avis privé via l'onglet **Security → Report a vulnerability** du
dépôt GitHub. N'ouvrez pas d'issue publique pour une faille exploitable.

Indiquez :

- la version concernée (nom de l'APK / commit) ;
- les étapes de reproduction ;
- l'impact attendu et les permissions nécessaires.

## Modèle de sécurité

TuneFy doit rester prévisible et vérifiable :

1. **Intégrité des fichiers** : chaque paquet du dépôt déclare un SHA-256.
   Après téléchargement, l'empreinte est recalculée ; toute différence supprime
   le fichier et fait échouer l'installation.
2. **Signatures** : les APK et splits sont inspectés localement. Une incohérence
   entre splits, entre le package attendu et le fichier, ou avec la version déjà
   installée bloque l'installation.
3. **Installation système** : TuneFy utilise `PackageInstaller`. La confirmation
   Android reste obligatoire. Aucune installation silencieuse.
4. **Données d'autrui** : TuneFy n'écrit jamais dans `/data/data/com.spotify.music`.
   Les sauvegardes root sont des archives en lecture, stockées dans TuneFy.
5. **Réseau** : HTTPS uniquement, pas de télémétrie, pas de publicité, pas de
   compte. L'index distant est un simple fichier JSON remplaçable.
6. **Permissions** : réseau, notifications, service de premier plan, installation
   de paquets. Pas de `QUERY_ALL_PACKAGES`, pas de stockage global, pas
   d'accessibilité.
7. **Contenu** : aucun mécanisme de contournement d'abonnement, de publicité
   audio ou de DRM.

## Ce qui n'est pas garanti

- Le dépôt distant est contrôlé par ses mainteneurs : un dépôt compromis peut
  publier des entrées malveillantes. Vérifiez l'URL du dépôt et les sources
  upstream. Les SHA-256 protègent contre la corruption, pas contre un éditeur de
  dépôt malveillant.
- Les patches Spicetify et les APK tiers proviennent de projets communautaires :
  leur qualité et leur légalité relèvent de leurs auteurs.
- L'accès root accordé à TuneFy permet techniquement d'exécuter des commandes
  root. Lisez le code (`RootManager`, `SpotifyManager`) avant d'accorder `su`.
- Android et les ROM personnalisées peuvent imposer des restrictions
  supplémentaires aux installations de splits.

## Versions prises en charge

| Version | Support |
| --- | --- |
| 1.x | Corrections de sécurité fournies |
