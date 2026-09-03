package de.tbhelfer.datenbank

import androidx.room.TypeConverter

class IndikatorKonverter {

    @TypeConverter
    fun fromIndikator(v: Indikator?): String? = v?.name

    @TypeConverter
    fun toIndikator(v: String?): Indikator? = Indikator.fromString(v)
}
