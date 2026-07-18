package ps.palpay.tracker

import android.app.Application
import ps.palpay.tracker.data.local.db.TransactionDatabase
import ps.palpay.tracker.data.local.pref.DataStoreManager
import ps.palpay.tracker.data.repository.TransactionRepository

class PalPayApplication : Application() {

    lateinit var repository: TransactionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = TransactionDatabase.getDatabase(this)
        val dataStoreManager = DataStoreManager(this)
        repository = TransactionRepository(database.transactionDao(), dataStoreManager)
    }
}
