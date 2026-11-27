# Sticker-Tracker – Web-App

Die Web-App (PWA) bietet dieselben Funktionen wie die Android-App und richtet sich vor allem an iPhones. Sie läuft im
Browser, lässt sich auf den Home-Bildschirm legen und funktioniert danach offline.

**Adresse:** https://saarlandpower.github.io/3liga-sticker-tracker/

## Auf dem Handy installieren
- **iPhone (Safari):** Seite öffnen → „Teilen“ → „Zum Home-Bildschirm“
- **Android (Chrome):** Seite öffnen → Menü → „App installieren“

## Unterschiede zur Android-App
| | Web-App | Android-App |
|---|---|---|
| Texterkennung | Tesseract.js (WebAssembly) im Browser, offline nach dem ersten Scan | ML Kit |
| Scan | Auslöser: 3 Bilder, Mehrheitsentscheid | Auslöser oder Automatik |
| Taschenlampe | nur Android-Chrome | ja |
| Vibration | nicht auf iPhones | ja |
| Daten | lokal im Browser (localStorage) | Room-Datenbank |

Formate von Checkliste, QR-Tauschcode und Backup sind identisch. Ein iPhone kann also mit einem Android-Handy per
QR-Code tauschen, und ein Backup lässt sich zwischen beiden Apps umziehen.

> Safari kann Daten von Web-Apps löschen, die einige Wochen nicht geöffnet wurden. Die App erinnert deshalb an den
> Backup-Export (Einstellungen → Bestand exportieren → „In Dateien sichern“).

## Entwicklung
```bash
cd web
npm ci
npm run dev      # Entwicklungsserver (Kamera nur über https oder localhost)
npm test         # Unit-Tests der Logik (portiert aus der Android-App)
npm run build    # Produktions-Build nach dist/
```

`scripts/copy-assets.mjs` kopiert vor jedem Build Folgendes nach `public/`:
- die Checkliste aus `app/src/main/assets/checklist.json`, damit es nur eine Quelle gibt,
- die Tesseract-Dateien für den Offline-Betrieb,
- die Datenschutzerklärung aus `docs/datenschutz.md` als HTML.

## Veröffentlichung
Jeder Push auf `main`, der `web/`, `docs/` oder die Checkliste ändert, baut die App über
`.github/workflows/pages.yml` und veröffentlicht sie auf GitHub Pages.
Einmalig nötig: *Settings → Pages → Build and deployment → Source: **GitHub Actions***.
