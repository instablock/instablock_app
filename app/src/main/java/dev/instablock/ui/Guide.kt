package dev.instablock.ui

import dev.instablock.i18n.tx

/**
 * The guide shown by the ? button: how to turn on the accessibility service.
 *
 * Labels in quotes are copied from Android's Settings app (Android 17, English and French), so
 * they match what people see. Other phone brands may word them slightly differently.
 */
object Guide {
    val title = tx("Activer le service d'accessibilité", "Turn on the accessibility service")

    val body = tx(
        """
InstaBlock a besoin du service d'accessibilité pour lire l'écran d'Instagram et toucher les boutons à votre place. Tant qu'il est désactivé, l'engrenage en haut à droite clignote doucement.

1. Touchez l'engrenage en haut à droite de cet écran : les paramètres « Accessibilité » s'ouvrent.
2. Dans la section « Applications téléchargées », touchez « InstaBlock ».
3. Activez « Utiliser InstaBlock ».
4. Une fenêtre demande d'autoriser InstaBlock à contrôler l'appareil : touchez « Autoriser ».

C'est fait quand l'engrenage ne clignote plus et que le bandeau « service désactivé » a disparu.

SI L'INTERRUPTEUR EST GRISÉ
Android bloque ce réglage pour les applis installées à partir d'un fichier. L'écran affiche « Contrôlé par les paramètres restreints », ou une fenêtre « Paramètre restreint » s'ouvre quand vous touchez l'interrupteur.
1. Touchez l'interrupteur une fois, puis « OK » dans la fenêtre « Paramètre restreint ». Cette tentative est nécessaire : l'option de l'étape 3 n'apparaît qu'après.
2. Ouvrez les Paramètres du téléphone › « Applications » › « Voir toutes les applis » › « InstaBlock ». L'écran « Infos sur l'appli » s'ouvre.
3. Touchez le menu ⋮ en haut à droite, puis « Autoriser les paramètres restreints ». Confirmez avec votre code, schéma ou empreinte.
4. Recommencez depuis l'étape 1 ci-dessus : l'interrupteur fonctionne maintenant.
        """.trim(),
        """
InstaBlock needs the accessibility service to read Instagram's screen and tap the buttons for you. While it's off, the gear at the top right gently pulses.

1. Tap the gear at the top right of this screen: the "Accessibility" settings open.
2. In the "Downloaded apps" section, tap "InstaBlock".
3. Turn on "Use InstaBlock".
4. A window asks whether InstaBlock may control the device: tap "Allow".

It's done when the gear stops pulsing and the "service is off" banner has gone.

IF THE SWITCH IS GREYED OUT
Android blocks this setting for apps installed from a file. The screen says "Controlled by Restricted Setting", or a "Restricted setting" window opens when you tap the switch.
1. Tap the switch once, then "OK" in the "Restricted setting" window. This attempt is needed: the option in step 3 only appears afterwards.
2. Open the phone's Settings › "Apps" › "See all apps" › "InstaBlock". The "App info" screen opens.
3. Tap the ⋮ menu at the top right, then "Allow restricted settings". Confirm with your PIN, pattern or fingerprint.
4. Start again from step 1 above: the switch now works.
        """.trim(),
    )
}
