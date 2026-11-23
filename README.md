# Unlock

Unlock ist eine schlanke, native Android-App (Kotlin + Jetpack Compose, Material 3 Expressive) zum angenehmen Lesen archivierter Webseiten auf dem Smartphone.

Teilst du aus einer beliebigen App (Chrome, Reddit, Feedly, X …) einen Link mit **Unlock**, bereinigt die App die URL, ruft die archivierte Version über **archive.today** (archive.ph/.is/.today/.md/.li) oder die **Wayback Machine** ab und zeigt den Artikel direkt in einer aufgeräumten, mobilen **Leseansicht** an – wahlweise auch im Custom Tab oder Standardbrowser.

- **Integrierter Editorial-Lesemodus:** Automatische Artikel-Extraktion auch für feste Desktop-Snapshots von `archive.today`, mit 4 Farbschemata (*System*, *Hell*, *Sepia*, *Dunkel*), *Serif*/*Sans-Serif*, stufenloser Schriftgröße, Lesefortschritt und 1-Tipp-Wechsel zur Original-Ansicht
- **Nahtloses Captcha-Handling:** Falls ein Archivdienst eine Sicherheitsabfrage oder „No results“ zeigt, öffnet sich automatisch die interaktive WebView; danach springt Unlock selbstständig in die Leseansicht
- **Datenschutz:** Kein Analytics, kein Tracking, keine Werbung; optionaler Verlauf bleibt ausschließlich lokal auf dem Gerät. `INTERNET` wird ausschließlich zum Laden der archivierten Seiten im Lesemodus genutzt
- **Ultraleicht:** Release-APK ≈ **1,7 MB** (R8 + Resource-Shrinking, keine Drittanbieter-Netzwerk- oder Bild-Libraries)
- **Modernes Android:** Material 3 Expressive, Dynamic Color, Dark Mode, Edge-to-Edge, Predictive Back, Deutsch & Englisch (inkl. App-Sprache ab Android 13)

## Installation (APK)

1. Die aktuelle `Unlock-v*.apk` unter **Releases** herunterladen.
2. Auf dem Android-Gerät öffnen und ggf. einmalig *„Installation aus dieser Quelle zulassen“* aktivieren.
3. Tipp für automatische Updates: Mit [Obtainium](https://obtainium.imranr.dev/) lässt sich dieses Repository direkt verfolgen.

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

Alternativ können in CI/CD die Umgebungsvariablen `UNLOCK_KEYSTORE_PATH`, `UNLOCK_KEYSTORE_PASSWORD`, `UNLOCK_KEY_ALIAS` und `UNLOCK_KEY_PASSWORD` gesetzt werden. Ohne Konfiguration fällt der Release-Build auf den lokalen Debug-Key zurück.

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
├── settings/   Settings, SettingsRepository (DataStore), SettingsViewModel, ui/
├── shortcuts/  ShortcutPublisher
└── ui/theme/   Theme (MaterialExpressiveTheme, Dynamic Color + Fallback)
app/src/test/   UrlExtractor-, UrlCleaner-, TargetUrlBuilder-, ArchiveUrls-,
                LinkProcessor-, ArticleParser- & ShareActivity-Tests
```

## Hinweise zu Archivdiensten

- archive.today (archive.ph/.is/.today/.md/.li) und die Wayback Machine sind **unabhängige Fremdangebote**. Unlock steht in keiner Verbindung zu ihnen.
- archive.today zeigt gelegentlich ein **Captcha** („One more step“) oder einzelne Domains sind zeitweise nicht erreichbar – im Lesemodus lässt sich mit einem Tipp die Domain wechseln oder zur Wayback Machine springen.

## Lizenz

[MIT](LICENSE)
