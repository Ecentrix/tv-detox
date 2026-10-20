# App neu bauen

Die Skripte sind Shell-Dateien. Auf Windows in **Git Bash** ausführen, im Projektordner.

## Voraussetzungen

- Java 17
- Android SDK mit `platforms;android-34` und `build-tools;34.0.0`
- adb (Platform Tools), nur zum Installieren auf dem Fernseher

Den SDK-Ordner einmal setzen. Liegt er woanders, diesen Pfad nehmen:

```sh
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
```

## Bauen

```sh
sh build.sh
```

Ergebnis: `out/tvhome.apk`.

Beim ersten Build entsteht `key.jks`. Die Datei behalten. Updates müssen mit demselben Schlüssel signiert sein, sonst installiert der Fernseher die neue Fassung nicht über die alte.

## Auf den Fernseher

Fernseher und Rechner im selben WLAN. USB-Debugging an. Dann die IP des Fernsehers:

```sh
sh install.sh <TV_IP>
```

Liegt `out/tvhome.apk` vor, installiert das Skript diese Datei. Sonst lädt es die letzte Release-APK.
