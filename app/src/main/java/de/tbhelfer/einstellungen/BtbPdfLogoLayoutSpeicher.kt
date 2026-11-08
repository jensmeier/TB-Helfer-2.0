package de.tbhelfer.einstellungen

import android.content.Context

/**
 * Firmenweite Darstellung des zentralen Firmenlogos im BTB/PDF.
 *
 * Die Werte werden lokal gecacht, damit PDFs auch offline exakt gleich erzeugt werden.
 * Sobald Server-Schema 12 aktiv ist, wird derselbe Datensatz ueber CompanyPdfLogoApi
 * firmenweit geladen/gespeichert.
 */
object BtbPdfLogoLayoutSpeicher {
    data class Einstellungen(
        val logoWidthPt: Int = 90,
        val offsetXPt: Int = 0,
        val offsetYPt: Int = 0,
        val showCompanyName: Boolean = true,
        val showCompanyEmail: Boolean = true,
        val showCompanyPhone: Boolean = true,
        val contactsInImage: Boolean = false,
        val serverVersion: Long = 0L
    )

    private const val PREFS = "tbhelfer_btb_pdf_logo_layout"
    private const val WIDTH = "logo_width_pt"
    private const val X = "offset_x_pt"
    private const val Y = "offset_y_pt"
    private const val SHOW_NAME = "show_company_name"
    private const val SHOW_EMAIL = "show_company_email"
    private const val SHOW_PHONE = "show_company_phone"
    private const val CONTACTS_IN_IMAGE = "contacts_in_image"
    private const val SERVER_VERSION = "server_version"

    fun defaults(): Einstellungen = Einstellungen()

    fun laden(context: Context): Einstellungen {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return normalize(
            Einstellungen(
                logoWidthPt = p.getInt(WIDTH, 90),
                offsetXPt = p.getInt(X, 0),
                offsetYPt = p.getInt(Y, 0),
                showCompanyName = p.getBoolean(SHOW_NAME, true),
                showCompanyEmail = p.getBoolean(SHOW_EMAIL, true),
                showCompanyPhone = p.getBoolean(SHOW_PHONE, true),
                contactsInImage = p.getBoolean(CONTACTS_IN_IMAGE, false),
                serverVersion = p.getLong(SERVER_VERSION, 0L)
            )
        )
    }

    fun speichern(context: Context, cfg: Einstellungen) {
        val n = normalize(cfg)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(WIDTH, n.logoWidthPt)
            .putInt(X, n.offsetXPt)
            .putInt(Y, n.offsetYPt)
            .putBoolean(SHOW_NAME, n.showCompanyName)
            .putBoolean(SHOW_EMAIL, n.showCompanyEmail)
            .putBoolean(SHOW_PHONE, n.showCompanyPhone)
            .putBoolean(CONTACTS_IN_IMAGE, n.contactsInImage)
            .putLong(SERVER_VERSION, n.serverVersion.coerceAtLeast(0L))
            .apply()
    }

    fun zuruecksetzen(context: Context) {
        speichern(context, defaults())
    }

    fun normalize(cfg: Einstellungen): Einstellungen = cfg.copy(
        // 200042: Der rechte PDF-Kopf bietet deutlich mehr Platz als die fruehere 180-pt-Grenze.
        // Der Renderer begrenzt das Logo zusaetzlich auf den tatsaechlich verfuegbaren Kopfbereich.
        logoWidthPt = cfg.logoWidthPt.coerceIn(40, 400),
        offsetXPt = cfg.offsetXPt.coerceIn(-120, 120),
        offsetYPt = cfg.offsetYPt.coerceIn(-60, 180),
        serverVersion = cfg.serverVersion.coerceAtLeast(0L)
    )
}
