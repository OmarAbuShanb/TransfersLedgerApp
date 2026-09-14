package dev.anonymous.transfers_ledger.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.app.TransfersLedgerApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.databinding.ActivitySearchBinding
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import dev.anonymous.transfers_ledger.domain.model.TransactionFilter
import dev.anonymous.transfers_ledger.ui.adapters.TransactionPagingAdapter
import dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu
import dev.anonymous.transfers_ledger.ui.common.AppDialogs
import dev.anonymous.transfers_ledger.ui.common.SegmentedControlAnimator
import dev.anonymous.transfers_ledger.ui.viewmodel.MainViewModel
import kotlin.time.Duration.Companion.milliseconds

class SearchActivity : FragmentActivity() {
    companion object {
        private const val KEY_ACTIVE_POPUP = "active_popup_tag"
        private const val POPUP_TRANSACTION = "transaction"
        private const val KEY_POPUP_TRANSACTION_ID = "popup_transaction_id"
    }

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: TransactionPagingAdapter
    private val repository by lazy { (application as TransfersLedgerApplication).repository }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, repository)
    }
    private var filter = TransactionFilter.ALL
    private var searchJob: Job? = null
    private var lastQuery = ""
    private var pendingPopupTag: String? = null
    private var pendingTransactionId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        adapter = TransactionPagingAdapter(TimeUtils.getLocale(resources.configuration), ::showTransactionMenu, ::openCustomerTransactions)
        binding.resultsRecycler.layoutManager = LinearLayoutManager(this)
        binding.resultsRecycler.itemAnimator = null
        binding.resultsRecycler.adapter = adapter
        binding.resultsRecycler.visibility = View.GONE
        adapter.addOnPagesUpdatedListener {
            binding.resultsRecycler.post { adapter.notifyDataSetChanged() }
        }

        binding.backButton.setOnClickListener { finish() }
        binding.allButton.setOnClickListener { setFilter(TransactionFilter.ALL) }
        binding.incomingButton.setOnClickListener { setFilter(TransactionFilter.INCOMING) }
        binding.outgoingButton.setOnClickListener { setFilter(TransactionFilter.OUTGOING) }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = runSearch()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                if (lastQuery.isBlank()) {
                    showEmptyState(getString(R.string.search_empty_prompt))
                } else if (loadStates.refresh is LoadState.NotLoading && adapter.itemCount == 0) {
                    showEmptyState(getString(R.string.no_search_results))
                } else {
                    hideEmptyState()
                }
            }
        }

        binding.searchInput.requestFocus()
        binding.searchInput.postDelayed({
            binding.searchInput.requestFocus()
            getSystemService<InputMethodManager>()?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }, 240L)
        updateFilterUi()
        runSearch()

        pendingPopupTag = savedInstanceState?.getString(KEY_ACTIVE_POPUP)
        pendingTransactionId = savedInstanceState?.getLong(KEY_POPUP_TRANSACTION_ID, -1L) ?: -1L
        if (pendingPopupTag == POPUP_TRANSACTION && pendingTransactionId > 0L) {
            restorePopupMenu()
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

    private fun restorePopupMenu() {
        val tag = pendingPopupTag ?: return
        when (tag) {
            POPUP_TRANSACTION -> {
                if (pendingTransactionId <= 0L) return
                val snapshot = adapter.snapshot()
                val existingIndex = snapshot.items.indexOfFirst { it.transaction.id == pendingTransactionId }
                if (existingIndex >= 0) {
                    val item = snapshot.items[existingIndex]
                    tryRestoreTransactionMenu(existingIndex, item)
                } else {
                    val listener = object : Function0<Unit> {
                        override fun invoke() {
                            val currentSnapshot = adapter.snapshot()
                            val index = currentSnapshot.items.indexOfFirst { it.transaction.id == pendingTransactionId }
                            if (index < 0) return
                            adapter.removeOnPagesUpdatedListener(this)
                            val item = currentSnapshot.items[index]
                            tryRestoreTransactionMenu(index, item)
                        }
                    }
                    adapter.addOnPagesUpdatedListener(listener)
                }
            }
        }
    }

    private fun tryRestoreTransactionMenu(position: Int, item: TransactionWithCustomer, retriesLeft: Int = 8) {
        binding.resultsRecycler.post {
            binding.resultsRecycler.scrollToPosition(position)
            val holder = binding.resultsRecycler.findViewHolderForAdapterPosition(position)
            val anchor = holder?.itemView?.findViewById<View>(R.id.menuButton)
            if (anchor != null) {
                pendingPopupTag = null
                showTransactionMenu(anchor, item)
            } else if (retriesLeft > 0) {
                binding.resultsRecycler.postDelayed({
                    tryRestoreTransactionMenu(position, item, retriesLeft - 1)
                }, 50L)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Pass animate=false so SegmentedControlAnimator waits for the new layout pass
        // before reading button positions. If animate=true were passed, the old
        // orientation's positions would be applied immediately and never corrected.
        updateFilterUi(animate = false)
    }

    private fun setFilter(newFilter: TransactionFilter) {
        filter = newFilter
        updateFilterUi()
        runSearch()
    }

    private fun updateFilterUi(animate: Boolean = binding.filterIndicator.width > 0) {
        val buttons = listOf(
            binding.allButton to TransactionFilter.ALL,
            binding.incomingButton to TransactionFilter.INCOMING,
            binding.outgoingButton to TransactionFilter.OUTGOING
        )
        buttons.forEach { (button, buttonFilter) ->
            val selected = buttonFilter == filter
            button.setTextColor(getColor(if (selected) android.R.color.white else R.color.text_secondary))
        }
        val selectedButton = buttons.first { it.second == filter }.first
        SegmentedControlAnimator.select(
            container = binding.filterSegment,
            indicator = binding.filterIndicator,
            selected = selectedButton,
            animate = animate
        )
    }

    private fun runSearch() {
        searchJob?.cancel()
        lastQuery = binding.searchInput.text.toString().trim()
        searchJob = lifecycleScope.launch {
            delay(250.milliseconds)
            if (lastQuery.isBlank()) {
                adapter.submitData(PagingData.empty())
                showEmptyState(getString(R.string.search_empty_prompt))
                return@launch
            }

            hideEmptyState()
            viewModel.searchPagedTransactions(lastQuery, filter).collectLatest {
                adapter.submitData(it)
            }
        }
    }

    private fun showEmptyState(message: String) {
        binding.emptyStateText.text = message
        binding.emptyStateText.visibility = View.VISIBLE
        binding.resultsRecycler.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateText.visibility = View.GONE
        binding.resultsRecycler.visibility = View.VISIBLE
    }

    private fun showTransactionMenu(anchor: View, item: TransactionWithCustomer) {
        pendingTransactionId = item.transaction.id
        val transaction = item.transaction
        val toggleTitle = if (transaction.direction == TransactionDirection.OUTGOING) R.string.mark_incoming else R.string.mark_outgoing
        val excludedTitle = if (transaction.excluded) R.string.mark_included else R.string.mark_excluded
        AnimatedPopupMenu.show(
            this,
            anchor,
            buildList {
                if (transaction.customerId == null) {
                    add(AnimatedPopupMenu.Action(getString(R.string.create_customer)) {
                        showCreateCustomerDialog(transaction)
                    })
                }
                add(AnimatedPopupMenu.Action(getString(R.string.customer_transactions)) {
                    openCustomerTransactions(item)
                })
                if (transaction.customerId == null) {
                    add(AnimatedPopupMenu.Action(getString(R.string.link_customer)) {
                        showLinkCustomerSheet(transaction)
                    })
                }
                add(AnimatedPopupMenu.Action(getString(toggleTitle)) {
                    handleDirectionToggle(transaction)
                })
                add(AnimatedPopupMenu.Action(getString(excludedTitle)) {
                    handleExcludedToggle(transaction)
                })
            },
            tag = POPUP_TRANSACTION,
            onDismiss = { pendingTransactionId = -1L }
        )
    }

    private fun handleDirectionToggle(transaction: TransactionEntity) {
        val customerId = transaction.customerId
        if (transaction.direction == TransactionDirection.INCOMING) {
            viewModel.updateDirection(transaction.id, TransactionDirection.OUTGOING)
            adapter.refresh()
            if (customerId != null && customerId > 0L) {
                lifecycleScope.launch {
                    val isDefault = viewModel.isCustomerDefaultOutgoing(customerId)
                    if (!isDefault) {
                        AppDialogs.showConfirmation(
                            fragmentManager = supportFragmentManager,
                            title = getString(R.string.default_outgoing_enable_title),
                            message = getString(R.string.default_outgoing_enable_message),
                            positiveText = getString(R.string.default_outgoing_enable_confirm)
                        ) {
                            lifecycleScope.launch {
                                viewModel.setCustomerDefaultOutgoing(customerId, true)
                            }
                        }
                    }
                }
            } else {
                AppDialogs.showConfirmation(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.default_outgoing_enable_title),
                    message = getString(R.string.default_outgoing_enable_message),
                    positiveText = getString(R.string.default_outgoing_enable_confirm)
                ) {
                    viewModel.createCustomerAndSetDefaultOutgoing(transaction) {
                        adapter.refresh()
                    }
                }
            }
        } else {
            viewModel.updateDirection(transaction.id, TransactionDirection.INCOMING)
            adapter.refresh()
            if (customerId != null && customerId > 0L) {
                lifecycleScope.launch {
                    val isDefault = viewModel.isCustomerDefaultOutgoing(customerId)
                    if (isDefault) {
                        AppDialogs.showConfirmation(
                            fragmentManager = supportFragmentManager,
                            title = getString(R.string.default_outgoing_disable_title),
                            message = getString(R.string.default_outgoing_disable_message),
                            positiveText = getString(R.string.default_outgoing_disable_confirm)
                        ) {
                            lifecycleScope.launch {
                                viewModel.setCustomerDefaultOutgoing(customerId, false)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleExcludedToggle(transaction: TransactionEntity) {
        val newExcluded = !transaction.excluded
        lifecycleScope.launch {
            if (newExcluded && !viewModel.isExcludedExplanationShown()) {
                AppDialogs.showConfirmation(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.excluded_first_time_title),
                    message = getString(R.string.excluded_first_time_message),
                    positiveText = getString(R.string.default_excluded_enable_confirm)
                ) {
                    lifecycleScope.launch {
                        viewModel.setExcludedExplanationShown()
                        applyExcludedToggle(transaction, newExcluded)
                    }
                }
            } else {
                applyExcludedToggle(transaction, newExcluded)
            }
        }
    }

    private fun applyExcludedToggle(transaction: TransactionEntity, newExcluded: Boolean) {
        viewModel.setTransactionExcluded(transaction.id, newExcluded) {
            adapter.refresh()
        }
        
        if (newExcluded) {
            if (transaction.customerId != null && transaction.customerId > 0L) {
                lifecycleScope.launch {
                    val isAlreadyDefault = viewModel.isCustomerDefaultExcluded(transaction.customerId)
                    if (!isAlreadyDefault) {
                        AppDialogs.showConfirmation(
                            fragmentManager = supportFragmentManager,
                            title = getString(R.string.default_excluded_enable_title),
                            message = getString(R.string.default_excluded_enable_message),
                            positiveText = getString(R.string.default_excluded_enable_confirm)
                        ) {
                            lifecycleScope.launch {
                                viewModel.setCustomerDefaultExcluded(transaction.customerId, true)
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
                    viewModel.createCustomerAndSetDefaultExcluded(transaction) {
                        adapter.refresh()
                    }
                }
            }
        } else if (transaction.customerId != null && transaction.customerId > 0L) {
            lifecycleScope.launch {
                val isDefault = viewModel.isCustomerDefaultExcluded(transaction.customerId)
                if (isDefault) {
                    AppDialogs.showConfirmation(
                        fragmentManager = supportFragmentManager,
                        title = getString(R.string.default_excluded_disable_title),
                        message = getString(R.string.default_excluded_disable_message),
                        positiveText = getString(R.string.default_excluded_disable_confirm)
                    ) {
                        lifecycleScope.launch {
                            viewModel.setCustomerDefaultExcluded(transaction.customerId, false)
                        }
                    }
                }
            }
        }
    }

    private fun openCustomerTransactions(item: TransactionWithCustomer) {
        startActivity(Intent(this, CustomerTransactionsActivity::class.java).apply {
            putExtra(CustomerTransactionsActivity.EXTRA_CUSTOMER_ID, item.transaction.customerId ?: -1L)
            putExtra(CustomerTransactionsActivity.EXTRA_SENDER, item.transaction.senderName)
            putExtra(CustomerTransactionsActivity.EXTRA_TITLE, item.displayName)
        })
    }

    private fun showCreateCustomerDialog(transaction: TransactionEntity) {
        AppDialogs.showTextInput(
            fragmentManager = supportFragmentManager,
            title = getString(R.string.create_customer),
            hint = transaction.senderName.ifBlank { getString(R.string.customer_name_hint) },
            initialValue = transaction.senderName
        ) { name ->
            viewModel.createCustomerFromTransaction(transaction, name) {
                adapter.refresh()
            }
        }
    }

    private fun showLinkCustomerSheet(transaction: TransactionEntity) {
        AppDialogs.showCustomerLinkSheet(
            fragmentManager = supportFragmentManager,
            searchCustomers = viewModel::searchCustomers
        ) { customer ->
            viewModel.linkTransactionToCustomer(transaction, customer.id) {
                adapter.refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
    }

    override fun onDestroy() {
        AnimatedPopupMenu.dismissAll()
        super.onDestroy()
    }
}
