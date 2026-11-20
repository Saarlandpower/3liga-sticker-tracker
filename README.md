# 3. Liga Sticker-Tracker

Native Android-App, mit der du deine Sticker der Panini-Kollektion zur 3. Liga erfasst. Die App zeigt dir
jederzeit, welche Sticker du hast, welche fehlen und welche doppelt sind. Gescannt wird mit der Handykamera.

* Kotlin, Jetpack Compose (Material 3, Dark Mode), MVVM, Hilt, Room
* CameraX + Google ML Kit Text Recognition (on-device, offline) · ZXing für QR-Codes
* minSdk 26, target/compileSdk 36
* Kein Login, keine Internetpflicht, keine Internet-Berechtigung. Alle Daten bleiben auf dem Gerät.

## Funktionen

| Bereich | Was geht |
|---|---|
| **Übersicht** | Fortschritt „x von N“, Prozent und Balken. Dazu die Zahl fehlender und doppelter Sticker sowie der Fortschritt pro Verein und pro Kategorie. |
| **Album** | Alle Nummern als farbcodierte Kacheln: grau = fehlt, grün = vorhanden, orange mit Badge = doppelt. **Tippen = +1** (mit „Rückgängig“), **lange drücken** öffnet ein Detail mit +/- und Bearbeiten (Name, Verein, Kategorie). Tabs: Alle · Fehlend · Doppelte · Nach Verein · Kategorie. Dazu kommen Filter nach Verein/Kategorie und eine Suche nach Nummer, Spielername oder Verein. |
| **Scannen** | Kamera-Vorschau mit Rahmen. Die Nummer auf der Rückseite wird per ML Kit erkannt und im Serienmodus gezählt, mit Vibration und Ton. Ein Overlay zeigt „#125 – NEU“ (grün) oder „#125 – doppelt (jetzt 3x)“ (orange). Dazu gibt es eine Sitzungsliste mit Rückgängig pro Eintrag, Pause und Taschenlampe. Als Fallback dient ein Nummernblock. |
| **Tauschen** | „Tauschliste teilen“ über das Android-Share-Sheet, z. B. `Fehlen (6): 3, 17, 45-48` / `Doppelt (5): 12 (2x), 88-89, S1`. Mit dem eigenen QR-Code scannt ein zweites Handy deine Liste und sieht sofort die passenden Tauschpaare. Der Tausch lässt sich auf Wunsch direkt buchen. |
| **Einstellungen** | Checkliste importieren (JSON/CSV) oder eine Platzhalter-Liste 1…N erzeugen. Außerdem: Vereine umbenennen und ihnen Nummern zuordnen, den Bestand als JSON exportieren/importieren, Ton/Vibration und Design einstellen. |

### Scan-Logik
* Akzeptiert werden nur Nummern, die in der Checkliste stehen.
* Typische OCR-Verwechslungen werden korrigiert: `I25`/`l25`/`|25` → `125`, `O` → `0`, `S` → `5`, `Z` → `2`,
  `B` → `8`, und es gibt weitere. Ein exakter Treffer gewinnt immer, z. B. bleibt `S1` erhalten, wenn es in der Liste steht.
* Stehen mehrere Zahlen im Bild, zählt die plausibelste: Sie muss im Rahmen liegen, die größte Schrift und die Nähe zur Mitte geben den Ausschlag.
* Eine Nummer muss in zwei Bildern innerhalb von 1 s erkannt werden, das filtert Fehllesungen heraus.
* Sperre gegen Doppelzählung: Dieselbe Nummer wird ignoriert, solange sie im Bild bleibt, und danach noch 2 s lang.
  Hast du denselben Sticker zweimal, nimm ihn kurz aus dem Bild. Eine andere Nummer zählt sofort.

Die Logik liegt Android-frei in `domain/` und ist mit Unit-Tests abgedeckt (`OcrNumberParser`, `ScanGate`).

## Bauen

