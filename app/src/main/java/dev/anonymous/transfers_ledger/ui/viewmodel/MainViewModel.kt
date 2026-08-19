package dev.anonymous.transfers_ledger.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.core.JawwalPayMode
import dev.anonymous.transfers_ledger.core.SystemStatusUtils
import dev.anonymous.transfers_ledger.core.TransactionStatsCalculator
import dev.anonymous.transfers_ledger.data.local.db.CustomerEntity
import dev.anonymous.transfers_ledger.data.local.db.CustomerSummary
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.data.repository.TransactionRepository
import dev.anonymous.transfers_ledger.domain.model.AppStatus
import dev.anonymous.transfers_ledger.domain.model.DateRange
import dev.anonymous.transfers_ledger.domain.model.DirectionSource
import dev.anonymous.transfers_ledger.domain.model.SummaryPeriod
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import dev.anonymous.transfers_ledger.domain.model.TransactionFilter
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(
    application: Application,
    private val repository: TransactionRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppStatus())
    val uiState: StateFlow<AppStatus> = _uiState.asStateFlow()

    private val customSummaryRange = MutableStateFlow<DateRange?>(null)
    private val customSummaryTitle = MutableStateFlow<String?>(null)
    val listFilter = MutableStateFlow(TransactionFilter.ALL)
    val listRange = MutableStateFlow(DateRange(null, null))
    private var systemStatusRefreshJob: Job? = null

    val pagedTransactions: Flow<PagingData<TransactionWithCustomer>> =
        combine(listFilter, listRange) { filter, range -> filter to range }
            .flatMapLatest { (filter, range) -> repository.getPagedTransactions(filter, range) }
            .cachedIn(viewModelScope)

    init {
        viewModelScope.launch { repository.ensureFirstOpenAt() }
        observeSummary()
        observeTracking()
        refreshSystemStatus()
    }

    private fun observeSummary() {
        combine(
            repository.summaryPeriod,
            customSummaryRange,
            customSummaryTitle,
            listFilter,
            listRange
        ) { period, customRange, customTitle, filter, range ->
            SummarySelection(period, customRange, customTitle, filter, range)
        }
            .flatMapLatest { selection ->
                flow {
                    val range = selection.customRange ?: repository.rangeForPeriod(selection.period)
                    emit(selection to range)
                }.flatMapLatest { (resolvedSelection, range) ->
                    repository.getTransactionsForStats(range).map { transactions -> resolvedSelection to transactions }
                }
            }
            .onEach { (selection, transactions) ->
                val stats = TransactionStatsCalculator.calculate(transactions)
                val summaryTitle = formatSummaryTitle(selection.period, selection.customRange)
                val listTitle = formatListTitle(selection.listFilter, selection.listRange)
                _uiState.update {
                    it.copy(
                        isReady = true,
                        selectedPeriod = selection.period,
                        isCustomPeriod = selection.customTitle != null,
                        summaryTitle = summaryTitle,
                        listTitle = listTitle,
                        stats = stats
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeTracking() {
        repository.isTrackingEnabled
            .onEach { enabled ->
                _uiState.update { it.copy(trackingEnabled = enabled) }
            }
            .launchIn(viewModelScope)

        repository.jawwalPayMode
            .onEach { mode ->
                _uiState.update { it.copy(jawwalPayMode = mode) }
            }
            .launchIn(viewModelScope)
    }

    fun setSummaryPeriod(period: SummaryPeriod) {
        customSummaryRange.value = null
        customSummaryTitle.value = null
        viewModelScope.launch { repository.setSummaryPeriod(period) }
    }

    fun setCustomSummaryRange(range: DateRange, title: String) {
        customSummaryRange.value = range
        customSummaryTitle.value = title
    }

    fun setListFilter(filter: TransactionFilter) {
        listFilter.value = filter
    }

    fun setListRange(range: DateRange) {
        listRange.value = range
    }

    fun clearListRange() {
        listRange.value = DateRange(null, null)
    }

    fun searchPagedTransactions(query: String, filter: TransactionFilter): Flow<PagingData<TransactionWithCustomer>> {
        return repository.searchPagedTransactions(query, filter).cachedIn(viewModelScope)
    }

    fun getPagedCustomers(query: String): Flow<PagingData<CustomerSummary>> {
        return repository.getPagedCustomers(query).cachedIn(viewModelScope)
    }

    suspend fun getTransactionsForExport(range: DateRange): List<TransactionWithCustomer> {
        return repository.getTransactionsForExport(range)
    }

    fun updateDirection(transactionId: Long, direction: TransactionDirection) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateDirection(transactionId, direction, DirectionSource.MANUAL)
        }
    }

    fun createCustomerFromTransaction(
        transaction: TransactionEntity,
        displayName: String,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.createCustomerFromTransaction(transaction, displayName)
            }
            onComplete?.invoke()
        }
    }

    fun linkTransactionToCustomer(
        transaction: TransactionEntity,
        customerId: Long,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.linkTransactionToCustomer(transaction, customerId)
            }
            onComplete?.invoke()
        }
    }

    suspend fun searchCustomers(query: String): List<CustomerEntity> {
        return repository.searchCustomers(query)
    }

    fun toggleTracking(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setTrackingEnabled(enabled)
        }
    }

    fun setJawwalPayMode(mode: JawwalPayMode) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setJawwalPayMode(mode)
        }
    }

    fun customDayRange(dayMillis: Long): DateRange = repository.customDayRange(dayMillis)
    fun customRange(startMillis: Long, endMillis: Long): DateRange = repository.customRange(startMillis, endMillis)
    suspend fun getFirstTransactionTimestamp(): Long? = withContext(Dispatchers.IO) { repository.getFirstTransactionTimestamp() }
    suspend fun getLastTransactionTimestamp(): Long? = withContext(Dispatchers.IO) { repository.getLastTransactionTimestamp() }
    suspend fun hasTransactionsInRange(range: DateRange): Boolean = withContext(Dispatchers.IO) { repository.hasTransactionsInRange(range) }

    fun refreshSystemStatus() {
        systemStatusRefreshJob?.cancel()
        systemStatusRefreshJob = viewModelScope.launch(Dispatchers.Default) {
            repeat(SYSTEM_STATUS_REFRESH_ATTEMPTS) { attempt ->
                val isIgnored = isBatteryOptimizationIgnored()
                val listenerEnabled = SystemStatusUtils.isNotificationListenerEnabled(getApplication())
                _uiState.update {
                    it.copy(
                        batteryOptimizationIgnored = isIgnored,
                        listenerConnected = listenerEnabled
                    )
                }
                if (attempt < SYSTEM_STATUS_REFRESH_ATTEMPTS - 1) {
                    delay(SYSTEM_STATUS_REFRESH_INTERVAL_MS.milliseconds)
                }
            }
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    private val headerDateFormat by lazy { java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.forLanguageTag("ar")) }

    private fun formatSummaryTitle(period: SummaryPeriod, customRange: DateRange?): String {
        if (customRange?.startAt != null) {
            val start = customRange.startAt
            val end = customRange.endAt ?: start
            return if (isSameDay(start, end)) {
                "إجمالي يوم ${headerDateFormat.format(start)}"
            } else {
                "إجمالي من تاريخ ${headerDateFormat.format(start)} وحتى ${headerDateFormat.format(end)}"
            }
        }
        val app = getApplication<Application>()
        return when (period) {
            SummaryPeriod.DAILY -> app.getString(R.string.daily_total)
            SummaryPeriod.WEEKLY -> app.getString(R.string.weekly_total)
            SummaryPeriod.MONTHLY -> app.getString(R.string.monthly_total)
            SummaryPeriod.YEARLY -> app.getString(R.string.yearly_total)
            SummaryPeriod.ALL -> app.getString(R.string.all_total)
        }
    }

    private fun formatListTitle(filter: TransactionFilter, range: DateRange): String {
        val start = range.startAt
        if (start != null) {
            val end = range.endAt ?: start
            return if (isSameDay(start, end)) {
                "حوالات يوم ${headerDateFormat.format(start)}"
            } else {
                "من ${headerDateFormat.format(start)} حتى ${headerDateFormat.format(end)}"
            }
        }
        val app = getApplication<Application>()
        return when (filter) {
            TransactionFilter.ALL -> app.getString(R.string.all_transactions_title)
            TransactionFilter.INCOMING -> app.getString(R.string.incoming_transactions_title)
            TransactionFilter.OUTGOING -> app.getString(R.string.outgoing_transactions_title)
        }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
               c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private data class SummarySelection(
        val period: SummaryPeriod,
        val customRange: DateRange?,
        val customTitle: String?,
        val listFilter: TransactionFilter,
        val listRange: DateRange
    )

    class Factory(private val application: Application, private val repository: TransactionRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
    }

    companion object {
        private const val SYSTEM_STATUS_REFRESH_ATTEMPTS = 6
        private const val SYSTEM_STATUS_REFRESH_INTERVAL_MS = 350L
    }
}
