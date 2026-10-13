# TB Helfer – Release Shrinker (R8) safe rules
#
# Ziel: Release-Build klein machen, ohne dass Navigation / Fragments / Widgets / Room
# durch Reflection- oder Manifest-Referenzen wegoptimiert werden.

# --- Android components used via manifest / navigation graphs ---
-keep class ** extends androidx.fragment.app.Fragment { *; }
-keep class ** extends androidx.appcompat.app.AppCompatActivity { *; }
-keep class ** extends android.appwidget.AppWidgetProvider { *; }
-keep class ** extends android.content.BroadcastReceiver { *; }

# ViewModels are often looked up via reflection (SavedStateHandle etc.)
-keep class ** extends androidx.lifecycle.ViewModel { *; }

# --- Room ---
# Keep Room annotations metadata + generated adapters safe
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# (Optional) keep Kotlin metadata (usually not needed, but harmless)
-keep class kotlin.Metadata { *; }


# --- PdfBox-Android (200058) ---
# JPX decoding is optional in PdfBox-Android and is not used by TB-Helfer for URI annotations.
# R8 otherwise reports the optional Gemalto decoder as a missing class in release builds.
-dontwarn com.gemalto.jp2.**
