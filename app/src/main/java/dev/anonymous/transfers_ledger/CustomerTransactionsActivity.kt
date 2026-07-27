package dev.anonymous.transfers_ledger

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.core.TransactionStatsCalculator
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.databinding.ActivityCustomerTransactionsBinding
import dev.anonymous.transfers_ledger.databinding.ViewStatCardBinding
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import dev.anonymous.transfers_ledger.domain.model.WalletStats
import dev.anonymous.transfers_ledger.ui.adapters.CustomerAccount
import dev.anonymous.transfers_ledger.ui.adapters.CustomerAccountAdapter
import dev.anonymous.transfers_ledger.ui.adapters.TransactionListAdapter
import dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu
import dev.anonymous.transfers_ledger.ui.common.AppDialogs

class CustomerTransactionsActivity : ComponentActivity() {
    private lateinit var binding: ActivityCustomerTransactionsBinding
    private lateinit var adapter: TransactionListAdapter
    private lateinit var accountsAdapter: CustomerAccountAdapter
    private val repository by lazy { (application as PalPayApplication).repository }
    private var sender: String = ""

    // Reactive customer ID. Initialized from the Intent extra. When the user creates
    // a customer while viewing sender-only transactions, we update this flow and the
    // collection below switches to the customer query automatically — no finish/restart.
    private val customerIdFlow = MutableStateFlow(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customerIdFlow.value = intent.getLongExtra(EXTRA_CUSTOMER_ID, -1L)
        sender = intent.getStringExtra(EXTRA_SENDER).orEmpty()

        // Title and button visibility reflect the current customerId.
        val initialTitle = intent.getStringExtra(EXTRA_TITLE).takeUnless { it.isNullOrBlank() }
            ?: getString(R.string.customer_title)
        binding.titleText.text = initialTitle
        updateHeaderButtons(customerIdFlow.value)

        accountsAdapter = CustomerAccountAdapter(::confirmUnlinkAccount)
        binding.accountsRecycler.layoutManager = LinearLayoutManager(this)
        binding.accountsRecycler.itemAnimator = null
        binding.accountsRecycler.adapter = accountsAdapter

        adapter = TransactionListAdapter(TimeUtils.getLocale(resources.configuration), ::showTransactionMenu)
        binding.transactionsRecycler.layoutManager = LinearLayoutManager(this)
        binding.transactionsRecycler.itemAnimator = null
        binding.transactionsRecycler.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        binding.editButton.setOnClickListener { showEditNameDialog() }
        binding.createCustomerButton.setOnClickListener { showCreateCustomerDialog() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // flatMapLatest re-subscribes whenever customerIdFlow emits a new value.
                // This is how the screen switches from the sender query to the customer
                // query after createCustomer is called, without any finish/restart.
                customerIdFlow
                    .flatMapLatest { id ->
                        if (id > 0L) {
                            repository.getTransactionsForCustomer(id)
                        } else {
                            repository.getTransactionsForSender(sender)
                        }
                    }
                    .map { items -> items to TransactionStatsCalculator.calculate(items.map { it.transaction }) }
                    .collect { (items, stats) ->
                        val customerId = customerIdFlow.value
                        adapter.submitList(items)
                        accountsAdapter.submitList(buildAccounts(items))
                        val showAccounts = customerId > 0L && items.isNotEmpty()
                        binding.accountsTitle.visibility = if (showAccounts) View.VISIBLE else View.GONE
                        binding.accountsRecycler.visibility = if (showAccounts) View.VISIBLE else View.GONE
                        bindStat(binding.statsCard, binding.titleText.text.toString(), stats.total)
                    }
            }
        }
    }

    private fun updateHeaderButtons(customerId: Long) {
        val hasCustomer = customerId > 0L
        binding.editButton.visibility = if (hasCustomer) View.VISIBLE else View.GONE
        binding.createCustomerButton.visibility = if (hasCustomer) View.GONE else View.VISIBLE
    }

    private fun buildAccounts(items: List<TransactionWithCustomer>): List<CustomerAccount> {
        return items
            .groupBy { it.transaction.normalizedSender to it.transaction.walletSource }
            .map { (key, groupedItems) ->
                CustomerAccount(
                    senderName = groupedItems.first().transaction.senderName,
                    normalizedSender = key.first,
                    walletSource = key.second,
                    transactionCount = groupedItems.size
                )
            }
            .sortedWith(compareBy<CustomerAccount> { it.walletSource }.thenBy { it.senderName })
    }

    private fun confirmUnlinkAccount(account: CustomerAccount) {
        val customerId = customerIdFlow.value
        if (customerId <= 0L) return
        val isLastAccount = accountsAdapter.currentList.size <= 1
        AppDialogs.showConfirmation(
            context = this,
            title = getString(if (isLastAccount) R.string.confirm_delete_customer_title else R.string.confirm_unlink_title),
            message = if (isLastAccount) {
                getString(R.string.confirm_delete_customer_message)
            } else {
                getString(R.string.confirm_unlink_message, account.senderName, account.walletSource)
            },
            positiveText = getString(if (isLastAccount) R.string.delete else R.string.unlink)
        ) {
            lifecycleScope.launch {
                if (isLastAccount) {
                    repository.deleteCustomer(customerId)
                    finish()
                } else {
                    repository.unlinkCustomerAccount(customerId, account.normalizedSender, account.walletSource)
                }
            }
        }
    }

    private fun bindStat(card: ViewStatCardBinding, title: String, stats: WalletStats) {
        val locale = TimeUtils.getLocale(resources.configuration)
        card.cardTitle.text = title

        // Set labels
        card.incomingLabel.text = getString(R.string.incoming_label)
        card.outgoingLabel.text = getString(R.string.outgoing_label)

        // Set amounts and colors
        val symbol = getString(R.string.currency_symbol)
        val incomingFormatted = TimeUtils.formatMoney(stats.incoming, locale)
        val outgoingFormatted = TimeUtils.formatMoney(stats.outgoing, locale)

        card.incomingAmount.text = "$incomingFormatted $symbol"
        card.outgoingAmount.text = "$outgoingFormatted $symbol"

        card.incomingAmount.setTextColor(getColor(R.color.jawwal_green))
        card.outgoingAmount.setTextColor(getColor(R.color.outgoing))
    }

    private fun showTransactionMenu(anchor: View, item: TransactionWithCustomer) {
        val transaction = item.transaction
        val toggleTitle = if (transaction.direction == TransactionDirection.OUTGOING) R.string.mark_incoming else R.string.mark_outgoing
        AnimatedPopupMenu.show(
            this,
            anchor,
            listOf(
                AnimatedPopupMenu.Action(getString(toggleTitle)) {
                    val newDirection = if (transaction.direction == TransactionDirection.OUTGOING) {
                        TransactionDirection.INCOMING
                    } else {
                        TransactionDirection.OUTGOING
                    }
                    lifecycleScope.launch { repository.updateDirection(transaction.id, newDirection) }
                }
            )
        )
    }

    private fun showCreateCustomerDialog() {
        AppDialogs.showTextInput(
            context = this,
            title = getString(R.string.create_customer),
            hint = binding.titleText.text.toString().ifBlank { getString(R.string.customer_name_hint) },
            initialValue = ""
        ) { name ->
            lifecycleScope.launch {
                // Create the customer and get its new ID.
                val newCustomerId = repository.createCustomerFromSender(sender, name)

                // Update the screen in place — no finish/restart needed.
                // Updating customerIdFlow causes the flatMapLatest above to re-subscribe
                // to getTransactionsForCustomer, so data updates automatically.
                customerIdFlow.value = newCustomerId
                binding.titleText.text = name
                updateHeaderButtons(newCustomerId)
            }
        }
    }

    private fun showEditNameDialog() {
        AppDialogs.showTextInput(
            context = this,
            title = getString(R.string.customer_name_hint),
            hint = getString(R.string.customer_name_hint),
            initialValue = binding.titleText.text.toString()
        ) { name ->
            lifecycleScope.launch {
                repository.updateCustomerName(customerIdFlow.value, name)
                binding.titleText.text = name
            }
        }
    }

    companion object {
        const val EXTRA_CUSTOMER_ID = "customer_id"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_TITLE = "title"
    }
}
