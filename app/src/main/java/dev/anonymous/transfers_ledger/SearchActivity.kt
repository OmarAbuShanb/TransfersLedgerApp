package dev.anonymous.transfers_ledger

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
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

class SearchActivity : ComponentActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: TransactionPagingAdapter
    private val repository by lazy { (application as PalPayApplication).repository }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, repository)
    }
    private var filter = TransactionFilter.ALL
    private var searchJob: Job? = null
    private var lastQuery = ""

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
            delay(250)
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
        val transaction = item.transaction
        val toggleTitle = if (transaction.direction == TransactionDirection.OUTGOING) R.string.mark_incoming else R.string.mark_outgoing
        AnimatedPopupMenu.show(
            this,
            anchor,
            buildList {
                add(AnimatedPopupMenu.Action(getString(toggleTitle)) {
                val newDirection = if (transaction.direction == TransactionDirection.OUTGOING) TransactionDirection.INCOMING else TransactionDirection.OUTGOING
                viewModel.updateDirection(transaction.id, newDirection)
                adapter.refresh()
                })
                add(AnimatedPopupMenu.Action(getString(R.string.customer_transactions)) {
                    openCustomerTransactions(item)
                })
                if (transaction.customerId == null) {
                    add(AnimatedPopupMenu.Action(getString(R.string.link_customer)) {
                        showLinkCustomerSheet(transaction)
                    })
                    add(AnimatedPopupMenu.Action(getString(R.string.create_customer)) {
                        showCreateCustomerDialog(transaction)
                    })
                }
            }
        )
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
            context = this,
            title = getString(R.string.create_customer),
            hint = transaction.senderName.ifBlank { getString(R.string.customer_name_hint) },
            initialValue = ""
        ) { name ->
            viewModel.createCustomerFromTransaction(transaction, name) {
                adapter.refresh()
            }
        }
    }

    private fun showLinkCustomerSheet(transaction: TransactionEntity) {
        AppDialogs.showCustomerLinkSheet(
            context = this,
            lifecycleScope = lifecycleScope,
            searchCustomers = viewModel::searchCustomers
        ) { customer ->
            viewModel.linkTransactionToCustomer(transaction, customer.id) {
                adapter.refresh()
            }
        }
    }
}
