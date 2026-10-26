package de.tbhelfer.ui.einstellungen.wartung

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import de.tbhelfer.R
import de.tbhelfer.server.AppUpdateManager
import de.tbhelfer.util.StorageCleanupScheduler
import de.tbhelfer.util.StorageCleanupWorker
import kotlinx.coroutines.launch

class WartungDiagnoseFragment : Fragment(R.layout.fragment_wartung_diagnose) {

    private var updateCandidate: AppUpdateManager.CheckResult.Available? = null
    private lateinit var updateStatus: TextView
    private lateinit var updateNotes: TextView
    private lateinit var updateInstallButton: MaterialButton
    private var updateDefaultTextColor: Int = Color.BLACK

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnWartungHandybestand).setOnClickListener {
            startActivity(android.content.Intent(requireContext(), de.tbhelfer.server.recovery.RecoveryActivity::class.java))
        }

        view.findViewById<View>(R.id.btnWartungGeraeteDiagnose).setOnClickListener {
            findNavController().navigate(R.id.action_wartung_diagnose_to_geraete_diagnose)
        }

        view.findViewById<View>(R.id.btnWartungFehlerprotokoll).setOnClickListener {
            de.tbhelfer.ui.einstellungen.diagnose.ErrorLogDialog.show(this)
        }

        updateStatus = view.findViewById(R.id.txtWartungUpdateStatus)
        updateDefaultTextColor = updateStatus.currentTextColor
        updateNotes = view.findViewById(R.id.txtWartungUpdateNotes)
        updateInstallButton = view.findViewById(R.id.btnWartungUpdateInstallieren)
        updateStatus.text = "Aktuell: ${AppUpdateManager.currentVersionLabel()}\nNoch nicht geprüft."
        updateNotes.visibility = View.GONE
        updateInstallButton.visibility = View.GONE

        view.findViewById<View>(R.id.btnWartungNachUpdates).setOnClickListener {
            pruefeAufUpdate()
        }
        updateInstallButton.setOnClickListener {
            updateCandidate?.let { starteUpdate(it) }
        }

        val storageButton = view.findViewById<View>(R.id.btnWartungSpeicherBereinigen)
        val storageStatus = view.findViewById<TextView>(R.id.txtWartungSpeicherStatus)
        storageButton.setOnClickListener {
            storageButton.isEnabled = false
            storageStatus.text = "Fotospeicher wird geprüft … Es werden keine Daten verändert."
            val req = StorageCleanupScheduler.runOnce(requireContext().applicationContext)
            WorkManager.getInstance(requireContext().applicationContext)
                .getWorkInfoByIdLiveData(req.id)
                .observe(viewLifecycleOwner) { info ->
                    if (info != null && info.state.isFinished) {
                        storageButton.isEnabled = true
                        storageStatus.text = if (info.state == WorkInfo.State.SUCCEEDED) {
                            val out = info.outputData
                            "Prüfung abgeschlossen.\n" +
                                "Dateien nicht gefunden: ${out.getInt(StorageCleanupWorker.KEY_MISSING_DB_ENTRIES, 0)}\n" +
                                "Bilder ohne Foto-Datenbankzuordnung: ${out.getInt(StorageCleanupWorker.KEY_ORPHAN_CANDIDATES, 0)}\n" +
                                "Vorschaubilder ohne Originaldatei: ${out.getInt(StorageCleanupWorker.KEY_THUMB_CANDIDATES, 0)}\n" +
                                "Dies sind Prüfhinweise, keine Löschfreigabe. Nichts gelöscht oder verschoben."
                        } else {
                            "Speicherprüfung nicht abgeschlossen. Keine Daten gelöscht oder verschoben."
                        }
                    }
                }
        }
    }

    private fun pruefeAufUpdate() {
        val logContext = requireContext().applicationContext
        updateStatus.setTextColor(updateDefaultTextColor)
        updateStatus.text = "Aktuell: ${AppUpdateManager.currentVersionLabel()}\nSuche nach Updates …"
        updateNotes.visibility = View.GONE
        updateInstallButton.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = AppUpdateManager.check()) {
                is AppUpdateManager.CheckResult.Current -> {
                    updateCandidate = null
                    updateStatus.setTextColor(Color.rgb(35, 120, 70))
                    updateStatus.text = "Aktuell: ${AppUpdateManager.currentVersionLabel()}\n✓ TB-Helfer ist aktuell."
                }

                is AppUpdateManager.CheckResult.Available -> {
                    updateCandidate = result
                    val release = result.release
                    val accent = if (result.required) Color.rgb(211, 47, 47) else Color.rgb(47, 128, 237)
                    updateStatus.setTextColor(accent)
                    updateStatus.text = buildString {
                        append("Aktuell: ${AppUpdateManager.currentVersionLabel()}\n")
                        append("Neu: TB-Helfer ${release.versionName} (${release.buildNumber})")
                        if (result.required) append("\nWichtiges Update")
                    }
                    updateNotes.text = "Was ist neu?\n" +
                        (release.releaseNotes?.takeIf { it.isNotBlank() } ?: "Keine zusätzlichen Hinweise.")
                    updateNotes.visibility = View.VISIBLE
                    updateInstallButton.backgroundTintList = ColorStateList.valueOf(accent)
                    updateInstallButton.visibility = View.VISIBLE
                }

                is AppUpdateManager.CheckResult.Error -> {
                    de.tbhelfer.server.ErrorLogStore.record(logContext, de.tbhelfer.server.ErrorHistory.Operation.UPDATE, result.message)
                    updateCandidate = null
                    updateStatus.setTextColor(Color.rgb(180, 60, 40))
                    updateStatus.text =
                        "Aktuell: ${AppUpdateManager.currentVersionLabel()}\nUpdateprüfung fehlgeschlagen: ${result.message}"
                }
            }
        }
    }

    private fun starteUpdate(candidate: AppUpdateManager.CheckResult.Available) {
        val logContext = requireContext().applicationContext
        if (!AppUpdateManager.ensureInstallPermission(requireActivity())) {
            Toast.makeText(
                requireContext(),
                "Bitte „Apps aus dieser Quelle installieren“ erlauben und danach erneut auf Aktualisieren tippen.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        updateInstallButton.isEnabled = false
        updateInstallButton.text = "Wird geladen …"
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = AppUpdateManager.downloadApk(requireContext().applicationContext, candidate.release)) {
                is AppUpdateManager.DownloadResult.Success -> {
                    if (isAdded) AppUpdateManager.launchInstaller(requireActivity(), result.file)
                }

                is AppUpdateManager.DownloadResult.Error -> {
                    de.tbhelfer.server.ErrorLogStore.record(logContext, de.tbhelfer.server.ErrorHistory.Operation.UPDATE, result.message)
                    if (isAdded) Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
            if (isAdded) {
                updateInstallButton.isEnabled = true
                updateInstallButton.text = "Aktualisieren"
            }
        }
    }
}


