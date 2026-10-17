# Skylanders: automatische Spielprofile

Stand: 17. Oktober 2026. Dieses Dokument hält die auf dem Samsung SM-T970
(Snapdragon 865+/`kona`) mit Spyro's Adventure bestätigte Konfiguration fest.
Änderungen an Swap Force oder Trap Team erst nach einem eigenen Spieltest.

## Automatische Anwendung

Die Konsole ruft `applyConsoleGraphics()` in `ConsoleGraphics.kt` unmittelbar vor
dem Start eines bekannten Skylanders-Spiels auf. `auto_graphics` ist bei einer
frischen Installation standardmäßig `true`. Das gilt auch für neue Konten und
lokale Profile auf dem Gerät. Bei ausgeschalteter Automatik bleiben die
manuellen Einstellungen des Nutzers aktiv.

Die App wählt zuerst einen vorsichtigen Plan anhand des Geräts: Grafikbackend,
Shader-Modus und interne Auflösung. Auf dem SM-T970 erkennt sie Qualcomm/kona,
nutzt OpenGL und 2× Auflösung. Schwächere oder unbekannte Geräte erhalten
konservativere Werte. Eine identische Bildrate auf jedem Gerät ist damit nicht
garantiert; das bestätigte Ergebnis gilt zunächst für das SM-T970.

## Spyro's Adventure: bestätigtes Profil auf SM-T970

| Einstellung | Wert | Grund |
|---|---:|---|
| Backend | OpenGL | stabiler Qualcomm-Pfad dieses Builds |
| Interne Auflösung | 2× | gute Grafik; 1× hatte das Zeitlupenproblem nicht beseitigt |
| `ShaderCompilationMode` | 0 | synchrone spezialisierte Shader; kein asynchroner Adreno-Treiberpfad |
| `ShaderCache` | `True` | bereits gesehene Grafikeffekte wiederverwenden |
| `WaitForShadersBeforeStarting` | `True` auf OpenGL | bekannte Effekte vor dem Spielstart laden |
| `ShaderCompilerThreads` / `ShaderPrecompilerThreads` | 0 / 0 auf OpenGL | Kompilierung auf dem sicheren Hauptpfad |
| `VISkip` | `True` | Spiel und Ton laufen normal schnell, wenn die Emulation kurz zurückliegt |
| `EFBScaledCopy` | `True` | Bildqualität der 2× Darstellung erhalten |
| MSAA / SSAA | 1 / `False` | zusätzliche GPU-Last vermeiden |

Verlauf: 1× Auflösung allein machte das Bild schlechter und beseitigte die
Zeitlupe nicht. 2× plus `VISkip` brachte eine deutliche Verbesserung, ließ aber
kleine Hänger bei Dialogen und Interaktionssymbolen. Das Vorladen bekannter
Shader beseitigte diese Hänger laut Spieltest des Nutzers; der Start blieb
schnell. Der Nutzer meldete anschließend: „es läuft perfekt“.

Das Profil wird beim Start von Spyro's Adventure erneut geschrieben. Es ist
nicht an ein bestimmtes Konto gebunden. Neue Grafikeffekte können auf einem
frischen Gerät beim allerersten Auftreten weiterhin kompiliert werden; die
Bestätigung oben stammt von einem Tablet, auf dem das Spiel zuvor lief.

## Andere Spiele

Giants behält `VISkip=False`, `EFBScaledCopy=False` und den bisherigen
Geräteplan. Spyros Werte dürfen nicht pauschal für Giants übernommen werden.
Swap Force und Trap Team verwenden bisher den allgemeinen Geräteplan. Für beide
separat Bild, Ton, schnelle Bewegung, Dialoge und Portalwechsel auf Tablet und
Handy prüfen und nur dann ein eigenes Spielprofil ergänzen.

Relevanter Code: `Source/Android/app/src/main/java/org/dolphinemu/dolphinemu/features/skylanders/console/ConsoleGraphics.kt`
und `ConsoleActivity.kt` im gleichen Verzeichnis.
