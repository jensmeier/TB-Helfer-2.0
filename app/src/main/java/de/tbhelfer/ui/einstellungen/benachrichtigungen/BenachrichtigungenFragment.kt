package de.tbhelfer.ui.einstellungen.benachrichtigungen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import de.tbhelfer.R
import de.tbhelfer.einstellungen.SyncBenachrichtigungSpeicher

/**
 * Gerätespezifische Benachrichtigungseinstellung für synchronisierte Baustellendaten.
 * Die Wahl bleibt bewusst lokal und wird nicht mit anderen Handys synchronisiert.
 */
class BenachrichtigungenFragment : Fragment(R.layout.fragment_benachrichtigungen) {

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioTextTon: RadioButton
    private lateinit var radioNurTon: RadioButton
    private lateinit var radioAus: RadioButton
    private lateinit var permissionHint: TextView
    private var ignoreChanges = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!isAdded) return@registerForActivityResult
        if (!granted) {
            // Der Modus bleibt TEXT_TON. Die Ausgabeschicht fällt ohne Android-Freigabe
            // automatisch auf nur Ton zurück und erklärt das hier sichtbar.
            aktualisiereHinweis()
        } else {
            aktualisiereHinweis()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioGroup = view.findViewById(R.id.rgSyncBenachrichtigung)
        radioTextTon = view.findViewById(R.id.rbSyncTextTon)
        radioNurTon = view.findViewById(R.id.rbSyncNurTon)
        radioAus = view.findViewById(R.id.rbSyncAus)
        permissionHint = view.findViewById(R.id.txtSyncNotificationPermissionHint)

        setzeGespeichertenModus(SyncBenachrichtigungSpeicher.laden(requireContext()))
        aktualisiereHinweis()

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (ignoreChanges) return@setOnCheckedChangeListener
            val modus = when (checkedId) {
                R.id.rbSyncNurTon -> SyncBenachrichtigungSpeicher.Modus.NUR_TON
                R.id.rbSyncAus -> SyncBenachrichtigungSpeicher.Modus.AUS
                else -> SyncBenachrichtigungSpeicher.Modus.TEXT_TON
            }
            SyncBenachrichtigungSpeicher.speichern(requireContext(), modus)
            if (modus == SyncBenachrichtigungSpeicher.Modus.TEXT_TON) {
                fordereNotificationPermissionFallsNoetig()
            }
            aktualisiereHinweis()
        }
    }

    private fun setzeGespeichertenModus(modus: SyncBenachrichtigungSpeicher.Modus) {
        ignoreChanges = true
        when (modus) {
            SyncBenachrichtigungSpeicher.Modus.TEXT_TON -> radioTextTon.isChecked = true
            SyncBenachrichtigungSpeicher.Modus.NUR_TON -> radioNurTon.isChecked = true
            SyncBenachrichtigungSpeicher.Modus.AUS -> radioAus.isChecked = true
        }
        ignoreChanges = false
    }

    private fun fordereNotificationPermissionFallsNoetig() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun aktualisiereHinweis() {
        val modus = SyncBenachrichtigungSpeicher.laden(requireContext())
        val androidFreigabeFehlt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        permissionHint.text = when {
            modus == SyncBenachrichtigungSpeicher.Modus.AUS ->
                "Für dieses Gerät sind Baustellen-Benachrichtigungen ausgeschaltet."
            modus == SyncBenachrichtigungSpeicher.Modus.NUR_TON ->
                "Neue Mängel, Abnahmen und BTB-Einträge werden nur akustisch gemeldet."
            androidFreigabeFehlt ->
                "Android erlaubt noch keine sichtbaren Benachrichtigungen. Bis zur Freigabe wird nur ein Ton ausgegeben."
            else ->
                "Neue berechtigte Mängel, Abnahmen und BTB-Einträge werden mit Text und Ton gemeldet."
        }
    }
}
