package dev.anonymous.transfers_ledger.ui.screens

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.app.TransfersLedgerApplication
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
import dev.anonymous.transfers_ledger.ui.adapters.EmptyStateAdapter
import dev.anonymous.transfers_ledger.ui.adapters.TransactionPagingAdapter
import dev.anonymous.transfers_ledger.ui.common.AnimatedPopupMenu
import dev.anonymous.transfers_ledger.ui.common.AppDialogs
import dev.anonymous.transfers_ledger.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : FragmentActivity() {

    companion object {
        private const val KEY_ACTIVE_POPUP = "active_popup_tag"
        private const val POPUP_SUMMARY_PERIOD = "summary_period"
        private const val POPUP_LIST_FILTER = "list_filter"
        private const val POPUP_SOURCE_FILTER = "source_filter"
        private const val POPUP_TRANSACTION = "transaction"
        private const val KEY_POPUP_TRANSACTION_ID = "popup_transaction_id"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var headerAdapter: MainHeaderAdapter
    private lateinit var adapter: TransactionPagingAdapter
    private lateinit var emptyStateAdapter: EmptyStateAdapter
    private val repository by lazy { (application as TransfersLedgerApplication).repository }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, repository)
    }
    private var lastTrackingEnabled: Boolean? = null
    private var pendingPopupTag: String? = null
    private var pendingTransactionId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val locale = TimeUtils.getLocale(resources.configuration)
        headerAdapter = MainHeaderAdapter(
            locale = locale,
            onBatteryClick = { startActivity(IntentUtils.getBatteryOptimizationIntent()) },
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

        // First launch flow: Privacy Policy -> Overview
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val privacyAccepted = prefs.getBoolean("privacy_policy_accepted", false)
        if (!privacyAccepted) {
            binding.root.post {
                AppDialogs.showPrivacyPolicy(supportFragmentManager) {
                    prefs.edit {
                        putBoolean("privacy_policy_accepted", true)
                            .putBoolean("overview_shown", true)
                    }
                    AppDialogs.showOverview(supportFragmentManager)
                }
            }
        } else if (!prefs.getBoolean("overview_shown", false)) {
            binding.root.post {
                AppDialogs.showOverview(supportFragmentManager)
            }
            prefs.edit {putBoolean("overview_shown", true)}
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState(it) } }
                launch { viewModel.pagedTransactions.collectLatest { adapter.submitData(it) } }
            }
        }

        // Restore popup menu that was open before configuration change (theme toggle)
        pendingPopupTag = savedInstanceState?.getString(KEY_ACTIVE_POPUP)
        pendingTransactionId = savedInstanceState?.getLong(KEY_POPUP_TRANSACTION_ID, -1L) ?: -1L
        if (pendingPopupTag != null) {
            binding.transactionsRecycler.post {
                restorePopupMenu()
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

    private fun restorePopupMenu() {
        val tag = pendingPopupTag ?: return
        when (tag) {
            POPUP_SUMMARY_PERIOD, POPUP_LIST_FILTER, POPUP_SOURCE_FILTER -> {
                tryRestoreHeaderMenu(tag)
            }

            POPUP_TRANSACTION -> {
                if (pendingTransactionId <= 0L) return
                val snapshot = adapter.snapshot()
                val existingIndex =
                    snapshot.items.indexOfFirst { it?.transaction?.id == pendingTransactionId }
                if (existingIndex >= 0) {
                    val item = snapshot.items[existingIndex] ?: return
                    tryRestoreTransactionMenu(existingIndex + 1, item)
                } else {
                    val listener = object : Function0<Unit> {
                        override fun invoke() {
                            val currentSnapshot = adapter.snapshot()
                            val index =
                                currentSnapshot.items.indexOfFirst { it?.transaction?.id == pendingTransactionId }
                            if (index < 0) return
                            adapter.removeOnPagesUpdatedListener(this)
                            val item = currentSnapshot.items[index] ?: return
                            tryRestoreTransactionMenu(index + 1, item)
                        }
                    }
                    adapter.addOnPagesUpdatedListener(listener)
                }
            }
        }
    }

    private fun tryRestoreHeaderMenu(tag: String, retriesLeft: Int = 8) {
        binding.transactionsRecycler.post {
            val headerHolder =
                binding.transactionsRecycler.findViewHolderForAdapterPosition(0)?.itemView
            val anchorId =
                if (tag == POPUP_SUMMARY_PERIOD) R.id.summaryPeriodButton else R.id.listFilterButton
            val anchor = headerHolder?.findViewById<View>(anchorId)
            if (anchor != null) {
                pendingPopupTag = null
                when (tag) {
                    POPUP_SUMMARY_PERIOD -> showSummaryPeriodMenu(anchor)
                    POPUP_SOURCE_FILTER -> showSourceFilterMenu(anchor)
                    else -> showListFilterMenu(anchor)
                }
            } else if (retriesLeft > 0) {
                binding.transactionsRecycler.postDelayed({
                    tryRestoreHeaderMenu(tag, retriesLeft - 1)
                }, 50L)
            }
        }
    }

    private fun tryRestoreTransactionMenu(
        adapterPosition: Int,
        item: TransactionWithCustomer,
        retriesLeft: Int = 8
    ) {
        binding.transactionsRecycler.post {
            binding.transactionsRecycler.scrollToPosition(adapterPosition)
            val holder =
                binding.transactionsRecycler.findViewHolderForAdapterPosition(adapterPosition)
            val anchor = holder?.itemView?.findViewById<View>(R.id.menuButton)
            if (anchor != null) {
                pendingPopupTag = null
                showTransactionMenu(anchor, item)
            } else if (retriesLeft > 0) {
                binding.transactionsRecycler.postDelayed({
                    tryRestoreTransactionMenu(adapterPosition, item, retriesLeft - 1)
                }, 50L)
            }
        }
    }

    override fun onDestroy() {
        AnimatedPopupMenu.dismissAll()
        super.onDestroy()
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
            ),
            tag = POPUP_SUMMARY_PERIOD
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
                    checked = currentFilter == TransactionFilter.ALL && !hasCustomRange && viewModel.sourceFilter.value == null
                ) {
                    viewModel.setListFilter(TransactionFilter.ALL)
                    viewModel.clearListRange()
                    viewModel.setSourceFilter(null)
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
                },
                AnimatedPopupMenu.Action(
                    getString(R.string.source_filter),
                    checked = viewModel.sourceFilter.value != null
                ) {
                    showSourceFilterMenu(anchor)
                }
            ),
            tag = POPUP_LIST_FILTER
        )
    }

    private fun showSourceFilterMenu(anchor: View) {
        val currentSource = viewModel.sourceFilter.value
        AnimatedPopupMenu.show(
            this,
            anchor,
            listOf(
                AnimatedPopupMenu.Action(
                    getString(R.string.source_all),
                    checked = currentSource == null
                ) {
                    viewModel.setSourceFilter(null)
                },
                AnimatedPopupMenu.Action(
                    getString(R.string.palpay_label),
                    checked = currentSource == dev.anonymous.transfers_ledger.core.PaymentSources.PALPAY
                ) {
                    viewModel.setSourceFilter(dev.anonymous.transfers_ledger.core.PaymentSources.PALPAY)
                },
                AnimatedPopupMenu.Action(
                    getString(R.string.jawwalpay_label),
                    checked = currentSource == dev.anonymous.transfers_ledger.core.PaymentSources.JAWWAL_PAY
                ) {
                    viewModel.setSourceFilter(dev.anonymous.transfers_ledger.core.PaymentSources.JAWWAL_PAY)
                },
                AnimatedPopupMenu.Action(
                    getString(R.string.bop_label),
                    checked = currentSource == dev.anonymous.transfers_ledger.core.PaymentSources.BANK_OF_PALESTINE
                ) {
                    viewModel.setSourceFilter(dev.anonymous.transfers_ledger.core.PaymentSources.BANK_OF_PALESTINE)
                }
            ),
            tag = POPUP_SOURCE_FILTER
        )
    }

    private fun showTransactionMenu(anchor: View, item: TransactionWithCustomer) {
        pendingTransactionId = item.transaction.id
        val transaction = item.transaction
        val toggleTitle =
            if (transaction.direction == TransactionDirection.OUTGOING) R.string.mark_incoming else R.string.mark_outgoing
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
            // Switching from INCOMING -> OUTGOING
            viewModel.updateDirection(transaction.id, TransactionDirection.OUTGOING)
            // If the customer exists, check if we should suggest enabling defaultOutgoing
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
            // Switching from OUTGOING -> INCOMING
            viewModel.updateDirection(transaction.id, TransactionDirection.INCOMING)
            // If the customer has defaultOutgoing, suggest disabling it
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
            fragmentManager = supportFragmentManager,
            title = getString(R.string.create_customer),
            hint = transaction.senderName.ifBlank { getString(R.string.customer_name_hint) },
            initialValue = transaction.senderName
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
            fragmentManager = supportFragmentManager,
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
                AppDialogs.showExportNotice(supportFragmentManager) {
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
            AppDialogs.showDateDialog(
                fragmentManager = supportFragmentManager,
                title = title,
                actionText = actionText,
                buildDayRange = viewModel::customDayRange,
                buildRange = viewModel::customRange,
                minDate = first,
                maxDate = last,
                onApply = onApply
            )
        }
    }
}

