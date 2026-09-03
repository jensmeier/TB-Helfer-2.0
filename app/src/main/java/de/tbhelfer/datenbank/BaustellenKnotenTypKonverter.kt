package de.tbhelfer.datenbank

import androidx.room.TypeConverter

class BaustellenKnotenTypKonverter {

    @TypeConverter
    fun vonString(wert: String?): BaustellenKnotenTyp? {
        return wert?.let { BaustellenKnotenTyp.valueOf(it) }
    }

    @TypeConverter
    fun zuString(typ: BaustellenKnotenTyp?): String? {
        return typ?.name
    }
}
