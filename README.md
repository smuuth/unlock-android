# Unlock

Unlock ist eine sehr leichte Android-App, die vor allem im **Teilen-Menü** lebt.
Teilst du aus einer beliebigen App (Chrome, Reddit, Feedly, X …) einen Link mit „Unlock“,
leitet die App ihn an einen Archivdienst weiter (Standard: **archive.today / archive.ph**)
und öffnet dort sofort die archivierte Version im Custom Tab.

- Kein Internet-Zugriff der App selbst (keine `INTERNET`-Permission), kein Analytics, keine Werbung
- Release-APK ≈ 1,6 MB (R8 + Resource-Shrinking)
- Material 3 Expressive, Dynamic Color, Dark Mode, Edge-to-Edge, Predictive Back
- Deutsch und Englisch (inkl. App-Sprache pro App ab Android 13)

## Build

Voraussetzungen: JDK 17+, Android SDK mit Platform **37.2** (`platforms;android-37.2`).

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # nicht eingecheckt

./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest     # JUnit + Robolectric
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk (R8)
```

Der Release-Build wird der Einfachheit halber mit dem Debug-Key signiert. Für eine
Veröffentlichung in `app/build.gradle.kts` eine eigene `signingConfig` hinterlegen.

| Komponente | Version |
|---|---|
| AGP / Gradle | 9.5.2 / 9.9.2 (Kotlin in AGP integriert) |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.11.01 (material3 1.5.0 mit Expressive-APIs) |
| compileSdk / targetSdk / minSdk | 37.2 / 37 / 26 |
| DataStore / Browser | 1.3.0 / 1.10.0 |

## Funktionsweise

1. **Share-Flow** – `share/ShareActivity` nimmt `ACTION_SEND` (`text/plain`) und
   `ACTION_PROCESS_TEXT` (Link markieren → „Unlock“) entgegen. Sie hat kein Fenster
   (`Theme.NoDisplay`, `noHistory`, `excludeFromRecents`, eigene `taskAffinity`),
   verarbeitet den Intent, startet das Ziel und ruft sofort `finish()` auf. Kein Compose
   in diesem Pfad. `ACTION_VIEW` für http/https ist bewusst **nicht** registriert.
2. **URL-Extraktion** (`UrlExtractor`) – erste http(s)-URL aus Texten wie
   „Titel https://…“, Satzzeichen am Ende werden entfernt, ausgeglichene Klammern bleiben
   erhalten (Wikipedia). Fallback: `EXTRA_SUBJECT`, dann nackte `www.`-Adressen.
   Kein Link → Toast „Kein Link gefunden“.
3. **Bereinigung** (`UrlCleaner`, abschaltbar) – entfernt Tracking-Parameter (`utm_*`,
   `fbclid`, `gclid`, `mc_cid`, `mc_eid`, `igshid`, `ref`, `si` …), löst Redirects offline
   auf (Google `/url?q=`, Facebook `l.php`, YouTube `/redirect`, DuckDuckGo …) und entpackt
   AMP-Caches (`google.com/amp/s/…`, `cdn.ampproject.org`). Optional zusätzlich:
   `amp.`/`m.`-Subdomains und `/amp`-Pfade normalisieren.
   Bereits archivierte Links werden erkannt (`ArchiveUrls`) und nicht doppelt verpackt.
4. **Ziel-URL** (`archive/TargetUrlBuilder`, Dienste als `enum ArchiveTarget` mit URL-Template):

   | Ziel | Template |
   |---|---|
   | archive.today – Neueste | `https://{domain}/newest/{url}` |
   | archive.today – Neu archivieren | `https://{domain}/submit/?url={encodedUrl}` |
   | archive.today – Alle Snapshots | `https://{domain}/{url}` |
   | Wayback Machine | `https://web.archive.org/web/{url}` |

   `{domain}` ist wählbar: archive.ph / .is / .today / .md / .li.
   Neue Dienste = ein weiterer Enum-Eintrag.
