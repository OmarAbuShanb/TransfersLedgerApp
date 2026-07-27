package dev.anonymous.transfers_ledger.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.data.local.db.CustomerSummary
import dev.anonymous.transfers_ledger.databinding.ItemCustomerSummaryBinding
import java.util.Locale

class CustomerSummaryPagingAdapter(
    private val locale: Locale,
    private val onClick: (CustomerSummary) -> Unit
) : PagingDataAdapter<CustomerSummary, CustomerSummaryPagingAdapter.CustomerViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val context = holder.binding.root.context
        holder.binding.nameText.text = item.displayName
        holder.binding.accountsText.text = context.getString(R.string.customer_accounts_count, item.accountCount)
        holder.binding.totalText.text = context.getString(
            R.string.customer_incoming_total,
            TimeUtils.formatMoney(item.incomingTotal, locale),
            context.getString(R.string.currency_symbol)
        )
        holder.binding.card.setOnClickListener { onClick(item) }
    }

    class CustomerViewHolder(val binding: ItemCustomerSummaryBinding) : RecyclerView.ViewHolder(binding.root)

    object Diff : DiffUtil.ItemCallback<CustomerSummary>() {
        override fun areItemsTheSame(oldItem: CustomerSummary, newItem: CustomerSummary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CustomerSummary, newItem: CustomerSummary): Boolean {
            return oldItem == newItem
        }
    }
}
