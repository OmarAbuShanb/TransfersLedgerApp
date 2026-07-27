package dev.anonymous.transfers_ledger.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.databinding.ItemTransactionBinding
import java.util.Locale

class TransactionListAdapter(
    private val locale: Locale,
    private val onMenuClick: (View, TransactionWithCustomer) -> Unit,
    private val onItemClick: ((TransactionWithCustomer) -> Unit)? = null
) : ListAdapter<TransactionWithCustomer, TransactionListAdapter.TransactionViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val previous = if (position > 0) getItem(position - 1) else null
        TransactionViewBinder.bind(holder.binding, getItem(position), previous, locale, onMenuClick, onItemClick)
    }

    override fun onCurrentListChanged(
        previousList: MutableList<TransactionWithCustomer>,
        currentList: MutableList<TransactionWithCustomer>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        if (previousList.isNotEmpty() && currentList.isNotEmpty()) {
            notifyDataSetChanged()
        }
    }

    class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    object Diff : DiffUtil.ItemCallback<TransactionWithCustomer>() {
        override fun areItemsTheSame(oldItem: TransactionWithCustomer, newItem: TransactionWithCustomer): Boolean {
            return oldItem.transaction.id == newItem.transaction.id
        }

        override fun areContentsTheSame(oldItem: TransactionWithCustomer, newItem: TransactionWithCustomer): Boolean {
            return oldItem == newItem
        }
    }
}
