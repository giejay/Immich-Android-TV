package nl.giejay.android.tv.immich.assets

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nl.giejay.android.tv.immich.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateJumpDialogFragment : DialogFragment() {
    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault(Locale.Category.FORMAT))
    private val options = mutableListOf<DateJumpOption>()
    private lateinit var adapter: MonthYearAdapter
    private var nextMonthToAdd: YearMonth = YearMonth.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        options.add(DateJumpOption(clearFilter = true))
        appendMoreMonths(5)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_date_jump, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.month_year_list)
        adapter = MonthYearAdapter(
            options = options,
            formatter = dateFormatter,
            onOptionClick = onOptionClick@{ option ->
                if (option.clearFilter) {
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,
                        bundleOf(RESULT_CLEAR to true)
                    )
                } else {
                    val selectedMonth = option.yearMonth ?: return@onOptionClick
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,
                        bundleOf(
                            RESULT_CLEAR to false,
                            RESULT_YEAR to selectedMonth.year,
                            RESULT_MONTH to selectedMonth.monthValue
                        )
                    )
                }
                dismiss()
            },
            onLastItemFocused = {
                appendMoreMonths(5)
                adapter.notifyItemRangeInserted(options.size - 5, 5)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
        return dialog
    }

    private fun appendMoreMonths(amount: Int) {
        repeat(amount) {
            options.add(DateJumpOption(yearMonth = nextMonthToAdd))
            nextMonthToAdd = nextMonthToAdd.minusMonths(1)
        }
    }

    data class DateJumpOption(
        val yearMonth: YearMonth? = null,
        val clearFilter: Boolean = false
    )

    class MonthYearAdapter(
        private val options: List<DateJumpOption>,
        private val formatter: DateTimeFormatter,
        private val onOptionClick: (DateJumpOption) -> Unit,
        private val onLastItemFocused: () -> Unit
    ) : RecyclerView.Adapter<MonthYearAdapter.MonthYearViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthYearViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_month_year, parent, false)
            return MonthYearViewHolder(view)
        }

        override fun getItemCount(): Int = options.size

        override fun onBindViewHolder(holder: MonthYearViewHolder, position: Int) {
            val option = options[position]
            val text = if (option.clearFilter) {
                holder.itemView.context.getString(R.string.date_jump_clear_filter)
            } else {
                option.yearMonth?.format(formatter).orEmpty()
            }
            holder.label.text = text
            holder.itemView.setOnClickListener { onOptionClick(option) }
            holder.itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus && position == itemCount - 1 && !option.clearFilter) {
                    onLastItemFocused()
                }
            }
        }

        class MonthYearViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val label: TextView = view.findViewById(R.id.month_year_label)
        }
    }

    companion object {
        const val REQUEST_KEY = "date_jump_request"
        const val RESULT_CLEAR = "result_clear"
        const val RESULT_YEAR = "result_year"
        const val RESULT_MONTH = "result_month"
    }
}
