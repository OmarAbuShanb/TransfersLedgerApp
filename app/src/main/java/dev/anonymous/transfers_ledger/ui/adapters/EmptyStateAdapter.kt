package dev.anonymous.transfers_ledger.ui.adapters

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.R

class EmptyStateAdapter(private val message: String) :
    RecyclerView.Adapter<EmptyStateAdapter.ViewHolder>() {
    private var visible: Boolean = false

    fun updateVisible(isVisible: Boolean) {
        if (visible != isVisible) {
            visible = isVisible
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_empty_state, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = message
    }

    override fun getItemCount(): Int = if (visible) 1 else 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: android.widget.TextView = view.findViewById(R.id.emptyText)
    }
}
