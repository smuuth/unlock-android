# Unlock

Unlock ist eine schlanke, native Android-App (Kotlin + Jetpack Compose, Material 3 Expressive) zum angenehmen Lesen archivierter Webseiten auf dem Smartphone.

Teilst du aus einer beliebigen App (Chrome, Reddit, Feedly, X …) einen Link mit **Unlock**, bereinigt die App die URL, ruft die archivierte Version über **archive.today** (archive.ph/.is/.today/.md/.li) oder die **Wayback Machine** ab und zeigt den Artikel direkt in einer aufgeräumten, mobilen **Leseansicht** an – wahlweise auch im In-App-Browser (Custom Tab) oder Standardbrowser.

- **Integrierter Editorial-Lesemodus:** Automatische Artikel-Extraktion auch für feste Desktop-Snapshots von `archive.today`, mit 4 Farbschemata (*System*, *Hell*, *Sepia*, *Dunkel*), *Serif*/*Sans-Serif*, stufenloser Schriftgröße, Lesefortschritt und 1-Tipp-Wechsel zur Original-Ansicht
- **Nahtloses Captcha-Handling:** Falls ein Archivdienst eine Sicherheitsabfrage oder „No results“ zeigt, öffnet sich automatisch die interaktive WebView; danach springt Unlock selbstständig in die Leseansicht
- **Datenschutz:** Keine Werbung, kein personenbezogenes Tracking, niemals Übermittlung gelesener Links oder Artikelinhalte; optionaler Verlauf bleibt lokal. Optionale, rein anonyme Nutzungsstatistiken (TelemetryDeck, ohne SDK) lassen sich jederzeit in den Einstellungen abschalten und sind nur aktiv, wenn eine `telemetryAppId` konfiguriert ist
- **Selbst-Update ohne Play Store:** Beim Start fragt Unlock höchstens etwa einmal am Tag bei der GitHub-API (`releases/latest` dieses Repos) nach einer neuen Version – ohne persönliche Daten, offline oder bei Fehlern passiert lautlos nichts. Gibt es eine, erscheint „Update verfügbar – jetzt installieren“; ein Tipp lädt die APK, prüft Paket, Version und Signatur und öffnet den Android-Installer. Manuell: *Einstellungen → Nach Updates suchen*
- **Ultraleicht:** Release-APK ≈ **1,7 MB** (R8 + Resource-Shrinking, keine Drittanbieter-Netzwerk- oder Bild-Libraries)
- **Einfach bedienbar:** Ein einziger Eintrag im Teilen-Menü, eine schlanke Startseite (Link einfügen, Statistik, Verlauf) und wenige verständliche Einstellungen – Archivdienst, Domain und Browser-Details liegen eingeklappt unter *Erweitert*
- **Modernes Android:** Material 3 Expressive, Dynamic Color, Dark Mode, Edge-to-Edge, Predictive Back, Deutsch & Englisch (inkl. App-Sprache ab Android 13)

## Installation (APK)

1. Auf dem Android-Handy diesen Link öffnen: **<https://github.com/smuuth/unlock-android/releases/latest/download/Unlock.apk>** (immer die neueste Version).
2. Die heruntergeladene Datei antippen und ggf. einmalig *„Installation aus dieser Quelle zulassen“* aktivieren.
3. Fertig – künftige Updates meldet die App selbst („Update verfügbar – jetzt installieren“). Beim ersten Update fragt Android einmalig, ob Unlock Apps installieren darf.

> Von **1.1.0** (noch ohne Update-Funktion) einmalig wie oben 1.2.0 installieren – einfach drüber, Einstellungen bleiben erhalten.

## Build

Voraussetzungen: JDK 17+, Android SDK mit Platform **37.2** (`platforms;android-37.2`).

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # nicht eingecheckt

./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest     # JUnit + Robolectric
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk (R8)
```

### Release-Signing

Für signierte Release-Builds eine lokale (nicht eingecheckte) `keystore.properties` im Projektroot anlegen:

```properties
storeFile=/pfad/zu/unlock-release.jks
storePassword=...
keyAlias=unlock
keyPassword=...
```

Alternativ können in CI/CD die Umgebungsvariablen `UNLOCK_KEYSTORE_PATH`, `UNLOCK_KEYSTORE_PASSWORD`, `UNLOCK_KEY_ALIAS` und `UNLOCK_KEY_PASSWORD` gesetzt werden. Ohne Konfiguration fällt ein lokaler Release-Build auf den Debug-Key zurück; mit `UNLOCK_REQUIRE_RELEASE_SIGNING=true` (so im Release-Workflow) bricht der Build stattdessen ab.

> **Wichtig:** Alle Updates müssen mit demselben Release-Schlüssel signiert sein (Zertifikat-SHA-256 `92e6ae96…7cadee`), sonst lassen sie sich nicht über die installierte App installieren. Keystore und Passwörter sicher aufbewahren.

### Release veröffentlichen

1. In `app/build.gradle.kts` `versionCode` um 1 erhöhen und `versionName` setzen (z. B. `1.3.0`), per PR nach `main` mergen.
2. Tag pushen: `git tag v1.3.0 && git push origin v1.3.0`.
3. Der Workflow *Release* prüft Secrets und Tag ↔ `versionName`, baut und testet, verifiziert die Signatur gegen den erwarteten Fingerprint und veröffentlicht `Unlock.apk` als GitHub-Release. Spätestens am nächsten Tag zeigen die installierten Apps den Update-Hinweis.

Benötigte Repository-Secrets: `UNLOCK_KEYSTORE_BASE64` (`base64 -i unlock-release.jks`), `UNLOCK_KEYSTORE_PASSWORD`, `UNLOCK_KEY_ALIAS`, `UNLOCK_KEY_PASSWORD`, `UNLOCK_TELEMETRY_APP_ID`. Tags mit Bindestrich (`v1.3.0-beta.1`) werden als Vorabversion veröffentlicht und der App nicht angeboten.

Für optionale anonyme Nutzungsstatistiken über [TelemetryDeck](https://telemetrydeck.com/) einfach `telemetryAppId=<deine-app-id>` in `keystore.properties` (oder `UNLOCK_TELEMETRY_APP_ID` als Umgebungsvariable/GitHub-Secret) eintragen. Ohne gesetzte ID sendet die App keinerlei Telemetrie.

| Komponente | Version |
|---|---|
| AGP / Gradle | 9.5.2 / 9.9.2 (Kotlin in AGP integriert) |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.11.01 (material3 1.5.0 mit Expressive-APIs) |
| compileSdk / targetSdk / minSdk | 37.2 / 37 / 26 |
| DataStore / Browser | 1.3.0 / 1.10.0 |

## Projektstruktur

```
app/src/main/java/dev/unlock/
├── UnlockApp.kt, MainActivity.kt
├── reader/     ReaderActivity, ReaderScreen, ArticleExtractorScript,
│               ArticleParser, ArticleModels, ArticleImageLoader, ReaderThemePalette
├── share/      ShareActivity, ClipboardActivity, LinkProcessor, LinkOpener,
│               UrlExtractor, UrlCleaner
├── archive/    ArchiveTarget/ArchiveDomain, TargetUrlBuilder, ArchiveUrls
├── settings/   Settings, SettingsRepository (DataStore), SettingsViewModel, SettingsActivity, ui/ (Home, Einstellungen, Update-Hinweis)
├── update/     UpdateManager (GitHub Releases, Download, PackageInstaller), ReleaseParser/AppVersion, UpdateInstallReceiver
└── ui/theme/   Theme (MaterialExpressiveTheme, Dynamic Color + Fallback)
app/src/test/   UrlExtractor-, UrlCleaner-, TargetUrlBuilder-, ArchiveUrls-,
                LinkProcessor-, ArticleParser-, ShareActivity-, ReleaseParser- & AppVersion-Tests
```

## Hinweise zu Archivdiensten

- archive.today (archive.ph/.is/.today/.md/.li) und die Wayback Machine sind **unabhängige Fremdangebote**. Unlock steht in keiner Verbindung zu ihnen.
- archive.today zeigt gelegentlich ein **Captcha** („One more step“) oder einzelne Domains sind zeitweise nicht erreichbar – im Lesemodus lässt sich mit einem Tipp die Domain wechseln oder zur Wayback Machine springen.

## Lizenz

[MIT](LICENSE)
