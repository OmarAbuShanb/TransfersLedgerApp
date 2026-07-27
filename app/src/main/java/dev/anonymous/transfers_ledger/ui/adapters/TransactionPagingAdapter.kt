package dev.anonymous.transfers_ledger.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.databinding.ItemTransactionBinding
import java.util.Locale

class TransactionPagingAdapter(
    private val locale: Locale,
    private val onMenuClick: (View, TransactionWithCustomer) -> Unit,
    private val onItemClick: ((TransactionWithCustomer) -> Unit)? = null
) : PagingDataAdapter<TransactionWithCustomer, TransactionPagingAdapter.TransactionViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val previous = if (position > 0) snapshot().items.getOrNull(position - 1) ?: item else null
        TransactionViewBinder.bind(holder.binding, item, previous, locale, onMenuClick, onItemClick)
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
