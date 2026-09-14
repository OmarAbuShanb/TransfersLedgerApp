package dev.anonymous.transfers_ledger.ui.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.transfers_ledger.core.PaymentSources
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.data.local.db.UnprocessedNotificationEntity
import dev.anonymous.transfers_ledger.databinding.ItemUnprocessedNotificationBinding
import java.net.URLEncoder
import java.util.Locale

class UnprocessedNotificationAdapter(
    private val locale: Locale
) : PagingDataAdapter<UnprocessedNotificationEntity, UnprocessedNotificationAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUnprocessedNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            holder.bind(item)
        }
    }

    inner class ViewHolder(
        private val binding: ItemUnprocessedNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UnprocessedNotificationEntity) {
            val appName = when (item.packageName) {
                PaymentSources.PALPAY_PACKAGE -> "PalPay"
                PaymentSources.JAWWAL_PAY_PACKAGE -> "JawwalPay"
                PaymentSources.BANK_OF_PALESTINE_PACKAGE -> "بنك فلسطين"
                PaymentSources.QUDS_BANK_PACKAGE -> "بنك القدس"
                PaymentSources.ARAB_ISLAMIC_BANK_PACKAGE -> "البنك العربي الإسلامي"
                PaymentSources.PIB_PACKAGE -> "البنك الاستثماري"
                else -> item.packageName
            }
            binding.packageBadge.text = appName
            binding.timeText.text = TimeUtils.getFullDateTimeArabic(item.timestamp, locale)
            binding.titleText.text = item.title.ifBlank { "(بدون عنوان)" }
            binding.contentText.text = item.text

            binding.sendButton.setOnClickListener {
                val context = binding.root.context
                val message = """
                    إشعار جديد غير معالج:
                    التطبيق: $appName
                    الحزمة: ${item.packageName}
                    العنوان: ${item.title}
                    النص: ${item.text}
                """.trimIndent()
                val encoded = URLEncoder.encode(message, "UTF-8")
                val url = "https://wa.me/970597152714?text=$encoded"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<UnprocessedNotificationEntity>() {
        override fun areItemsTheSame(
            oldItem: UnprocessedNotificationEntity,
            newItem: UnprocessedNotificationEntity
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: UnprocessedNotificationEntity,
            newItem: UnprocessedNotificationEntity
        ): Boolean = oldItem == newItem
    }
}
