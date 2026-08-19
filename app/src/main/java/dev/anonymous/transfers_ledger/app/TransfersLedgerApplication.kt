package dev.anonymous.transfers_ledger.app

import android.app.Application
import dev.anonymous.transfers_ledger.data.local.db.TransactionDatabase
import dev.anonymous.transfers_ledger.data.local.pref.DataStoreManager
import dev.anonymous.transfers_ledger.data.repository.TransactionRepository

class TransfersLedgerApplication : Application() {

    lateinit var repository: TransactionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = TransactionDatabase.getDatabase(this)
        val dataStoreManager = DataStoreManager(this)
        repository = TransactionRepository(database, dataStoreManager)
    }
}
