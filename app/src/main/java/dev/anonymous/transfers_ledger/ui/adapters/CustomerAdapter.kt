package dev.anonymous.transfers_ledger.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.data.local.db.CustomerEntity
import dev.anonymous.transfers_ledger.databinding.ItemCustomerBinding

class CustomerAdapter(
    private val onCustomerClick: (CustomerEntity) -> Unit
) : ListAdapter<CustomerEntity, CustomerAdapter.CustomerViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CustomerViewHolder(private val binding: ItemCustomerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: CustomerEntity) {
            binding.nameText.text = customer.displayName
            binding.root.setOnClickListener { onCustomerClick(customer) }
        }
    }

    object Diff : DiffUtil.ItemCallback<CustomerEntity>() {
        override fun areItemsTheSame(oldItem: CustomerEntity, newItem: CustomerEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CustomerEntity, newItem: CustomerEntity): Boolean {
            return oldItem == newItem
        }
    }
}
