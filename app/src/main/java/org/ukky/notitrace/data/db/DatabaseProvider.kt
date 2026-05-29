package org.ukky.notitrace.data.db

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.ukky.notitrace.data.crypto.KeyStoreManager
import java.security.SecureRandom

/**
 * SQLCipher 暗号化付きの Room Database を提供する。
 *
 * 暗号鍵フロー:
 * 1. 初回: ランダムパスフレーズ生成 → Keystore で暗号化 → SharedPreferences に保存
 * 2. 2回目以降: SharedPreferences から取得 → Keystore で復号 → SQLCipher に渡す
 */
object DatabaseProvider {

    private const val PREFS_NAME = "notitrace_db_prefs"
    private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val PASSPHRASE_LENGTH = 32

    @Volatile
    private var INSTANCE: NotiTraceDatabase? = null

    fun getDatabase(context: Context): NotiTraceDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
        }
    }

    private fun buildDatabase(context: Context): NotiTraceDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = getOrCreatePassphrase(context)
        val factory: SupportSQLiteOpenHelper.Factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context.applicationContext,
            NotiTraceDatabase::class.java,
            "notitrace.db"
        )
            .openHelperFactory(factory)
            .addMigrations(
                NotiTraceDatabase.MIGRATION_1_2,
                NotiTraceDatabase.MIGRATION_2_3,
                NotiTraceDatabase.MIGRATION_1_3,
                NotiTraceDatabase.MIGRATION_3_4,
                NotiTraceDatabase.MIGRATION_4_5,
                NotiTraceDatabase.MIGRATION_5_6,
            )
            .build()
    }

    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)

        return if (stored != null) {
            val encrypted = Base64.decode(stored, Base64.NO_WRAP)
            KeyStoreManager.decrypt(encrypted)
        } else {
            val passphrase = ByteArray(PASSPHRASE_LENGTH).also {
                SecureRandom().nextBytes(it)
            }
            val encrypted = KeyStoreManager.encrypt(passphrase)
            prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
            passphrase
        }
    }
}
