# Den Fernseher sicher aufräumen

TV Detox macht zwei Dinge. Der Startbildschirm läuft automatisch. Welche Apps aus sind, ist eine Entscheidung pro App und bleibt bei der Person, die die Einrichtung steuert. Diese Seite ist diese Entscheidung, plus die Regeln, die alles rückgängig machbar halten.

Starte mit `sh candidates.sh <TV_IP>`. Das listet jede App auf dem Fernseher mit einem Urteil und ändert nichts:

| Urteil | Bedeutung |
| --- | --- |
| `never` | Der Fernseher braucht sie, oder Android startet sie sowieso wieder. An lassen. |
| `keep` | Dahinter steckt eine Funktion, die du nutzen kannst: Cast, Suche oder Live-TV. Nur aus, wenn du sie nicht brauchst. |
| `safe` | Üblicher vorinstallierter Ballast: Werbung, gesponserte Zeilen, ungenutzte Hersteller- und Google-Apps. |
| `review` | Darüber ist nichts bekannt. An lassen, solange du nicht weißt, was es ist. |

Die Liste von oben nach unten abarbeiten. Ausschalten, warten, prüfen, ob der Fernseher noch funktioniert.

## Die vier Regeln

### 1. Apps ausschalten, nie deinstallieren

`pm disable-user --user 0 <Paket>` blendet eine App aus, `pm enable <Paket>` holt sie zurück. `pm uninstall` bei einer System-App lässt sich vom Fernseher aus nicht rückgängig machen. Alles auf dem Fernseher soll vom Fernseher aus umkehrbar sein. Also nur ausschalten und jedes angefasste Paket in die Undo-Liste schreiben.

### 2. Nie ausschalten, was den Fernseher startet

Bildschirm, Fernbedienung, Tastatur, Einstellungen und der Paket-Installer tragen das System. Fällt eines davon aus, kann der Fernseher auf einen schwarzen Bildschirm booten, und ADB ist der einzige Weg zurück. `candidates.sh` markiert diese Apps als `never`.

### 3. Apps, die Android als PERSISTENT markiert, lassen sich nicht ausschalten

Manche Apps starten sich selbst, sobald das System hochfährt. `pm disable-user` meldet Erfolg, nach einem Neustart ist die App wieder da:

```
adb shell dumpsys package com.tcl.guard | grep -m1 pkgFlags
# pkgFlags=[ SYSTEM HAS_CODE PERSISTENT ALLOW_CLEAR_USER_DATA ... ]
```

Steht dort `PERSISTENT`, lass sie in Ruhe. Ausschalten bleibt nicht, und auf manchen Fernsehern bringt es das System durcheinander. `candidates.sh` markiert auch diese als `never`.

### 4. Cast prüfen, wenn du castest

Cast ist nicht eine App, die man am Namen erkennt. Auf Google TV ist der Empfänger `com.google.android.apps.mediashell`. Die lässt sich leicht mit ausschalten, wenn man alles mit „media“ im Namen abräumt. An lassen. Nach einer Runde, die irgendetwas von Google berührt hat, ein Video vom Handy casten und prüfen, ob der Fernseher noch auftaucht. Wenn nicht: `pm enable com.google.android.apps.mediashell` und neu starten. Auf einem Fernseher, der nie castet, darf sie aus sein.

## Ein Paketname ist nicht immer die App

Der Paketname sagt nicht immer, welche App das ist. Prime Video installiert sich zum Beispiel als `com.amazon.amazonvideo.livingroom`, Netflix als `com.netflix.ninja`. Vor dem Ausschalten nachsehen, was wirklich drinsteckt:

```
adb shell cmd package resolve-activity --brief <Paket>
```

Ist es eine App, die du selbst installiert hast und benutzt, gehört sie zu deinen Apps, egal wie sie heißt.

## In Runden arbeiten

Fünf oder zehn Apps ausschalten, dann den Fernseher prüfen: Eingang wechseln, Einstellungen öffnen, etwas abspielen, etwas casten. Ist eine Runde sauber, kommt die nächste. Benimmt sich der Fernseher falsch, weißt du, welche Runde rückgängig muss, statt über vierzig Änderungen zu raten.

Ein Neustart nach jeder Runde lohnt sich. Das Ausschalten wirkt sofort, Prozesse und Dienste können aber bis zum Neustart weiterlaufen. Wirklich geprüft ist der Fernseher erst nach einem frischen Start.

## Wenn etwas schiefgeht

