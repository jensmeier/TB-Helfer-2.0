package de.tbhelfer.ui.suche

import java.io.File
import java.time.LocalDate

data class BtbDatei(
    val file: File,
    val baustellenName: String,
    val streckennummer: String?,
    val datum: LocalDate
)
