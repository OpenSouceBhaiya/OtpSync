package com.opensourcebhaiya.otpsync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [OtpEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun otpDao(): OtpDao

    companion object {
        val insertMutex = kotlinx.coroutines.sync.Mutex()
        
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE otps ADD COLUMN sentToDevices TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Initialize SQLCipher libraries
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context.applicationContext)
                
                // Use a securely generated key (we can reuse the EncryptedSharedPreferences key approach or a secure random key)
                // For simplicity and to prevent data loss on every reboot, we need a persistent key.
                // We'll generate/retrieve it from EncryptedSharedPreferences securely.
                val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    "db_secure_prefs",
                    androidx.security.crypto.MasterKey.Builder(context)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                
                var dbPassphrase = prefs.getString("db_passphrase", null)
                if (dbPassphrase == null) {
                    dbPassphrase = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
                    prefs.edit().putString("db_passphrase", dbPassphrase).apply()
                }
                
                val supportFactory = net.sqlcipher.database.SupportFactory(dbPassphrase.toByteArray())

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "otp_syncer_database"
                )
                .openHelperFactory(supportFactory)
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