- Eine App zurückholen: `adb shell pm enable <Paket>`
- Die Launcher zurückholen, die `install.sh` ausgeschaltet hat: `sh undo.sh <TV_IP>`. Das kennt nur, was `install.sh` ausgeschaltet hat. Apps, die du von Hand nach dieser Anleitung ausschaltest, stehen nicht auf der Liste. Jede notieren und mit `pm enable` wieder einschalten.
- Der Bildschirm ist schwarz, der Fernseher antwortet aber auf ADB: `com.google.android.tvlauncher` wieder einschalten, darauf booten und `undo.sh` ausführen.

## Was kein Paket ist

Manche Störungen sind das Verhalten des Fernsehers, keine App. Ein Gerät, das beim Start Live-TV oder einen Eingang öffnet, ist so in seinen eigenen Einstellungen eingerichtet, meist unter System oder Energie. Dafür gibt es kein Paket zum Ausschalten. Das stellst du mit der Fernbedienung um.

## Nachschlagen

### Nie ausschalten

| Paket | Was es ist |
| --- | --- |
| `com.google.android.gms`, `com.google.android.gsf` | Google-Play-Dienste und Framework |
| `com.android.vending` | Google Play Store |
| `com.android.tv.settings`, `com.android.settings` | Einstellungen |
| `com.android.systemui` | Systemoberfläche |
| `com.android.providers.*` | Speicher für Einstellungen, Medien, Kontakte, Downloads |
| `com.android.bluetooth` | Bluetooth und die Fernbedienung |
| `com.android.packageinstaller`, `com.google.android.packageinstaller` | Apps installieren |
| `com.dolby.android.audio.service` | Tonverarbeitung, auf manchen Fernsehern sonst kein Ton |
| Tastaturen | `com.google.android.inputmethod.latin` (Gboard), `com.android.inputmethod.latin`, `com.tcl.inputmethod.international` |
| Einrichtungs-Apps | `com.google.android.tungsten.setupwraith`, `com.google.android.partnersetup`, `com.google.android.onetimeinitializer` |
| TCL-Grundlagen | `com.tcl.tvinput` (HDMI und Tuner), `com.tcl.suspension` (Eingangsauswahl), `com.tcl.providers.config`, `com.tcl.useragreement` |
| `com.google.android.apps.mediashell` | Cast, wenn du castest |
| alles mit `PERSISTENT` | bleibt nicht aus |

### Oft gefahrlos aus

Werbung und gesponserte Zeilen, ungenutzte Google-Apps, Hersteller-Füllsel und Reste der seriellen Schnittstelle:

| Paket | Was es ist |
| --- | --- |
| `com.google.android.tvrecommendations` | gesponserte Zeile von Google TV |
| `com.google.android.backdrop` | Hintergrundwerbung von Google TV |
| `com.google.android.videos`, `com.google.android.youtube.tvmusic`, `com.google.android.play.games` | Google-Apps, die wenige Fernseher nutzen |
| `com.google.android.feedback` | Google-Feedback |
| `com.tcl.waterfall.overseas`, `com.tcl.esticker` | TCL-Inhalte und Sticker |
| `com.tcl.dashboard`, `com.tcl.messagebox` | TCL-Flächen und Popups |
| `com.tcl.copydatatotv`, `com.tcl.audioplayer`, `com.tcl.imageplayer`, `com.tcl.videoplayer` | TCL-Medienhelfer |
| `com.tcl.usercenter`, `com.tcl.keyhelp` | TCL-Konto und Hilfe |
| `com.android.printspooler`, `com.android.dreams.basic`, `com.android.htmlviewer` | Android-Beigaben |

### Hängt von dir ab

Nur ausschalten, wenn du die Funktion nicht nutzt:

| Paket | Funktion |
| --- | --- |
| `com.google.android.apps.mediashell` | Cast vom Handy |
| `com.google.android.katniss` | Google-TV-Suche und Sprache |
| `com.google.android.tv.assistant` | Google Assistant |
| `com.google.android.marvin.talkback` | TalkBack-Bildschirmleser |
| `com.google.android.tv.remote.service` | Fernbedienung vom Handy |
| `com.google.android.tts` | Sprachausgabe |
| `com.tcl.tv` | Live-TV, Antenne und Kabel |

Hersteller benennen diese Listen um und erweitern sie. Sie sind starke Hinweise, kein Gesetz. Im Zweifel an lassen: ein langsamer Fernseher ist besser als einer, den du nicht benutzen kannst.
