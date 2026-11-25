# Play Console – Antworten und Checkliste

## Store-Eintrag
| Feld | Datei / Wert |
|---|---|
| App-Name | `store/de-DE/title.txt` |
| Kurzbeschreibung (max. 80 Zeichen) | `store/de-DE/short_description.txt` |
| Vollständige Beschreibung | `store/de-DE/full_description.txt` |
| App-Symbol 512 × 512 | `store/grafiken/icon-512.png` |
| Feature-Grafik 1024 × 500 | `store/grafiken/feature-1024x500.png` |
| Screenshots (mind. 2, Smartphone) | selbst auf dem Gerät aufnehmen: Übersicht, Album, Scannen, Tauschen |
| Kategorie | Bücher & Nachschlagewerke oder Lifestyle (Vorschlag: Lifestyle) |
| Datenschutzerklärung | URL zu `docs/datenschutz.md`, z. B. über GitHub Pages |

## App-Inhalte
- **Datenschutzerklärung:** URL eintragen (Kontaktdaten in `docs/datenschutz.md` vorher ausfüllen!)
- **Werbung:** Nein, die App enthält keine Werbung
- **App-Zugriff:** Alle Funktionen ohne Anmeldung verfügbar
- **Einstufung (IARC-Fragebogen):** Kategorie „Dienstprogramm/Produktivität“; alle Fragen zu Gewalt, Glücksspiel,
  Nutzerinteraktion usw. mit „Nein“ → ergibt „USK ab 0“
- **Zielgruppe:** ab 13 Jahren empfohlen (bei „unter 13“ gelten die strengeren Familienrichtlinien)
- **Nachrichten-App / Behörden-App / Finanzfunktionen / Gesundheit:** Nein

## Datensicherheit (Data safety)
- Erhebt oder teilt die App Nutzerdaten? → **Nein**
- Begründung: Keine Internetberechtigung; Kamerabilder werden nur on-device verarbeitet und nicht gespeichert;
  Sammlungsdaten bleiben lokal. (Android Auto Backup zählt laut Google nicht als Datenerhebung durch die App.)
- Verschlüsselung bei der Übertragung: nicht zutreffend (keine Übertragung)
- Datenlöschung: Nutzer kann alle Daten über „App-Daten löschen“ bzw. Deinstallation entfernen

## Kamera-Berechtigung
Wird nur für die Kernfunktion (Nummern-Scan, QR-Tausch) verwendet und erst beim Öffnen des Scan-Bereichs
angefragt; ohne Berechtigung bleibt die manuelle Eingabe nutzbar.

## Release
1. Upload-Keystore erzeugen (siehe README) und als GitHub-Secrets hinterlegen (siehe README → „Release per GitHub“).
2. Tag `v1.0.0` pushen → die Action baut `app-release.aab` und `app-release.apk` und legt einen GitHub-Release an.
3. Erste Version in der Play Console manuell hochladen (Interner Test → Geschlossener Test → Produktion).
4. Play App-Signatur aktivieren; der Upload-Keystore bleibt bei dir.

## Händlerstatus
Für eine kostenlose App ohne In-App-Käufe und Werbung ist in der Regel „Kein Händler“ die passende Angabe.
Bitte selbst prüfen – die Angabe gilt für das gesamte Entwicklerkonto (Frist laut Play Console: Februar 2027).
