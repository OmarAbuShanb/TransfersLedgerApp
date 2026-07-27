package dev.anonymous.transfers_ledger

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.core.IntentUtils
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.core.export.ExcelExporter
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.databinding.ActivityMainBinding
import dev.anonymous.transfers_ledger.domain.model.AppStatus
import dev.anonymous.transfers_ledger.domain.model.DateRange
import dev.anonymous.transfers_ledger.domain.model.SummaryPeriod
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import dev.anonymous.transfers_ledger.domain.model.TransactionFilter
import dev.anonymous.transfers_ledger.service.TrackingForegroundService
import dev.anonymous.transfers_ledger.ui.adapters.MainHeaderAdapter
import dev.anonymous.transfers_ledger.ui.adapters.TransactionPagingAdapter
import dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu
import dev.anonymous.transfers_ledger.ui.common.AppDialogs
import dev.anonymous.transfers_ledger.ui.common.DateRangeDialog
import dev.anonymous.transfers_ledger.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var headerAdapter: MainHeaderAdapter
    private lateinit var adapter: TransactionPagingAdapter
    private lateinit var emptyStateAdapter: EmptyStateAdapter
    private val repository by lazy { (application as PalPayApplication).repository }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, repository)
    }
    private var lastTrackingEnabled: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val locale = TimeUtils.getLocale(resources.configuration)
        headerAdapter = MainHeaderAdapter(
            locale = locale,
            onBatteryClick = { startActivity(IntentUtils.getBatteryOptimizationIntent(this)) },
            onPermissionClick = {
                startActivity(
                    IntentUtils.getNotificationListenerSettingsIntent(
                        this
                    )
                )
            },
            onSummaryPeriodClick = ::showSummaryPeriodMenu,
            onListFilterClick = ::showListFilterMenu
        )
        emptyStateAdapter = EmptyStateAdapter("لا توجد حوالات بعد")
        adapter =
            TransactionPagingAdapter(locale, ::showTransactionMenu, ::openCustomerTransactions)
        binding.transactionsRecycler.layoutManager = LinearLayoutManager(this)
        binding.transactionsRecycler.itemAnimator = null
        binding.transactionsRecycler.adapter =
            ConcatAdapter(headerAdapter, emptyStateAdapter, adapter)
        adapter.addOnPagesUpdatedListener {
            binding.transactionsRecycler.post {
                adapter.notifyDataSetChanged()
                emptyStateAdapter.updateVisible(adapter.itemCount == 0)
            }
        }
        binding.transactionsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                when {
                    dy > 8 -> binding.searchFab.hide()
                    dy < -8 -> binding.searchFab.show()
                }
            }
        })

        binding.settingsButton.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    SettingsActivity::class.java
                )
            )
        }
        binding.customersButton.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    CustomersActivity::class.java
                )
            )
        }
        binding.searchFab.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    SearchActivity::class.java
                )
            )
        }
        binding.exportButton.setOnClickListener { explainAndExport() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState(it) } }
                launch { viewModel.pagedTransactions.collectLatest { adapter.submitData(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemStatus()
    }

    private fun renderState(state: AppStatus) {
        headerAdapter.submitState(state)

        if (lastTrackingEnabled != state.trackingEnabled) {
            lastTrackingEnabled = state.trackingEnabled
            updateForegroundService(state.trackingEnabled)
        }
    }

    private fun updateForegroundService(enabled: Boolean) {
        val serviceIntent = Intent(this, TrackingForegroundService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
    }

    private fun showSummaryPeriodMenu(anchor: View) {
        val state = viewModel.uiState.value
        val currentPeriod = if (state.isCustomPeriod) null else state.selectedPeriod
        AnimatedPopupMenu.show(
            this,
            anchor,
            listOf(
                AnimatedPopupMenu.Action(
                    getString(R.string.daily_total),
                    checked = currentPeriod == SummaryPeriod.DAILY
                ) { viewModel.setSummaryPeriod(SummaryPeriod.DAILY) },
                AnimatedPopupMenu.Action(
                    getString(R.string.weekly_total),
                    checked = currentPeriod == SummaryPeriod.WEEKLY
                ) { viewModel.setSummaryPeriod(SummaryPeriod.WEEKLY) },
                AnimatedPopupMenu.Action(
                    getString(R.string.monthly_total),
                    checked = currentPeriod == SummaryPeriod.MONTHLY
                ) { viewModel.setSummaryPeriod(SummaryPeriod.MONTHLY) },
                AnimatedPopupMenu.Action(
                    getString(R.string.yearly_total),
                    checked = currentPeriod == SummaryPeriod.YEARLY
                ) { viewModel.setSummaryPeriod(SummaryPeriod.YEARLY) },
                AnimatedPopupMenu.Action(
                    getString(R.string.all_total),
                    checked = currentPeriod == SummaryPeriod.ALL
                ) { viewModel.setSummaryPeriod(SummaryPeriod.ALL) },
                AnimatedPopupMenu.Action(
                    getString(R.string.custom_total),
                    checked = state.isCustomPeriod
                ) {
                    showDateDialog(
                        getString(R.string.date_dialog_title),
                        getString(R.string.apply)
                    ) { range, label, dismiss ->
                        lifecycleScope.launch {
                            if (!viewModel.hasTransactionsInRange(range)) {
                                Snackbar.make(
                                    binding.root,
                                    R.string.excel_empty,
                                    Snackbar.LENGTH_LONG
                                ).show()
                                return@launch
                            }
                            viewModel.setCustomSummaryRange(range, label)
                            dismiss()
                        }
                    }
                }
            )
        )
    }

    private fun showListFilterMenu(anchor: View) {
        val currentFilter = viewModel.listFilter.value
        val hasCustomRange = viewModel.listRange.value.startAt != null
        AnimatedPopupMenu.show(
            this,
            anchor,
            listOf(
                AnimatedPopupMenu.Action(
                    getString(R.string.all_filter),
                    checked = currentFilter == TransactionFilter.ALL && !hasCustomRange
                ) {
                    viewModel.setListFilter(TransactionFilter.ALL)
                    viewModel.clearListRange()
                },
                AnimatedPopupMenu.Action(
                    getString(R.string.incoming_filter),
                    checked = currentFilter == TransactionFilter.INCOMING
                ) {
                    viewModel.setListFilter(TransactionFilter.INCOMING)
                },
                AnimatedPopupMenu.Action(
                    getString(R.string.outgoing_filter),
                    checked = currentFilter == TransactionFilter.OUTGOING
                ) {
                    viewModel.setListFilter(TransactionFilter.OUTGOING)
                },
                AnimatedPopupMenu.Action(
                    getString(R.string.date_filter),
                    checked = hasCustomRange
                ) {
                    showDateDialog(
                        getString(R.string.date_dialog_title),
                        getString(R.string.apply)
                    ) { range, _, dismiss ->
                        lifecycleScope.launch {
                            if (!viewModel.hasTransactionsInRange(range)) {
                                Snackbar.make(
                                    binding.root,
                                    R.string.excel_empty,
                                    Snackbar.LENGTH_LONG
                                ).show()
                                return@launch
                            }
                            viewModel.setListRange(range)
                            dismiss()
                        }
                    }
                }
            )
        )
    }

    private fun showTransactionMenu(anchor: View, item: TransactionWithCustomer) {
        val transaction = item.transaction
        val toggleTitle =
            if (transaction.direction == TransactionDirection.OUTGOING) R.string.mark_incoming else R.string.mark_outgoing
        AnimatedPopupMenu.show(
            this,
            anchor,
            buildList {
                add(AnimatedPopupMenu.Action(getString(toggleTitle)) {
                    val newDirection = if (transaction.direction == TransactionDirection.OUTGOING) {
                        TransactionDirection.INCOMING
                    } else {
                        TransactionDirection.OUTGOING
                    }
                    viewModel.updateDirection(transaction.id, newDirection)
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
        val intent = Intent(this, CustomerTransactionsActivity::class.java).apply {
            putExtra(
                CustomerTransactionsActivity.EXTRA_CUSTOMER_ID,
                item.transaction.customerId ?: -1L
            )
            putExtra(CustomerTransactionsActivity.EXTRA_SENDER, item.transaction.senderName)
            putExtra(CustomerTransactionsActivity.EXTRA_TITLE, item.displayName)
        }
        startActivity(intent)
    }

    private fun showCreateCustomerDialog(transaction: TransactionEntity) {
        AppDialogs.showTextInput(
            context = this,
            title = getString(R.string.create_customer),
            hint = transaction.senderName.ifBlank { getString(R.string.customer_name_hint) },
            initialValue = ""
        ) { name ->
            viewModel.createCustomerFromTransaction(transaction, name) {
                // Invalidate the paging source so the displayed transactions pick up
                // the new customer name immediately without requiring a restart.
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
            viewModel.linkTransactionToCustomer(transaction, customer.id)
        }
    }

    private fun explainAndExport() {
        lifecycleScope.launch {
            if (repository.isExportNoticeShown()) {
                showExportDateDialog()
            } else {
                AppDialogs.showExportNotice(this@MainActivity) {
                    lifecycleScope.launch {
                        repository.setExportNoticeShown(true)
                        showExportDateDialog()
                    }
                }
            }
        }
    }

    private fun showExportDateDialog() {
        showDateDialog(
            getString(R.string.export_dialog_title),
            getString(R.string.export)
        ) { range, _, dismiss ->
            exportRange(range, dismiss)
        }
    }

    private fun exportRange(range: DateRange, dismissDialog: (() -> Unit)? = null) {
        lifecycleScope.launch {
            val transactions = viewModel.getTransactionsForExport(range)
            if (transactions.isEmpty()) {
                Snackbar.make(binding.root, R.string.excel_empty, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val result = ExcelExporter(this@MainActivity).export(transactions)
            dismissDialog?.invoke()
            Snackbar.make(
                binding.root,
                getString(R.string.excel_success, result.fileName),
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.open_file) { openExportedFile(result) }
                .show()
        }
    }

    private fun openExportedFile(result: ExcelExporter.ExportResult) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(result.uri, result.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.open_file)))
        }
    }

    private fun showDateDialog(
        title: String,
        actionText: String,
        onApply: (DateRange, String, () -> Unit) -> Unit
    ) {
        lifecycleScope.launch {
            val first = viewModel.getFirstTransactionTimestamp()
            val last = viewModel.getLastTransactionTimestamp()
            if (first == null || last == null) {
                Snackbar.make(binding.root, R.string.no_transactions, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            DateRangeDialog(
                context = this@MainActivity,
                title = title,
                actionText = actionText,
                buildDayRange = viewModel::customDayRange,
                buildRange = viewModel::customRange,
                minDate = first,
                maxDate = last,
                onApply = onApply
            ).show()
        }
    }
}

class EmptyStateAdapter(private val message: String) :
    RecyclerView.Adapter<EmptyStateAdapter.ViewHolder>() {
    private var visible: Boolean = false

    fun updateVisible(isVisible: Boolean) {
        if (visible != isVisible) {
            visible = isVisible
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_empty_state, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = message
    }

    override fun getItemCount(): Int = if (visible) 1 else 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: android.widget.TextView = view.findViewById(R.id.emptyText)
    }
}
