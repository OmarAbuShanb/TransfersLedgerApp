package dev.anonymous.transfers_ledger

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
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.data.local.db.CustomerSummary
import dev.anonymous.transfers_ledger.databinding.ActivityCustomersBinding
import dev.anonymous.transfers_ledger.ui.adapters.CustomerSummaryPagingAdapter
import dev.anonymous.transfers_ledger.ui.viewmodel.MainViewModel

class CustomersActivity : ComponentActivity() {
    private lateinit var binding: ActivityCustomersBinding
    private lateinit var adapter: CustomerSummaryPagingAdapter
    private val repository by lazy { (application as PalPayApplication).repository }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, repository)
    }
    private var searchJob: Job? = null
    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        adapter = CustomerSummaryPagingAdapter(TimeUtils.getLocale(resources.configuration), ::openCustomer)
        binding.customersRecycler.layoutManager = LinearLayoutManager(this)
        binding.customersRecycler.itemAnimator = null
        binding.customersRecycler.adapter = adapter

        binding.backButton.setOnClickListener { finish() }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = runSearch()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                if (loadStates.refresh is LoadState.NotLoading && adapter.itemCount == 0) {
                    binding.emptyStateText.text = getString(
                        if (lastQuery.isBlank()) R.string.no_customers else R.string.no_matching_customers
                    )
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.customersRecycler.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.customersRecycler.visibility = View.VISIBLE
                }
            }
        }

        binding.searchInput.requestFocus()
        binding.searchInput.postDelayed({
            binding.searchInput.requestFocus()
            getSystemService<InputMethodManager>()?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }, 240L)
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        lastQuery = binding.searchInput.text.toString().trim()
        searchJob = lifecycleScope.launch {
            delay(180)
            viewModel.getPagedCustomers(lastQuery).collectLatest { adapter.submitData(it) }
        }
    }

    private fun openCustomer(customer: CustomerSummary) {
        startActivity(Intent(this, CustomerTransactionsActivity::class.java).apply {
            putExtra(CustomerTransactionsActivity.EXTRA_CUSTOMER_ID, customer.id)
            putExtra(CustomerTransactionsActivity.EXTRA_TITLE, customer.displayName)
        })
    }
}
