package de.tbhelfer.datenbank

import androidx.room.TypeConverter

class BildtypKonverter {
    @TypeConverter
    fun vonBildtyp(typ: Bildtyp): String = typ.name

    @TypeConverter
    fun nachBildtyp(value: String): Bildtyp = Bildtyp.valueOf(value)
}
