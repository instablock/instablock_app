# InstaBlock

InstaBlock est une application Android (et Android uniquement) pour bloquer tous les comptes d'extrême droite en France.

*Le guide d'installation est juste en dessous !*

Basée sur le mouvement [On bloque l'extrême droite](https://www.onbloquelextremedroite.fr/), elle recense les comptes de partis, d'élus, de médias, de militants et d'influenceurs d'extrême droite actifs sur Instagram. Ils proviennent de :
- la liste [trace-archive.fr/onbloque](https://trace-archive.fr/onbloque) ;
- les comptes suggérés par [onbloquelextremedroite.fr](https://www.onbloquelextremedroite.fr/) et son dépôt [juliencoquet/onbloquelextremedroite](https://github.com/juliencoquet/onbloquelextremedroite) ;
- les comptes suivis par [@blockout_extremedroitefrance](https://www.instagram.com/blockout_extremedroitefrance/) ;
- la liste [Vaalgtir/blockout_ed](https://github.com/Vaalgtir/blockout_ed) ;
- les comptes Instagram des député·es RN et UDR, d'après les [données de l'Assemblée nationale](https://data.assemblee-nationale.fr/) ;
- les comptes Instagram de membres et de partis d'extrême droite référencés sur [Wikidata](https://www.wikidata.org/).

On veut les bloquer, mais ça prend un temps fou manuellement... Donc pourquoi ne pas automatiser ça ? Vous le lancez, laissez votre téléphone tranquille une heure, et tout est fait.

**Concrètement :**
- garantie sans virus : open source, donc vérifiable par n'importe qui
- tourne en une heure environ, et bloque 619 comptes
- votre compte Instagram ne risque pas de sauter pour cause d'etre un "bot" : l'application simule l'utilisation humaine, et appuie sur les boutons d'Instagram, c'est donc indétectable

**Reprenons le contrôle de l'espace médiatique, et freinons l'expansion de l'extrême droite et de leurs idées dangereuses !**

### Disclaimer : IA

Oui, l'IA a été utilisée pour construire cette application. MAIS je suis aussi étudiant ingénieur en informatique, et je savais programmer bien avant l'avènement de l'IA... Ce n'est pas un projet vomi par un LLM, son fonctionnement reste controlé et entièrement supervisé par un humain. Tout faire a la main aurait pris un temps fou, et c'est bien ce qu'il nous manque, le temps...

## Installation

Ce guide est fait pour tout le monde, sans aucune connaissance technique. Comptez 5 minutes. **Lisez le jusqu'au bout !** Votre téléphone va probablement raler à un moment et ne pas vouloir l'installer, mais c'est facilement faisable, tout est décrit.

### 1. Autoriser l'installation

InstaBlock n'est pas sur le Play Store : on l'installe à partir d'un fichier (une "APK", ce qu'Android utilise). Google Play Protect bloque souvent ce type d'installation (on adore le controle des géants de la tech...), il faut donc désactiver ce service le temps de l'installer (cela ne signifie pas que l'appli est un virus !) :

1. Ouvrez l'application **Play Store**.
2. Touchez votre **photo de profil**, en haut à droite.
3. Touchez **Play Protect**.
4. Touchez la **roue dentée** (Paramètres), en haut à droite.
5. Désactivez **Analyser les applis avec Play Protect**, puis confirmez.

Pensez à le réactiver une fois InstaBlock installé (étape 3).

### 2. Télécharger l'application

1. Sur votre téléphone, ouvrez cette page : **[github.com/instablock/instablock_app/releases/latest](https://github.com/instablock/instablock_app/releases/latest)**
2. Dans la partie **Assets**, touchez le fichier qui finit par **.apk**. Le téléchargement commence.

### 3. Installer

1. Une fois le téléchargement terminé, touchez la notification, ou ouvrez le fichier depuis l'application **Fichiers**, dossier **Téléchargements**.
2. Si le téléphone affiche que l'installation d'applis inconnues n'est pas autorisée depuis cette source, touchez **Paramètres**, activez **Autoriser cette source**, puis revenez en arrière.
3. Faites **Installer**.
4. Retournez dans le Play Store -> photo de profil -> Play Protect -> roue dentée, et réactivez **Analyser les applis avec Play Protect**.

### 4. Autoriser les paramètres

Ouvrez **Paramètres** -> **Applications** -> **InstaBlock**, puis dans le menu "trois petits points" en haut à droite, sélectionner **Autoriser les paramètres restreints**.

Cela va vous permettre de donner accès à InstaBlock au "service d'accessibilité", qui est nécessaire pour toucher les boutons d'Instagram à votre place. Pour donner ces paramètres, il faut ensuite :

1. Ouvrir **InstaBlock**.
2. Appuyer sur le **?** en haut à droite et suivre le guide.

### 5. Lancer

1. Vérifiez que vous êtes connecté·e à votre compte dans l'application Instagram.
2. Branchez votre téléphone si possible (comptez environ une heure), et touchez **Démarrer**.
3. Ne touchez plus le téléphone : InstaBlock ouvre Instagram et bloque les comptes un par un. L'écran reste allumé pendant tout le blocage.
4. Pour arrêter, revenez dans InstaBlock et touchez **Arrêter**.

Si le blocage est interrompu, touchez simplement **Démarrer** plus tard : InstaBlock reprend là où il s'était arrêté.

## Infos

**La limite d'Instagram.** Instagram limite le nombre d'actions qu'un compte peut faire en peu de temps, sans publier de chiffres. S'il juge que vous bloquez trop de comptes d'un coup, il affiche "Réessayez plus tard". InstaBlock s'arrête alors de lui-même. Ce n'est pas grave : attendez quelques heures, voire un jour, puis refaites **Démarrer** pour continuer là où vous en étiez.

**Votre compte.** InstaBlock n'est pas un bot qui se connecte à votre place : il ne demande pas votre mot de passe et passe par l'application Instagram de votre téléphone, en touchant les mêmes boutons que vous, au rythme d'une personne. Bloquer des comptes est une action normale sur Instagram, et le risque pour votre compte est donc faible. Au pire, Instagram limite temporairement les blocages, comme expliqué ci-dessus.

**Vos données.** InstaBlock n'a pas accès à Internet : il ne peut rien envoyer, ni à nous ni à personne. Tout reste sur votre téléphone.

**Votre responsabilité.** Automatiser des actions sur Instagram n'est pas prévu par ses conditions d'utilisation, et aucun risque ne peut être exclu à 100 %. Vous utilisez InstaBlock sous votre propre responsabilité. Vous pouvez à tout moment modifier la liste avant de lancer, et débloquer un compte depuis Instagram.

## Building

Cette partie est technique : si vous voulez seulement utiliser l'application, vous pouvez l'ignorer. Mais c'est aussi cette partie qui permet a n'importe qui de vérifier l'application, et donc de la garantir sans virus !

### Prérequis

- Le SDK Android (plateforme 35), par exemple installé avec [Android Studio](https://developer.android.com/studio).
- Un JDK 17. Gradle le trouve tout seul.
- `adb`, fourni avec le SDK (dossier `platform-tools`).

### Compiler

```sh
git clone git@github.com:instablock/instablock_app.git
cd instablock_app
./gradlew test            # tests unitaires
./gradlew assembleDebug   # crée app/build/outputs/apk/debug/app-debug.apk
```

### Installer sur un téléphone avec adb

1. Sur le téléphone, activez les **Options pour les développeurs** : Paramètres -> À propos du téléphone -> touchez 7 fois **Numéro de build**.
2. Dans Paramètres -> Système -> Options pour les développeurs, activez **Débogage USB**.
3. Branchez le téléphone et acceptez la demande d'autorisation qui s'affiche.
4. Lancez :

```sh
adb devices                                              # le téléphone doit apparaître
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Installée avec adb, l'application n'est pas soumise aux « paramètres restreints » : le service d'accessibilité s'active directement.

Une version debug et une version release sont signées avec des clés différentes : pour passer de l'une à l'autre, il faut d'abord désinstaller l'application (`adb uninstall dev.instablock`), ce qui efface la liste et les résultats enregistrés.

### Version release

```sh
./gradlew assembleRelease   # crée app/build/outputs/apk/release/app-release.apk
```

La version release est signée avec la clé décrite dans `.signing/keystore.properties`, un fichier qui n'est jamais publié :

```properties
storeFile=../.signing/instablock-release.jks
storePassword=...
keyAlias=instablock
keyPassword=...
```

Gardez une sauvegarde de ce dossier : une mise à jour ne s'installe par-dessus une version existante que si elle est signée avec la même clé. Pensez à augmenter `versionCode` et `versionName` dans `app/build.gradle.kts` à chaque version.

### Journaux et captures

L'application enregistre son journal (en anglais) et, en cas de problème, une capture de l'écran :

```sh
adb pull /sdcard/Android/data/dev.instablock/files/logs/
adb pull /sdcard/Android/data/dev.instablock/files/dumps/
```

Si Instagram change ses écrans, les identifiants de vues sont dans `app/src/main/java/dev/instablock/instagram/Instagram.kt` et les textes dans `instagram/Labels.kt`.

## License

InstaBlock est distribué sous licence [GNU GPL v3.0](LICENSE) : vous pouvez l'utiliser, l'étudier, le modifier et le redistribuer, à condition que les versions modifiées restent elles aussi libres, sous la même licence.
