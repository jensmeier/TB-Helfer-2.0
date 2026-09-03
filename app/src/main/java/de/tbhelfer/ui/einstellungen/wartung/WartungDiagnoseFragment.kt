package de.tbhelfer.ui.einstellungen.wartung

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.work.WorkManager
import de.tbhelfer.R
import de.tbhelfer.util.StorageCleanupScheduler
import de.tbhelfer.util.StorageCleanupWorker

class WartungDiagnoseFragment : Fragment(R.layout.fragment_wartung_diagnose) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        zeigeInstallierteVersion(view)

        view.findViewById<View>(R.id.btnWartungGeraeteDiagnose).setOnClickListener {
            findNavController().navigate(R.id.action_wartung_diagnose_to_geraete_diagnose)
        }

        view.findViewById<View>(R.id.btnWartungSpeicherBereinigen).setOnClickListener {
            val req = StorageCleanupScheduler.runOnce(requireContext().applicationContext)
            Toast.makeText(
                requireContext(),
                getString(R.string.ui_fragment_einstellungen_storage_cleanup_running),
                Toast.LENGTH_SHORT
            ).show()

            WorkManager.getInstance(requireContext().applicationContext)
                .getWorkInfoByIdLiveData(req.id)
                .observe(viewLifecycleOwner) { info ->
                    if (info != null && info.state.isFinished) {
                        val out = info.outputData
                        val dbDeleted = out.getInt(StorageCleanupWorker.KEY_DB_DELETED, 0)
                        val moved = out.getInt(StorageCleanupWorker.KEY_ORPHAN_MOVED, 0)
                        val thumbs = out.getInt(StorageCleanupWorker.KEY_THUMB_DELETED, 0)

                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.ui_fragment_einstellungen_storage_cleanup_done,
                                dbDeleted,
                                moved,
                                thumbs
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun zeigeInstallierteVersion(view: View) {
        val context = requireContext()
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val versionName = packageInfo.versionName ?: "?"
        val teststand = (versionCode % 1000L).toString().padStart(3, '0')
        view.findViewById<TextView>(R.id.txtWartungVersion).text =
            "TB-Helfer · Version $versionName ($teststand)"
    }
}