5. **Öffnen** (`LinkOpener`) – Custom Tab mit Dynamic-Color-Toolbar, Teilen-Button und
   „Im Browser öffnen“; alternativ (Einstellung) Standardbrowser per `ACTION_VIEW`, ebenso
   als Fallback ohne Custom-Tabs-Anbieter. Beim Öffnen der Einstellungen wird der Browser per
   `CustomTabsClient.connectAndInitialize` vorgewärmt.

**Performance:** Die Einstellungen liegen in Jetpack DataStore. Beim Prozessstart füllt
`UnlockApp` einen In-Memory-Cache; nur wenn der Share-Intent schneller ist, wird einmalig per
`runBlocking { first() }` gelesen. Im Emulator vergehen vom Antippen im Share-Sheet bis zum
Start des Custom Tabs etwa 60 ms.

### Direct Share & Shortcuts

- Pro Archivziel wird ein **Sharing Shortcut** (`ShortcutManagerCompat`, Kategorie aus
  `res/xml/shortcuts.xml`) veröffentlicht – z. B. „archive.ph – neueste“, „Wayback Machine“.
  So wählst du den Dienst direkt im Share-Sheet, ohne die Einstellungen zu ändern.
- Long-Press aufs App-Icon: **„Link aus Zwischenablage öffnen“** (plus je Dienst).
  Da Android ≥ 10 die Zwischenablage nur mit Fensterfokus freigibt, läuft das über die
  unsichtbare `ClipboardActivity`.

### Einstellungen

Eine einzige Compose-Oberfläche (`settings/ui/SettingsScreen.kt`): Erklärung mit animierter
Expressive-Shape, Testfeld mit Vorschau der Ziel-URL und großem Shape-Morphing-Button,
Dienst/Modus als Connected Button Group, Domain-Auswahl, Schalter (Bereinigung,
Desktop-Seiten, Custom Tab, Verlauf) sowie ein optionaler Verlauf der letzten 20 Links.
Der Verlauf ist **standardmäßig aus**, bleibt nur auf dem Gerät und wird beim Abschalten
sofort gelöscht.

## Projektstruktur

```
app/src/main/java/dev/unlock/
├── UnlockApp.kt, MainActivity.kt
├── share/      ShareActivity, ClipboardActivity, LinkProcessor, LinkOpener,
│               UrlExtractor, UrlCleaner
├── archive/    ArchiveTarget/ArchiveDomain, TargetUrlBuilder, ArchiveUrls
├── settings/   Settings, SettingsRepository (DataStore), SettingsViewModel, ui/
├── shortcuts/  ShortcutPublisher
└── ui/theme/   Theme (MaterialExpressiveTheme, Dynamic Color + Fallback)
app/src/test/   UrlExtractor-, UrlCleaner-, TargetUrlBuilder-, ArchiveUrls-,
                LinkProcessor-Tests + ShareActivityTest (Robolectric)
```

`UrlExtractor`, `UrlCleaner`, `TargetUrlBuilder`, `ArchiveUrls` und `LinkProcessor` sind
reines Kotlin ohne Android-Abhängigkeiten.

## Hinweise zu archive.today

- archive.today (archive.ph/.is/.today/.md/.li) und die Wayback Machine sind **unabhängige
  Fremdangebote**. Unlock steht in keiner Verbindung zu ihnen.
- archive.today zeigt häufig ein **Captcha** („One more step“), besonders bei
  „Neu archivieren“ oder über VPN/Cloudflare-DNS. Das liegt am Dienst, nicht an der App.
- Die Domains sind **zeitweise nicht erreichbar** oder werden gesperrt – dann in den
  Einstellungen eine andere Domain wählen.
- „Neueste“ zeigt „No results“, wenn es noch keinen Snapshot gibt; dort lässt sich direkt
  „archive this url“ wählen (oder Modus „Neu archivieren“).
