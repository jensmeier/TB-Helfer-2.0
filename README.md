# TB Helfer – Neustart (übersichtlich, deutsch, Fragments + Navigation)

## Ordnerstruktur (damit es nicht unübersichtlich wird)
- `de.tbhelfer.ui` → UI (Activity + Fragments)
- `de.tbhelfer.datenbank` → Room (Entities/DAO/Datenbank)
- `de.tbhelfer.einstellungen` → DataStore/Preferences
- später zusätzlich (wenn wir soweit sind):
  - `de.tbhelfer.kamera`
  - `de.tbhelfer.export`
  - `de.tbhelfer.suche`
  - `de.tbhelfer.widget`

## Startmenü-Style
`app/src/main/res/layout/fragment_startmenue.xml` ist **1:1** dein hochgeladenes Layout (Farben/Style bleiben gleich).

## Wichtig: Gradle/AGP Versionen
- Android Gradle Plugin (AGP): 9.0.0
- Gradle (Wrapper): 9.1.0
- Kotlin: **built-in Kotlin** über AGP 9 (kein `org.jetbrains.kotlin.android` Plugin mehr nötig)

Hinweis: Im Build-Output sollten keine Warnungen mehr auftauchen zu:
`android.builtInKotlin`, `android.newDsl`, `android.enableJetifier` oder dem
`org.jetbrains.kotlin.android` Plugin.
