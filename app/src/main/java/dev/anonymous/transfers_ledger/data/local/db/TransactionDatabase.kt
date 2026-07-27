package dev.anonymous.transfers_ledger.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CustomerEntity::class,
        CustomerIdentifierEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TransactionDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: TransactionDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN normalizedSender TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN customerId INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN direction TEXT NOT NULL DEFAULT 'INCOMING'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN directionSource TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("UPDATE transactions SET normalizedSender = lower(senderName) WHERE normalizedSender = ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        displayName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS customer_identifiers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        value TEXT NOT NULL,
                        normalizedValue TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_walletSource ON transactions(walletSource)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_direction ON transactions(direction)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_customerId ON transactions(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_normalizedSender ON transactions(normalizedSender)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_identifiers_customerId ON customer_identifiers(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_identifiers_normalizedValue ON customer_identifiers(normalizedValue)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customer_identifiers_type_normalizedValue ON customer_identifiers(type, normalizedValue)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE transactions
                    SET normalizedSender = replace(replace(replace(replace(lower(senderName), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا')
                    WHERE senderName IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE customer_identifiers
                    SET normalizedValue = replace(replace(replace(replace(lower(value), 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ٱ', 'ا')
                    WHERE value IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): TransactionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransactionDatabase::class.java,
                    "palpay_tracker_db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
