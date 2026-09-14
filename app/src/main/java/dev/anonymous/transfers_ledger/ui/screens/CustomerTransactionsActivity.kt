package dev.anonymous.transfers_ledger.ui.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.app.TransfersLedgerApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.core.TransactionStatsCalculator
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
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

class CustomerTransactionsActivity : FragmentActivity() {
    private lateinit var binding: ActivityCustomerTransactionsBinding
    private lateinit var adapter: TransactionListAdapter
    private lateinit var accountsAdapter: CustomerAccountAdapter
    private val repository by lazy { (application as TransfersLedgerApplication).repository }
    private var sender: String = ""
    private var pendingPopupTag: String? = null
    private var pendingTransactionId: Long = -1L
    private var isDefaultOutgoing: Boolean = false

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

        // Default outgoing card cancel button
        binding.cancelDefaultOutgoingBtn.setOnClickListener {
            showDisableDefaultOutgoingDialog()
        }

        // Default excluded card cancel button
        binding.cancelDefaultExcludedBtn.setOnClickListener {
            showDisableDefaultExcludedDialog()
        }

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
        binding.linkCustomerButton.setOnClickListener { showLinkCustomerSheet() }

        // Load defaultOutgoing and defaultExcluded state
        lifecycleScope.launch {
            loadDefaultOutgoingState()
            loadDefaultExcludedState()
        }

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
                        var customerId = customerIdFlow.value
                        if (customerId <= 0L && items.isNotEmpty()) {
                            val detectedId = items.firstNotNullOfOrNull { it.transaction.customerId }
                            if (detectedId != null && detectedId > 0L) {
                                customerIdFlow.value = detectedId
                                customerId = detectedId
                                binding.titleText.text = items.first().displayName
                                updateHeaderButtons(detectedId)
                                loadDefaultOutgoingState()
                                loadDefaultExcludedState()
                            }
                        }
                        adapter.submitList(items) {
                            if (pendingPopupTag == POPUP_TRANSACTION && pendingTransactionId > 0L) {
                                val index = items.indexOfFirst { it.transaction.id == pendingTransactionId }
                                if (index >= 0) {
                                    val item = items[index]
                                    tryRestoreTransactionMenu(index, item)
                                }
                            }
                        }
                        accountsAdapter.submitList(buildAccounts(items))
                        val showAccounts = customerId > 0L && items.isNotEmpty()
                        binding.accountsTitle.visibility = if (showAccounts) View.VISIBLE else View.GONE
                        binding.accountsRecycler.visibility = if (showAccounts) View.VISIBLE else View.GONE
                        bindStat(binding.statsCard, binding.titleText.text.toString(), stats.total)
                    }
            }
        }

        pendingPopupTag = savedInstanceState?.getString(KEY_ACTIVE_POPUP)
        pendingTransactionId = savedInstanceState?.getLong(KEY_POPUP_TRANSACTION_ID, -1L) ?: -1L
    }

    private fun updateHeaderButtons(customerId: Long) {
        val hasCustomer = customerId > 0L
        binding.editButton.visibility = if (hasCustomer) View.VISIBLE else View.GONE
        binding.customerActionsLayout.visibility = if (hasCustomer) View.GONE else View.VISIBLE
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
            fragmentManager = supportFragmentManager,
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
                    sender = account.senderName.ifBlank { sender }
                    customerIdFlow.value = -1L
                    binding.titleText.text = sender.ifBlank { getString(R.string.customer_title) }
                    updateHeaderButtons(-1L)
                    loadDefaultOutgoingState()
                    loadDefaultExcludedState()
                } else {
                    repository.unlinkCustomerAccount(customerId, account.normalizedSender, account.walletSource)
                }
            }
        }
    }

    private fun showLinkCustomerSheet() {
        val firstTx = adapter.currentList.firstOrNull()?.transaction
        AppDialogs.showCustomerLinkSheet(
            fragmentManager = supportFragmentManager,
            searchCustomers = { query -> repository.searchCustomers(query) }
        ) { customer ->
            lifecycleScope.launch {
                if (firstTx != null) {
                    repository.linkTransactionToCustomer(firstTx, customer.id)
                } else if (sender.isNotBlank()) {
                    repository.addIdentifier(customer.id, sender)
                }
                customerIdFlow.value = customer.id
                binding.titleText.text = customer.displayName
                updateHeaderButtons(customer.id)
                loadDefaultOutgoingState()
                loadDefaultExcludedState()
            }
        }
    }

    private fun bindStat(card: ViewStatCardBinding, title: String, stats: WalletStats) {
        val locale = TimeUtils.getLocale(resources.configuration)
        card.cardTitle.text = title
        card.cardTitle.setTextColor(getColor(R.color.text_primary))
        card.copyTitleButton.visibility = View.VISIBLE
        card.copyTitleButton.setOnClickListener {
            dev.anonymous.transfers_ledger.core.ClipboardUtils.copy(this, title)
        }

        // Set labels
        card.incomingLabel.text = getString(R.string.incoming_label)
        card.outgoingLabel.text = getString(R.string.outgoing_label)

        val greenColor = getColor(R.color.jawwal_green)
        val redColor = getColor(R.color.outgoing)

        card.incomingLabel.setTextColor(greenColor)
        card.outgoingLabel.setTextColor(redColor)
        card.incomingAmount.setTextColor(greenColor)
        card.outgoingAmount.setTextColor(redColor)

        // Set amounts
        val symbol = getString(R.string.currency_symbol)
        val incomingFormatted = TimeUtils.formatMoney(stats.incoming, locale)
        val outgoingFormatted = TimeUtils.formatMoney(stats.outgoing, locale)

        card.incomingAmount.text = "$incomingFormatted $symbol"
        card.outgoingAmount.text = "$outgoingFormatted $symbol"
    }

    private fun showTransactionMenu(anchor: View, item: TransactionWithCustomer) {
        pendingTransactionId = item.transaction.id
        val transaction = item.transaction
        val toggleTitle = if (transaction.direction == TransactionDirection.OUTGOING) R.string.mark_incoming else R.string.mark_outgoing
        val excludedTitle = if (transaction.excluded) R.string.mark_included else R.string.mark_excluded
        AnimatedPopupMenu.show(
            this,
            anchor,
            listOf(
                AnimatedPopupMenu.Action(getString(toggleTitle)) {
                    handleDirectionToggle(transaction)
                },
                AnimatedPopupMenu.Action(getString(excludedTitle)) {
                    handleExcludedToggle(transaction)
                }
            ),
            tag = POPUP_TRANSACTION,
            onDismiss = { pendingTransactionId = -1L }
        )
    }

    private fun handleDirectionToggle(transaction: TransactionEntity) {
        val customerId = transaction.customerId
        if (transaction.direction == TransactionDirection.INCOMING) {
            // Switching from INCOMING -> OUTGOING
            lifecycleScope.launch {
                repository.updateDirection(transaction.id, TransactionDirection.OUTGOING)
            }
            // If the customer exists and is NOT already defaultOutgoing, suggest enabling it
            if (customerId != null && customerId > 0L && !isDefaultOutgoing) {
                AppDialogs.showConfirmation(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.default_outgoing_enable_title),
                    message = getString(R.string.default_outgoing_enable_message),
                    positiveText = getString(R.string.default_outgoing_enable_confirm)
                ) {
                    lifecycleScope.launch {
                        repository.setCustomerDefaultOutgoing(customerId, true)
                        loadDefaultOutgoingState()
                    }
                }
            } else if (customerId == null || customerId <= 0L) {
                AppDialogs.showConfirmation(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.default_outgoing_enable_title),
                    message = getString(R.string.default_outgoing_enable_message),
                    positiveText = getString(R.string.default_outgoing_enable_confirm)
                ) {
                    lifecycleScope.launch {
                        val newCustomerId = repository.createCustomerFromSender(sender, sender)
                        repository.setCustomerDefaultOutgoing(newCustomerId, true)
                        customerIdFlow.value = newCustomerId
                        binding.titleText.text = sender
                        updateHeaderButtons(newCustomerId)
                        loadDefaultOutgoingState()
                    }
                }
            }
        } else {
            // Switching from OUTGOING -> INCOMING
            lifecycleScope.launch {
                repository.updateDirection(transaction.id, TransactionDirection.INCOMING)
            }
            // If the customer has defaultOutgoing enabled, suggest disabling it
            if (customerId != null && customerId > 0L && isDefaultOutgoing) {
                AppDialogs.showConfirmation(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.default_outgoing_disable_title),
                    message = getString(R.string.default_outgoing_disable_message),
                    positiveText = getString(R.string.default_outgoing_disable_confirm)
                ) {
                    lifecycleScope.launch {
                        repository.setCustomerDefaultOutgoing(customerId, false)
                        loadDefaultOutgoingState()
                    }
                }
            }
        }
    }

    private fun handleExcludedToggle(transaction: TransactionEntity) {
        val newExcluded = !transaction.excluded
        lifecycleScope.launch {
            if (newExcluded && !repository.isExcludedExplanationShown()) {
                AppDialogs.showConfirmation(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.excluded_first_time_title),
                    message = getString(R.string.excluded_first_time_message),
                    positiveText = getString(R.string.default_excluded_enable_confirm)
                ) {
                    lifecycleScope.launch {
                        repository.setExcludedExplanationShown(true)
                        applyExcludedToggle(transaction, newExcluded)
                    }
                }
            } else {
                applyExcludedToggle(transaction, newExcluded)
            }
        }
    }

    private fun applyExcludedToggle(transaction: TransactionEntity, newExcluded: Boolean) {
        lifecycleScope.launch {
            repository.setTransactionExcluded(transaction.id, newExcluded)
        }
        
        if (newExcluded) {
            if (transaction.customerId != null && transaction.customerId > 0L) {
                lifecycleScope.launch {
                    val isAlreadyDefault = repository.isCustomerDefaultExcluded(transaction.customerId)
                    if (!isAlreadyDefault) {
                        AppDialogs.showConfirmation(
                            fragmentManager = supportFragmentManager,
                            title = getString(R.string.default_excluded_enable_title),
                            message = getString(R.string.default_excluded_enable_message),
                            positiveText = getString(R.string.default_excluded_enable_confirm)
                        ) {
                            lifecycleScope.launch {
                                repository.setCustomerDefaultExcluded(transaction.customerId, true)
                                loadDefaultExcludedState()
                            }
                        }
                    }
                }
            } else {
                AppDialogs.showConfirmation(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.default_excluded_enable_title),
                    message = getString(R.string.default_excluded_enable_message),
                    positiveText = getString(R.string.default_excluded_enable_confirm)
                ) {
                    lifecycleScope.launch {
                        val newCustomerId = repository.createCustomerFromSender(sender, sender)
                        repository.setCustomerDefaultExcluded(newCustomerId, true)
                        customerIdFlow.value = newCustomerId
                        binding.titleText.text = sender
                        updateHeaderButtons(newCustomerId)
                        loadDefaultOutgoingState()
                        loadDefaultExcludedState()
                    }
                }
            }
        } else if (transaction.customerId != null && transaction.customerId > 0L) {
            lifecycleScope.launch {
                val isDefault = repository.isCustomerDefaultExcluded(transaction.customerId)
                if (isDefault) {
                    AppDialogs.showConfirmation(
                        fragmentManager = supportFragmentManager,
                        title = getString(R.string.default_excluded_disable_title),
                        message = getString(R.string.default_excluded_disable_message),
                        positiveText = getString(R.string.default_excluded_disable_confirm)
                    ) {
                        lifecycleScope.launch {
                            repository.setCustomerDefaultExcluded(transaction.customerId, false)
                            loadDefaultExcludedState()
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadDefaultOutgoingState() {
        val customerId = customerIdFlow.value
        if (customerId > 0L) {
            isDefaultOutgoing = repository.isCustomerDefaultOutgoing(customerId)
        } else {
            isDefaultOutgoing = false
        }
        binding.defaultOutgoingCard.visibility = if (isDefaultOutgoing) View.VISIBLE else View.GONE
    }

    private fun showDisableDefaultOutgoingDialog() {
        val customerId = customerIdFlow.value
        if (customerId <= 0L) return
        AppDialogs.showConfirmation(
            fragmentManager = supportFragmentManager,
            title = getString(R.string.default_outgoing_disable_title),
            message = getString(R.string.default_outgoing_disable_message),
            positiveText = getString(R.string.default_outgoing_disable_confirm)
        ) {
            lifecycleScope.launch {
                repository.setCustomerDefaultOutgoing(customerId, false)
                loadDefaultOutgoingState()
            }
        }
    }

    private suspend fun loadDefaultExcludedState() {
        val customerId = customerIdFlow.value
        val isDefaultExcluded = if (customerId > 0L) {
            repository.isCustomerDefaultExcluded(customerId)
        } else {
            false
        }
        binding.defaultExcludedCard.visibility = if (isDefaultExcluded) View.VISIBLE else View.GONE
    }

    private fun showDisableDefaultExcludedDialog() {
        val customerId = customerIdFlow.value
        if (customerId <= 0L) return
        AppDialogs.showConfirmation(
            fragmentManager = supportFragmentManager,
            title = getString(R.string.default_excluded_disable_title),
            message = getString(R.string.default_excluded_disable_message),
            positiveText = getString(R.string.default_excluded_disable_confirm)
        ) {
            lifecycleScope.launch {
                repository.setCustomerDefaultExcluded(customerId, false)
                loadDefaultExcludedState()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        AnimatedPopupMenu.activeTag?.let {
            outState.putString(KEY_ACTIVE_POPUP, it)
        }
        if (pendingTransactionId > 0L || AnimatedPopupMenu.activeTag == POPUP_TRANSACTION) {
            outState.putLong(KEY_POPUP_TRANSACTION_ID, pendingTransactionId)
        }
    }

    private fun tryRestoreTransactionMenu(position: Int, item: TransactionWithCustomer, retriesLeft: Int = 6) {
        binding.transactionsRecycler.post {
            val holder = binding.transactionsRecycler.findViewHolderForAdapterPosition(position)
            val anchor = holder?.itemView?.findViewById<View>(R.id.menuButton)
            if (anchor != null) {
                pendingPopupTag = null
                showTransactionMenu(anchor, item)
            } else if (retriesLeft > 0) {
                binding.transactionsRecycler.postDelayed({
                    tryRestoreTransactionMenu(position, item, retriesLeft - 1)
                }, 60L)
            }
        }
    }

    private fun showCreateCustomerDialog() {
        AppDialogs.showTextInput(
            fragmentManager = supportFragmentManager,
            title = getString(R.string.create_customer),
            hint = binding.titleText.text.toString().ifBlank { getString(R.string.customer_name_hint) },
            initialValue = sender.ifBlank { binding.titleText.text.toString() }
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
            fragmentManager = supportFragmentManager,
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

    override fun onDestroy() {
        AnimatedPopupMenu.dismissAll()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CUSTOMER_ID = "customer_id"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_TITLE = "title"
        private const val KEY_ACTIVE_POPUP = "active_popup_tag"
        private const val POPUP_TRANSACTION = "transaction"
        private const val KEY_POPUP_TRANSACTION_ID = "popup_transaction_id"
    }
}
