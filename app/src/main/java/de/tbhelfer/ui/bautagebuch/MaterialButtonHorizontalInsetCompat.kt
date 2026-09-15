package de.tbhelfer.ui.bautagebuch

import com.google.android.material.button.MaterialButton

/**
 * Kompatibilitaet fuer die aktuell verwendete Material-Components-Version.
 *
 * `MaterialButton` stellt hier keine Kotlin-Properties `insetLeft`/`insetRight`
 * bereit. Der kompakte Foto-Button aus Android 200042 braucht dort lediglich
 * keinen zusaetzlichen horizontalen Innenabstand. Deshalb werden die beiden
 * Zuweisungen auf normales View-Padding abgebildet.
 */
var MaterialButton.insetLeft: Int
    get() = paddingLeft
    set(value) {
        setPadding(value, paddingTop, paddingRight, paddingBottom)
    }

var MaterialButton.insetRight: Int
    get() = paddingRight
    set(value) {
        setPadding(paddingLeft, paddingTop, value, paddingBottom)
    }
