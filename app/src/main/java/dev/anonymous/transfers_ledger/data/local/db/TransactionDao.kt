package dev.anonymous.transfers_ledger.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsForRestore(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomersForRestore(customers: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIdentifier(identifier: CustomerIdentifierEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentifiersForRestore(identifiers: List<CustomerIdentifierEntity>)

    @Query(
        """
        SELECT t.*, c.displayName AS customerDisplayName
        FROM transactions t
        LEFT JOIN customers c ON c.id = t.customerId
        WHERE (:direction IS NULL OR t.direction = :direction)
          AND (:startAt IS NULL OR t.timestamp >= :startAt)
          AND (:endAt IS NULL OR t.timestamp <= :endAt)
        ORDER BY t.timestamp DESC
        """
    )
    fun getPagedTransactions(
        direction: String?,
        startAt: Long?,
        endAt: Long?
    ): PagingSource<Int, TransactionWithCustomer>

    @Query(
        """
        SELECT
            c.id AS id,
            c.displayName AS displayName,
            c.createdAt AS createdAt,
            (SELECT COUNT(*) FROM customer_identifiers ci WHERE ci.customerId = c.id) AS accountCount,
            (SELECT COALESCE(SUM(t.amount), 0) FROM transactions t WHERE t.customerId = c.id AND t.direction = 'INCOMING') AS incomingTotal,
            (SELECT COALESCE(SUM(t.amount), 0) FROM transactions t WHERE t.customerId = c.id AND t.direction = 'OUTGOING') AS outgoingTotal
        FROM customers c
        WHERE :normalizedQuery = ''
           OR replace(replace(replace(replace(lower(c.displayName), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا') LIKE '%' || :normalizedQuery || '%'
        ORDER BY c.createdAt DESC
        """
    )
    fun getPagedCustomers(normalizedQuery: String): PagingSource<Int, CustomerSummary>

    @Query(
        """
        SELECT DISTINCT t.*, c.displayName AS customerDisplayName
        FROM transactions t
        LEFT JOIN customers c ON c.id = t.customerId
        LEFT JOIN customer_identifiers ci ON ci.customerId = c.id
        WHERE :normalizedQuery != ''
          AND (:direction IS NULL OR t.direction = :direction)
          AND (
            replace(replace(replace(replace(lower(c.displayName), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا') LIKE '%' || :normalizedQuery || '%'
            OR (
                ci.type != 'PHONE'
                AND replace(replace(replace(replace(lower(ci.normalizedValue), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا') LIKE '%' || :normalizedQuery || '%'
            )
            OR (
                :searchPhone = 1
                AND ci.type = 'PHONE'
                AND replace(replace(replace(replace(lower(ci.normalizedValue), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا') LIKE '%' || :normalizedQuery || '%'
            )
            OR (
                replace(replace(replace(replace(lower(t.normalizedSender), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا') LIKE '%' || :normalizedQuery || '%'
                AND (
                    :searchPhone = 1
                    OR (
                        t.normalizedSender NOT LIKE '05%' 
                        AND t.normalizedSender NOT LIKE '0097%' 
                        AND t.normalizedSender NOT LIKE '+97%' 
                        AND t.normalizedSender NOT LIKE '97%' 
                        AND t.normalizedSender NOT LIKE '25%'
                    )
                )
            )
            OR (:amount IS NOT NULL AND abs(t.amount - :amount) < 0.001)
          )
        ORDER BY t.timestamp DESC
        """
    )
    fun searchPagedTransactions(
        normalizedQuery: String,
        amount: Double?,
        direction: String?,
        searchPhone: Int
    ): PagingSource<Int, TransactionWithCustomer>

    @Query(
        """
        SELECT t.*, c.displayName AS customerDisplayName
        FROM transactions t
        LEFT JOIN customers c ON c.id = t.customerId
        WHERE (:startAt IS NULL OR t.timestamp >= :startAt)
          AND (:endAt IS NULL OR t.timestamp <= :endAt)
        ORDER BY t.timestamp DESC
        """
    )
    suspend fun getTransactionsForExport(startAt: Long?, endAt: Long?): List<TransactionWithCustomer>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:startAt IS NULL OR timestamp >= :startAt)
          AND (:endAt IS NULL OR timestamp <= :endAt)
        ORDER BY timestamp DESC
        """
    )
    fun getTransactionsForStats(startAt: Long?, endAt: Long?): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:startAt IS NULL OR timestamp >= :startAt)
          AND (:endAt IS NULL OR timestamp <= :endAt)
        ORDER BY timestamp DESC
        """
    )
    suspend fun getTransactionsForStatsOnce(startAt: Long?, endAt: Long?): List<TransactionEntity>

    @Query(
        """
        SELECT t.*, c.displayName AS customerDisplayName
        FROM transactions t
        LEFT JOIN customers c ON c.id = t.customerId
        WHERE t.direction = 'INCOMING'
          AND t.directionSource = 'AUTO'
          AND t.walletSource IN (:walletSources)
        ORDER BY t.timestamp DESC
        LIMIT 1
        """
    )
    fun getLatestConvertibleTransaction(walletSources: List<String>): Flow<TransactionWithCustomer?>

    @Query(
        """
        SELECT t.*, c.displayName AS customerDisplayName
        FROM transactions t
        LEFT JOIN customers c ON c.id = t.customerId
        WHERE t.customerId = :customerId
           OR t.normalizedSender IN (
                SELECT normalizedValue FROM customer_identifiers WHERE customerId = :customerId
           )
        ORDER BY t.timestamp DESC
        """
    )
    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionWithCustomer>>

    @Query(
        """
        SELECT t.*, c.displayName AS customerDisplayName
        FROM transactions t
        LEFT JOIN customers c ON c.id = t.customerId
        WHERE t.normalizedSender = :normalizedSender
        ORDER BY t.timestamp DESC
        """
    )
    fun getTransactionsForSender(normalizedSender: String): Flow<List<TransactionWithCustomer>>

    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM customers ORDER BY id ASC")
    suspend fun getAllCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM customer_identifiers ORDER BY id ASC")
    suspend fun getAllIdentifiers(): List<CustomerIdentifierEntity>

    @Query(
        """
        SELECT c.* FROM customers c
        INNER JOIN (
            SELECT MIN(id) AS id FROM customers
            WHERE :query = '' OR replace(replace(replace(replace(lower(displayName), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا') LIKE '%' || :query || '%'
            GROUP BY lower(displayName)
        ) uniqueCustomers ON uniqueCustomers.id = c.id
        ORDER BY c.displayName ASC
        LIMIT 50
        """
    )
    suspend fun searchCustomers(query: String): List<CustomerEntity>

    @Query("SELECT * FROM customer_identifiers WHERE type = :type AND normalizedValue = :normalizedValue LIMIT 1")
    suspend fun findIdentifier(type: String, normalizedValue: String): CustomerIdentifierEntity?

    @Query("UPDATE customers SET displayName = :displayName, updatedAt = :updatedAt WHERE id = :customerId")
    suspend fun updateCustomerName(customerId: Long, displayName: String, updatedAt: Long)

    @Query("DELETE FROM customer_identifiers WHERE customerId = :customerId AND normalizedValue = :normalizedSender")
    suspend fun deleteIdentifierByCustomerAndValue(customerId: Long, normalizedSender: String)

    @Query("DELETE FROM customer_identifiers WHERE customerId = :customerId")
    suspend fun deleteIdentifiersForCustomer(customerId: Long)

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteCustomer(customerId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM customer_identifiers")
    suspend fun deleteAllIdentifiers()

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Query("UPDATE transactions SET customerId = :customerId WHERE id = :transactionId")
    suspend fun updateTransactionCustomer(transactionId: Long, customerId: Long?)

    @Query("UPDATE transactions SET customerId = :customerId WHERE normalizedSender = :normalizedSender")
    suspend fun updateTransactionsCustomerBySender(normalizedSender: String, customerId: Long)

    @Query("UPDATE transactions SET customerId = NULL WHERE customerId = :customerId AND normalizedSender = :normalizedSender AND walletSource = :walletSource")
    suspend fun unlinkCustomerTransactionsBySource(customerId: Long, normalizedSender: String, walletSource: String)

    @Query("UPDATE transactions SET customerId = NULL WHERE customerId = :customerId")
    suspend fun unlinkAllCustomerTransactions(customerId: Long)

    @Query("UPDATE transactions SET direction = :direction, directionSource = :directionSource WHERE id = :transactionId")
    suspend fun updateDirection(transactionId: Long, direction: String, directionSource: String)

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE notificationKey = :key OR (transactionReference IS NOT NULL AND transactionReference = :ref))")
    suspend fun exists(key: String, ref: String?): Boolean

    @Query("SELECT MIN(timestamp) FROM transactions")
    suspend fun getFirstTransactionTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM transactions")
    suspend fun getLastTransactionTimestamp(): Long?

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE (:startAt IS NULL OR timestamp >= :startAt)
          AND (:endAt IS NULL OR timestamp <= :endAt)
        """
    )
    suspend fun countTransactionsInRange(startAt: Long?, endAt: Long?): Int
}
