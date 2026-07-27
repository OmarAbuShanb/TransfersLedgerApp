package dev.anonymous.transfers_ledger.ui.adapters

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.databinding.ItemMainHeaderBinding
import dev.anonymous.transfers_ledger.databinding.ViewStatCardBinding
import dev.anonymous.transfers_ledger.domain.model.AppStatus
import dev.anonymous.transfers_ledger.domain.model.WalletStats
import java.util.Locale

class MainHeaderAdapter(
    private val locale: Locale,
    private val onBatteryClick: () -> Unit,
    private val onPermissionClick: () -> Unit,
    private val onSummaryPeriodClick: (View) -> Unit,
    private val onListFilterClick: (View) -> Unit
) : RecyclerView.Adapter<MainHeaderAdapter.HeaderViewHolder>() {

    private var state = AppStatus()

    fun submitState(newState: AppStatus) {
        if (state == newState) return
        state = newState
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemMainHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(state)
    }

    override fun getItemCount(): Int = 1

    inner class HeaderViewHolder(private val binding: ItemMainHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(state: AppStatus) {
            val context = binding.root.context
            binding.summaryTitle.text = state.summaryTitle.ifBlank {
                context.getString(R.string.daily_total)
            }
            binding.listTitle.text = state.listTitle.ifBlank {
                context.getString(R.string.all_transactions_title)
            }
            setAlertVisible(binding.batteryAlert, state.isReady && !state.batteryOptimizationIgnored)
            setAlertVisible(binding.permissionAlert, state.isReady && !state.listenerConnected)
            binding.batteryButton.setOnClickListener { onBatteryClick() }
            binding.permissionButton.setOnClickListener { onPermissionClick() }
            binding.summaryPeriodButton.setOnClickListener { onSummaryPeriodClick(it) }
            binding.listFilterButton.setOnClickListener { onListFilterClick(it) }

            bindStat(
                context = context,
                card = binding.totalCard,
                title = context.getString(R.string.wallet_total_title),
                stats = state.stats.total,
                backgroundColor = context.getColor(R.color.primary),
                titleColor = context.getColor(android.R.color.white),
                incomingColor = context.getColor(android.R.color.white),
                outgoingColor = context.getColor(android.R.color.white)
            )
            bindStat(
                context = context,
                card = binding.palpayCard,
                title = context.getString(R.string.palpay_label),
                stats = state.stats.palpay,
                backgroundColor = context.getColor(R.color.primary_soft),
                titleColor = context.getColor(R.color.primary),
                incomingColor = context.getColor(R.color.jawwal_green),
                outgoingColor = context.getColor(R.color.outgoing)
            )
            bindStat(
                context = context,
                card = binding.jawwalCard,
                title = context.getString(R.string.jawwalpay_label),
                stats = state.stats.jawwalPay,
                backgroundColor = context.getColor(R.color.jawwal_soft),
                titleColor = context.getColor(R.color.jawwal_green),
                incomingColor = context.getColor(R.color.jawwal_green),
                outgoingColor = context.getColor(R.color.outgoing)
            )
            bindStat(
                context = context,
                card = binding.bopCard,
                title = context.getString(R.string.bop_label),
                stats = state.stats.bankOfPalestine,
                backgroundColor = context.getColor(R.color.bop_soft),
                titleColor = context.getColor(R.color.bop_blue),
                incomingColor = context.getColor(R.color.jawwal_green),
                outgoingColor = context.getColor(R.color.outgoing)
            )
        }

        private fun setAlertVisible(view: View, visible: Boolean) {
            val targetVisibility = if (visible) View.VISIBLE else View.GONE
            if (view.visibility == targetVisibility) return

            if (binding.root.isLaidOut) {
                TransitionManager.beginDelayedTransition(
                    binding.root as ViewGroup,
                    AutoTransition().apply { duration = 220L }
                )
            }
            view.visibility = targetVisibility
            if (visible) {
                val startAnimation = {
                    view.alpha = 0f
                    view.translationY = -12f
                    view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180L)
                        .start()
                }
                if (binding.root.isLaidOut) startAnimation() else view.post { startAnimation() }
            }
        }
    }

    private fun bindStat(
        context: Context,
        card: ViewStatCardBinding,
        title: String,
        stats: WalletStats,
        backgroundColor: Int,
        titleColor: Int,
        incomingColor: Int,
        outgoingColor: Int
    ) {
        card.root.background = GradientDrawable().apply {
            cornerRadius = context.resources.displayMetrics.density * 14
            setColor(backgroundColor)
        }
        card.cardTitle.text = title
        card.cardTitle.setTextColor(titleColor)
        card.incomingAmount.setTextColor(incomingColor)
        card.outgoingAmount.setTextColor(outgoingColor)

        // Set labels for incoming and outgoing
        card.incomingLabel.text = context.getString(R.string.incoming_label)
        card.outgoingLabel.text = context.getString(R.string.outgoing_label)

        // Format amounts
        val symbol = context.getString(R.string.currency_symbol)
        val incomingFormatted = TimeUtils.formatMoney(stats.incoming, locale)
        val outgoingFormatted = TimeUtils.formatMoney(stats.outgoing, locale)

        // Set formatted amounts
        card.incomingAmount.text = "$incomingFormatted $symbol"
        card.outgoingAmount.text = "$outgoingFormatted $symbol"
    }
}
