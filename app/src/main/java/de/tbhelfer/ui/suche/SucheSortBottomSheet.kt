package de.tbhelfer.ui.suche

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SucheSortBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: SucheViewModel by activityViewModels {
        SucheViewModel.Fabrik(
            requireContext().applicationContext,
            ServiceLocator.baustellenRepository,
            ServiceLocator.fotoRepository,
            ServiceLocator.streckenpunkteRepository
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_suche_sort, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupBildtypen)
        val rg = view.findViewById<RadioGroup>(R.id.rgOrder)
        val rbNeueste = view.findViewById<RadioButton>(R.id.rbNeueste)
        val rbAelteste = view.findViewById<RadioButton>(R.id.rbAelteste)
        val btnReset = view.findViewById<Button>(R.id.btnReset)
        val btnApply = view.findViewById<Button>(R.id.btnApply)
        // Chips: definierte Reihenfolge (Foto, Mängel, Abnahme, Schäden, Besonderheiten, Einweisungen, Unterweisungen)
        data class ChipSpec(val label: String, val type: Bildtyp)
        val chipSpecs = listOf(
            ChipSpec("Foto", Bildtyp.FOTODOKUMENTATION),
            ChipSpec("Mängel", Bildtyp.MAENGEL_SCHAEDEN),
            ChipSpec("Abnahme", Bildtyp.ABNAHMEN),
            ChipSpec("Schäden", Bildtyp.MAENGEL_SCHAEDEN),
            ChipSpec("Besonderheiten", Bildtyp.BESONDERHEITEN),
            ChipSpec("Einweisungen", Bildtyp.EINWEISUNG_UNTERWEISUNG),
            ChipSpec("Unterweisungen", Bildtyp.EINWEISUNG_UNTERWEISUNG),
        )

        chipGroup.removeAllViews()
        chipSpecs.forEach { spec ->
            val chip = Chip(requireContext()).apply {
                text = spec.label
                isCheckable = true
                tag = spec.type
            }
            chipGroup.addView(chip)
        }

        fun setUiFromFilter(f: SucheFilter) {
            // Bildtypen (leer = alle)
            val selected = f.bildtypen
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as Chip
                val bt = chip.tag as Bildtyp
                chip.isChecked = selected.isNotEmpty() && bt in selected
            }

            // Sortierung
            if (f.sortDesc) rbNeueste.isChecked = true else rbAelteste.isChecked = true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filter.collectLatest { f ->
                setUiFromFilter(f)
            }
        }

        btnReset.setOnClickListener {
            viewModel.setFilter { it.copy(bildtypen = emptySet(), sortDesc = true) }
            dismiss()
        }

        btnApply.setOnClickListener {
            val selectedTypes = mutableSetOf<Bildtyp>()
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as Chip
                if (chip.isChecked) selectedTypes.add(chip.tag as Bildtyp)
            }

            val sortDesc = rg.checkedRadioButtonId == R.id.rbNeueste

            viewModel.setFilter { it.copy(bildtypen = selectedTypes, sortDesc = sortDesc) }
            dismiss()
        }
    }

    companion object {
        fun newInstance(): SucheSortBottomSheet = SucheSortBottomSheet()
    }
}