Voraussetzungen: JDK 17 oder neuer und das Android SDK mit Platform 36. Android Studio bringt beides mit.
Steht der SDK-Pfad nicht in `ANDROID_HOME`, gehört er in `local.properties`:

```properties
sdk.dir=/pfad/zum/Android/sdk
```

```bash
cd 3liga-sticker-tracker
./gradlew testDebugUnitTest    # Unit-Tests
./gradlew assembleDebug        # Debug-APK  → app/build/outputs/apk/debug/
./gradlew assembleRelease      # Release-APK → app/build/outputs/apk/release/
```

Das Release-APK ist mit R8 verkleinert und enthält nur die ARM-ABIs (`arm64-v8a`, `armeabi-v7a`). So bleibt es bei
etwa 22 MB, obwohl die ML-Kit-OCR-Bibliothek groß ist.

### Release-Signing

Keystore und Passwörter kommen **nie ins Repo**: `*.jks`, `*.keystore` und `local.properties` stehen in `.gitignore`.
Einmalig einen Keystore anlegen:

```bash
keytool -genkeypair -v -keystore ~/keys/sticker-tracker.jks -alias sticker \
        -keyalg RSA -keysize 4096 -validity 10000
```

Danach **entweder** in `local.properties` eintragen:

```properties
release.storeFile=/home/ich/keys/sticker-tracker.jks
release.storePassword=…
release.keyAlias=sticker
release.keyPassword=…
```

**oder** als Umgebungsvariablen setzen, praktisch für CI:

```bash
export STICKER_KEYSTORE_PATH=~/keys/sticker-tracker.jks
export STICKER_KEYSTORE_PASSWORD=…
export STICKER_KEY_ALIAS=sticker
export STICKER_KEY_PASSWORD=…
./gradlew assembleRelease
```

Fehlen die Angaben, baut `assembleRelease` trotzdem und erzeugt `app-release-unsigned.apk` (mit Warnung im Log).

## Checkliste

Beim ersten Start lädt die App `app/src/main/assets/checklist.json`. Solange die echte Liste fehlt, ist das eine
Platzhalter-Liste mit den Nummern 1 bis N.

**N konfigurieren:**
* in `assets/checklist.json` über `"placeholderCount"`,
* alternativ, falls dort kein Wert steht, über `stickertracker.placeholderCount` in `gradle.properties`
  (Standard: 423),
* oder jederzeit in der App: *Einstellungen → Platzhalter-Liste erzeugen*.

Sobald das Heft da ist, importierst du die echte Liste unter *Einstellungen → Checkliste importieren*.
Vor dem Übernehmen zeigt eine Vorschau, wie viele Sticker und Vereine die Liste hat und wie viele Anzahlen erhalten bleiben.
**Vorhandene Anzahlen bleiben für jede Nummer erhalten**, die auch in der neuen Liste vorkommt. Nummern mit Bestand,
die in der neuen Liste fehlen, werden in der Vorschau rot aufgeführt.

### JSON-Format

```json
{
  "formatVersion": 1,
  "name": "Panini 3. Liga 2026/27",
  "placeholderCount": 0,
  "clubs": [
    "1. FC Saarbrücken",
    "SV Waldhof Mannheim"
  ],
  "stickers": [
    { "number": "1",   "name": "Wappen",         "club": "1. FC Saarbrücken",   "category": "Wappen" },
    { "number": "2",   "name": "Mannschaft",     "club": "1. FC Saarbrücken",   "category": "Mannschaftsfoto" },
    { "number": "3",   "name": "Max Mustermann", "club": "1. FC Saarbrücken",   "category": "Spieler" },
    { "number": "S1",  "name": "Liga-Logo",                                     "category": "Sonder" },
    { "number": "FW3", "name": "Glitzer-Wappen", "club": "SV Waldhof Mannheim", "category": "Glitzer" }
  ]
}
```

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `name` | nein | Name der Checkliste (wird in der Übersicht angezeigt) |
| `placeholderCount` | nein | Ist `stickers` leer, werden die Nummern `1..placeholderCount` erzeugt |
| `clubs` | nein | Vereine in Anzeigereihenfolge: Strings oder `{ "name": "…" }`. Vereine, die nur bei Stickern stehen, werden automatisch ergänzt |
| `stickers[].number` | **ja** | Stickernummer als String: `"1"`, `"125"`, `"S1"`, `"FW3"`. Groß-/Kleinschreibung und führende Nullen sind egal |
| `stickers[].name` | nein | Spielername oder Titel |
| `stickers[].club` | nein | Vereinsname (alternativ `clubIndex` = Index in `clubs`) |
| `stickers[].category` | nein | `Spieler` (Standard), `Wappen`, `Mannschaftsfoto`, `Sonder`/`Glitzer` |

