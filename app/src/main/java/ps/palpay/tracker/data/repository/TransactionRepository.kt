package ps.palpay.tracker.data.repository

import kotlinx.coroutines.flow.Flow
import ps.palpay.tracker.data.local.db.TransactionDao
import ps.palpay.tracker.data.local.db.TransactionEntity
import ps.palpay.tracker.data.local.pref.DataStoreManager
import java.util.Calendar

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val dataStoreManager: DataStoreManager
) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    fun getTodayTransactions(): Flow<List<TransactionEntity>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return transactionDao.getTodayTransactions(calendar.timeInMillis)
    }

    suspend fun insertTransaction(transaction: TransactionEntity) {
        if (!transactionDao.exists(transaction.notificationKey, transaction.transactionReference)) {
            transactionDao.insertTransaction(transaction)
        }
    }

    val isTrackingEnabled: Flow<Boolean> = dataStoreManager.isTrackingEnabled
    suspend fun setTrackingEnabled(enabled: Boolean) = dataStoreManager.setTrackingEnabled(enabled)

    val isListenerConnected: Flow<Boolean> = dataStoreManager.isListenerConnected
    suspend fun setListenerConnected(connected: Boolean) = dataStoreManager.setListenerConnected(connected)
}
