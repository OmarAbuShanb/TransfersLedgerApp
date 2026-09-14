package dev.anonymous.transfers_ledger.ui.adapters

import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.View
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.core.PaymentSources
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.databinding.ItemTransactionBinding
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import java.util.Locale

object TransactionViewBinder {
    fun bind(
        binding: ItemTransactionBinding,
        item: TransactionWithCustomer,
        previousItem: TransactionWithCustomer?,
        locale: Locale,
        onMenuClick: (View, TransactionWithCustomer) -> Unit,
        onItemClick: ((TransactionWithCustomer) -> Unit)? = null
    ) {
        val transaction = item.transaction
        val previousHeader = previousItem?.transaction?.timestamp?.let { TimeUtils.getDayHeader(it, locale) }
        val currentHeader = TimeUtils.getDayHeader(transaction.timestamp, locale)
        binding.headerText.visibility = if (currentHeader != previousHeader) View.VISIBLE else View.GONE
        binding.headerText.text = currentHeader

        binding.nameText.text = item.displayName
        binding.metaText.text = if (item.customerDisplayName.isNullOrBlank()) {
            transaction.walletSource
        } else {
            "${transaction.walletSource} | ${transaction.senderName}"
        }
        binding.dateText.text = TimeUtils.getFullDateTimeArabic(transaction.timestamp, locale)

        val context = binding.root.context
        val isOutgoing = transaction.direction == TransactionDirection.OUTGOING
        val signedAmount = "${if (isOutgoing) "-" else "+"}${TimeUtils.formatMoney(transaction.amount, locale)}"
        binding.amountText.text = "$signedAmount ${context.getString(R.string.currency_symbol)}"
        
        if (transaction.excluded) {
            binding.directionText.text = context.getString(R.string.excluded_label)
            binding.directionText.setBackgroundResource(R.drawable.bg_excluded)
            binding.directionText.setTextColor(context.getColor(R.color.text_secondary))
            binding.amountText.paintFlags = binding.amountText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.amountText.setTextColor(context.getColor(R.color.text_secondary))
            binding.card.alpha = 0.6f
        } else {
            binding.amountText.paintFlags = binding.amountText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            binding.amountText.setTextColor(context.getColor(if (isOutgoing) R.color.outgoing else R.color.incoming))
            binding.directionText.text = context.getString(if (isOutgoing) R.string.outgoing_label else R.string.incoming_label)
            binding.directionText.setTextColor(context.getColor(if (isOutgoing) R.color.outgoing else R.color.incoming))
            binding.directionText.setBackgroundResource(if (isOutgoing) R.drawable.bg_outgoing else R.drawable.bg_incoming)
            binding.card.alpha = 1.0f
        }

        binding.sourceBadge.text = PaymentSources.badgeText(transaction.walletSource)
        binding.sourceBadge.background = badgeBackground(context.getColor(PaymentSources.badgeColorRes(transaction.walletSource)))
        binding.menuButton.setOnClickListener { onMenuClick(it, item) }
        binding.card.setOnClickListener { onItemClick?.invoke(item) }
    }

    private fun badgeBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }
}
