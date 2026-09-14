package dev.anonymous.transfers_ledger.ui.screens

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.app.TransfersLedgerApplication
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.data.local.db.CustomerSummary
import dev.anonymous.transfers_ledger.databinding.ActivityCustomersBinding
import dev.anonymous.transfers_ledger.ui.adapters.CustomerSummaryPagingAdapter
import dev.anonymous.transfers_ledger.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomersActivity : ComponentActivity() {
    private lateinit var binding: ActivityCustomersBinding
    private lateinit var adapter: CustomerSummaryPagingAdapter
    private val repository by lazy { (application as TransfersLedgerApplication).repository }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        adapter = CustomerSummaryPagingAdapter(TimeUtils.getLocale(resources.configuration), ::openCustomer)
        val layoutManager = LinearLayoutManager(this)
        binding.customersRecycler.layoutManager = layoutManager
        binding.customersRecycler.itemAnimator = null
        binding.customersRecycler.adapter = adapter

        binding.backButton.setOnClickListener { finish() }

        binding.sortAlphabeticalBtn.setOnClickListener {
            viewModel.customerSortByPurchase.value = false
        }

        binding.sortMostPurchasesBtn.setOnClickListener {
            viewModel.customerSortByPurchase.value = true
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.customerSearchQuery.value = s?.toString()?.trim() ?: ""
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Observe sort mode from ViewModel to update UI buttons (persists on configuration change)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.customerSortByPurchase.collect { sortByPurchase ->
                    updateSortButtonsUI(sortByPurchase)
                }
            }
        }

        // Collect paged customers from ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pagedCustomers.collectLatest {
                    adapter.submitData(it)
                }
            }
        }

        // Empty state handling
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collectLatest { loadStates ->
                    if (loadStates.refresh is LoadState.NotLoading) {
                        val isEmpty = adapter.itemCount == 0
                        binding.emptyStateText.text = getString(
                            if (viewModel.customerSearchQuery.value.isBlank()) R.string.no_customers else R.string.no_matching_customers
                        )
                        binding.emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        binding.customersRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    }
                }
            }
        }

        binding.searchInput.requestFocus()
        binding.searchInput.postDelayed({
            binding.searchInput.requestFocus()
            getSystemService<InputMethodManager>()?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }, 240L)
    }

    private fun updateSortButtonsUI(sortByPurchase: Boolean) {
        if (!sortByPurchase) {
            binding.sortAlphabeticalBtn.setBackgroundResource(R.drawable.ripple_chip_selected)
            binding.sortAlphabeticalBtn.setTextColor(getColor(android.R.color.white))
            binding.sortMostPurchasesBtn.setBackgroundResource(R.drawable.ripple_dialog_button_secondary)
            binding.sortMostPurchasesBtn.setTextColor(getColor(R.color.text_primary))
        } else {
            binding.sortAlphabeticalBtn.setBackgroundResource(R.drawable.ripple_dialog_button_secondary)
            binding.sortAlphabeticalBtn.setTextColor(getColor(R.color.text_primary))
            binding.sortMostPurchasesBtn.setBackgroundResource(R.drawable.ripple_chip_selected)
            binding.sortMostPurchasesBtn.setTextColor(getColor(android.R.color.white))
        }
    }

    private fun openCustomer(customer: CustomerSummary) {
        startActivity(Intent(this, CustomerTransactionsActivity::class.java).apply {
            putExtra(CustomerTransactionsActivity.EXTRA_CUSTOMER_ID, customer.id)
            putExtra(CustomerTransactionsActivity.EXTRA_TITLE, customer.displayName)
        })
    }
}