Die Reihenfolge in `stickers` ist die Reihenfolge im Album. Erlaubt ist auch eine reine Liste wie `["1", "2", "S1"]`.
Deutsche Feldnamen (`nummer`, `verein`, `kategorie`) werden ebenfalls verstanden.

### CSV-Format

Trennzeichen `;`, `,` oder Tab, UTF-8, optional mit Anführungszeichen. Die Kopfzeile ist optional. Ohne Kopfzeile gilt
die Reihenfolge Nummer, Name, Verein, Kategorie.

```csv
nummer;name;verein;kategorie
1;Wappen;1. FC Saarbrücken;Wappen
2;Mannschaftsfoto;1. FC Saarbrücken;Mannschaftsfoto
3;"Mustermann, Max";1. FC Saarbrücken;Spieler
S1;Liga-Logo;;Sonder
```

Enthält eine importierte Liste **keine** Vereinsangaben, z. B. nur eine Nummernspalte, bleiben die bisherigen Vereine
und Zuordnungen erhalten.

### Vereine

`checklist.json` enthält als Startwert die 20 Klubs der Saison 2025/26. Bitte gegen das Heft prüfen.
Unter *Einstellungen → Vereine bearbeiten* kannst du jeden Namen ändern und einem Verein Nummern zuordnen, etwa
`21-38, S3`. So lässt sich die Platzhalter-Liste schon gruppieren, bevor die echte Checkliste vorliegt.

## Datensicherung

* **Export/Import** unter *Einstellungen → Bestand exportieren/importieren*. Gespeichert wird über das Storage Access
  Framework, also in einen beliebigen Ordner, auf Drive usw. Das Format ist JSON mit `app: "3liga-sticker-tracker"`,
  `clubs` und `stickers` (Nummer, Name, Verein, Kategorie, Anzahl). Ein Import ersetzt den Stand komplett.
* **Android Auto Backup** ist aktiv (`allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml`): Datenbank und
  Einstellungen werden mit dem Google-Konto gesichert und beim Gerätewechsel übertragen.

## Formate für den Tausch

* Der Text zum Teilen fasst fortlaufende Nummern zu Bereichen zusammen (`45-48`, `S1-S3`). Doppelte mit mehr als
  einem Ersatz erscheinen als `12 (2x)`.
* Der QR-Code enthält eine kompakte Zeichenkette, z. B. `3LST:1|N:423|M:3,17,45-48|D:12x2,88-89,S1`
  (`N` = Größe der Checkliste, `M` = fehlend, `D` = doppelt mit Anzahl der Ersatz-Sticker). Nutzt der Partner eine
  andere Checkliste, weist die App darauf hin.

## Projektstruktur

```
app/src/main/java/de/dritteliga/stickertracker/
├── domain/      reine Kotlin-Logik (Zähllogik, Parser, OCR-Auswahl, Scan-Sperre, Tausch, Backup) – unit-getestet
├── data/        Room-Datenbank, Repository, Einstellungen
├── di/          Hilt-Module
└── ui/          Compose-Screens: overview, album, scan, trade, settings
app/src/test/    Unit-Tests
app/schemas/     exportierte Room-Schemata (für spätere Migrationen)
```
