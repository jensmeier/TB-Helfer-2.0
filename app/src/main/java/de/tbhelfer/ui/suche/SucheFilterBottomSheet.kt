package de.tbhelfer.ui.suche

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SucheFilterBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: SucheViewModel by activityViewModels {
        SucheViewModel.Fabrik(
            requireContext().applicationContext,
            ServiceLocator.baustellenRepository,
            ServiceLocator.fotoRepository,
            ServiceLocator.streckenpunkteRepository
        )
    }

    private val df = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_suche_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spBaustelle = view.findViewById<Spinner>(R.id.spBaustelleSheet)
        val btnTag = view.findViewById<Button>(R.id.btnDatumEinzeln)
        val btnVon = view.findViewById<Button>(R.id.btnDatumVonSheet)
        val btnBis = view.findViewById<Button>(R.id.btnDatumBisSheet)
        val edtVolltext = view.findViewById<EditText>(R.id.edtVolltextSheet)
        val edtStrecke = view.findViewById<EditText>(R.id.edtStreckeSheet)
        val edtKmVon = view.findViewById<EditText>(R.id.edtKmVonSheet)
        val edtKmBis = view.findViewById<EditText>(R.id.edtKmBisSheet)
        val cbNurBeiSp = view.findViewById<CheckBox>(R.id.cbNurBeiStreckenpunktenSheet)
        val edtSpText = view.findViewById<EditText>(R.id.edtStreckenpunktTextSheet)

        val btnReset = view.findViewById<Button>(R.id.btnResetSheet)
        val btnApply = view.findViewById<Button>(R.id.btnApplySheet)

        fun parseKm(text: String?): Double? {
            val t = text?.trim().orEmpty()
            if (t.isBlank()) return null
            return t.replace(',', '.').toDoubleOrNull()
        }

        fun setDateButtons(filter: SucheFilter) {
            val von = filter.datumVonEpochMs
            val bis = filter.datumBisEpochMs
            btnVon.text = if (von != null) df.format(von) else "von"
            btnBis.text = if (bis != null) df.format(bis) else "bis"
        }

        fun openPicker(currentEpoch: Long?, onPicked: (Long) -> Unit) {
            val cal = Calendar.getInstance()
            if (currentEpoch != null) cal.timeInMillis = currentEpoch
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    val picked = Calendar.getInstance().apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, d)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onPicked(picked)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Baustellen Spinner füllen
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.baustellen.collectLatest { list ->
                val items = mutableListOf<Pair<Long?, String>>()
                items += null to "Alle Baustellen"
                items += list.map { it.id to it.baustellenname }

                spBaustelle.adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, items.map { it.second }) {}
                val current = viewModel.currentFilter().baustelleId
                val idx = items.indexOfFirst { it.first == current }.takeIf { it >= 0 } ?: 0
                spBaustelle.setSelection(idx, false)
            }
        }

        // UI initialisieren aus Filter
        fun applyFilterToUi(f: SucheFilter) {
            edtVolltext.setText(f.volltext.orEmpty())
            edtStrecke.setText(f.streckenNrQuery.orEmpty())
            edtKmVon.setText(f.kmVon?.toString() ?: "")
            edtKmBis.setText(f.kmBis?.toString() ?: "")
            cbNurBeiSp.isChecked = f.nurBeiStreckenpunkten
            edtSpText.setText(f.streckenpunktText.orEmpty())

            setDateButtons(f)
        }

        applyFilterToUi(viewModel.currentFilter())

        btnTag.setOnClickListener {
            openPicker(viewModel.currentFilter().datumVonEpochMs) { dayStart ->
                val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
                val start = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                viewModel.setFilter { it.copy(datumVonEpochMs = start, datumBisEpochMs = end) }
                applyFilterToUi(viewModel.currentFilter())
            }
        }

        btnVon.setOnClickListener {
            openPicker(viewModel.currentFilter().datumVonEpochMs) { epoch ->
                viewModel.setFilter { it.copy(datumVonEpochMs = epoch) }
                setDateButtons(viewModel.currentFilter())
            }
        }
        btnBis.setOnClickListener {
            openPicker(viewModel.currentFilter().datumBisEpochMs) { epoch ->
                // Ende des Tages
                val cal = Calendar.getInstance().apply { timeInMillis = epoch }
                val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                viewModel.setFilter { it.copy(datumBisEpochMs = end) }
                setDateButtons(viewModel.currentFilter())
            }
        }

        btnReset.setOnClickListener {
            viewModel.setFilter { SucheFilter() }
            applyFilterToUi(viewModel.currentFilter())
        }

        btnApply.setOnClickListener {
            val selectedBaustelleId = runCatching {
                val pos = spBaustelle.selectedItemPosition
                // adapter stores list only as strings; resolve through current baustellen list:
                val list = viewModel.baustellen.value
                when {
                    pos <= 0 -> null
                    else -> list.getOrNull(pos - 1)?.id
                }
            }.getOrNull()

            viewModel.setFilter { f ->
                f.copy(
                    baustelleId = selectedBaustelleId,
                    volltext = edtVolltext.text?.toString()?.trim().takeIf { !it.isNullOrBlank() },
                    streckenNrQuery = edtStrecke.text?.toString()?.trim().takeIf { !it.isNullOrBlank() },
                    kmVon = parseKm(edtKmVon.text?.toString()),
                    kmBis = parseKm(edtKmBis.text?.toString()),
                    nurBeiStreckenpunkten = cbNurBeiSp.isChecked,
                    streckenpunktText = edtSpText.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                )
            }

            dismiss()
        }
    }

    companion object {
        fun newInstance(): SucheFilterBottomSheet = SucheFilterBottomSheet()
    }
}
