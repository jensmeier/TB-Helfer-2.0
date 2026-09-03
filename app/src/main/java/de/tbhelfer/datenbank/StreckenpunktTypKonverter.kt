package de.tbhelfer.datenbank

import androidx.room.TypeConverter

class StreckenpunktTypKonverter {
    @TypeConverter
    fun vonTyp(typ: StreckenpunktTyp): String = typ.name

    @TypeConverter
    fun nachTyp(value: String): StreckenpunktTyp = StreckenpunktTyp.valueOf(value)
}
