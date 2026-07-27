package dev.anonymous.transfers_ledger.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.databinding.ItemCustomerAccountBinding

data class CustomerAccount(
    val senderName: String,
    val normalizedSender: String,
    val walletSource: String,
    val transactionCount: Int
)

class CustomerAccountAdapter(
    private val onUnlinkClick: (CustomerAccount) -> Unit
) : ListAdapter<CustomerAccount, CustomerAccountAdapter.AccountViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemCustomerAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountViewHolder(private val binding: ItemCustomerAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(account: CustomerAccount) {
            binding.nameText.text = account.senderName
            binding.sourceText.text = account.walletSource
            binding.unlinkButton.setOnClickListener { onUnlinkClick(account) }
        }
    }

    object Diff : DiffUtil.ItemCallback<CustomerAccount>() {
        override fun areItemsTheSame(oldItem: CustomerAccount, newItem: CustomerAccount): Boolean {
            return oldItem.normalizedSender == newItem.normalizedSender && oldItem.walletSource == newItem.walletSource
        }

        override fun areContentsTheSame(oldItem: CustomerAccount, newItem: CustomerAccount): Boolean {
            return oldItem == newItem
        }
    }
}
