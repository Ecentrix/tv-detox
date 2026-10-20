# Komplette Anleitung

So kommt diese Fassung auf deinen Fernseher. Die Wallpaper-Auswahl steckt in deinem lokalen Stand. Dafür baust du die App selbst und installierst `out/tvhome.apk`. Die Release von GitHub hat diese Änderung nicht.

Alles in **Git Bash**, im Projektordner. Fernseher und Rechner im selben Netzwerk.

## 1. Am Fernseher

1. **Einstellungen → Gerätepräferenzen** (oder **System**) **→ Info**.
2. Auf **Build** siebenmal OK drücken, bis „Du bist jetzt Entwickler“ kommt.
3. Zurück, **Entwickleroptionen**, **USB-Debugging** an. Gibt es **Netzwerk-Debugging** oder **Wireless debugging**, das auch an.
4. Beim ersten Verbinden fragt der Fernseher „Debugging zulassen?“. **Immer zulassen** anhaken und **Zulassen** drücken.

Die IP steht unter **Einstellungen → Netzwerk**.

## 2. Am Rechner

- Java 17
- Android SDK mit `platforms;android-34` und `build-tools;34.0.0`
- [adb / Platform Tools](https://developer.android.com/tools/releases/platform-tools), der Ordner `platform-tools` muss im PATH liegen

SDK-Pfad, falls er im üblichen Android-Studio-Ordner liegt:

```sh
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
```

## 3. Bauen

```sh
sh build.sh
```

Ergebnis: `out/tvhome.apk`.

Beim ersten Build entsteht `key.jks`. Die Datei behalten. Spätere Updates müssen mit demselben Schlüssel signiert sein.

## 4. Installieren

```sh
sh install.sh <TV_IP>
```

Beispiele:

```sh
sh install.sh 192.168.1.50
sh install.sh 192.168.1.50 "com.netflix.ninja,com.google.android.youtube.tv"
sh install.sh 192.168.1.50 "" "bluetooth,sound,picture"
```

Das Skript nimmt `out/tvhome.apk`, sobald die Datei da ist. Die meisten Fernseher öffnen danach weiter ihren eigenen Startbildschirm. Das Skript fragt, ob es den ausschalten soll. Nichts wird deinstalliert.

## 5. Hintergrund

Auf dem neuen Startbildschirm: **Einstellungen** (oben rechts) → **Hintergrund**.

Im Menü liegen die farbigen Vorlagen und die cleanen Flächen Tinte, Graphit, Nebel, Stein und Sand. **Eigenes Bild** öffnet die Bildauswahl des Fernsehers. Zurück schließt das Menü. Die Wahl bleibt gespeichert.

## 6. Aufräumen

Optional, nach dem Startbildschirm. Zuerst nur lesen, nichts ändern:

```sh
sh candidates.sh <TV_IP>
```

Die Urteile und die Regeln stehen in [debloat.md](debloat.md). Kurz:

- `never` an lassen
- `keep` nur aus, wenn du die Funktion nicht brauchst (Cast, Suche, Live-TV)
- `safe` ist üblicher Ballast
- `review` an lassen, solange unklar ist, was es ist

Fünf bis zehn Apps ausschalten, Fernseher prüfen, neu starten, dann die nächste Runde. Jede von Hand ausgeschaltete App notieren. Zurückholen:

```sh
adb shell pm enable <Paket>
```

Cast bleibt an, solange du vom Handy wirfst: `com.google.android.apps.mediashell`.

## 7. Rückgängig

Nur den Startbildschirm zurück:

```sh
sh undo.sh <TV_IP>
```

Apps, die du selbst ausgeschaltet hast, stehen nicht in `undo.sh`. Die holst du mit `pm enable` zurück.

## Wenn der Fernseher beim Start hängen bleibt

Oft ist ein paar Sekunden lang ADB erreichbar:

```sh
adb connect <TV_IP>
adb wait-for-device
adb shell pm list packages -d
```

Alles Ausgeschaltete wieder an:

```sh
for p in $(adb shell pm list packages -d | tr -d '\r' | sed 's/package://'); do adb shell pm enable "$p"; done
```

Danach `sh undo.sh <TV_IP>` und neu starten.
