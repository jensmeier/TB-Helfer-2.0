package de.tbhelfer.ui.bautagebuch

import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Minimaler Kalender-Dialog ohne externe Dependencies.
 * - Heute: grün
 * - ±rangeDays: gelb
 * - außerhalb: rot + ✕ (disabled)
 */
object BtbRangeCalendarDialog {

    private enum class State { EMPTY, TODAY, ALLOWED, LOCKED }

    private data class Cell(val date: LocalDate?, val state: State)

    fun show(
        fragment: Fragment,
        initialDate: LocalDate,
        rangeDays: Long = 7,
        onPicked: (LocalDate) -> Unit
    ) {
        val ctx = fragment.requireContext()
        val view = fragment.layoutInflater.inflate(R.layout.dialog_btb_calendar, null)

        val tvMonth = view.findViewById<TextView>(R.id.tvMonth)
        val rvDays = view.findViewById<RecyclerView>(R.id.rvDays)
        val btnPrev = view.findViewById<MaterialButton>(R.id.btnPrevMonth)
        val btnNext = view.findViewById<MaterialButton>(R.id.btnNextMonth)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnOk = view.findViewById<MaterialButton>(R.id.btnOk)

        val today = LocalDate.now()
        val min = today.minusDays(rangeDays)
        val max = today.plusDays(rangeDays)

        var month = YearMonth.from(initialDate)
        var selected = initialDate.coerceIn(min, max)

        fun monthTitle(ym: YearMonth): String {
            val m = ym.month.getDisplayName(TextStyle.FULL, Locale.GERMANY)
            val mm = m.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }
            return "$mm ${ym.year}"
        }

        fun buildCells(ym: YearMonth): List<Cell> {
            val first = ym.atDay(1)
            val daysInMonth = ym.lengthOfMonth()

            // Woche beginnt Montag
            val offset = ((first.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7
            val total = 42

            val out = ArrayList<Cell>(total)
            for (i in 0 until total) {
                val dayNum = i - offset + 1
                if (dayNum < 1 || dayNum > daysInMonth) {
                    out.add(Cell(null, State.EMPTY))
                } else {
                    val d = ym.atDay(dayNum)
                    val state = when {
                        d == today -> State.TODAY
                        d in min..max -> State.ALLOWED
                        else -> State.LOCKED
                    }
                    out.add(Cell(d, state))
                }
            }
            return out
        }

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            private var cells: List<Cell> = emptyList()

            fun submit(list: List<Cell>) {
                cells = list
                notifyDataSetChanged()
            }

            override fun getItemCount(): Int = cells.size

            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = android.view.LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_btb_calendar_day, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val cell = cells[position]
                val card = holder.itemView.findViewById<MaterialCardView>(R.id.cardDay)
                val tv = holder.itemView.findViewById<TextView>(R.id.tvDay)

                if (cell.date == null) {
                    card.visibility = View.INVISIBLE
                    card.isClickable = false
                    return
                }
                card.visibility = View.VISIBLE

                val d = cell.date
                val isSelected = (d == selected)

                when (cell.state) {
                    State.TODAY -> {
                        card.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
                        tv.setTextColor(Color.BLACK)
                        tv.text = d.dayOfMonth.toString()
                        card.isClickable = true
                    }
                    State.ALLOWED -> {
                        card.setCardBackgroundColor(Color.parseColor("#FFF9C4"))
                        tv.setTextColor(Color.BLACK)
                        tv.text = d.dayOfMonth.toString()
                        card.isClickable = true
                    }
                    State.LOCKED -> {
                        card.setCardBackgroundColor(Color.parseColor("#FFCDD2"))
                        tv.setTextColor(Color.BLACK)
                        tv.text = "${d.dayOfMonth}\n✕"
                        card.isClickable = false
                    }
                    else -> {}
                }

                if (isSelected) {
                    card.strokeWidth = dp(ctx, 2)
                    card.setStrokeColor(Color.parseColor("#1976D2"))
                } else {
                    card.strokeWidth = 0
                }

                card.setOnClickListener {
                    if (cell.state == State.LOCKED) return@setOnClickListener
                    selected = d
                    notifyDataSetChanged()
                }
            }
        }

        rvDays.layoutManager = GridLayoutManager(ctx, 7)
        rvDays.adapter = adapter

        fun refresh() {
            tvMonth.text = monthTitle(month)
            adapter.submit(buildCells(month))
        }

        refresh()

        val dlg = MaterialAlertDialogBuilder(ctx)
            .setView(view)
            .create()

        btnPrev.setOnClickListener {
            month = month.minusMonths(1)
            refresh()
        }
        btnNext.setOnClickListener {
            month = month.plusMonths(1)
            refresh()
        }
        btnCancel.setOnClickListener { dlg.dismiss() }
        btnOk.setOnClickListener {
            dlg.dismiss()
            onPicked(selected)
        }

        dlg.show()
    }

    private fun dp(ctx: android.content.Context, v: Int): Int {
        val d = ctx.resources.displayMetrics.density
        return (v * d).toInt()
    }

    private fun LocalDate.coerceIn(min: LocalDate, max: LocalDate): LocalDate = when {
        this.isBefore(min) -> min
        this.isAfter(max) -> max
        else -> this
    }
}