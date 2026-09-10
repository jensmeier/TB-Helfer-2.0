# Android-Warnungen – Aufräumliste

Stand: 2026-09-10

Quelle: lokaler Android-Studio-Build der Android-200019-Fassung am 2026-09-10 um ca. 12:38.

Der Build war erfolgreich (`BUILD SUCCESSFUL in 1m 24s`, 69 actionable tasks: 18 executed, 51 up-to-date). Es bestanden 15 Compiler-Warnungen. Diese Warnungen blockieren den aktuellen Funktionstest nicht, sollen aber vor dem späteren Merge von PR #5 systematisch bereinigt werden. Bei API-Deprecations nicht blind ersetzen: Verhalten auf den vorgesehenen Android-Versionen/Pixel/Poco muss erhalten bleiben.

## Erfasste Warnungen

1. `KeyboardAutoScroll.kt`
   - `SOFT_INPUT_ADJUST_RESIZE` ist deprecated.

2. `BaustelleNeuFragment.kt`
   - unchecked cast von `SpinnerAdapter` zu `ArrayAdapter<String>`.

3. `BaustellenKnotenFragment.kt`
   - Coroutine-API benötigt Opt-in (`ExperimentalCoroutinesApi`).

4. `BautagebuchFragment.kt`
   - `getFromLocation(...)` ist deprecated.
   - `SOFT_INPUT_ADJUST_RESIZE` ist deprecated.
   - eine Bedingung wird vom Compiler als immer `false` erkannt.

5. `BtbExportPaket.kt`
   - `getLatLong(FloatArray)` ist deprecated.

6. `DkwKreuzungSchemaView.kt`
   - `scaledDensity` ist an zwei Stellen deprecated.

7. `ImportExportFragment.kt`
   - `statusBarColor` ist deprecated.
   - `navigationBarColor` ist deprecated.

8. `FotoErfassenFragment.kt`
   - `get(String): Any?` ist deprecated.
   - `SOFT_INPUT_ADJUST_RESIZE` ist deprecated.

9. `OverlayRenderer.kt`
   - `scaledDensity` ist deprecated.

10. `CurrentSiteWidgetProvider.kt`
    - redundantes `else` in einem vollständigen `when`.

## Bereinigungsreihenfolge

1. Zuerst echte Logik-Warnungen: immer-false-Bedingung, unchecked cast, Coroutine-Opt-in, redundantes `else`.
2. Danach API-Deprecations mit kompatibler Ersatzimplementierung: Geocoder, EXIF-GPS, DisplayMetrics, Insets/Soft-Input, Status-/Navigation-Bar.
3. Nach jeder Gruppe lokal `Make Project` und relevante Bildschirmfunktion auf Pixel/Poco prüfen.
4. Keine reine Warnungsunterdrückung (`@Suppress`) verwenden, wenn eine saubere kompatible Lösung möglich ist.

Diese Aufräumliste ändert keine produktive NAS-/Serverkonfiguration.