package com.attentionstats.storage

import android.database.sqlite.SQLiteException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.driver.SQLCipherConnection
import java.util.concurrent.atomic.AtomicInteger

/** Never delete/retry a damaged generation and never log paths, SQL or key material. */
internal class OwnedCipherDriver(key: ByteArray) : SQLiteDriver {
    private val ownedKey = key.copyOf()
    private val active = AtomicInteger()
    private var retired = false
    private val errorHandler = object : DatabaseErrorHandler {
        override fun onCorruption(dbObj: SQLiteDatabase, exception: SQLiteException) {
            throw SQLiteException("Encrypted history unavailable; original files preserved")
        }
    }
    // SQLCipherConnection owns SQLiteDatabase's internal pool, as SQLCipherDriver does.
    override val hasConnectionPool: Boolean get() = true

    @Synchronized
    override fun open(fileName: String): SQLiteConnection {
        check(!retired)
        val nativeDatabase = SQLiteDatabase.openOrCreateDatabase(fileName, ownedKey, null, errorHandler, null)
        val connection = try {
            // A raw PRAGMA changes only the acquired handle. Configure the native pool too,
            // so newly created readers inherit enforcement rather than its default OFF.
            nativeDatabase.setForeignKeyConstraintsEnabled(true)
            SQLCipherConnection(nativeDatabase)
        } catch (failure: Throwable) {
            nativeDatabase.close()
            throw failure
        }
        active.incrementAndGet()
        return object : SQLiteConnection by connection {
            private var closed = false
            override fun prepare(sql: String): SQLiteStatement {
                // Room 3 configures NORMAL for WAL on every connection, before migrations.
                // Raise the primary writer's pinned framework command before migrations.
                // Native read-only pool handles can retain NORMAL; they cannot commit writes.
                val durableSql = if (sql.trim().equals("PRAGMA synchronous = NORMAL", ignoreCase = true))
                    "PRAGMA synchronous = FULL" else sql
                return connection.prepare(durableSql)
            }
            @Synchronized override fun close() {
                if (!closed) {
                    connection.close()
                    closed = true
                    active.decrementAndGet()
                }
            }
        }
    }

    @Synchronized fun retire() {
        check(active.get() == 0)
        ownedKey.fill(0)
        retired = true
    }
}
