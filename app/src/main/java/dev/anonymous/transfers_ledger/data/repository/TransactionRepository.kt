package dev.anonymous.transfers_ledger.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import dev.anonymous.transfers_ledger.core.TextNormalizer
import dev.anonymous.transfers_ledger.core.PaymentSources
import dev.anonymous.transfers_ledger.data.local.db.CustomerEntity
import dev.anonymous.transfers_ledger.data.local.db.CustomerIdentifierEntity
import dev.anonymous.transfers_ledger.data.local.db.CustomerSummary
import dev.anonymous.transfers_ledger.data.local.db.TransactionDao
import dev.anonymous.transfers_ledger.data.local.db.TransactionDatabase
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.data.local.pref.DataStoreManager
import dev.anonymous.transfers_ledger.domain.model.DateRange
import dev.anonymous.transfers_ledger.domain.model.DirectionSource
import dev.anonymous.transfers_ledger.domain.model.IdentifierType
import dev.anonymous.transfers_ledger.domain.model.SummaryPeriod
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import dev.anonymous.transfers_ledger.domain.model.TransactionFilter
import dev.anonymous.transfers_ledger.core.JawwalPayMode
import java.util.Calendar

class TransactionRepository(
    private val database: TransactionDatabase,
    private val dataStoreManager: DataStoreManager
) {
    private val transactionDao: TransactionDao = database.transactionDao()

    val isTrackingEnabled: Flow<Boolean> = dataStoreManager.isTrackingEnabled
    val summaryPeriod: Flow<SummaryPeriod> = dataStoreManager.summaryPeriod
    val jawwalPayMode: Flow<JawwalPayMode> = dataStoreManager.jawwalPayMode

    suspend fun setTrackingEnabled(enabled: Boolean) = dataStoreManager.setTrackingEnabled(enabled)
    suspend fun setListenerConnected(connected: Boolean) = dataStoreManager.setListenerConnected(connected)
    suspend fun ensureFirstOpenAt(): Long = dataStoreManager.ensureFirstOpenAt()
    suspend fun setSummaryPeriod(period: SummaryPeriod) = dataStoreManager.setSummaryPeriod(period)
    suspend fun isExportNoticeShown(): Boolean = dataStoreManager.isExportNoticeShown()
    suspend fun setExportNoticeShown(shown: Boolean) = dataStoreManager.setExportNoticeShown(shown)
    suspend fun setJawwalPayMode(mode: JawwalPayMode) = dataStoreManager.setJawwalPayMode(mode)

    fun getPagedTransactions(
        filter: TransactionFilter = TransactionFilter.ALL,
        range: DateRange = DateRange(null, null)
    ): Flow<PagingData<TransactionWithCustomer>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                transactionDao.getPagedTransactions(filter.toDirectionName(), range.startAt, range.endAt)
            }
        ).flow
    }

    fun getPagedCustomers(query: String): Flow<PagingData<CustomerSummary>> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                transactionDao.getPagedCustomers(TextNormalizer.normalize(query))
            }
        ).flow
    }

    fun getTransactionsForStats(range: DateRange): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsForStats(range.startAt, range.endAt)
    }

    suspend fun getTransactionsForStatsOnce(range: DateRange): List<TransactionEntity> {
        return transactionDao.getTransactionsForStatsOnce(range.startAt, range.endAt)
    }

    fun getLatestConvertibleTransaction(): Flow<TransactionWithCustomer?> {
        return transactionDao.getLatestConvertibleTransaction(PaymentSources.quickManualOutgoingSources)
    }

    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionWithCustomer>> {
        return transactionDao.getTransactionsForCustomer(customerId)
    }

    fun getTransactionsForSender(sender: String): Flow<List<TransactionWithCustomer>> {
        return transactionDao.getTransactionsForSender(TextNormalizer.normalize(sender))
    }

    private fun isPhoneSearchQuery(query: String): Boolean {
        val cleanDigits = TextNormalizer.normalizeDigits(query).trim()
        val cleanedPhone = dev.anonymous.transfers_ledger.core.PhoneNumberUtils.cleanPhoneNumber(query)
        return cleanDigits.startsWith("05") || cleanedPhone.startsWith("05")
    }

    fun searchPagedTransactions(query: String, filter: TransactionFilter): Flow<PagingData<TransactionWithCustomer>> {
        val normalized = TextNormalizer.normalize(query)
        val amount = TextNormalizer.normalizeDigits(query).trim().toDoubleOrNull()
        val searchPhone = if (isPhoneSearchQuery(query)) 1 else 0
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                transactionDao.searchPagedTransactions(
                    normalizedQuery = normalized,
                    amount = amount,
                    direction = filter.toDirectionName(),
                    searchPhone = searchPhone
                )
            }
        ).flow
    }

    suspend fun getTransactionsForExport(range: DateRange): List<TransactionWithCustomer> {
        return transactionDao.getTransactionsForExport(range.startAt, range.endAt)
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        if (transactionDao.exists(transaction.notificationKey, transaction.transactionReference)) return -1L
        val normalized = transaction.normalizedSender.ifBlank {
            TextNormalizer.normalize(transaction.senderName)
        }
        val linkedCustomerId = transaction.customerId ?: transactionDao.findIdentifier(
            inferIdentifierType(transaction.senderName).name,
            normalized
        )?.customerId

        return transactionDao.insertTransaction(
            transaction.copy(
                normalizedSender = normalized,
                customerId = linkedCustomerId
            )
        )
    }

    suspend fun createCustomerFromTransaction(transaction: TransactionEntity, displayName: String): Long {
        val now = System.currentTimeMillis()
        val customerId = transactionDao.insertCustomer(CustomerEntity(displayName = displayName.trim(), createdAt = now, updatedAt = now))
        addIdentifier(customerId, transaction.senderName)
        transactionDao.updateTransactionCustomer(transaction.id, customerId)
        transactionDao.updateTransactionsCustomerBySender(transaction.normalizedSender.ifBlank { TextNormalizer.normalize(transaction.senderName) }, customerId)
        return customerId
    }

    suspend fun createCustomerFromSender(sender: String, displayName: String): Long {
        val now = System.currentTimeMillis()
        val normalized = TextNormalizer.normalize(sender)
        val customerId = transactionDao.insertCustomer(CustomerEntity(displayName = displayName.trim(), createdAt = now, updatedAt = now))
        addIdentifier(customerId, sender)
        transactionDao.updateTransactionsCustomerBySender(normalized, customerId)
        return customerId
    }

    suspend fun linkTransactionToCustomer(transaction: TransactionEntity, customerId: Long) {
        addIdentifier(customerId, transaction.senderName)
        transactionDao.updateTransactionCustomer(transaction.id, customerId)
        transactionDao.updateTransactionsCustomerBySender(transaction.normalizedSender.ifBlank { TextNormalizer.normalize(transaction.senderName) }, customerId)
    }

    suspend fun addIdentifier(customerId: Long, value: String): Long {
        val normalized = TextNormalizer.normalize(value)
        val type = inferIdentifierType(value)
        return transactionDao.insertIdentifier(
            CustomerIdentifierEntity(
                customerId = customerId,
                type = type,
                value = value.trim(),
                normalizedValue = normalized,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateCustomerName(customerId: Long, displayName: String) {
        transactionDao.updateCustomerName(customerId, displayName.trim(), System.currentTimeMillis())
    }

    suspend fun unlinkCustomerAccount(customerId: Long, normalizedSender: String, walletSource: String) {
        transactionDao.deleteIdentifierByCustomerAndValue(customerId, normalizedSender)
        transactionDao.unlinkCustomerTransactionsBySource(customerId, normalizedSender, walletSource)
    }

    suspend fun deleteCustomer(customerId: Long) {
        transactionDao.unlinkAllCustomerTransactions(customerId)
        transactionDao.deleteIdentifiersForCustomer(customerId)
        transactionDao.deleteCustomer(customerId)
    }

    suspend fun updateDirection(
        transactionId: Long,
        direction: TransactionDirection,
        source: DirectionSource = DirectionSource.MANUAL
    ) {
        transactionDao.updateDirection(transactionId, direction.name, source.name)
    }

    suspend fun searchCustomers(query: String): List<CustomerEntity> {
        return transactionDao.searchCustomers(TextNormalizer.normalize(query))
    }

    suspend fun getBackupData(): BackupData {
        return BackupData(
            transactions = transactionDao.getAllTransactions(),
            customers = transactionDao.getAllCustomers(),
            identifiers = transactionDao.getAllIdentifiers()
        )
    }

    suspend fun replaceAllData(data: BackupData) {
        database.withTransaction {
            transactionDao.deleteAllTransactions()
            transactionDao.deleteAllIdentifiers()
            transactionDao.deleteAllCustomers()
            transactionDao.insertCustomersForRestore(data.customers)
            transactionDao.insertTransactionsForRestore(data.transactions)
            transactionDao.insertIdentifiersForRestore(data.identifiers)
        }
    }

    suspend fun getFirstTransactionTimestamp(): Long? = transactionDao.getFirstTransactionTimestamp()
    suspend fun getLastTransactionTimestamp(): Long? = transactionDao.getLastTransactionTimestamp()
    suspend fun hasTransactionsInRange(range: DateRange): Boolean {
        return transactionDao.countTransactionsInRange(range.startAt, range.endAt) > 0
    }
    
    suspend fun getTotalTransactionCount(): Int {
        return transactionDao.countTransactionsInRange(null, null)
    }

    fun rangeForPeriod(period: SummaryPeriod): DateRange {
        val todayStart = startOfDay()
        return when (period) {
            SummaryPeriod.DAILY -> DateRange(todayStart, null)
            SummaryPeriod.WEEKLY -> DateRange(todayStart - 6 * DAY_MS, null)
            SummaryPeriod.MONTHLY -> DateRange(todayStart - 29 * DAY_MS, null)
            SummaryPeriod.YEARLY -> DateRange(startOfYear(), null)
            SummaryPeriod.ALL -> DateRange(null, null)
        }
    }

    fun customDayRange(dayMillis: Long): DateRange {
        val start = calendarAt(dayMillis).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return DateRange(start, start + DAY_MS - 1)
    }

    fun customRange(startMillis: Long, endMillis: Long): DateRange {
        val start = customDayRange(startMillis).startAt
        val end = customDayRange(endMillis).endAt
        return DateRange(start, end)
    }

    private fun inferIdentifierType(value: String): IdentifierType {
        return if (dev.anonymous.transfers_ledger.core.PhoneNumberUtils.isPhoneNumber(TextNormalizer.normalizeDigits(value))) {
            IdentifierType.PHONE
        } else {
            IdentifierType.NAME
        }
    }

    private fun TransactionFilter.toDirectionName(): String? {
        return when (this) {
            TransactionFilter.ALL -> null
            TransactionFilter.INCOMING -> TransactionDirection.INCOMING.name
            TransactionFilter.OUTGOING -> TransactionDirection.OUTGOING.name
        }
    }

    private fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfYear(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun calendarAt(time: Long): Calendar = Calendar.getInstance().apply { timeInMillis = time }

    companion object {
        private const val PAGE_SIZE = 30
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

data class BackupData(
    val transactions: List<TransactionEntity>,
    val customers: List<CustomerEntity>,
    val identifiers: List<CustomerIdentifierEntity>
)
