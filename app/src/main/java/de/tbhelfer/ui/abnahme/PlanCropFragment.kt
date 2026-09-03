package de.tbhelfer.ui.abnahme

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R
import de.tbhelfer.util.ImageExifUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PlanCropFragment : Fragment(R.layout.fragment_plan_crop) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val abnahmeId = arguments?.getString(ARG_ABNAHME_ID).orEmpty()
        val originalPath = arguments?.getString(ARG_ORIGINAL_PATH).orEmpty()
        val cropView = view.findViewById<PlanCropView>(R.id.planCropView)
        val btnRotate = view.findViewById<View>(R.id.btnPlanRotate)
        val btnCircle = view.findViewById<View>(R.id.btnPlanCircle)
        val btnArrow = view.findViewById<View>(R.id.btnPlanArrow)
        val btnClear = view.findViewById<View>(R.id.btnPlanMarkClear)
        val markText = view.findViewById<EditText>(R.id.etPlanMarkText)
        val btnCancel = view.findViewById<View>(R.id.btnPlanCancel)
        val btnSave = view.findViewById<View>(R.id.btnPlanSave)

        if (abnahmeId.isBlank() || originalPath.isBlank() || !File(originalPath).exists()) {
            Toast.makeText(requireContext(), "Planausschnitt konnte nicht geöffnet werden", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageExifUtil.decodeSampledBitmapRespectingExif(
                    originalPath,
                    reqW = 2400,
                    reqH = 2400,
                    preferredConfig = Bitmap.Config.ARGB_8888
                )
            }
            if (bitmap == null) {
                Toast.makeText(requireContext(), "Bild konnte nicht gelesen werden", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } else {
                cropView.setSourceBitmap(bitmap)
            }
        }

        btnRotate.setOnClickListener { cropView.rotate90() }
        btnCircle.setOnClickListener {
            cropView.setMarkerMode(PlanCropView.MarkerMode.CIRCLE)
            Toast.makeText(requireContext(), "Kreis im Plan aufziehen", Toast.LENGTH_SHORT).show()
        }
        btnArrow.setOnClickListener {
            cropView.setMarkerMode(PlanCropView.MarkerMode.ARROW)
            Toast.makeText(requireContext(), "Pfeil im Plan aufziehen", Toast.LENGTH_SHORT).show()
        }
        btnClear.setOnClickListener {
            cropView.clearMarker()
            markText.setText("")
        }
        btnCancel.setOnClickListener {
            runCatching { File(originalPath).delete() }
            findNavController().navigateUp()
        }
        btnSave.setOnClickListener {
            btnSave.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                cropView.setAnnotationText(markText.text?.toString().orEmpty())
                val result = cropView.createCroppedBitmap()
                val abnahme = AbnahmeSpeicher.laden(requireContext(), abnahmeId)
                if (result == null || abnahme == null) {
                    btnSave.isEnabled = true
                    Toast.makeText(requireContext(), "Ausschnitt konnte nicht gespeichert werden", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val target = AbnahmeSpeicher.neuePlanAusschnittDatei(requireContext(), abnahme)
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            result.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        result.recycle()
                        AbnahmeSpeicher.anlageHinzufuegen(
                            requireContext(),
                            abnahmeId,
                            AbnahmeSpeicher.Anlage(
                                typ = AbnahmeSpeicher.AnlageTyp.BILD,
                                originalPfad = originalPath,
                                ausschnittPfad = target.absolutePath,
                                bezeichnung = "Planausschnitt / Querprofil"
                            )
                        )
                    }.isSuccess
                }
                if (ok) {
                    Toast.makeText(requireContext(), "Planausschnitt übernommen", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    btnSave.isEnabled = true
                    Toast.makeText(requireContext(), "Fehler beim Speichern", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val ARG_ABNAHME_ID = "abnahmeId"
        const val ARG_ORIGINAL_PATH = "originalPath"
    }
}
